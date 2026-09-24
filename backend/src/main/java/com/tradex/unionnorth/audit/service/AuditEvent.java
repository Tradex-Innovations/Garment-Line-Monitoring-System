package com.tradex.unionnorth.audit.service;

import java.util.UUID;

public record AuditEvent(
        String userId,
        UUID employeeId,
        String action,
        String entityType,
        UUID entityId,
        String oldValue,
        String newValue,
        String reason,
        String ipAddress) {
}
