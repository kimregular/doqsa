package com.doqsa.backend.adapter.out.storage.s3;

import com.doqsa.backend.application.service.OcrExceptions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class S3ImageStorageTest {
    private final S3Client client = mock(S3Client.class);
    private final S3ImageStorage storage = new S3ImageStorage(client, mock(S3Presigner.class), "test-bucket");

    @Test void missingObjectIsNotAnInfrastructureError() {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThat(storage.exists("images/test")).isFalse();
    }
    @Test void s3OutageIsRetryableAndNotReportedAsMissing() {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        assertThatThrownBy(() -> storage.exists("images/test")).isInstanceOf(OcrExceptions.Retryable.class);
    }
}
