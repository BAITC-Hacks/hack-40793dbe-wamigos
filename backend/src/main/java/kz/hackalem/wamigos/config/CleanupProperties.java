package kz.hackalem.wamigos.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.cleanup")
public record CleanupProperties(@NotNull Duration retention, @NotNull Duration interval) {
}
