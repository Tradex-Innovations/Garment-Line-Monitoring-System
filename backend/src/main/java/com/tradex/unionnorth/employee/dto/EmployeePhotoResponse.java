package com.tradex.unionnorth.employee.dto;

import java.time.Instant;
import java.util.UUID;

public record EmployeePhotoResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant updatedAt) {}
