package com.tradex.unionnorth.setup;

import static com.tradex.unionnorth.setup.MasterDataService.*;

import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployee;
import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployeeLookup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PayrollProfileService {
    public record Save(long version, Map<String, Object> data, String reason) {}

    public record Action(long version, String reason, LocalDate effectiveFrom) {}

    public record Link(long version, String employeeNumber, String reason) {}

    private final SetupStore store;
    private final SetupCatalog catalog;
    private final MasterDataService masters;
    private final LineMatrixEmployeeLookup lookup;

    public PayrollProfileService(
            SetupStore store,
            SetupCatalog catalog,
            MasterDataService masters,
            LineMatrixEmployeeLookup lookup) {
        this.store = store;
        this.catalog = catalog;
        this.masters = masters;
        this.lookup = lookup;
    }

    @Transactional
    public void initialize(UUID employeeId, String sourceNumber) {
        initialize(employeeId, sourceNumber, null, null, null);
    }

    @Transactional
    public void initialize(
            UUID employeeId,
            String sourceNumber,
            UUID departmentId,
            UUID designationId,
            LocalDate joinedDate) {
        store.jdbc()
                .update(
                        "INSERT INTO employee_payroll_profiles(employee_id) VALUES (?) ON CONFLICT"
                                + " DO NOTHING",
                        employeeId);
        if (sourceNumber != null && !sourceNumber.isBlank())
            linkInternal(employeeId, sourceNumber, "Linked during employee registration");
        applyInitialEmployment(employeeId, departmentId, designationId, joinedDate);
        store.audit(
                employeeId,
                "PAYROLL_DRAFT_CREATED",
                "PAYROLL_PROFILE",
                employeeId,
                "Employee registration",
                List.of("registrationStatus"));
    }

    private void applyInitialEmployment(
            UUID employeeId,
            UUID departmentId,
            UUID designationId,
            LocalDate joinedDate) {
        if (departmentId == null && designationId == null && joinedDate == null) return;

        var row = load(employeeId, true);
        var general = data(row, "general");
        Map<String, Object> selected = new LinkedHashMap<>();
        if (departmentId != null) {
            var department = store.get("DEPARTMENT", departmentId, null);
            selected.put("departmentId", departmentId.toString());
            general.put("departmentId", departmentId.toString());
            Object companyId = department.data().get("companyId");
            Object locationId = department.data().get("locationId");
            if (companyId != null) general.put("companyId", companyId.toString());
            if (locationId != null) general.put("locationId", locationId.toString());
        }
        if (designationId != null) {
            selected.put("designationId", designationId.toString());
            general.put("designationId", designationId.toString());
        }
        if (joinedDate != null) general.put("joinedDate", joinedDate.toString());

        masters.validateReferences(catalog.generalFields(), selected, joinedDate, true);
        masters.validateHierarchy(general, joinedDate);
        store.jdbc()
                .update(
                        "UPDATE employee_payroll_profiles SET general_data=?::jsonb,version=version+1,updated_at=now() WHERE employee_id=?",
                        store.json(general),
                        employeeId);
        var changed = new ArrayList<String>();
        if (departmentId != null) changed.add("departmentId");
        if (designationId != null) changed.add("designationId");
        if (joinedDate != null) changed.add("joinedDate");
        store.audit(
                employeeId,
                "INITIAL_EMPLOYMENT_SAVED",
                "PAYROLL_PROFILE",
                employeeId,
                "Employee registration",
                changed);
    }

    private Map<String, Object> load(UUID id, boolean lock) {
        var rows =
                store.jdbc()
                        .query(
                                "SELECT * FROM employee_payroll_profiles WHERE employee_id=?"
                                        + (lock ? " FOR UPDATE" : ""),
                                (r, i) -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("employeeId", id);
                                    m.put("version", r.getLong("version"));
                                    m.put("status", r.getString("registration_status"));
                                    m.put("general", store.object(r.getString("general_data")));
                                    m.put("financial", store.object(r.getString("financial_data")));
                                    m.put("source", store.object(r.getString("source_details")));
                                    m.put("sourceId", r.getString("linematrix_employee_id"));
                                    m.put(
                                            "sourceEmployeeNumber",
                                            r.getString("source_employee_number"));
                                    m.put(
                                            "updatedAt",
                                            r.getTimestamp("updated_at").toInstant().toString());
                                    return m;
                                },
                                id);
        if (rows.isEmpty()) throw SetupException.missing();
        var row = rows.getFirst();
        var publication =
                store.jdbc()
                        .queryForList(
                                "SELECT effective_from FROM payroll_profile_revisions WHERE"
                                        + " employee_id=? ORDER BY effective_from DESC LIMIT 1",
                                id);
        row.put(
                "publishedFrom",
                publication.isEmpty()
                        ? null
                        : publication.getFirst().get("effective_from").toString());
        var base =
                store.jdbc()
                        .queryForMap(
                                "SELECT"
                                    + " employee_number,first_name,last_name,display_name,identity_number,email,phone,employment_status,cadre_status,payroll_status"
                                    + " FROM employees WHERE id=?",
                                id);
        row.put("employeeNumber", base.get("employee_number"));
        row.put("employmentStatus", base.get("employment_status"));
        row.put("cadreStatus", base.get("cadre_status"));
        row.put("payrollStatus", base.get("payroll_status"));
        Map<String, Object> g = data(row, "general");
        Map.of(
                        "firstName",
                        "first_name",
                        "lastName",
                        "last_name",
                        "displayName",
                        "display_name",
                        "identityNumber",
                        "identity_number",
                        "email",
                        "email",
                        "phone",
                        "phone")
                .forEach(
                        (key, col) -> {
                            if (base.get(col) != null) g.putIfAbsent(key, base.get(col));
                        });
        return row;
    }

    public Map<String, Object> get(UUID id) {
        SetupAccess.require("EMPLOYEE_VIEW_ALL");
        var row = load(id, false);
        row.put("missing", readiness(row));
        if (!SetupAccess.has("SALARY_VIEW")) row.remove("financial");
        return row;
    }

    @Transactional
    public Map<String, Object> saveGeneral(UUID id, Save request) {
        SetupAccess.require("EMPLOYEE_UPDATE");
        var row = checked(id, request.version());
        String reason = required(request.reason(), "Reason for change", 500);
        var general = catalog.validate(catalog.generalFields(), request.data(), false);
        assertSharedIdentity(row, general);
        // Identity is always required; employment/payment setup can remain a draft.
        for (String key : List.of("firstName", "lastName", "displayName", "identityNumber"))
            required(
                    text(general, key),
                    key,
                    key.equals("identityNumber") ? 80 : key.equals("displayName") ? 240 : 120);
        if (text(general, "phone").length() > 40 || text(general, "epfNumber").length() > 50)
            throw new SetupException("Phone or member number is too long.");
        LocalDate from =
                general.containsKey("effectiveFrom") ? date(general, "effectiveFrom") : null;
        masters.validateReferences(catalog.generalFields(), general, from, false);
        masters.validateHierarchy(general, from);
        if (general.containsKey("dateOfBirth")
                && date(general, "dateOfBirth").isAfter(LocalDate.now(ZoneId.of("Asia/Colombo"))))
            throw new SetupException("Date of birth cannot be in the future.");
        if (general.containsKey("joinedDate")) {
            for (String key : List.of("contractEndDate", "probationEndDate"))
                if (general.containsKey(key)
                        && date(general, key).isBefore(date(general, "joinedDate")))
                    throw new SetupException("Contract/probation end cannot precede joined date.");
            if (general.containsKey("groupJoinedDate")
                    && date(general, "groupJoinedDate").isAfter(date(general, "joinedDate")))
                throw new SetupException("Group joined date cannot be after joined date.");
        }
        if (general.containsKey("contractDurationMonths")) {
            BigDecimal months = new BigDecimal(general.get("contractDurationMonths").toString());
            if (months.stripTrailingZeros().scale() > 0
                    || months.compareTo(BigDecimal.valueOf(1200)) > 0)
                throw new SetupException("Contract duration must be a whole number up to 1200 months.");
        }
        store.jdbc()
                .update(
                        """
UPDATE employees SET first_name=?,last_name=?,display_name=?,identity_number=?,email=?,phone=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=?
""",
                        general.get("firstName"),
                        general.get("lastName"),
                        general.get("displayName"),
                        general.get("identityNumber"),
                        general.get("email"),
                        general.get("phone"),
                        SetupAccess.actor(),
                        id);
        saveDraft(id, "general_data", general, row);
        store.audit(id, "EMPLOYMENT_DRAFT_SAVED", "PAYROLL_PROFILE", id, reason, general.keySet());
        return get(id);
    }

    @Transactional
    public Map<String, Object> saveFinancial(UUID id, Save request) {
        SetupAccess.require("SALARY_EDIT");
        var row = checked(id, request.version());
        String reason = required(request.reason(), "Reason for change", 500);
        var financial = catalog.validate(catalog.financialFields(), request.data(), false);
        if (financial.keySet().stream()
                .anyMatch(Set.of("basicSalary", "bra1", "bra2")::contains)) {
            BigDecimal total = amount(financial, "basicSalary")
                    .add(amount(financial, "bra1"))
                    .add(amount(financial, "bra2"));
            financial.put("totalBasicSalary", total);
        } else {
            financial.remove("totalBasicSalary");
        }
        var general = data(row, "general");
        LocalDate at = general.containsKey("effectiveFrom") ? date(general, "effectiveFrom") : null;
        masters.validateReferences(catalog.financialFields(), financial, at, false);
        masters.validateComponents(financial.get("components"), at, false);
        validateStatutory(financial, at, false);
        validateBank(financial, false, at);
        saveDraft(id, "financial_data", financial, row);
        store.audit(
                id,
                "PAYROLL_FINANCIAL_DRAFT_SAVED",
                "PAYROLL_PROFILE",
                id,
                reason,
                financial.keySet());
        return get(id);
    }

    private BigDecimal amount(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private void assertSharedIdentity(Map<String, Object> row, Map<String, Object> general) {
        if (row.get("sourceId") == null || row.get("sourceEmployeeNumber") == null)
            throw new SetupException("Link a LineMatrix employee before editing payroll setup.");
        LineMatrixEmployee source = lookup.lookup(row.get("sourceEmployeeNumber").toString());
        if (!Objects.equals(source.sourceId(), row.get("sourceId")))
            throw new SetupException("The linked LineMatrix employee changed. Ask an administrator to review the link.");
        Map<String, Object> details = source.payrollDetails() == null ? Map.of() : source.payrollDetails();
        String first = Objects.toString(details.get("first_name"), "").trim();
        String last = Objects.toString(details.get("last_name"), "").trim();
        String identity = Objects.toString(details.get("identity_number"), "").trim();
        if (first.isBlank() || last.isBlank() || identity.isBlank())
            throw new SetupException("Complete the employee name and identity number in LineMatrix first.");
        String display = source.displayName() == null || source.displayName().isBlank()
                ? first + " " + last : source.displayName().trim();
        String phone = firstNonBlank(source.phone(), Objects.toString(details.get("mobile_phone"), null),
                Objects.toString(details.get("phone"), null));
        if (!Objects.equals(text(general, "firstName"), first)
                || !Objects.equals(text(general, "lastName"), last)
                || !Objects.equals(text(general, "displayName"), display)
                || !Objects.equals(text(general, "identityNumber"), identity)
                || !Objects.equals(text(general, "email"), Objects.toString(details.get("email"), "").trim())
                || !Objects.equals(text(general, "phone"), Objects.toString(phone, "")))
            throw new SetupException("Edit employee master details in LineMatrix, then refresh the payroll form.");
    }

    private void saveDraft(
            UUID id, String column, Map<String, Object> value, Map<String, Object> row) {
        // column is a constant supplied only by the two typed section methods above.
        store.jdbc()
                .update(
                        "UPDATE employee_payroll_profiles SET "
                                + column
                                + "=?::jsonb,registration_status=?,version=version+1,updated_at=now()"
                                + " WHERE employee_id=?",
                        store.json(value),
                        "ACTIVE".equals(row.get("status")) ? "CHANGES_PENDING" : row.get("status"),
                        id);
    }

    @Transactional
    public Map<String, Object> link(UUID id, Link request) {
        SetupAccess.require("EMPLOYEE_UPDATE");
        checked(id, request.version());
        linkInternal(
                id,
                required(request.employeeNumber(), "LineMatrix employee number", 50),
                required(request.reason(), "Reason", 500));
        return get(id);
    }

    private void linkInternal(UUID id, String number, String reason) {
        LineMatrixEmployee source = lookup.lookup(number);
        if (source.sourceId() == null)
            throw new SetupException("LineMatrix did not supply a stable employee ID.");
        var row = load(id, true);
        if (!Objects.equals(row.get("employeeNumber"), source.employeeNumber()))
            throw new SetupException("Payroll and LineMatrix employee numbers differ. Review this record before linking.");
        if (row.get("sourceId") != null && !row.get("sourceId").equals(source.sourceId()))
            throw new SetupException(
                    "This payroll profile is already linked to a different LineMatrix employee."
                            + " Relinking requires an explicit data migration.");
        var general = data(row, "general");
        var financial = data(row, "financial");
        Map<String, Object> details = new LinkedHashMap<>();
        var previousGeneral = new LinkedHashMap<>(general);
        var previousFinancial = new LinkedHashMap<>(financial);
        Map<String, Object> master = source.payrollDetails() == null ? Map.of() : source.payrollDetails();
        String first = Objects.toString(master.get("first_name"), "").trim();
        String last = Objects.toString(master.get("last_name"), "").trim();
        String identity = Objects.toString(master.get("identity_number"), "").trim();
        if (first.isBlank() || last.isBlank() || identity.isBlank())
            throw new SetupException("Complete the employee name and identity number in LineMatrix first.");
        String display = firstNonBlank(source.displayName(), first + " " + last);
        String email = firstNonBlank(Objects.toString(master.get("email"), null));
        String phone = firstNonBlank(source.phone(), Objects.toString(master.get("mobile_phone"), null),
                Objects.toString(master.get("phone"), null));
        general.put("firstName", first);
        general.put("lastName", last);
        general.put("displayName", display);
        general.put("identityNumber", identity);
        if (email == null) general.remove("email"); else general.put("email", email);
        if (phone == null) general.remove("phone"); else general.put("phone", phone);
        store.jdbc().update(
                "UPDATE employees SET first_name=?,last_name=?,display_name=?,identity_number=?,email=?,phone=?,updated_at=now(),version=version+1 WHERE id=?",
                first, last, display, identity, email, phone, id);
        details.put("employeeNumber", source.employeeNumber());
        details.put("displayName", source.displayName());
        details.put("department", source.department());
        details.put("designation", source.designation());
        details.put("team", source.team());
        details.put("grade", source.grade());
        details.put("active", source.active());
        details.put("employmentStatus", source.employmentStatus());
        details.put("category", source.sourceCategory());
        details.put("shift", source.shiftName());
        details.put("lastCheckedAt", java.time.Instant.now().toString());
        details.put("epfNumber", source.epfNumber());
        details.put("phone", source.phone());
        details.put("joinedDate", source.joinedDate());
        details.put("photoUrl", source.photoUrl());
        details.put("productionLineCode", source.productionLineCode());
        details.put("productionLineName", source.productionLineName());
        details.put("bank", detail(source, "bank_name"));
        details.put("bankBranch", detail(source, "bank_branch"));
        details.put("sharedDetailFields", source.payrollDetails() == null
                ? List.of()
                : source.payrollDetails().keySet());
        fill(general, "firstName", detail(source, "first_name"));
        fill(general, "lastName", detail(source, "last_name"));
        fill(general, "fullName", detail(source, "full_name"));
        fill(general, "displayName", source.displayName());
        fill(general, "displayName", detail(source, "call_name"));
        fill(general, "fullName", source.displayName());
        fill(general, "initials", detail(source, "initials"));
        fill(general, "nameWithInitials", detail(source, "name_with_initials"));
        fill(general, "identityNumber", detail(source, "identity_number"));
        fill(general, "email", detail(source, "email"));
        fill(general, "epfNumber", source.epfNumber());
        fill(general, "joinedDate", source.joinedDate());
        fill(general, "phone", firstNonBlank(
                source.phone(), detail(source, "mobile_phone"), detail(source, "phone")));
        fill(general, "dateOfBirth", detail(source, "date_of_birth"));
        fill(general, "gender", detail(source, "gender"));
        fill(general, "residentialAddress", detail(source, "residential_address"));
        fill(general, "barcode", detail(source, "barcode_number"));
        fill(general, "occupationCode", detail(source, "occupation_code"));
        fill(general, "busRoute", detail(source, "bus_route"));
        fillObject(general, "distanceKm", detailObject(source, "distance_km"));
        fill(general, "district", detail(source, "district"));
        fill(general, "electorate", detail(source, "electorate"));
        fill(general, "groupJoinedDate", detail(source, "group_joined_date"));
        fill(general, "employeeStatus", detail(source, "direct_indirect_status"));
        fill(general, "emergencyName", detail(source, "emergency_name"));
        fill(general, "emergencyPhone", detail(source, "emergency_phone"));
        fill(general, "emergencyRelationship", detail(source, "emergency_relationship"));
        fillChoice(
                general,
                "employeeCategory",
                firstNonBlank(detail(source, "payroll_category"), source.sourceCategory()),
                Set.of("MANAGEMENT", "STAFF", "WORKER", "TRAINEE", "EXECUTIVE"));
        fillChoice(general, "employmentType", source.sourceCategory(),
                Set.of("PERMANENT", "CONTRACT", "TEMPORARY", "INTERN"));
        match(general, "departmentId", "DEPARTMENT", source.department());
        if (!text(general, "departmentId").isBlank()) {
            var dept = store.get("DEPARTMENT", uuid(general, "departmentId"), null);
            fill(general, "companyId", Objects.toString(dept.data().get("companyId"), null));
            fill(general, "locationId", Objects.toString(dept.data().get("locationId"), null));
        }
        match(general, "designationId", "DESIGNATION", source.designation());
        match(general, "teamId", "TEAM", source.team());
        match(general, "gradeId", "GRADE", source.grade());
        matchShift(general, source.shiftName());
        matchLine(general, source.productionLineCode(), source.productionLineName());
        fillObject(financial, "basicSalary", detailObject(source, "basic_salary"));
        fillObject(financial, "totalBasicSalary", detailObject(source, "basic_salary"));
        fill(financial, "accountNumber", detail(source, "bank_account_number"));
        fillObject(financial, "overtimePaid", detailObject(source, "overtime_paid"));
        fillObject(
                financial,
                "attendanceBonusEligible",
                detailObject(source, "attendance_bonus_eligible"));
        matchBankDetails(
                financial,
                detail(source, "bank_name"),
                detail(source, "bank_branch"));
        details.put("prefilledFields", general.entrySet().stream()
                .filter(entry -> !Objects.equals(previousGeneral.get(entry.getKey()), entry.getValue()))
                .map(Map.Entry::getKey).toList());
        details.put("prefilledFinancialFields", financial.entrySet().stream()
                .filter(entry -> !Objects.equals(previousFinancial.get(entry.getKey()), entry.getValue()))
                .map(Map.Entry::getKey).toList());
        details.put("unmatchedReferences", unmatchedReferences(general, source));
        store.jdbc()
                .update(
                        """
UPDATE employee_payroll_profiles SET linematrix_employee_id=?,source_employee_number=?,source_details=?::jsonb,general_data=?::jsonb,financial_data=?::jsonb,
registration_status=?,version=version+1,updated_at=now() WHERE employee_id=?
""",
                        UUID.fromString(source.sourceId()),
                        source.employeeNumber(),
                        store.json(details),
                        store.json(general),
                        store.json(financial),
                        "ACTIVE".equals(row.get("status"))
                                        && (!previousGeneral.equals(general)
                                                || !previousFinancial.equals(financial))
                                ? "CHANGES_PENDING"
                                : row.get("status"),
                        id);
        store.audit(
                id,
                "LINEMATRIX_PROFILE_LINKED",
                "PAYROLL_PROFILE",
                id,
                reason,
                List.of("sourceEmployeeId", "sourceDetails"));
    }

    private void match(Map<String, Object> general, String key, String kind, String name) {
        if (name == null || !text(general, key).isBlank()) return;
        // Legacy catalogs can contain several codes for the same displayed label.
        // SetupStore orders by code, so an exact label match remains deterministic
        // without treating a harmless duplicate label as missing employee data.
        store.list(kind).stream()
                .filter(i -> i.active() && i.name().equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(item -> general.put(key, item.id().toString()));
    }

    private void matchShift(Map<String, Object> general, String sourceName) {
        if (sourceName == null || !text(general, "shiftId").isBlank()) return;
        var matches = store.list("SHIFT").stream()
                .filter(i -> i.active() && sourceName.equalsIgnoreCase(Objects.toString(i.data().get("sourceName"), "")))
                .toList();
        if (matches.size() == 1) general.put("shiftId", matches.getFirst().id().toString());
    }

    private void matchLine(Map<String, Object> general, String code, String name) {
        if ((code == null && name == null) || !text(general, "productionLineId").isBlank()) return;
        var matches = store.list("LINE").stream()
                .filter(i -> i.active() && (i.code().equalsIgnoreCase(Objects.toString(code, ""))
                        || i.name().equalsIgnoreCase(Objects.toString(name, ""))))
                .filter(i -> text(general, "departmentId").isBlank()
                        || text(general, "departmentId").equals(i.data().get("departmentId")))
                .toList();
        if (matches.size() == 1) general.put("productionLineId", matches.getFirst().id().toString());
    }

    private List<String> unmatchedReferences(Map<String, Object> general, LineMatrixEmployee source) {
        Map<String, String> references = new LinkedHashMap<>();
        references.put("departmentId", source.department());
        references.put("designationId", source.designation());
        references.put("teamId", source.team());
        references.put("gradeId", source.grade());
        references.put("shiftId", source.shiftName());
        references.put("productionLineId", source.productionLineName() == null
                ? source.productionLineCode() : source.productionLineName());
        return references.entrySet().stream()
                .filter(entry -> entry.getValue() != null && text(general, entry.getKey()).isBlank())
                .map(Map.Entry::getKey).toList();
    }

    private void fill(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank() && text(data, key).isBlank()) data.put(key, value);
    }

    private void fillObject(Map<String, Object> data, String key, Object value) {
        if (value != null && text(data, key).isBlank()) data.put(key, value);
    }

    private String detail(LineMatrixEmployee source, String key) {
        Object value = detailObject(source, key);
        return value == null ? null : value.toString();
    }

    private Object detailObject(LineMatrixEmployee source, String key) {
        return source.payrollDetails() == null ? null : source.payrollDetails().get(key);
    }

    private String firstNonBlank(String... values) {
        for (String value : values)
            if (value != null && !value.isBlank()) return value;
        return null;
    }

    private void matchBankDetails(Map<String, Object> financial, String bankName, String branchName) {
        if (bankName == null || bankName.isBlank()) return;
        if (bankName.equalsIgnoreCase("CASH")) {
            match(financial, "paymentMethodId", "PAYMENT_METHOD", "Cash");
            return;
        }
        match(financial, "paymentMethodId", "PAYMENT_METHOD", "Bank");
        match(financial, "bankId", "BANK", bankName);
        if (branchName == null || branchName.isBlank() || !text(financial, "branchId").isBlank())
            return;
        String bankId = text(financial, "bankId");
        var matches = store.list("BANK_BRANCH").stream()
                .filter(item -> item.active()
                        && normalizeBranchLabel(item.name()).equalsIgnoreCase(branchName.trim()))
                .filter(item -> bankId.isBlank()
                        || bankId.equals(Objects.toString(item.data().get("bankId"), "")))
                .toList();
        if (matches.size() == 1) financial.put("branchId", matches.getFirst().id().toString());
    }

    private String normalizeBranchLabel(String value) {
        return value.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
    }

    private void fillChoice(
            Map<String, Object> data,
            String key,
            String sourceValue,
            Set<String> allowedValues) {
        if (sourceValue == null || sourceValue.isBlank() || !text(data, key).isBlank()) return;
        String normalized = sourceValue.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        normalized =
                switch (normalized) {
                    case "TEAM_MEMBER" -> "WORKER";
                    case "MANAGER" -> "MANAGEMENT";
                    default -> normalized;
                };
        if (allowedValues.contains(normalized)) data.put(key, normalized);
    }

    @Transactional
    public Map<String, Object> activate(UUID id, Action request) {
        SetupAccess.require("PAYROLL_SETUP_EDIT");
        SetupAccess.require("SALARY_EDIT");
        var row = checked(id, request.version());
        String reason = required(request.reason(), "Activation reason", 500);
        if (request.effectiveFrom() != null)
            data(row, "general").put("effectiveFrom", request.effectiveFrom().toString());
        // Re-check a linked source immediately before activation, without overwriting user data.
        if (row.get("sourceId") == null || row.get("sourceEmployeeNumber") == null)
            throw new SetupException("Link a LineMatrix employee before activating payroll setup.");
        LineMatrixEmployee source = lookup.lookup(row.get("sourceEmployeeNumber").toString());
        if (!Objects.equals(source.sourceId(), row.get("sourceId"))
                || !source.active()
                || !"active".equalsIgnoreCase(source.employmentStatus()))
            throw new SetupException(
                    "The linked LineMatrix employee is inactive or their number changed."
                            + " Refresh the link before activation.");
        var missing = readiness(row);
        if (!missing.isEmpty())
            throw new SetupException("Complete payroll setup: " + String.join("; ", missing));
        var general = data(row, "general");
        var financial = data(row, "financial");
        LocalDate from = date(general, "effectiveFrom");
        var previous =
                store.jdbc()
                        .queryForList(
                                "SELECT effective_from FROM payroll_profile_revisions WHERE"
                                        + " employee_id=? ORDER BY effective_from DESC LIMIT 1",
                                id);
        if (!previous.isEmpty()
                && !from.isAfter(
                        ((java.sql.Date) previous.getFirst().get("effective_from")).toLocalDate()))
            throw new SetupException(
                    "New salary/employment setup must start after the latest published effective"
                            + " date. Earlier revisions cannot be overwritten.");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshotReferences(snapshot, catalog.generalFields(), general, from);
        snapshotReferences(snapshot, catalog.financialFields(), financial, from);
        var structure = store.get("SALARY_STRUCTURE", uuid(financial, "salaryStructureId"), from);
        for (var c : componentRows(structure.data().get("components")))
            snapshot.put(
                    c.get("componentId").toString(),
                    store.get("COMPONENT", UUID.fromString(c.get("componentId").toString()), from));
        for (var c : componentRows(financial.get("components")))
            snapshot.put(
                    c.get("componentId").toString(),
                    store.get("COMPONENT", UUID.fromString(c.get("componentId").toString()), from));
        for (UUID rule : ruleIds(financial))
            snapshot.put(rule.toString(), store.get("STATUTORY_RULE", rule, from));
        store.jdbc()
                .update(
                        """
INSERT INTO payroll_profile_revisions(employee_id,effective_from,general_data,financial_data,master_snapshot,created_by,reason)
VALUES (?,?,?::jsonb,?::jsonb,?::jsonb,?,?)
""",
                        id,
                        from,
                        store.json(general),
                        store.json(financial),
                        store.json(snapshot),
                        SetupAccess.actor(),
                        reason);
        // Keep the existing employment-history model connected to payroll registration.
        if (Boolean.TRUE.equals(
                store.jdbc()
                        .queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM employment_records WHERE employee_id=?"
                                        + " AND effective_from>=?)",
                                Boolean.class,
                                id,
                                from)))
            throw new SetupException(
                    "An employment record already exists on or after this effective date.");
        store.jdbc()
                .update(
                        "UPDATE employment_records SET"
                            + " effective_to=?,updated_at=now(),updated_by=?,version=version+1"
                            + " WHERE employee_id=? AND (effective_to IS NULL OR effective_to>=?)",
                        from.minusDays(1),
                        SetupAccess.actor(),
                        id,
                        from);
        store.jdbc()
                .update(
                        """
INSERT INTO employment_records(employee_id,company_id,location_id,department_id,production_line_id,designation_id,employment_type,employee_category,joined_date,effective_from,created_by,updated_by)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
""",
                        id,
                        uuid(general, "companyId"),
                        uuid(general, "locationId"),
                        uuid(general, "departmentId"),
                        optionalUuid(general, "productionLineId"),
                        uuid(general, "designationId"),
                        general.get("employmentType"),
                        general.get("employeeCategory"),
                        date(general, "joinedDate"),
                        from,
                        SetupAccess.actor(),
                        SetupAccess.actor());
        store.jdbc()
                .update(
                        "UPDATE employee_payroll_profiles SET"
                            + " registration_status='ACTIVE',general_data=?::jsonb,version=version+1,updated_at=now()"
                            + " WHERE employee_id=?",
                        store.json(general),
                        id);
        store.jdbc()
                .update(
                        "UPDATE employees SET"
                            + " payroll_status='ACTIVE',version=version+1,updated_at=now(),updated_by=?"
                            + " WHERE id=?",
                        SetupAccess.actor(),
                        id);
        store.audit(
                id,
                "PAYROLL_SETUP_ACTIVATED",
                "PAYROLL_PROFILE",
                id,
                reason,
                List.of("effectiveFrom", "registrationStatus"));
        return get(id);
    }

    @Transactional
    public Map<String, Object> hold(UUID id, Action request) {
        SetupAccess.require("PAYROLL_SETUP_EDIT");
        checked(id, request.version());
        String reason = required(request.reason(), "Hold reason", 500);
        store.jdbc()
                .update(
                        "UPDATE employee_payroll_profiles SET"
                            + " registration_status='HOLD',version=version+1,updated_at=now() WHERE"
                            + " employee_id=?",
                        id);
        store.jdbc()
                .update(
                        "UPDATE employees SET"
                            + " payroll_status='HOLD',version=version+1,updated_at=now(),updated_by=?"
                            + " WHERE id=?",
                        SetupAccess.actor(),
                        id);
        store.audit(
                id,
                "PAYROLL_SETUP_HELD",
                "PAYROLL_PROFILE",
                id,
                reason,
                List.of("registrationStatus"));
        return get(id);
    }

    @Transactional
    public Map<String, Object> resume(UUID id, Action request) {
        SetupAccess.require("PAYROLL_SETUP_EDIT");
        SetupAccess.require("SALARY_EDIT");
        var row = checked(id, request.version());
        String reason = required(request.reason(), "Resume reason", 500);
        if (!"HOLD".equals(row.get("status")))
            throw new SetupException("Only a profile on hold can be resumed.");
        if (!"ACTIVE".equals(row.get("employmentStatus"))
                || !"ACTIVE".equals(row.get("cadreStatus")))
            throw new SetupException("Inactive employees cannot resume payroll.");
        var published =
                store.jdbc()
                        .queryForList(
                                "SELECT general_data,financial_data FROM payroll_profile_revisions"
                                    + " WHERE employee_id=? ORDER BY effective_from DESC LIMIT 1",
                                id);
        if (published.isEmpty())
            throw new SetupException("Publish complete payroll setup before resuming payroll.");
        if (row.get("sourceId") == null || row.get("sourceEmployeeNumber") == null)
            throw new SetupException("Link a LineMatrix employee before resuming payroll.");
        var source = lookup.lookup(row.get("sourceEmployeeNumber").toString());
        if (!Objects.equals(source.sourceId(), row.get("sourceId"))
                || !source.active()
                || !"active".equalsIgnoreCase(source.employmentStatus()))
            throw new SetupException(
                    "Refresh the linked LineMatrix employee before resuming payroll.");
        boolean unchanged =
                data(row, "general")
                                .equals(
                                        store.object(
                                                published
                                                        .getFirst()
                                                        .get("general_data")
                                                        .toString()))
                        && data(row, "financial")
                                .equals(
                                        store.object(
                                                published
                                                        .getFirst()
                                                        .get("financial_data")
                                                        .toString()));
        store.jdbc()
                .update(
                        "UPDATE employee_payroll_profiles SET"
                                + " registration_status=?,version=version+1,updated_at=now() WHERE"
                                + " employee_id=?",
                        unchanged ? "ACTIVE" : "CHANGES_PENDING",
                        id);
        store.jdbc()
                .update(
                        "UPDATE employees SET"
                            + " payroll_status='ACTIVE',version=version+1,updated_at=now(),updated_by=?"
                            + " WHERE id=?",
                        SetupAccess.actor(),
                        id);
        store.audit(
                id,
                "PAYROLL_SETUP_RESUMED",
                "PAYROLL_PROFILE",
                id,
                reason,
                List.of("registrationStatus"));
        return get(id);
    }

    public Map<String, Object> history(UUID id) {
        SetupAccess.require("EMPLOYEE_VIEW_ALL");
        load(id, false);
        var revisions =
                store.jdbc()
                        .query(
                                "SELECT * FROM payroll_profile_revisions WHERE employee_id=? ORDER"
                                        + " BY effective_from DESC",
                                (r, i) -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("id", r.getString("id"));
                                    m.put("effectiveFrom", r.getDate("effective_from").toString());
                                    m.put("general", store.object(r.getString("general_data")));
                                    m.put("actor", r.getString("created_by"));
                                    m.put(
                                            "at",
                                            r.getTimestamp("created_at").toInstant().toString());
                                    m.put("reason", r.getString("reason"));
                                    Map<String, Object> snapshot =
                                            store.object(r.getString("master_snapshot"));
                                    Map<String, String> labels = new LinkedHashMap<>();
                                    var general = store.object(r.getString("general_data"));
                                    Set<String> generalReferences = new HashSet<>();
                                    for (var field : catalog.generalFields())
                                        if (field.type().equals("reference")
                                                && general.containsKey(field.key()))
                                            generalReferences.add(
                                                    general.get(field.key()).toString());
                                    snapshot.forEach(
                                            (key, value) -> {
                                                if (value instanceof Map<?, ?> item
                                                        && (SetupAccess.has("SALARY_VIEW")
                                                                || generalReferences.contains(key)))
                                                    labels.put(
                                                            key,
                                                            Objects.toString(
                                                                    item.get("name"), key));
                                            });
                                    m.put("references", labels);
                                    if (SetupAccess.has("SALARY_VIEW")) {
                                        m.put(
                                                "financial",
                                                maskBank(
                                                        store.object(
                                                                r.getString("financial_data"))));
                                        m.put("masters", snapshot);
                                    }
                                    return m;
                                },
                                id);
        for (int i = 1; i < revisions.size(); i++)
            revisions
                    .get(i)
                    .put(
                            "effectiveTo",
                            LocalDate.parse(revisions.get(i - 1).get("effectiveFrom").toString())
                                    .minusDays(1)
                                    .toString());
        return Map.of("revisions", revisions, "audit", store.auditHistory(id));
    }

    private List<String> readiness(Map<String, Object> row) {
        List<String> missing = new ArrayList<>();
        var g = data(row, "general");
        var f = data(row, "financial");
        for (var field : catalog.generalFields())
            if (field.required() && text(g, field.key()).isBlank()) missing.add(field.label());
        for (var field : catalog.financialFields())
            if (field.required()
                    && (!f.containsKey(field.key())
                            || "".equals(f.get(field.key()))
                            || Boolean.FALSE.equals(f.get(field.key()))))
                missing.add(field.label());
        if (!"ACTIVE".equals(row.get("employmentStatus"))
                || !"ACTIVE".equals(row.get("cadreStatus")))
            missing.add("Employee must have active employment and cadre status");
        Map<String, Object> source = data(row, "source");
        if (row.get("sourceId") == null) missing.add("Link an existing LineMatrix employee");
        if (row.get("sourceId") != null && !Boolean.TRUE.equals(source.get("active")))
            missing.add("Linked LineMatrix employee must be active");
        if (!missing.isEmpty()) return missing;
        try {
            LocalDate at = date(g, "effectiveFrom");
            if (at.isBefore(date(g, "joinedDate")))
                throw new SetupException("Setup effective date cannot precede joined date.");
            masters.validateReferences(catalog.generalFields(), g, at, true);
            masters.validateReferences(catalog.financialFields(), f, at, true);
            masters.validateHierarchy(g, at);
            var shift = store.get("SHIFT", uuid(g, "shiftId"), at);
            if (Boolean.FALSE.equals(shift.data().get("scheduleVerified")))
                throw new SetupException(
                        "Selected legacy shift needs its break, grace and overnight schedule"
                                + " reviewed in Payroll Setup.");
            var structure = store.get("SALARY_STRUCTURE", uuid(f, "salaryStructureId"), at);
            if (!Objects.equals(g.get("companyId"), structure.data().get("companyId"))
                    || !Objects.equals(f.get("payBasis"), structure.data().get("payBasis")))
                throw new SetupException(
                        "Salary structure must match the employee's company and pay basis.");
            masters.validateComponents(structure.data().get("components"), at, false);
            masters.validateComponents(f.get("components"), at, false);
            Set<String> assigned = new HashSet<>();
            for (var c : componentRows(structure.data().get("components")))
                assigned.add(c.get("componentId").toString());
            for (var c : componentRows(f.get("components")))
                if (!assigned.contains(c.get("componentId").toString()))
                    throw new SetupException(
                            "Employee overrides must belong to the selected salary structure.");
            var period = store.get("PAY_PERIOD", uuid(f, "payPeriodId"), at);
            if (!Objects.equals(g.get("companyId"), period.data().get("companyId")))
                throw new SetupException("Payroll period must belong to the employee's company.");
            if (at.isAfter(date(period.data(), "endDate")))
                throw new SetupException(
                        "Choose a payroll period that includes or follows the effective date.");
            validateBank(f, true, at);
            validateStatutory(f, at, true);
            for (UUID rule : ruleIds(f))
                if (Boolean.TRUE.equals(
                                store.get("STATUTORY_RULE", rule, at)
                                        .data()
                                        .get("requiresMemberNumber"))
                        && text(g, "epfNumber").isBlank())
                    throw new SetupException(
                            "A member number is required for the selected statutory rules.");
        } catch (SetupException e) {
            missing.add(e.getMessage());
        }
        return missing;
    }

    private void validateBank(Map<String, Object> f, boolean complete, LocalDate at) {
        if (f.containsKey("branchId")
                && !Objects.equals(
                        store.get("BANK_BRANCH", uuid(f, "branchId"), at).data().get("bankId"),
                        f.get("bankId")))
            throw new SetupException("Branch does not belong to the selected bank.");
        if (complete
                && "BANK"
                        .equals(
                                store.get("PAYMENT_METHOD", uuid(f, "paymentMethodId"), at)
                                        .data()
                                        .get("mode"))) {
            for (String key : List.of("bankId", "accountHolder", "accountNumber"))
                if (text(f, key).isBlank())
                    throw new SetupException(
                            "Bank payment requires bank, account holder and account number.");
            var bank = store.get("BANK", uuid(f, "bankId"), at);
            if (!Boolean.TRUE.equals(bank.data().get("branchOptional"))
                    && text(f, "branchId").isBlank())
                throw new SetupException("Bank payment requires a branch for the selected bank.");
            if (!text(f, "accountNumber").matches("[A-Za-z0-9 -]{4,40}"))
                throw new SetupException("Enter a valid bank account number (4–40 characters).");
        }
    }

    private void validateStatutory(Map<String, Object> f, LocalDate at, boolean complete) {
        var ids = ruleIds(f);
        for (UUID id : ids)
            if (!store.get("STATUTORY_RULE", id, at).active())
                throw new SetupException("An assigned statutory rule is inactive.");
        if (complete && ids.isEmpty() && text(f, "statutoryExemptionReason").isBlank())
            throw new SetupException(
                    "Assign applicable statutory rules or document the approved exemption reason.");
        if (!ids.isEmpty() && !text(f, "statutoryExemptionReason").isBlank())
            throw new SetupException("Use rule assignments or an exemption reason, not both.");
    }

    private List<UUID> ruleIds(Map<String, Object> f) {
        Object value = f.get("statutoryRules");
        if (value == null) return List.of();
        if (!(value instanceof List<?> values))
            throw new SetupException("Invalid statutory rule assignments.");
        try {
            var ids = values.stream().map(v -> UUID.fromString(v.toString())).toList();
            if (new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException();
            return ids;
        } catch (RuntimeException e) {
            throw new SetupException("Statutory rules must be unique valid references.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> componentRows(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    private void snapshotReferences(
            Map<String, Object> snapshot,
            List<SetupCatalog.Field> fields,
            Map<String, Object> data,
            LocalDate at) {
        for (var field : fields)
            if (field.type().equals("reference") && data.containsKey(field.key())) {
                UUID id = uuid(data, field.key());
                snapshot.put(id.toString(), store.get(field.reference(), id, at));
            }
    }

    private Map<String, Object> checked(UUID id, long version) {
        var row = load(id, true);
        if (!Objects.equals(row.get("version"), version)) throw SetupException.conflict();
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> row, String key) {
        return (Map<String, Object>) row.get(key);
    }

    private UUID optionalUuid(Map<String, Object> data, String key) {
        return data.containsKey(key) ? uuid(data, key) : null;
    }

    private Map<String, Object> maskBank(Map<String, Object> f) {
        if (f.containsKey("accountNumber")) {
            String n = text(f, "accountNumber");
            f.put("accountNumber", "••••" + n.substring(Math.max(0, n.length() - 4)));
        }
        return f;
    }
}
