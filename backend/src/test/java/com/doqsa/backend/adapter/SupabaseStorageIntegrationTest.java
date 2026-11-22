package com.doqsa.backend.adapter;

import com.doqsa.backend.adapter.out.storage.*;
import com.doqsa.backend.application.port.out.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SUPABASE_STORAGE_TEST_ENDPOINT", matches = ".+")
class SupabaseStorageIntegrationTest {
    @Test void presignedPutHeadReadAndPresignedGetRoundTripThroughStorage() {
        var endpoint = System.getenv("SUPABASE_STORAGE_TEST_ENDPOINT");
        var bucket = System.getenv().getOrDefault("SUPABASE_STORAGE_TEST_BUCKET", "doqsa-images");
        new ApplicationContextRunner().withUserConfiguration(SupabaseStorageConfiguration.class, SupabaseImageStorage.class)
                .withPropertyValues("doqsa.storage.provider=supabase",
                        "doqsa.storage.supabase.endpoint=" + endpoint,
                        "doqsa.storage.supabase.public-endpoint=" + System.getenv().getOrDefault("SUPABASE_STORAGE_TEST_PUBLIC_ENDPOINT", endpoint),
                        "doqsa.storage.supabase.bucket=" + bucket,
                        "doqsa.storage.supabase.region=" + System.getenv().getOrDefault("SUPABASE_STORAGE_TEST_REGION", "local"),
                        "doqsa.storage.supabase.access-key=" + required("SUPABASE_STORAGE_TEST_ACCESS_KEY"),
                        "doqsa.storage.supabase.secret-key=" + required("SUPABASE_STORAGE_TEST_SECRET_KEY"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ImageStorage.class);
                    var storage = context.getBean(ImageStorage.class);
                    var upload = storage.issueUpload("receipt.png", "image/png");
                    try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                        assertThat(storage.exists(upload.storageKey())).isFalse();
                        byte[] image = "supabase-integration-image".getBytes(StandardCharsets.UTF_8);
                        var put = HttpRequest.newBuilder(URI.create(upload.url())).timeout(Duration.ofSeconds(10))
                                .header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(image)).build();
                        var putResponse = http.send(put, HttpResponse.BodyHandlers.ofString());
                        assertThat(putResponse.statusCode()).withFailMessage("PUT failed: %s %s", putResponse.statusCode(), putResponse.body())
                                .isBetween(200, 299);
                        assertThat(storage.exists(upload.storageKey())).isTrue();
                        assertThat(storage.read(upload.storageKey())).containsExactly(image);
                        var get = HttpRequest.newBuilder(URI.create(storage.readUrl(upload.storageKey())))
                                .timeout(Duration.ofSeconds(10)).GET().build();
                        var response = http.send(get, HttpResponse.BodyHandlers.ofByteArray());
                        assertThat(response.statusCode()).isEqualTo(200);
                        assertThat(response.body()).containsExactly(image);
                        var reissued = storage.reissueUpload(upload.storageKey(), "image/png");
                        var retry = HttpRequest.newBuilder(URI.create(reissued.url())).timeout(Duration.ofSeconds(10))
                                .header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(image)).build();
                        assertThat(http.send(retry, HttpResponse.BodyHandlers.discarding()).statusCode()).isBetween(200, 299);
                    } finally {
                        context.getBean("supabaseS3Client", S3Client.class)
                                .deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(upload.storageKey()).build());
                    }
                });
    }

    private static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing integration test variable: " + name);
        return value;
    }
}
