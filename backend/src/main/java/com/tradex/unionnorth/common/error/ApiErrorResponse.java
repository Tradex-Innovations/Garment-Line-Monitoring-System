package com.tradex.unionnorth.common.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String path,
        Map<String, Object> details) {
}
