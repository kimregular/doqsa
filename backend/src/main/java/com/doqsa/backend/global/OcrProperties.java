package com.doqsa.backend.global;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.UUID;

@Component
@ConfigurationProperties("doqsa.queue")
@Validated @Getter @Setter
public class OcrProperties {
    private boolean enabled = true;
    private boolean pollingEnabled = true;
    @NotBlank @Size(max = 128) private String owner = UUID.randomUUID().toString();
    @Min(1) private int concurrency = 2;
    @Min(1) private int maxAttempts = 4;
    @NotNull private Duration leaseDuration = Duration.ofSeconds(60);
    @NotNull private Duration heartbeatInterval = Duration.ofSeconds(10);
    @NotNull private Duration taskTimeout = Duration.ofMinutes(2);
    @NotNull private Duration externalCallTimeout = Duration.ofSeconds(30);
    @NotNull private Duration shutdownTimeout = Duration.ofSeconds(40);

    @PostConstruct
    public void validate() {
        for (var duration : new Duration[]{leaseDuration, heartbeatInterval, taskTimeout, externalCallTimeout, shutdownTimeout}) {
            if (duration == null || duration.toMillis() < 1) throw new IllegalArgumentException("Queue durations must be positive");
        }
        if (heartbeatInterval.compareTo(leaseDuration) >= 0 || externalCallTimeout.compareTo(taskTimeout) > 0
                || externalCallTimeout.toMillis() > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid queue timeouts");
    }
}
