package com.doqsa.backend.adapter.out.storage.s3;

import com.doqsa.backend.application.model.OcrModels.Upload;
import com.doqsa.backend.application.port.out.ImageStorage;
import com.doqsa.backend.application.service.OcrExceptions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import java.time.Duration;
import java.util.UUID;

/** S3 presigned-URL image storage; registered by {@link S3StorageConfiguration}. */
public class S3ImageStorage implements ImageStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ImageStorage(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }
    @Override public Upload issueUpload(String fileName, String contentType) {
        return reissueUpload("images/" + UUID.randomUUID(), contentType);
    }
    @Override public Upload reissueUpload(String key, String contentType) {
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build()).build());
        return new Upload(key, signed.url().toString(), signed.expiration());
    }
    @Override public boolean exists(String key) {
        try { client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()); return true; }
        catch (S3Exception exception) {
            if (exception.statusCode() == 404) return false;
            throw new OcrExceptions.Retryable("S3 availability check failed", exception);
        }
    }
    @Override public byte[] read(String key) {
        try { return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray(); }
        catch (RuntimeException exception) { throw new OcrExceptions.Retryable("S3 image read failed", exception); }
    }
    @Override public String readUrl(String key) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build()).build()).url().toString();
    }
}
