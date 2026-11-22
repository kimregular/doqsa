package com.doqsa.backend.adapter.out.ocr.openai;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("doqsa.ocr.openai")
@Validated @Getter @Setter
public class OpenAiOcrProperties {
    @NotBlank private String apiKey;
    @NotBlank private String model = "gpt-4o-mini";
    @NotNull private URI baseUrl = URI.create("https://api.openai.com/v1");
    @NotNull private Duration timeout = Duration.ofSeconds(30);

    @PostConstruct
    public void validate() {
        if (baseUrl == null || !("https".equalsIgnoreCase(baseUrl.getScheme())
                || "http".equalsIgnoreCase(baseUrl.getScheme())) || baseUrl.getHost() == null
                || baseUrl.getRawUserInfo() != null || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null) {
            throw new IllegalArgumentException("OpenAi OCR base URL must be an HTTP(S) URL without credentials, query or fragment");
        }
        if (timeout == null || timeout.toMillis() < 1) throw new IllegalArgumentException("OpenAi OCR timeout must be positive");
    }
}
