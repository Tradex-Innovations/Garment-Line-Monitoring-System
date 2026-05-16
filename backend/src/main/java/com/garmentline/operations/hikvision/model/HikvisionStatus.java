package com.garmentline.operations.hikvision.model;

import java.time.OffsetDateTime;

public record HikvisionStatus(
    boolean configured,
    boolean running,
    String baseUrl,
    String username,
    int pollIntervalSeconds,
    int lookbackMinutes,
    OffsetDateTime lastPollAt,
    OffsetDateTime lastSuccessAt,
    String lastError,
    HikvisionDeviceInfo deviceInfo,
    int eventCount,
    int matchedEventCount
) {
}
