package com.doqsa.backend.adapter.out.ocr.gemini;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("doqsa.ocr.gemini")
@Validated @Getter @Setter
public class GeminiOcrProperties {
    @NotBlank private String apiKey;
    @NotBlank private String model = "gemini-2.5-flash";
}
