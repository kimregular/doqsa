package com.doqsa.backend.adapter.out.ocr.local;

import com.doqsa.backend.adapter.out.ocr.ReceiptExtraction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Offline OCR plugin. Active when {@code doqsa.ocr.provider=local}, never in the {@code prod} profile. */
@Configuration(proxyBeanMethods = false)
@Profile("!prod")
@ConditionalOnProperty(name = LocalOcrConfiguration.PROVIDER_PROPERTY, havingValue = LocalOcrConfiguration.PROVIDER)
@EnableConfigurationProperties(LocalOcrProperties.class)
public class LocalOcrConfiguration {
    public static final String PROVIDER_PROPERTY = "doqsa.ocr.provider";
    public static final String PROVIDER = "local";

    @Bean LocalOcrImageAnalyzer localOcrImageAnalyzer(LocalOcrProperties properties, ReceiptExtraction extraction)
            throws IOException {
        var json = properties.getResponseLocation().getContentAsString(StandardCharsets.UTF_8);
        return new LocalOcrImageAnalyzer(extraction.parse(json));
    }
}
