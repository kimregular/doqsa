package com.doqsa.backend.adapter.out.ocr.local;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OCR-off plugin. Active when {@code doqsa.ocr.provider=none} or the property is absent. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = LocalOcrConfiguration.PROVIDER_PROPERTY, havingValue = DisabledOcrConfiguration.PROVIDER,
        matchIfMissing = true)
public class DisabledOcrConfiguration {
    public static final String PROVIDER = "none";

    @Bean DisabledOcrImageAnalyzer disabledOcrImageAnalyzer() { return new DisabledOcrImageAnalyzer(); }
}
