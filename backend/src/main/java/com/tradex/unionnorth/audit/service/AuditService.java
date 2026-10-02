package com.tradex.unionnorth.audit.service;

import com.tradex.unionnorth.audit.domain.AuditLog;
import com.tradex.unionnorth.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        AuditLog log = new AuditLog();
        log.setUserId(event.userId());
        log.setEmployeeId(event.employeeId());
        log.setAction(event.action());
        log.setEntityType(event.entityType());
        log.setEntityId(event.entityId());
        log.setOldValue(event.oldValue());
        log.setNewValue(event.newValue());
        log.setReason(event.reason());
        log.setIpAddress(event.ipAddress());
        auditLogRepository.save(log);
    }
}
