package com.doqsa.backend.adapter.out.storage.supabase;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("doqsa.storage.supabase")
@Validated @Getter @Setter
public class SupabaseStorageProperties {
    @NotNull private URI endpoint;
    private URI publicEndpoint;
    @NotBlank private String bucket;
    @NotBlank private String accessKey;
    @NotBlank private String secretKey;
    @NotBlank private String region = "local";
    @NotNull private Duration urlValidity = Duration.ofMinutes(10);
    @NotNull private Duration callTimeout = Duration.ofSeconds(30);

    public URI presignEndpoint() { return publicEndpoint == null ? endpoint : publicEndpoint; }

    @PostConstruct
    public void validate() {
        requireEndpoint(endpoint);
        if (publicEndpoint != null) requireEndpoint(publicEndpoint);
        if (urlValidity == null || urlValidity.compareTo(Duration.ofSeconds(1)) < 0
                || urlValidity.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Supabase URL validity must be between 1 second and 7 days");
        }
        if (callTimeout == null || callTimeout.isNegative() || callTimeout.isZero()) {
            throw new IllegalArgumentException("Supabase call timeout must be positive");
        }
    }

    private static void requireEndpoint(URI endpoint) {
        if (endpoint == null || !("https".equalsIgnoreCase(endpoint.getScheme())
                || "http".equalsIgnoreCase(endpoint.getScheme())) || endpoint.getHost() == null
                || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException("Supabase endpoint must be an HTTP(S) URL without credentials, query or fragment");
        }
    }
}
