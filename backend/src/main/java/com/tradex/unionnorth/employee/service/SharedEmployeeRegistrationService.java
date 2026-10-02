package com.tradex.unionnorth.employee.service;

import com.tradex.unionnorth.employee.dto.SharedEmployeeRegistrationRequest;
import com.tradex.unionnorth.security.WorkforceAccess;
import com.tradex.unionnorth.security.domain.WorkforceGroup;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedEmployeeRegistrationService {
    public record Option(UUID id, String name) {}
    public record Options(List<Option> departments, List<Option> designations, List<WorkforceGroup> groups) {}
    public record Result(UUID sourceId, String employeeNumber, String employeeType, WorkforceGroup workforceGroup,
                         boolean payrollEligible) {}

    private final JdbcTemplate jdbc;
    private final WorkforceAccess access;

    public SharedEmployeeRegistrationService(JdbcTemplate jdbc, WorkforceAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Options options() {
        var departments = jdbc.query("SELECT id,name FROM public.departments WHERE is_active=true ORDER BY name",
                (row, index) -> new Option(row.getObject("id", UUID.class), row.getString("name")));
        var designations = jdbc.query("SELECT id,name FROM public.designations WHERE is_active=true ORDER BY name",
                (row, index) -> new Option(row.getObject("id", UUID.class), row.getString("name")));
        return new Options(departments, designations, access.groups(WorkforceAccess.Action.EDIT));
    }

    @Transactional
    public Result register(SharedEmployeeRegistrationRequest request, UUID actorId) {
        access.require(request.workforceGroup(), WorkforceAccess.Action.EDIT);
        String number = request.employeeNumber().trim().replaceAll("\\s+", "");
        if (number.isEmpty() || number.length() > 50)
            throw badRequest("Enter an employee number up to 50 characters.");
        if (request.employeeType().equals("new_joiner") && !number.startsWith("101"))
            throw badRequest("New joiner numbers must start with 101.");
        if (request.employeeType().equals("intern") && !number.startsWith("303"))
            throw badRequest("Intern numbers must start with 303.");
        if (request.employeeType().equals("permanent") && (number.startsWith("101") || number.startsWith("303")))
            throw badRequest("Permanent employees need an official EPF number.");
        if (request.dateOfBirth() != null && request.dateOfBirth().isAfter(request.joinedDate()))
            throw badRequest("Date of birth must precede joining date.");
        if (request.basicSalary() != null && (request.basicSalary().signum() < 0 || request.basicSalary().scale() > 2))
            throw badRequest("Basic salary must be a non-negative amount with at most two decimal places.");
        checkChoice(request.gender(), "Gender", List.of("FEMALE", "MALE"));
        checkChoice(request.payrollCategory(), "Payroll category", List.of("WORKER", "STAFF", "MANAGEMENT", "EXECUTIVE"));
        checkChoice(request.directIndirectStatus(), "Direct/indirect status", List.of("DIRECT", "INDIRECT"));

        if (!exists("public.departments", request.departmentId()))
            throw badRequest("Select an active LineMatrix department.");
        if (!exists("public.designations", request.designationId()))
            throw badRequest("Select a LineMatrix designation.");

        UUID id = UUID.randomUUID();
        String displayName = first(request.displayName(), request.firstName().trim() + " " + request.lastName().trim());
        jdbc.update("""
            INSERT INTO public.employees
              (id,employee_code,employee_category,epf_no,display_name,department_id,designation_id,
               source_priority_name,employment_status,hire_date,is_active,workforce_group)
            VALUES (?,?,?,?,?,?,?,'Payroll HR registration','active',?,true,?)
            """, id, number, request.employeeType(), request.employeeType().equals("permanent") ? number : null,
                displayName, request.departmentId(), request.designationId(), request.joinedDate(),
                request.workforceGroup().name());
        jdbc.update("""
            INSERT INTO public.employee_master_details
              (employee_id,identity_number,first_name,last_name,full_name,email,phone,gender,date_of_birth,
               residential_address,emergency_name,emergency_phone,emergency_relationship,
               payroll_category,direct_indirect_status,source_name)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Payroll HR registration')
            """, id, request.identityNumber().trim(), request.firstName().trim(), request.lastName().trim(),
                displayName, blankToNull(request.email()), blankToNull(request.phone()), blankToNull(request.gender()),
                request.dateOfBirth(), blankToNull(request.residentialAddress()), blankToNull(request.emergencyName()),
                blankToNull(request.emergencyPhone()), blankToNull(request.emergencyRelationship()),
                blankToNull(request.payrollCategory()), blankToNull(request.directIndirectStatus()));
        if (request.employeeType().equals("permanent")) jdbc.update("""
            INSERT INTO payroll.employee_financial_master
              (employee_id,bank_name,bank_branch,bank_account_number,basic_salary,overtime_paid,attendance_bonus_eligible)
            VALUES (?,?,?,?,?,?,?)
            """, id, blankToNull(request.bankName()), blankToNull(request.bankBranch()),
                blankToNull(request.bankAccountNumber()), request.basicSalary(), request.overtimePaid(),
                request.attendanceBonusEligible());
        jdbc.update("INSERT INTO public.employee_profiles(employee_id,phone,join_date) VALUES (?,?,?)",
                id, blankToNull(request.phone()), request.joinedDate());
        jdbc.update("""
            INSERT INTO public.audit_logs(action_type,entity_type,entity_id,new_value,metadata)
            VALUES ('employee_created_by_hr','employees',?,jsonb_build_object('employee_code',?,'employee_category',?),
                    jsonb_build_object('source','payroll_central_registration','actor_auth_user_id',?))
            """, id.toString(), number, request.employeeType(), actorId.toString());
        return new Result(id, number, request.employeeType(), request.workforceGroup(),
                request.employeeType().equals("permanent"));
    }

    private boolean exists(String table, UUID id) {
        // Table names are constants in this class, never request input.
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id=? AND is_active=true", Long.class, id);
        return count != null && count == 1;
    }

    private static String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void checkChoice(String value, String label, List<String> allowed) {
        if (value != null && !value.isBlank() && !allowed.contains(value))
            throw badRequest(label + " is invalid.");
    }

    private static EmployeeRegistrationException badRequest(String message) {
        return new EmployeeRegistrationException(message);
    }
}
