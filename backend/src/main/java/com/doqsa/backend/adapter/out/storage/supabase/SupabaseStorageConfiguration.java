package com.doqsa.backend.adapter.out.storage.supabase;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "doqsa.storage.provider", havingValue = "supabase")
@EnableConfigurationProperties(SupabaseStorageProperties.class)
public class SupabaseStorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client supabaseS3Client(SupabaseStorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.getEndpoint())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .overrideConfiguration(config -> config.apiCallTimeout(properties.getCallTimeout())
                        .apiCallAttemptTimeout(properties.getCallTimeout()))
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner supabaseS3Presigner(SupabaseStorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.presignEndpoint())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true)
                        .checksumValidationEnabled(false).build())
                .build();
    }

    private StaticCredentialsProvider credentials(SupabaseStorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }
}
