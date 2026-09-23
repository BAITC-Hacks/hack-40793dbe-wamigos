package kz.hackalem.wamigos.storage.port;

import java.io.InputStream;
import org.springframework.core.io.Resource;

public interface StoragePort {

    String store(InputStream inputStream, String extension);

    Resource load(String storageKey);

    void delete(String storageKey);
}
