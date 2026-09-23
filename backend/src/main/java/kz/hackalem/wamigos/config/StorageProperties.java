package kz.hackalem.wamigos.config;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(@NotNull Path root) {
}
