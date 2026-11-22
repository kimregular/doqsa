package com.doqsa.backend.adapter.out.ocr.local;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("doqsa.ocr.local")
@Validated @Getter @Setter
public class LocalOcrProperties {
    /** JSON file in the receipt prompt's {@code {"detected_info": {...}}} format. */
    @NotNull private Resource responseLocation = new ClassPathResource("ocr/local-receipt.json");
}
