package kz.hackalem.wamigos.storage.port;

import java.io.InputStream;
import java.nio.file.Path;

public interface StoragePort {

    String store(InputStream inputStream, String extension);

    Path resolve(String storageKey);

    void delete(String storageKey);
}
