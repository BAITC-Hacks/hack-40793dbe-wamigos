package kz.hackalem.wamigos.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.upload")
public record UploadProperties(@Positive Long maxBytes, @NotNull Duration maxDuration) {
}
