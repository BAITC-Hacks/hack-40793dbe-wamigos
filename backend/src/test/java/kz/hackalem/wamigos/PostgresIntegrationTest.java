package kz.hackalem.wamigos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class PostgresIntegrationTest {

    private static final Path STORAGE_ROOT = createStorageRoot();
    protected static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("app.storage.root", () -> STORAGE_ROOT.toString());
        registry.add("app.processing.startup-recovery-enabled", () -> false);
        registry.add("app.cleanup.interval", () -> "PT1H");
        registry.add("app.processing.timeout-check-interval", () -> "PT1H");
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("wamigos-integration-storage-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
