package com.doqsa.backend.adapter.out.ocr.grok;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("doqsa.ocr.grok")
@Validated @Getter @Setter
public class GrokOcrProperties {
    @NotBlank private String apiKey;
    @NotBlank private String model = "grok-2-vision-1212";
    @NotNull private URI baseUrl = URI.create("https://api.x.ai/v1");
    @NotNull private Duration timeout = Duration.ofSeconds(30);

    @PostConstruct
    public void validate() {
        if (baseUrl == null || !("https".equalsIgnoreCase(baseUrl.getScheme())
                || "http".equalsIgnoreCase(baseUrl.getScheme())) || baseUrl.getHost() == null
                || baseUrl.getRawUserInfo() != null || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null) {
            throw new IllegalArgumentException("Grok OCR base URL must be an HTTP(S) URL without credentials, query or fragment");
        }
        if (timeout == null || timeout.toMillis() < 1) throw new IllegalArgumentException("Grok OCR timeout must be positive");
    }
}
