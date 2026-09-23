package kz.hackalem.wamigos.storage.adapter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.UUID;
import kz.hackalem.wamigos.config.StorageProperties;
import kz.hackalem.wamigos.error.StorageException;
import kz.hackalem.wamigos.storage.port.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalStorageAdapter implements StoragePort {

    private static final EnumSet<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final EnumSet<PosixFilePermission> PRIVATE_FILE_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final StorageProperties storageProperties;
    private Path root;

    @PostConstruct
    public void initialize() {
        try {
            root = storageProperties.root().toAbsolutePath().normalize();
            Files.createDirectories(root);
            setPermissions(root, PRIVATE_DIRECTORY_PERMISSIONS);
        } catch (IOException exception) {
            throw new StorageException("Не удалось подготовить файловое хранилище.", exception);
        }
    }

    @Override
    public String store(InputStream inputStream, String extension) {
        String key = UUID.randomUUID() + extension;
        Path target = resolvePath(key);
        try {
            Files.copy(inputStream, target);
            setPermissions(target, PRIVATE_FILE_PERMISSIONS);
            return key;
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new StorageException("Не удалось сохранить запись.", exception);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path source = resolvePath(storageKey);
        if (!Files.isRegularFile(source)) {
            throw new StorageException("Исходная запись недоступна в хранилище.");
        }
        return new FileSystemResource(source);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolvePath(storageKey));
        } catch (IOException exception) {
            throw new StorageException("Не удалось удалить запись.", exception);
        }
    }

    private void setPermissions(Path path, EnumSet<PosixFilePermission> permissions) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    private Path resolvePath(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Некорректный путь к записи.");
        }
        return resolved;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
