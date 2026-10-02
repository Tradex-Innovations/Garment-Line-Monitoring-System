package com.tradex.unionnorth.employee.dto;

import com.tradex.unionnorth.employee.domain.CadreStatus;
import com.tradex.unionnorth.employee.domain.EmploymentStatus;
import com.tradex.unionnorth.employee.domain.PayrollStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEmployeeRequest(
        @NotBlank @Size(max = 50) String employeeNumber,
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 240) String displayName,
        @NotBlank @Size(max = 80) String identityNumber,
        @Email @Size(max = 180) String email,
        @Size(max = 40) String phone,
        EmploymentStatus employmentStatus,
        CadreStatus cadreStatus,
        PayrollStatus payrollStatus,
        @Size(max = 50) String lineMatrixEmployeeNumber,
        UUID departmentId,
        UUID designationId,
        LocalDate joinedDate) {
    public CreateEmployeeRequest(String employeeNumber,String firstName,String lastName,String displayName,String identityNumber,
            String email,String phone,EmploymentStatus employmentStatus,CadreStatus cadreStatus,PayrollStatus payrollStatus) {
        this(employeeNumber,firstName,lastName,displayName,identityNumber,email,phone,employmentStatus,cadreStatus,payrollStatus,null,null,null,null);
    }

    public CreateEmployeeRequest(String employeeNumber,String firstName,String lastName,String displayName,String identityNumber,
            String email,String phone,EmploymentStatus employmentStatus,CadreStatus cadreStatus,PayrollStatus payrollStatus,
            String lineMatrixEmployeeNumber) {
        this(employeeNumber,firstName,lastName,displayName,identityNumber,email,phone,employmentStatus,cadreStatus,payrollStatus,
                lineMatrixEmployeeNumber,null,null,null);
    }
}
