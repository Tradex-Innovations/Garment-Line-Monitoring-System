package com.tradex.unionnorth.employee.repository;

import com.tradex.unionnorth.employee.domain.EmploymentRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentRecordRepository extends JpaRepository<EmploymentRecord, UUID> {

    List<EmploymentRecord> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);
}
