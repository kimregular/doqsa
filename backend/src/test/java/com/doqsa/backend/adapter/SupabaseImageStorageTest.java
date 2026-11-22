package com.doqsa.backend.adapter;

import com.doqsa.backend.adapter.out.storage.*;
import com.doqsa.backend.application.service.OcrExceptions;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupabaseImageStorageTest {
    private final S3Client client = mock(S3Client.class);
    private S3Presigner presigner;
    private SupabaseImageStorage storage;

    @BeforeEach void setup() {
        var properties = new SupabaseStorageProperties();
        properties.setBucket("receipts");
        properties.setUrlValidity(Duration.ofMinutes(10));
        presigner = S3Presigner.builder().endpointOverride(URI.create("https://project.storage.supabase.co/storage/v1/s3"))
                .region(Region.of("ap-northeast-2"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access", "test-secret")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).checksumValidationEnabled(false).build())
                .build();
        storage = new SupabaseImageStorage(client, presigner, properties);
    }

    @AfterEach void close() { presigner.close(); }

    @Test void uploadAndReissuePreserveTheObjectKeyAndSignTheEndpointPath() {
        var before = Instant.now();
        var upload = storage.issueUpload("receipt.png", "image/png");
        assertThat(upload.storageKey()).matches("images/[a-f0-9-]{36}");
        var uri = URI.create(upload.url());
        assertThat(uri.getHost()).isEqualTo("project.storage.supabase.co");
        assertThat(uri.getPath()).isEqualTo("/storage/v1/s3/receipts/" + upload.storageKey());
        assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8))
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256", "X-Amz-Expires=600", "X-Amz-Signature=",
                        "test-access/", "/ap-northeast-2/s3/aws4_request");
        assertThat(upload.expiresAt()).isBetween(before.plusSeconds(599), Instant.now().plusSeconds(601));
        var reissued = storage.reissueUpload(upload.storageKey(), "image/png");
        assertThat(reissued.storageKey()).isEqualTo(upload.storageKey());
        assertThat(URI.create(reissued.url()).getPath()).isEqualTo(uri.getPath());
    }

    @Test void downloadUrlUsesTheSameBucketAndPresignedQuery() {
        var uri = URI.create(storage.readUrl("images/existing"));
        assertThat(uri.getPath()).isEqualTo("/storage/v1/s3/receipts/images/existing");
        assertThat(uri.getRawQuery()).contains("X-Amz-Signature=", "X-Amz-Expires=600");
        assertThat(uri.getRawQuery()).doesNotContain("test-secret");
    }

    @Test void headDistinguishesExistingMissingAndUnavailableObjects() {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        assertThat(storage.exists("images/test")).isTrue();
        verify(client).headObject(HeadObjectRequest.builder().bucket("receipts").key("images/test").build());
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThat(storage.exists("images/test")).isFalse();
        var unavailable = S3Exception.builder().statusCode(503).build();
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(unavailable);
        assertThatThrownBy(() -> storage.exists("images/test")).isInstanceOf(OcrExceptions.Retryable.class).hasCause(unavailable);
    }

    @Test void transportFailureIsNotReportedAsAMissingImage() {
        var exception = SdkClientException.create("connection refused");
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(exception);
        assertThatThrownBy(() -> storage.exists("images/test")).isInstanceOf(OcrExceptions.Retryable.class).hasCause(exception);
    }

    @Test void readsTheConfiguredBucketAndWrapsReadFailures() {
        byte[] image = {1, 2, 3};
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), image));
        assertThat(storage.read("images/test")).containsExactly(image);
        verify(client).getObjectAsBytes(GetObjectRequest.builder().bucket("receipts").key("images/test").build());
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(SdkClientException.create("unavailable"));
        assertThatThrownBy(() -> storage.read("images/test")).isInstanceOf(OcrExceptions.Retryable.class);
    }
}
