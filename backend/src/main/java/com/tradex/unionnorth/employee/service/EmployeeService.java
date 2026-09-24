package com.tradex.unionnorth.employee.service;

import com.tradex.unionnorth.employee.domain.Employee;
import com.tradex.unionnorth.employee.domain.EmploymentStatus;
import com.tradex.unionnorth.employee.dto.CreateEmployeeRequest;
import com.tradex.unionnorth.employee.dto.EmployeeResponse;
import com.tradex.unionnorth.employee.dto.EmployeeSearchCriteria;
import com.tradex.unionnorth.employee.dto.UpdateEmployeeRequest;
import com.tradex.unionnorth.employee.mapper.EmployeeMapper;
import com.tradex.unionnorth.employee.repository.EmployeeRepository;
import com.tradex.unionnorth.employee.domain.PayrollStatus;
import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployee;
import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployeeLookup;
import com.tradex.unionnorth.setup.PayrollProfileService;
import com.tradex.unionnorth.setup.SetupException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper employeeMapper;
    private final PayrollProfileService payrollProfiles;
    private final LineMatrixEmployeeLookup lineMatrix;
    private final JdbcTemplate jdbc;

    public EmployeeService(EmployeeRepository employeeRepository, EmployeeMapper employeeMapper,
            PayrollProfileService payrollProfiles, LineMatrixEmployeeLookup lineMatrix, JdbcTemplate jdbc) {
        this.employeeRepository = employeeRepository;
        this.employeeMapper = employeeMapper;
        this.payrollProfiles = payrollProfiles;
        this.lineMatrix = lineMatrix;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> listEmployees(EmployeeSearchCriteria criteria, Pageable pageable) {
        return employeeRepository.findAll(toSpecification(criteria), pageable).map(employeeMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(UUID id) {
        return employeeRepository.findById(id)
                .map(employeeMapper::toResponse)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        if (request.lineMatrixEmployeeNumber() == null || request.lineMatrixEmployeeNumber().isBlank())
            throw new SetupException("Select an existing LineMatrix employee before payroll registration.");
        if (!request.employeeNumber().trim().equals(request.lineMatrixEmployeeNumber().trim()))
            throw new SetupException("Registration employee number must match the selected LineMatrix employee.");
        LineMatrixEmployee source = lineMatrix.lookup(request.lineMatrixEmployeeNumber());
        verifyInput(request.firstName(), request.lastName(), request.displayName(), request.identityNumber(),
                request.email(), request.phone(), request.employmentStatus(), source);
        Employee employee = employeeMapper.toEntity(request);
        applySource(employee, source);
        employee.setPayrollStatus(PayrollStatus.HOLD);
        employeeRepository.saveAndFlush(employee);
        payrollProfiles.initialize(
                employee.getId(),
                request.lineMatrixEmployeeNumber(),
                request.departmentId(),
                request.designationId(),
                request.joinedDate());
        return employeeMapper.toResponse(employee);
    }

    @Transactional
    public EmployeeResponse updateEmployee(UUID id, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        Map<String, Object> link = jdbc.queryForMap(
                "SELECT linematrix_employee_id,source_employee_number FROM payroll.employee_payroll_profiles WHERE employee_id=?", id);
        if (link.get("source_employee_number") == null || link.get("linematrix_employee_id") == null)
            throw new SetupException("Link this payroll record to a LineMatrix employee before editing it.");
        LineMatrixEmployee source = lineMatrix.lookup(link.get("source_employee_number").toString());
        if (!Objects.equals(link.get("linematrix_employee_id").toString(), source.sourceId()))
            throw new SetupException("The linked LineMatrix employee changed. Ask an administrator to review the link.");
        verifyInput(request.firstName(), request.lastName(), request.displayName(), request.identityNumber(),
                request.email(), request.phone(), request.employmentStatus(), source);
        PayrollStatus payrollStatus = employee.getPayrollStatus();
        if (request.payrollStatus()!=null && request.payrollStatus()!=payrollStatus)
            throw new SetupException("Change payroll eligibility through payroll setup activation or hold.");
        employeeMapper.update(employee, request);
        applySource(employee, source);
        employee.setPayrollStatus(payrollStatus);
        return employeeMapper.toResponse(employee);
    }

    private void verifyInput(String firstName, String lastName, String displayName, String identityNumber,
            String email, String phone, EmploymentStatus status, LineMatrixEmployee source) {
        Map<String, Object> details = source.payrollDetails();
        String canonicalFirst = detail(details, "first_name");
        String canonicalLast = detail(details, "last_name");
        String canonicalIdentity = detail(details, "identity_number");
        if (canonicalFirst == null || canonicalLast == null || canonicalIdentity == null)
            throw new SetupException("Complete the employee name and identity number in LineMatrix before payroll registration.");
        String canonicalDisplay = source.displayName() == null || source.displayName().isBlank()
                ? canonicalFirst + " " + canonicalLast : source.displayName();
        String canonicalPhone = first(source.phone(), detail(details, "mobile_phone"), detail(details, "phone"));
        EmploymentStatus canonicalStatus = sourceStatus(source);
        if (!same(firstName, canonicalFirst) || !same(lastName, canonicalLast)
                || !same(identityNumber, canonicalIdentity)
                || (displayName != null && !displayName.isBlank() && !same(displayName, canonicalDisplay))
                || !same(email, detail(details, "email"))
                || !same(phone, canonicalPhone)
                || (status != null && status != canonicalStatus))
            throw new SetupException("Employee master details changed in LineMatrix. Refresh the lookup and try again.");
    }

    private void applySource(Employee employee, LineMatrixEmployee source) {
        Map<String, Object> details = source.payrollDetails();
        employee.setEmployeeNumber(source.employeeNumber());
        employee.setFirstName(detail(details, "first_name"));
        employee.setLastName(detail(details, "last_name"));
        employee.setDisplayName(first(source.displayName(), employee.getFirstName() + " " + employee.getLastName()));
        employee.setIdentityNumber(detail(details, "identity_number"));
        employee.setEmail(detail(details, "email"));
        employee.setPhone(first(source.phone(), detail(details, "mobile_phone"), detail(details, "phone")));
        employee.setEmploymentStatus(sourceStatus(source));
    }

    private EmploymentStatus sourceStatus(LineMatrixEmployee source) {
        try {
            return EmploymentStatus.valueOf(source.employmentStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SetupException("Set a supported employment status in LineMatrix before payroll registration.");
        }
    }

    private String detail(Map<String, Object> details, String key) {
        Object value = details == null ? null : details.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    private boolean same(String left, String right) {
        return Objects.equals(left == null || left.isBlank() ? null : left.trim(),
                right == null || right.isBlank() ? null : right.trim());
    }

    private Specification<Employee> toSpecification(EmployeeSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null && criteria.status() != null) {
                predicates.add(builder.equal(root.get("cadreStatus"), criteria.status()));
            }
            if (criteria != null && criteria.search() != null && !criteria.search().isBlank()) {
                String pattern = "%" + criteria.search().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("employeeNumber")), pattern),
                        builder.like(builder.lower(root.get("firstName")), pattern),
                        builder.like(builder.lower(root.get("lastName")), pattern),
                        builder.like(builder.lower(root.get("displayName")), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
