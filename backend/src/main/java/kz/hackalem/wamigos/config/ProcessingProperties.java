package kz.hackalem.wamigos.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.processing")
public record ProcessingProperties(
        @NotNull Duration timeout,
        @NotNull Duration timeoutCheckInterval,
        @NotNull Boolean startupRecoveryEnabled
) {
}
