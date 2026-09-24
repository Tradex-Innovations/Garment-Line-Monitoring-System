package com.tradex.unionnorth.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.springframework.core.io.Resource;

public interface FileStorageService {

    StoredFile store(String originalFilename, String contentType, InputStream inputStream) throws IOException;

    Optional<Resource> load(String storageKey);

    void delete(String storageKey) throws IOException;
}
