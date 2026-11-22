package com.doqsa.backend.adapter.out.storage;

import com.doqsa.backend.global.OcrProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "doqsa.storage.provider", havingValue = "s3")
public class S3StorageConfiguration {
    @Bean S3Client s3Client(@Value("${doqsa.storage.region}") String region, OcrProperties properties) {
        return S3Client.builder().region(Region.of(region))
                .overrideConfiguration(config -> config.apiCallTimeout(properties.getExternalCallTimeout())
                        .apiCallAttemptTimeout(properties.getExternalCallTimeout())).build();
    }
    @Bean S3Presigner s3Presigner(@Value("${doqsa.storage.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
}
