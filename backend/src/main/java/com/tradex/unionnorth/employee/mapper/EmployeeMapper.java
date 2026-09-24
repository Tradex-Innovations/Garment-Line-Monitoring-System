package com.tradex.unionnorth.employee.mapper;

import com.tradex.unionnorth.employee.domain.CadreStatus;
import com.tradex.unionnorth.employee.domain.Employee;
import com.tradex.unionnorth.employee.domain.EmploymentStatus;
import com.tradex.unionnorth.employee.domain.PayrollStatus;
import com.tradex.unionnorth.employee.dto.CreateEmployeeRequest;
import com.tradex.unionnorth.employee.dto.EmployeeResponse;
import com.tradex.unionnorth.employee.dto.UpdateEmployeeRequest;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public Employee toEntity(CreateEmployeeRequest request) {
        Employee employee = new Employee();
        employee.setEmployeeNumber(trim(request.employeeNumber()));
        applyMutableFields(employee, request.firstName(), request.lastName(), request.displayName(), request.identityNumber(),
                request.email(), request.phone(), request.employmentStatus(), request.cadreStatus(), request.payrollStatus());
        return employee;
    }

    public void update(Employee employee, UpdateEmployeeRequest request) {
        applyMutableFields(employee, request.firstName(), request.lastName(), request.displayName(), request.identityNumber(),
                request.email(), request.phone(), request.employmentStatus(), request.cadreStatus(), request.payrollStatus());
    }

    public EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeNumber(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getDisplayName(),
                employee.getIdentityNumber(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getEmploymentStatus(),
                employee.getCadreStatus(),
                employee.getPayrollStatus(),
                employee.getCreatedAt(),
                employee.getUpdatedAt(),
                employee.getVersion());
    }

    private void applyMutableFields(
            Employee employee,
            String firstName,
            String lastName,
            String displayName,
            String identityNumber,
            String email,
            String phone,
            EmploymentStatus employmentStatus,
            CadreStatus cadreStatus,
            PayrollStatus payrollStatus) {
        employee.setFirstName(trim(firstName));
        employee.setLastName(trim(lastName));
        employee.setDisplayName(trim(displayName) == null ? buildDisplayName(firstName, lastName) : trim(displayName));
        employee.setIdentityNumber(trim(identityNumber));
        employee.setEmail(trim(email));
        employee.setPhone(trim(phone));
        employee.setEmploymentStatus(employmentStatus == null ? EmploymentStatus.ACTIVE : employmentStatus);
        employee.setCadreStatus(cadreStatus == null ? CadreStatus.ACTIVE : cadreStatus);
        employee.setPayrollStatus(payrollStatus == null ? PayrollStatus.ACTIVE : payrollStatus);
    }

    private String buildDisplayName(String firstName, String lastName) {
        return (trim(firstName) + " " + trim(lastName)).trim();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
