package com.doqsa.backend.adapter.out.storage.s3;

import com.doqsa.backend.global.OcrProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Amazon S3 storage plugin. Active when {@code doqsa.storage.provider=s3}. */
@Configuration
@ConditionalOnProperty(name = S3StorageConfiguration.PROVIDER_PROPERTY, havingValue = S3StorageConfiguration.PROVIDER)
public class S3StorageConfiguration {
    public static final String PROVIDER_PROPERTY = "doqsa.storage.provider";
    public static final String PROVIDER = "s3";

    @Bean S3Client s3Client(@Value("${doqsa.storage.region}") String region, OcrProperties properties) {
        return S3Client.builder().region(Region.of(region))
                .overrideConfiguration(config -> config.apiCallTimeout(properties.getExternalCallTimeout())
                        .apiCallAttemptTimeout(properties.getExternalCallTimeout())).build();
    }
    @Bean S3Presigner s3Presigner(@Value("${doqsa.storage.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
    @Bean S3ImageStorage s3ImageStorage(S3Client client, S3Presigner presigner,
                                        @Value("${doqsa.storage.bucket}") String bucket) {
        return new S3ImageStorage(client, presigner, bucket);
    }
}
