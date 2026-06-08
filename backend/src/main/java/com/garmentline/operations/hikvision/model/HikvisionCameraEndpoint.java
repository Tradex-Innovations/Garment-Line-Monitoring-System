package com.garmentline.operations.hikvision.model;

import java.time.OffsetDateTime;

public record HikvisionCameraEndpoint(
    String id,
    String name,
    String location,
    String baseUrl,
    boolean configured,
    OffsetDateTime lastPollAt,
    OffsetDateTime lastSuccessAt,
    String lastError,
    HikvisionDeviceInfo deviceInfo
) {
}
