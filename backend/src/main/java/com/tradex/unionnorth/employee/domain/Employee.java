package com.tradex.unionnorth.employee.domain;

import com.tradex.unionnorth.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "employees",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_employees_employee_number", columnNames = "employee_number"),
            @UniqueConstraint(name = "uk_employees_identity_number", columnNames = "identity_number"),
            @UniqueConstraint(name = "uk_employees_email", columnNames = "email")
        })
public class Employee extends AuditableEntity {

    @Column(name = "employee_number", nullable = false, length = 50)
    private String employeeNumber;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(name = "display_name", nullable = false, length = 240)
    private String displayName;

    @Column(name = "identity_number", nullable = false, length = 80)
    private String identityNumber;

    @Column(length = 180)
    private String email;

    @Column(length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 40)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "cadre_status", nullable = false, length = 40)
    private CadreStatus cadreStatus = CadreStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "payroll_status", nullable = false, length = 40)
    private PayrollStatus payrollStatus = PayrollStatus.ACTIVE;

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public CadreStatus getCadreStatus() {
        return cadreStatus;
    }

    public void setCadreStatus(CadreStatus cadreStatus) {
        this.cadreStatus = cadreStatus;
    }

    public PayrollStatus getPayrollStatus() {
        return payrollStatus;
    }

    public void setPayrollStatus(PayrollStatus payrollStatus) {
        this.payrollStatus = payrollStatus;
    }
}
