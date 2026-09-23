package kz.hackalem.wamigos.config;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.export")
public record ExportProperties(@NotNull Path fontPath) {
}
