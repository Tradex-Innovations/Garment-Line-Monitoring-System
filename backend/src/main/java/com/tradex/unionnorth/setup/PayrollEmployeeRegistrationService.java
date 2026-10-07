package com.tradex.unionnorth.setup;

import com.tradex.unionnorth.employee.dto.SharedEmployeeRegistrationRequest;
import com.tradex.unionnorth.employee.service.SharedEmployeeRegistrationService;
import com.tradex.unionnorth.security.WorkforceAccess;
import com.tradex.unionnorth.security.domain.WorkforceGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the shared roster record and its complete payroll draft atomically. */
@Service
public class PayrollEmployeeRegistrationService {
    public record Request(
            @NotBlank String employeeType,
            @NotNull WorkforceGroup workforceGroup,
            @NotBlank String employeeNumber,
            @NotNull UUID lineMatrixDepartmentId,
            @NotNull UUID lineMatrixDesignationId,
            @NotNull Map<String, Object> general,
            @NotNull Map<String, Object> financial,
            @NotBlank String reason) {}

    public record Result(UUID sourceId, UUID employeeId, String employeeNumber,
                         String employeeType, WorkforceGroup workforceGroup, boolean payrollEligible) {}

    private final SharedEmployeeRegistrationService shared;
    private final SetupStore store;
    private final SetupCatalog catalog;
    private final MasterDataService masters;
    private final WorkforceAccess workforce;

    public PayrollEmployeeRegistrationService(SharedEmployeeRegistrationService shared, SetupStore store,
            SetupCatalog catalog, MasterDataService masters, WorkforceAccess workforce) {
        this.shared = shared;
        this.store = store;
        this.catalog = catalog;
        this.masters = masters;
        this.workforce = workforce;
    }

    @Transactional
    public Result register(@Valid Request request, UUID actorId) {
        SetupAccess.require("EMPLOYEE_CREATE");
        SetupAccess.require("EMPLOYEE_UPDATE");
        SetupAccess.require("SALARY_EDIT");
        workforce.require(request.workforceGroup(), WorkforceAccess.Action.EDIT);
        if (!List.of("permanent", "new_joiner").contains(request.employeeType()))
            throw new SetupException("Use the basic registration form for interns.");
        if (request.reason().isBlank() || request.reason().length() > 500)
            throw new SetupException("Enter a reason for registration (up to 500 characters).");

        Map<String, Object> general = new LinkedHashMap<>(catalog.validate(catalog.generalFields(), request.general(), false));
        Map<String, Object> financial = new LinkedHashMap<>(catalog.validate(catalog.financialFields(), request.financial(), false));
        String type = request.employeeType().toUpperCase(java.util.Locale.ROOT);
        if (general.containsKey("employmentType") && !type.equals(general.get("employmentType")))
            throw new SetupException("Employment type must match the selected employee type.");
        general.put("employmentType", type);
        String number = request.employeeNumber().trim().replaceAll("\\s+", "");
        String first = required(general, "firstName");
        String last = required(general, "lastName");
        String identity = required(general, "identityNumber");
        String display = text(general, "displayName");
        if (display.isBlank()) display = first + " " + last;
        general.put("displayName", display);
        LocalDate joined;
        try {
            joined = LocalDate.parse(required(general, "joinedDate"));
        } catch (RuntimeException exception) {
            throw new SetupException("Enter a valid joining date.");
        }
        if (request.employeeType().equals("permanent") && !general.containsKey("epfNumber"))
            general.put("epfNumber", number);
        if (text(general, "phone").length() > 40 || text(general, "epfNumber").length() > 50)
            throw new SetupException("Phone or member number is too long.");
        if (general.containsKey("dateOfBirth")
                && LocalDate.parse(general.get("dateOfBirth").toString()).isAfter(LocalDate.now(ZoneId.of("Asia/Colombo"))))
            throw new SetupException("Date of birth cannot be in the future.");
        for (String key : List.of("contractEndDate", "probationEndDate"))
            if (general.containsKey(key) && LocalDate.parse(general.get(key).toString()).isBefore(joined))
                throw new SetupException("Contract/probation end cannot precede joined date.");
        if (general.containsKey("groupJoinedDate")
                && LocalDate.parse(general.get("groupJoinedDate").toString()).isAfter(joined))
            throw new SetupException("Group joined date cannot be after joined date.");
        LocalDate effective = general.containsKey("effectiveFrom")
                ? LocalDate.parse(general.get("effectiveFrom").toString()) : joined;
        masters.validateReferences(catalog.generalFields(), general, effective, false);
        masters.validateHierarchy(general, effective);
        masters.validateReferences(catalog.financialFields(), financial, effective, false);
        masters.validateComponents(financial.get("components"), effective, false);
        if (financial.containsKey("salaryStructureId"))
            store.lockSalaryStructure(UUID.fromString(financial.get("salaryStructureId").toString()), false);
        if (financial.containsKey("branchId") && !Objects.equals(
                store.get("BANK_BRANCH", UUID.fromString(financial.get("branchId").toString()), effective)
                        .data().get("bankId"), financial.get("bankId")))
            throw new SetupException("Branch does not belong to the selected bank.");
        Object rules = financial.get("statutoryRules");
        if (rules instanceof List<?> ids) {
            if (new HashSet<>(ids).size() != ids.size())
                throw new SetupException("Statutory rules must be unique valid references.");
            for (Object id : ids) {
                UUID ruleId;
                try {
                    ruleId = UUID.fromString(id.toString());
                } catch (RuntimeException exception) {
                    throw new SetupException("Statutory rules must be unique valid references.");
                }
                if (!store.get("STATUTORY_RULE", ruleId, effective).active())
                    throw new SetupException("An assigned statutory rule is inactive.");
            }
            if (!ids.isEmpty() && !text(financial, "statutoryExemptionReason").isBlank())
                throw new SetupException("Use rule assignments or an exemption reason, not both.");
        }
        if (financial.keySet().stream().anyMatch(List.of("basicSalary", "bra1", "bra2")::contains))
            financial.put("totalBasicSalary", amount(financial, "basicSalary")
                    .add(amount(financial, "bra1")).add(amount(financial, "bra2")));
        // The legacy shared financial-master column is two-decimal; the payroll
        // profile is authoritative for valid four-decimal daily/hourly rates.
        BigDecimal sharedBasic = financial.containsKey("basicSalary") ? amount(financial, "basicSalary") : null;
        if (sharedBasic != null && sharedBasic.scale() > 2) sharedBasic = null;

        var source = shared.register(new SharedEmployeeRegistrationRequest(
                request.employeeType(), request.workforceGroup(), number, first, last, display, identity,
                nullable(general, "email"), nullable(general, "phone"), request.lineMatrixDepartmentId(),
                request.lineMatrixDesignationId(), joined, nullable(general, "gender"),
                general.containsKey("dateOfBirth") ? LocalDate.parse(general.get("dateOfBirth").toString()) : null,
                nullable(general, "residentialAddress"), nullable(general, "emergencyName"),
                nullable(general, "emergencyPhone"), nullable(general, "emergencyRelationship"),
                null, null, nullable(financial, "accountNumber"), nullable(general, "employeeCategory"),
                nullable(general, "employeeStatus"), sharedBasic,
                (Boolean) financial.get("overtimePaid"), (Boolean) financial.get("attendanceBonusEligible")), actorId);
        UUID id = source.sourceId();
        // Keep overlapping non-financial master fields available to LineMatrix too.
        // Bank and salary data remain in the protected payroll schema.
        store.jdbc().update("""
                UPDATE public.employee_master_details SET full_name=?,initials=?,name_with_initials=?,
                    call_name=?,barcode_number=?,occupation_code=?,bus_route=?,distance_km=?,
                    district=?,electorate=?,group_joined_date=?,updated_at=now()
                WHERE employee_id=?
                """, firstNonBlank(nullable(general, "fullName"), display),
                nullable(general, "initials"), nullable(general, "nameWithInitials"), display,
                nullable(general, "barcode"), nullable(general, "occupationCode"),
                nullable(general, "busRoute"), general.get("distanceKm"),
                nullable(general, "district"), nullable(general, "electorate"),
                general.containsKey("groupJoinedDate") ? LocalDate.parse(general.get("groupJoinedDate").toString()) : null,
                id);
        if (general.containsKey("shiftId")) {
            var shift = store.get("SHIFT", UUID.fromString(general.get("shiftId").toString()), effective);
            store.jdbc().update("UPDATE public.employee_profiles SET shift_name=? WHERE employee_id=?",
                    firstNonBlank(Objects.toString(shift.data().get("sourceName"), null), shift.name()), id);
        }
        store.jdbc().update("""
                INSERT INTO payroll.employees
                (id,employee_number,first_name,last_name,display_name,identity_number,email,phone,
                 employment_status,cadre_status,payroll_status,workforce_group,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?,?,'ACTIVE','ACTIVE','HOLD',?,?,?)
                """, id, number, first, last, display, identity, nullable(general, "email"),
                nullable(general, "phone"), request.workforceGroup().name(), SetupAccess.actor(), SetupAccess.actor());
        Map<String, Object> sourceDetails = new LinkedHashMap<>();
        sourceDetails.put("displayName", display);
        sourceDetails.put("department", store.jdbc().queryForObject(
                "SELECT name FROM public.departments WHERE id=?", String.class, request.lineMatrixDepartmentId()));
        sourceDetails.put("designation", store.jdbc().queryForObject(
                "SELECT name FROM public.designations WHERE id=?", String.class, request.lineMatrixDesignationId()));
        sourceDetails.put("active", true);
        sourceDetails.put("category", request.employeeType());
        sourceDetails.put("joinedDate", joined.toString());
        sourceDetails.put("epfNumber", nullable(general, "epfNumber"));
        sourceDetails.put("lastCheckedAt", Instant.now().toString());
        store.jdbc().update("""
                INSERT INTO payroll.employee_payroll_profiles
                (employee_id,linematrix_employee_id,source_employee_number,source_details,general_data,financial_data)
                VALUES (?,?,?,?::jsonb,?::jsonb,?::jsonb)
                """, id, id, number, store.json(sourceDetails), store.json(general), store.json(financial));
        store.audit(id, "PAYROLL_DRAFT_CREATED", "PAYROLL_PROFILE", id,
                request.reason().trim(), List.of("registrationStatus", "general", "financial"));
        return new Result(id, id, number, request.employeeType(), request.workforceGroup(), true);
    }

    private static String text(Map<String, Object> data, String key) {
        return Objects.toString(data.get(key), "").trim();
    }

    private static String nullable(Map<String, Object> data, String key) {
        String value = text(data, key);
        return value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String required(Map<String, Object> data, String key) {
        String value = text(data, key);
        if (value.isBlank()) throw new SetupException(key + " is required.");
        return value;
    }

    private static BigDecimal amount(Map<String, Object> data, String key) {
        return data.get(key) == null ? BigDecimal.ZERO : new BigDecimal(data.get(key).toString());
    }
}
