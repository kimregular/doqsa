package com.doqsa.backend.adapter.out.storage.supabase;

import com.doqsa.backend.application.model.OcrModels.Upload;
import com.doqsa.backend.application.port.out.ImageStorage;
import com.doqsa.backend.application.service.OcrExceptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "doqsa.storage.provider", havingValue = "supabase")
public class SupabaseImageStorage implements ImageStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final SupabaseStorageProperties properties;

    public SupabaseImageStorage(@Qualifier("supabaseS3Client") S3Client client,
                                @Qualifier("supabaseS3Presigner") S3Presigner presigner,
                                SupabaseStorageProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override public Upload issueUpload(String fileName, String contentType) {
        return reissueUpload("images/" + UUID.randomUUID(), contentType);
    }

    @Override public Upload reissueUpload(String key, String contentType) {
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.getUrlValidity())
                .putObjectRequest(PutObjectRequest.builder().bucket(properties.getBucket()).key(key)
                        .contentType(contentType).build()).build());
        return new Upload(key, signed.url().toString(), signed.expiration());
    }

    @Override public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return false;
            throw new OcrExceptions.Retryable("Supabase availability check failed", exception);
        } catch (RuntimeException exception) {
            throw new OcrExceptions.Retryable("Supabase availability check failed", exception);
        }
    }

    @Override public byte[] read(String key) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build()).asByteArray();
        } catch (RuntimeException exception) {
            throw new OcrExceptions.Retryable("Supabase image read failed", exception);
        }
    }

    @Override public String readUrl(String key) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.getUrlValidity())
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build()).build())
                .url().toString();
    }
}
