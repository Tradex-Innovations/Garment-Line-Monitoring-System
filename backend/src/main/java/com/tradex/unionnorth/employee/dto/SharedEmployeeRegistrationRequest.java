package com.tradex.unionnorth.employee.dto;

import com.tradex.unionnorth.security.domain.WorkforceGroup;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Shared employee master data captured once by HR, before payroll setup. */
public record SharedEmployeeRegistrationRequest(
        @NotBlank @Pattern(regexp = "permanent|new_joiner|intern") String employeeType,
        @NotNull WorkforceGroup workforceGroup,
        @NotBlank @Size(max = 50) String employeeNumber,
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 240) String displayName,
        @NotBlank @Size(max = 80) String identityNumber,
        @Email @Size(max = 180) String email,
        @Size(max = 40) String phone,
        @NotNull UUID departmentId,
        @NotNull UUID designationId,
        @NotNull LocalDate joinedDate,
        String gender,
        LocalDate dateOfBirth,
        @Size(max = 500) String residentialAddress,
        @Size(max = 120) String emergencyName,
        @Size(max = 40) String emergencyPhone,
        @Size(max = 100) String emergencyRelationship,
        @Size(max = 120) String bankName,
        @Size(max = 120) String bankBranch,
        @Size(max = 80) String bankAccountNumber,
        String payrollCategory,
        String directIndirectStatus,
        BigDecimal basicSalary,
        Boolean overtimePaid,
        Boolean attendanceBonusEligible) {}
