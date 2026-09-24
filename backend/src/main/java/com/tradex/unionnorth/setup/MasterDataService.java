package com.tradex.unionnorth.setup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class MasterDataService {
    public record Save(
            String code,
            String name,
            boolean active,
            Long version,
            LocalDate effectiveFrom,
            Map<String, Object> data,
            String reason) {}

    private final SetupStore store;
    private final SetupCatalog catalog;

    public MasterDataService(SetupStore store, SetupCatalog catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    public List<SetupStore.Item> list(String kind) {
        SetupAccess.require(
                catalog.definition(kind).financial() ? "SALARY_VIEW" : "EMPLOYEE_VIEW_ALL");
        return store.list(kind);
    }

    public Map<String, Object> history(String kind, UUID id) {
        SetupAccess.require(
                catalog.definition(kind).financial() ? "SALARY_VIEW" : "EMPLOYEE_VIEW_ALL");
        store.get(kind, id, null);
        List<Map<String, Object>> revisions =
                catalog.organization(kind)
                        ? List.of()
                        : store.jdbc()
                                .query(
                                        """
SELECT name,active,effective_from,data,created_by,created_at,reason FROM setup_item_revisions
WHERE item_id=? ORDER BY effective_from DESC
""",
                                        (r, i) ->
                                                new java.util.LinkedHashMap<>(
                                                        Map.of(
                                                                "name",
                                                                r.getString("name"),
                                                                "active",
                                                                r.getBoolean("active"),
                                                                "effectiveFrom",
                                                                r.getDate("effective_from")
                                                                        .toString(),
                                                                "data",
                                                                store.object(r.getString("data")),
                                                                "actor",
                                                                r.getString("created_by"),
                                                                "at",
                                                                r.getTimestamp("created_at")
                                                                        .toInstant()
                                                                        .toString(),
                                                                "reason",
                                                                r.getString("reason"))),
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

    @Transactional
    public SetupStore.Item save(String kind, UUID id, Save request) {
        var definition = catalog.definition(kind);
        SetupAccess.require(
                definition.group().equals("Organization")
                        ? "ORGANIZATION_MANAGE"
                        : "PAYROLL_SETUP_EDIT");
        String code = required(request.code(), "Code", 30);
        String name = required(request.name(), "Name", 160);
        String reason = required(request.reason(), "Reason for change", 500);
        if (!code.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))
            throw new SetupException(
                    "Code must contain only letters, numbers, dots, underscores or hyphens.");
        LocalDate from = request.effectiveFrom();
        if (!catalog.organization(kind) && from == null)
            throw new SetupException("Effective date is required.");
        var data = catalog.validate(definition.fields(), request.data(), true);
        if (id != null) {
            var before = store.get(kind, id, null);
            if (request.version() == null || before.version() != request.version())
                throw SetupException.conflict();
            if (!before.code().equals(code))
                throw new SetupException(
                        "Codes cannot be changed. Create a new record if the identity changes.");
            if (before.effectiveFrom() != null && !from.isAfter(before.effectiveFrom()))
                throw new SetupException(
                        "A revision must start after the latest effective date. Existing revisions"
                                + " are immutable.");
            if (catalog.organization(kind)) {
                for (var f : definition.fields())
                    if (f.type().equals("reference")
                            && !Objects.equals(before.data().get(f.key()), data.get(f.key())))
                        throw new SetupException(
                                "An organization's parent cannot be changed. Create a new record"
                                        + " and move employee assignments with an effective date.");
            }
        }
        validateReferences(definition.fields(), data, from, request.active());
        validateHierarchy(data, from);
        validateRules(kind, data, from);
        if (kind.equals("PAY_PERIOD") && request.active()) {
            // Serialize conflicting period creation even across concurrent requests.
            store.jdbc()
                    .queryForList(
                            "SELECT pg_advisory_xact_lock(hashtext(?))",
                            "payroll-period-" + data.get("companyId"));
            for (var existing : store.list(kind)) {
                if (existing.id().equals(id)
                        || !existing.active()
                        || !Objects.equals(existing.data().get("companyId"), data.get("companyId")))
                    continue;
                if (!date(data, "startDate").isAfter(date(existing.data(), "endDate"))
                        && !date(data, "endDate").isBefore(date(existing.data(), "startDate")))
                    throw new SetupException(
                            "Payroll periods for the same company cannot overlap.");
            }
        }
        return store.write(
                kind,
                id,
                code,
                name,
                request.active(),
                request.version() == null ? 0 : request.version(),
                from,
                data,
                reason);
    }

    void validateReferences(
            List<SetupCatalog.Field> fields,
            Map<String, Object> data,
            LocalDate at,
            boolean active) {
        for (var field : fields)
            if (field.type().equals("reference") && data.containsKey(field.key())) {
                var item = store.get(field.reference(), uuid(data, field.key()), at);
                if (active && !item.active())
                    throw new SetupException(field.label() + " is inactive on the effective date.");
            }
    }

    void validateHierarchy(Map<String, Object> data, LocalDate at) {
        Object company = data.get("companyId");
        for (String key :
                List.of(
                        "locationId",
                        "departmentId",
                        "designationId",
                        "costCentreId",
                        "gradeId",
                        "sectionId",
                        "teamId")) {
            if (company == null || !data.containsKey(key)) continue;
            String kind =
                    Map.of(
                                    "locationId",
                                    "LOCATION",
                                    "departmentId",
                                    "DEPARTMENT",
                                    "designationId",
                                    "DESIGNATION",
                                    "costCentreId",
                                    "COST_CENTRE",
                                    "gradeId",
                                    "GRADE",
                                    "sectionId",
                                    "SECTION",
                                    "teamId",
                                    "TEAM")
                            .get(key);
            if (!company.equals(store.get(kind, uuid(data, key), at).data().get("companyId")))
                throw new SetupException(
                        "Selected organization records must belong to the same company.");
        }
        if (data.containsKey("locationId") && data.containsKey("departmentId")) {
            Object deptLocation =
                    store.get("DEPARTMENT", uuid(data, "departmentId"), null)
                            .data()
                            .get("locationId");
            if (deptLocation != null && !deptLocation.equals(data.get("locationId")))
                throw new SetupException("Department does not belong to the selected location.");
        }
        if (data.containsKey("productionLineId")
                && !Objects.equals(
                        store.get("LINE", uuid(data, "productionLineId"), null)
                                .data()
                                .get("departmentId"),
                        data.get("departmentId")))
            throw new SetupException("Production line does not belong to the selected department.");
    }

    void validateRules(String kind, Map<String, Object> d, LocalDate at) {
        if (kind.equals("COMPONENT")) {
            boolean earning = "EARNING".equals(d.get("category"));
            if ("FORMULA".equals(d.get("method")))
                PayrollFormula.parse(text(d, "formula"), earning);
            else if (!text(d, "formula").isBlank())
                throw new SetupException("A formula requires the FORMULA calculation method.");
            if ("PERCENTAGE".equals(d.get("method"))) {
                if (!d.containsKey("basis")
                        || number(d, "value").compareTo(BigDecimal.valueOf(100)) > 0)
                    throw new SetupException(
                            "Percentage components require a basis and a rate from 0 to 100.");
                if (earning && !"BASIC".equals(d.get("basis")))
                    throw new SetupException(
                            "Percentage earnings must use BASIC to avoid circular gross or taxable"
                                + " totals. Use a formula for other non-circular bases.");
            }
        }
        if (kind.equals("CALCULATION_POLICY")
                && "FIXED".equals(d.get("dayDivisor"))
                && (!d.containsKey("fixedDays") || number(d, "fixedDays").signum() <= 0))
            throw new SetupException("A fixed day divisor must be greater than zero.");
        if (kind.equals("SALARY_STRUCTURE")) validateComponents(d.get("components"), at, false);
        if (kind.equals("ALLOWANCE") || kind.equals("DEDUCTION")) {
            String expected = kind.equals("ALLOWANCE") ? "EARNING" : "DEDUCTION";
            if (!expected.equals(
                    store.get("COMPONENT", uuid(d, "componentId"), at).data().get("category")))
                throw new SetupException("Component category does not match this master type.");
        }
        if (kind.equals("LOAN_TYPE")) {
            if (number(d, "maxAmount").signum() <= 0
                    || number(d, "maxInstallments").signum() <= 0
                    || number(d, "maxInstallments").stripTrailingZeros().scale() > 0)
                throw new SetupException(
                        "Loan maximum amount and whole-number installments must be positive.");
            if ("NONE".equals(d.get("interestMethod")) && number(d, "annualRate").signum() != 0)
                throw new SetupException("Interest-free loans must have a zero rate.");
        }
        if (kind.equals("SHIFT")) {
            if (Boolean.TRUE.equals(d.get("notApplicable"))) {
                if (!text(d, "startTime").equals(text(d, "endTime"))
                        || number(d, "breakMinutes").signum() != 0
                        || number(d, "graceMinutes").signum() != 0
                        || Boolean.TRUE.equals(d.get("overnight")))
                    throw new SetupException(
                            "A not-applicable shift must have matching times and zero break and"
                                    + " grace minutes.");
            } else {
                long duration =
                        ChronoUnit.MINUTES.between(
                                LocalTime.parse(text(d, "startTime")),
                                LocalTime.parse(text(d, "endTime")));
                if (Boolean.TRUE.equals(d.get("overnight"))) duration += 1440;
                if (duration <= 0
                        || duration > 1440
                        || number(d, "breakMinutes").compareTo(BigDecimal.valueOf(duration)) >= 0
                        || number(d, "graceMinutes").compareTo(BigDecimal.valueOf(duration)) >= 0)
                    throw new SetupException(
                            "Check shift times, overnight setting, break and grace minutes.");
            }
        }
        if (kind.equals("STATUTORY_RULE")) {
            if (!"FIXED".equals(d.get("method"))
                    && (number(d, "employeeRate").compareTo(BigDecimal.valueOf(100)) > 0
                            || number(d, "employerRate").compareTo(BigDecimal.valueOf(100)) > 0))
                throw new SetupException("Percentage rates cannot exceed 100.");
            if ("TIERED".equals(d.get("method"))) validateBands(d.get("bands"));
            else if (d.get("bands") instanceof List<?> l && !l.isEmpty())
                throw new SetupException("Bands are supported only for tiered rules.");
        }
        if (kind.equals("PAY_PERIOD")) {
            if (date(d, "endDate").isBefore(date(d, "startDate"))
                    || date(d, "cutoffDate").isBefore(date(d, "startDate"))
                    || date(d, "paymentDate").isBefore(date(d, "cutoffDate"))
                    || date(d, "paymentDate").isBefore(date(d, "endDate")))
                throw new SetupException(
                        "Period end must follow start, and payment must follow period end and input"
                                + " cutoff.");
        }
    }

    void validateComponents(Object value, LocalDate at, boolean required) {
        if (!(value instanceof List<?> rows)) {
            if (required) throw new SetupException("Select at least one recurring component.");
            return;
        }
        if (required && rows.isEmpty())
            throw new SetupException("Select at least one recurring component.");
        Set<UUID> ids = new HashSet<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> m)
                    || !Set.of("componentId", "value").containsAll(m.keySet()))
                throw new SetupException("Invalid component assignment.");
            try {
                UUID id = UUID.fromString(Objects.toString(m.get("componentId"), ""));
                if (!ids.add(id))
                    throw new SetupException("The same component cannot be assigned twice.");
                var item = store.get("COMPONENT", id, at);
                if (!item.active()) throw new SetupException("An assigned component is inactive.");
                if (m.get("value") != null && !"".equals(m.get("value"))) {
                    if (Set.of("FORMULA", "INPUT").contains(item.data().get("method")))
                        throw new SetupException(
                                "Formula and period-input components cannot have recurring value"
                                    + " overrides.");
                    BigDecimal n = new BigDecimal(m.get("value").toString());
                    if (n.signum() < 0
                            || n.scale() > 4
                            || n.compareTo(new BigDecimal("9999999999")) > 0
                            || ("PERCENTAGE".equals(item.data().get("method"))
                                    && n.compareTo(BigDecimal.valueOf(100)) > 0))
                        throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                throw new SetupException(
                        "Component values must be valid non-negative amounts or percentages.");
            }
        }
    }

    private void validateBands(Object value) {
        if (!(value instanceof List<?> bands) || bands.isEmpty())
            throw new SetupException("Tiered rules require progressive bands.");
        BigDecimal previous = BigDecimal.ZERO;
        for (int i = 0; i < bands.size(); i++) {
            if (!(bands.get(i) instanceof Map<?, ?> b)
                    || !Set.of("upperBound", "rate").containsAll(b.keySet()))
                throw new SetupException("Invalid statutory band.");
            try {
                BigDecimal rate = new BigDecimal(Objects.toString(b.get("rate"), ""));
                if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0)
                    throw new IllegalArgumentException();
                Object upper = b.get("upperBound");
                if (upper == null || "".equals(upper)) {
                    if (i != bands.size() - 1) throw new IllegalArgumentException();
                } else {
                    BigDecimal limit = new BigDecimal(upper.toString());
                    if (limit.compareTo(previous) <= 0 || i == bands.size() - 1)
                        throw new IllegalArgumentException();
                    previous = limit;
                }
            } catch (IllegalArgumentException e) {
                throw new SetupException(
                        "Band limits must increase, rates must be 0–100, and the final band must"
                                + " have no upper limit.");
            }
        }
    }

    static String required(String value, String label, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new SetupException(label + " is required (maximum " + max + " characters).");
        return value.trim();
    }

    static String text(Map<String, Object> d, String key) {
        return Objects.toString(d.get(key), "");
    }

    static UUID uuid(Map<String, Object> d, String key) {
        try {
            return UUID.fromString(text(d, key));
        } catch (IllegalArgumentException e) {
            throw new SetupException("Invalid reference: " + key);
        }
    }

    static LocalDate date(Map<String, Object> d, String key) {
        try {
            return LocalDate.parse(text(d, key));
        } catch (RuntimeException e) {
            throw new SetupException("Invalid date: " + key);
        }
    }

    static BigDecimal number(Map<String, Object> d, String key) {
        try {
            return new BigDecimal(text(d, key));
        } catch (RuntimeException e) {
            throw new SetupException("Invalid amount: " + key);
        }
    }
}
