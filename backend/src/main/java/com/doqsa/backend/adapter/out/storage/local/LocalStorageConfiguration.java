package com.doqsa.backend.adapter.out.storage.local;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import java.io.IOException;

/** Local filesystem storage plugin. Active by default outside the {@code prod} profile. */
@Configuration
@Profile("!prod")
@ConditionalOnProperty(name = LocalStorageConfiguration.PROVIDER_PROPERTY,
        havingValue = LocalStorageConfiguration.PROVIDER, matchIfMissing = true)
public class LocalStorageConfiguration {
    public static final String PROVIDER_PROPERTY = "doqsa.storage.provider";
    public static final String PROVIDER = "local";

    @Bean LocalImageStorage localImageStorage(
            @Value("${doqsa.storage.local-directory:./data/images}") String directory) throws IOException {
        return new LocalImageStorage(directory);
    }
}
