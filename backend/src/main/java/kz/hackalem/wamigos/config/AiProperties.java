package kz.hackalem.wamigos.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.ai")
public record AiProperties(
        @NotBlank String mode,
        @NotNull Duration mockDelay,
        @NotNull Boolean mockForceFailure
) {
}
