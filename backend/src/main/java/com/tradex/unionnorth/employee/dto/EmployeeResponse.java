package com.tradex.unionnorth.employee.dto;

import com.tradex.unionnorth.employee.domain.CadreStatus;
import com.tradex.unionnorth.employee.domain.EmploymentStatus;
import com.tradex.unionnorth.employee.domain.PayrollStatus;
import java.time.Instant;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String employeeNumber,
        String firstName,
        String lastName,
        String displayName,
        String identityNumber,
        String email,
        String phone,
        EmploymentStatus employmentStatus,
        CadreStatus cadreStatus,
        PayrollStatus payrollStatus,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
