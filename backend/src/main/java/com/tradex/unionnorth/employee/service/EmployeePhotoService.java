package com.tradex.unionnorth.employee.service;

import com.tradex.unionnorth.common.storage.FileMetadata;
import com.tradex.unionnorth.common.storage.FileMetadataRepository;
import com.tradex.unionnorth.common.storage.FileStorageService;
import com.tradex.unionnorth.common.storage.StoredFile;
import com.tradex.unionnorth.employee.dto.EmployeePhotoResponse;
import com.tradex.unionnorth.employee.repository.EmployeeRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EmployeePhotoService {

    private static final String ENTITY_TYPE = "EMPLOYEE_PHOTO";
    private static final long MAXIMUM_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final int MAXIMUM_DIMENSION = 8_000;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png");

    public record EmployeePhoto(EmployeePhotoResponse metadata, Resource resource) {}

    private final EmployeeRepository employees;
    private final FileMetadataRepository metadata;
    private final FileStorageService storage;

    public EmployeePhotoService(
            EmployeeRepository employees,
            FileMetadataRepository metadata,
            FileStorageService storage) {
        this.employees = employees;
        this.metadata = metadata;
        this.storage = storage;
    }

    @Transactional
    public EmployeePhotoResponse upload(UUID employeeId, MultipartFile file) {
        requireEmployee(employeeId);
        byte[] bytes = validate(file);
        StoredFile stored;
        try {
            stored = storage.store(
                    safeFilename(file.getOriginalFilename(), ALLOWED_TYPES.get(file.getContentType())),
                    file.getContentType(),
                    new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            throw new EmployeePhotoException(
                    "EMPLOYEE_PHOTO_STORAGE_FAILED",
                    "Unable to store employee photo",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        FileMetadata saved;
        try {
            FileMetadata value = new FileMetadata();
            value.setOriginalFilename(stored.originalFilename());
            value.setStorageKey(stored.storageKey());
            value.setContentType(stored.contentType());
            value.setSizeBytes(stored.sizeBytes());
            value.setChecksum(checksum(bytes));
            value.setRelatedEntityType(ENTITY_TYPE);
            value.setRelatedEntityId(employeeId);
            saved = metadata.saveAndFlush(value);
        } catch (RuntimeException exception) {
            deleteStored(stored.storageKey());
            throw exception;
        }

        List<FileMetadata> previous = records(employeeId).stream()
                .filter(value -> !value.getId().equals(saved.getId()))
                .toList();
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            registerFileCleanup(stored.storageKey(), previous);
        }
        try {
            previous.forEach(metadata::delete);
        } catch (RuntimeException exception) {
            if (!synchronizedTransaction) deleteStored(stored.storageKey());
            throw exception;
        }
        if (!synchronizedTransaction)
            previous.forEach(value -> deleteStored(value.getStorageKey()));
        return response(saved);
    }

    @Transactional(readOnly = true)
    public EmployeePhoto load(UUID employeeId) {
        requireEmployee(employeeId);
        FileMetadata value = records(employeeId).stream()
                .findFirst()
                .orElseThrow(() -> new EmployeePhotoException(
                        "EMPLOYEE_PHOTO_NOT_FOUND", "Employee photo not found", HttpStatus.NOT_FOUND));
        Resource resource = storage.load(value.getStorageKey())
                .orElseThrow(() -> new EmployeePhotoException(
                        "EMPLOYEE_PHOTO_NOT_FOUND", "Employee photo not found", HttpStatus.NOT_FOUND));
        return new EmployeePhoto(response(value), resource);
    }

    private List<FileMetadata> records(UUID employeeId) {
        return metadata.findByRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
                ENTITY_TYPE, employeeId);
    }

    private void requireEmployee(UUID employeeId) {
        if (!employees.existsById(employeeId)) throw new EmployeeNotFoundException(employeeId);
    }

    private byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new EmployeePhotoException(
                    "EMPLOYEE_PHOTO_REQUIRED", "Choose an employee photo", HttpStatus.BAD_REQUEST);
        if (!ALLOWED_TYPES.containsKey(file.getContentType()))
            throw new EmployeePhotoException(
                    "EMPLOYEE_PHOTO_TYPE_INVALID",
                    "Employee photo must be a JPEG or PNG image",
                    HttpStatus.BAD_REQUEST);
        if (file.getSize() > MAXIMUM_SIZE_BYTES)
            throw new EmployeePhotoException(
                    "EMPLOYEE_PHOTO_TOO_LARGE",
                    "Employee photo must not exceed 5 MB",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        try {
            byte[] bytes = file.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null)
                throw new EmployeePhotoException(
                        "EMPLOYEE_PHOTO_INVALID", "Employee photo is not a valid image", HttpStatus.BAD_REQUEST);
            if (image.getWidth() > MAXIMUM_DIMENSION || image.getHeight() > MAXIMUM_DIMENSION)
                throw new EmployeePhotoException(
                        "EMPLOYEE_PHOTO_DIMENSIONS_INVALID",
                        "Employee photo dimensions are too large",
                        HttpStatus.BAD_REQUEST);
            return bytes;
        } catch (IOException exception) {
            throw new EmployeePhotoException(
                    "EMPLOYEE_PHOTO_INVALID", "Employee photo could not be read", HttpStatus.BAD_REQUEST);
        }
    }

    private String safeFilename(String original, String extension) {
        String value = original == null ? "employee-photo" : original.trim();
        if (value.isEmpty()) value = "employee-photo";
        int dot = value.lastIndexOf('.');
        String base = dot > 0 ? value.substring(0, dot) : value;
        if (base.length() > 120) base = base.substring(0, 120);
        return base + "." + extension;
    }

    private String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void deleteStored(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (IOException ignored) {
            // The new photo remains authoritative; orphan cleanup can retry later.
        }
    }

    private void registerFileCleanup(String currentStorageKey, List<FileMetadata> previous) {
        List<String> previousStorageKeys =
                previous.stream().map(FileMetadata::getStorageKey).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                previousStorageKeys.forEach(EmployeePhotoService.this::deleteStored);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED)
                    deleteStored(currentStorageKey);
            }
        });
    }

    private EmployeePhotoResponse response(FileMetadata value) {
        return new EmployeePhotoResponse(
                value.getId(),
                value.getOriginalFilename(),
                value.getContentType(),
                value.getSizeBytes(),
                value.getUpdatedAt());
    }
}
