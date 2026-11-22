package com.doqsa.backend.adapter;

import com.doqsa.backend.adapter.out.storage.*;
import com.doqsa.backend.application.port.out.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.net.URI;
import static org.assertj.core.api.Assertions.*;

class SupabaseStorageConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SupabaseStorageConfiguration.class, SupabaseImageStorage.class);

    private ApplicationContextRunner selected() {
        return runner.withPropertyValues("doqsa.storage.provider=supabase",
                "doqsa.storage.supabase.endpoint=http://storage-internal:5000/s3",
                "doqsa.storage.supabase.bucket=receipts", "doqsa.storage.supabase.access-key=test-access",
                "doqsa.storage.supabase.secret-key=test-secret");
    }

    @Test void disabledProviderNeedsNoCredentialsAndRegistersNoSupabaseBeans() {
        runner.withPropertyValues("doqsa.storage.provider=local").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(SupabaseImageStorage.class)
                    .doesNotHaveBean(SupabaseStorageProperties.class).doesNotHaveBean(S3Client.class)
                    .doesNotHaveBean(S3Presigner.class);
        });
    }

    @Test void selectedProviderUsesAnInternalClientAndAnIndependentPublicSigningEndpoint() {
        selected().withPropertyValues("doqsa.storage.supabase.public-endpoint=http://localhost:5000/s3").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ImageStorage.class);
            assertThat(context.getBean(ImageStorage.class)).isInstanceOf(SupabaseImageStorage.class);
            var client = context.getBean("supabaseS3Client", S3Client.class);
            assertThat(client.serviceClientConfiguration().endpointOverride()).contains(URI.create("http://storage-internal:5000/s3"));
            assertThat(URI.create(context.getBean(ImageStorage.class).readUrl("images/test")).getHost()).isEqualTo("localhost");
        });
    }

    @Test void publicEndpointDefaultsToTheApiEndpoint() {
        selected().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(URI.create(context.getBean(ImageStorage.class).readUrl("images/test")).getHost()).isEqualTo("storage-internal");
        });
    }

    @Test void selectedProviderFailsFastWithoutCredentials() {
        runner.withPropertyValues("doqsa.storage.provider=supabase",
                "doqsa.storage.supabase.endpoint=http://localhost:5000/s3",
                "doqsa.storage.supabase.bucket=receipts").run(context -> assertThat(context).hasFailed());
    }

    @Test void invalidEndpointAndSigningDurationFailAtStartup() {
        selected().withPropertyValues("doqsa.storage.supabase.endpoint=file:///tmp/storage")
                .run(context -> assertThat(context).hasFailed());
        selected().withPropertyValues("doqsa.storage.supabase.public-endpoint=http://localhost:5000/s3?token=secret")
                .run(context -> assertThat(context).hasFailed());
        selected().withPropertyValues("doqsa.storage.supabase.url-validity=8d")
                .run(context -> assertThat(context).hasFailed());
        selected().withPropertyValues("doqsa.storage.supabase.call-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }
}
