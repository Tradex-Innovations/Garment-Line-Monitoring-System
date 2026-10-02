package com.tradex.unionnorth.employee.dto;

import com.tradex.unionnorth.employee.domain.CadreStatus;
import com.tradex.unionnorth.employee.domain.EmploymentStatus;
import com.tradex.unionnorth.employee.domain.PayrollStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEmployeeRequest(
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 240) String displayName,
        @NotBlank @Size(max = 80) String identityNumber,
        @Email @Size(max = 180) String email,
        @Size(max = 40) String phone,
        EmploymentStatus employmentStatus,
        CadreStatus cadreStatus,
        PayrollStatus payrollStatus) {
}
