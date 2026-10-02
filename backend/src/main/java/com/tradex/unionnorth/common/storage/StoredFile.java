package com.tradex.unionnorth.common.storage;

public record StoredFile(
        String storageKey,
        String originalFilename,
        String contentType,
        long sizeBytes) {
}
