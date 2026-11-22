package com.doqsa.backend.adapter.out.storage;

import com.doqsa.backend.adapter.out.storage.local.*;
import com.doqsa.backend.adapter.out.storage.s3.*;
import com.doqsa.backend.adapter.out.storage.supabase.*;
import com.doqsa.backend.application.port.out.ImageStorage;
import com.doqsa.backend.global.OcrProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class StoragePluginSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(OcrProperties.class)
            .withUserConfiguration(LocalStorageConfiguration.class, S3StorageConfiguration.class,
                    SupabaseStorageConfiguration.class, SupabaseImageStorage.class)
            .withPropertyValues("doqsa.storage.local-directory=./build/test-images/plugin",
                    "doqsa.storage.region=ap-northeast-2", "doqsa.storage.bucket=test-bucket",
                    "doqsa.storage.supabase.endpoint=http://localhost:54321/storage/v1/s3",
                    "doqsa.storage.supabase.bucket=test-bucket",
                    "doqsa.storage.supabase.access-key=test-access",
                    "doqsa.storage.supabase.secret-key=test-secret");

    @Test void localPluginIsTheDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(ImageStorage.class)
                .getBean(ImageStorage.class).isInstanceOf(LocalImageStorage.class));
    }
    @Test void providerPropertySwapsInTheS3Plugin() {
        runner.withPropertyValues("doqsa.storage.provider=s3").run(context -> assertThat(context)
                .hasSingleBean(ImageStorage.class).doesNotHaveBean(LocalImageStorage.class)
                .getBean(ImageStorage.class).isInstanceOf(S3ImageStorage.class));
    }
    @Test void providerPropertySwapsInTheSupabasePlugin() {
        runner.withPropertyValues("doqsa.storage.provider=supabase").run(context -> assertThat(context)
                .hasSingleBean(ImageStorage.class).doesNotHaveBean(LocalImageStorage.class)
                .getBean(ImageStorage.class).isInstanceOf(SupabaseImageStorage.class));
    }
    @Test void unknownProviderRegistersNoStorage() {
        runner.withPropertyValues("doqsa.storage.provider=unknown")
                .run(context -> assertThat(context).doesNotHaveBean(ImageStorage.class));
    }
}
