package com.tradex.unionnorth.audit.repository;

import com.tradex.unionnorth.audit.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
