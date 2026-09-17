package com.garmentline.operations.hikvision.model;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record HikvisionBridgePushRequest(
    @NotBlank String cameraId,
    @NotBlank String cameraName,
    String cameraLocation,
    @NotBlank String cameraBaseUrl,
    OffsetDateTime polledAt,
    HikvisionDeviceInfo deviceInfo,
    List<Map<String, Object>> events
) {
}
