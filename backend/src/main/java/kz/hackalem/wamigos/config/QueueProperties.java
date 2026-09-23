package kz.hackalem.wamigos.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.queue")
public record QueueProperties(@Positive Integer capacity, @NotNull Duration pollInterval) {
}
