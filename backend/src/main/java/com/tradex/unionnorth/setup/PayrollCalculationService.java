package com.tradex.unionnorth.setup;

import static com.tradex.unionnorth.setup.MasterDataService.*;

import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployeeLookup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PayrollCalculationService {
    public record Request(
            UUID employeeId,
            UUID periodId,
            UUID policyId,
            Map<String, BigDecimal> inputs,
            Map<String, BigDecimal> componentAmounts,
            UUID requestId,
            String reason,
            String previewFingerprint) {}

    private record Context(
            SetupStore.Item period,
            SetupStore.Item policy,
            UUID revisionId,
            Map<String, Object> employee,
            Map<String, Object> financial,
            SetupStore.Item structure,
            List<SetupStore.Item> components,
            List<SetupStore.Item> rules,
            Map<String, Object> overrides) {}

    private final SetupStore store;
    private final LineMatrixEmployeeLookup lookup;

    public PayrollCalculationService(SetupStore store, LineMatrixEmployeeLookup lookup) {
        this.store = store;
        this.lookup = lookup;
    }

    private void viewAccess() {
        SetupAccess.require("PAYROLL_VIEW");
        SetupAccess.require("SALARY_VIEW");
    }

    private void calculateAccess() {
        viewAccess();
        SetupAccess.require("PAYROLL_CALCULATE");
    }

    public List<Map<String, Object>> employees(UUID periodId) {
        viewAccess();
        var period = period(periodId);
        LocalDate start = date(period.data(), "startDate");
        LocalDate end = date(period.data(), "endDate");
        return store.jdbc()
                .query(
                        """
SELECT e.id,e.employee_number,e.display_name,e.first_name,e.last_name,e.payroll_status,
       p.linematrix_employee_id,
       e.employment_status,e.cadre_status,p.registration_status,r.id AS revision_id,r.general_data,
       EXISTS(SELECT 1 FROM payroll_profile_revisions x WHERE x.employee_id=e.id AND x.effective_from>? AND x.effective_from<=?) AS mid_period
FROM employees e JOIN employee_payroll_profiles p ON p.employee_id=e.id
LEFT JOIN LATERAL (SELECT * FROM payroll_profile_revisions x WHERE x.employee_id=e.id AND x.effective_from<=? ORDER BY effective_from DESC LIMIT 1) r ON true
WHERE COALESCE(r.general_data->>'companyId',p.general_data->>'companyId')=?
ORDER BY e.employee_number
""",
                        (r, i) -> {
                            String blocker = "";
                            if (r.getObject("linematrix_employee_id") == null)
                                blocker = "Link a LineMatrix employee";
                            else if (!"ACTIVE".equals(r.getString("payroll_status"))
                                    || "HOLD".equals(r.getString("registration_status")))
                                blocker = "Payroll is on hold";
                            else if (!"ACTIVE".equals(r.getString("employment_status"))
                                    || !"ACTIVE".equals(r.getString("cadre_status")))
                                blocker = "Employee is inactive";
                            else if (r.getObject("revision_id") == null)
                                blocker = "Publish setup effective on or before period start";
                            else {
                                var general = store.object(r.getString("general_data"));
                                if (!"PERMANENT".equals(general.get("employmentType")))
                                    blocker = "Only permanent employees are eligible";
                                else if (date(general, "joinedDate").isAfter(start))
                                    blocker = "Joining date falls after period start";
                                else if (r.getBoolean("mid_period"))
                                    blocker =
                                            "Salary or employment changes during this period"
                                                    + " require a split-period calculation";
                            }
                            return Map.<String, Object>of(
                                    "id",
                                    r.getObject("id").toString(),
                                    "number",
                                    r.getString("employee_number"),
                                    "name",
                                    Objects.toString(
                                            r.getString("display_name"),
                                            r.getString("first_name")
                                                    + " "
                                                    + r.getString("last_name")),
                                    "eligible",
                                    blocker.isBlank(),
                                    "blocker",
                                    blocker);
                        },
                        start,
                        end,
                        start,
                        text(period.data(), "companyId"));
    }

    public Map<String, Object> configuration(UUID employeeId, UUID periodId, UUID policyId) {
        calculateAccess();
        var c = context(employeeId, periodId, policyId, false);
        return Map.of(
                "requiredInputs",
                PayrollCalculator.requiredInputs(c.financial, c.policy.data(), c.components),
                "inputComponents",
                c.components.stream()
                        .filter(
                                item ->
                                        "INPUT".equals(item.data().get("method"))
                                                && PayrollCalculator.eligible(
                                                        item.data(), c.financial))
                        .toList(),
                "structure",
                c.structure,
                "policy",
                c.policy,
                "profileRevisionId",
                c.revisionId,
                "payBasis",
                text(c.financial, "payBasis"));
    }

    public Map<String, Object> preview(Request request) {
        calculateAccess();
        validateRequest(request);
        var c = context(request.employeeId, request.periodId, request.policyId, true);
        var result = calculate(c, request);
        var snapshot = snapshot(c, request);
        return Map.of(
                "result",
                result,
                "snapshot",
                snapshot,
                "previewFingerprint",
                fingerprint(snapshot, result));
    }

    @Transactional
    public Map<String, Object> save(Request request) {
        calculateAccess();
        validateRequest(request);
        if (request.requestId == null)
            throw new SetupException("A unique calculation request ID is required.");
        required(request.reason, "Calculation reason", 500);
        // Serialize a single idempotency key, including simultaneous browser retries.
        store.jdbc()
                .queryForList(
                        "SELECT pg_advisory_xact_lock(hashtext(?))",
                        "calculation-" + request.requestId);
        String requestJson = store.json(request);
        var existing =
                store.jdbc()
                        .queryForList(
                                "SELECT id,request_data=?::jsonb AS same FROM payroll_calculations"
                                        + " WHERE request_id=?",
                                requestJson,
                                request.requestId);
        if (!existing.isEmpty()) {
            if (!Boolean.TRUE.equals(existing.getFirst().get("same")))
                throw new SetupException(
                        "This request ID was used with different inputs. Refresh and submit"
                                + " again.");
            return detail((UUID) existing.getFirst().get("id"));
        }
        var locked =
                store.jdbc()
                        .queryForList(
                                "SELECT employee_id FROM employee_payroll_profiles WHERE"
                                        + " employee_id=? FOR UPDATE",
                                request.employeeId);
        if (locked.isEmpty()) throw SetupException.missing();
        var c = context(request.employeeId, request.periodId, request.policyId, true);
        var result = calculate(c, request);
        var snapshot = snapshot(c, request);
        if (!Objects.equals(request.previewFingerprint, fingerprint(snapshot, result)))
            throw new SetupException(
                    "Preview the current inputs and rules again before saving. The calculation may"
                            + " have changed.");
        UUID id = UUID.randomUUID();
        store.jdbc()
                .update(
                        """
INSERT INTO payroll_calculations(id,request_id,request_data,employee_id,period_id,profile_revision_id,policy_id,snapshot,result,created_by,reason)
VALUES (?,?,?::jsonb,?,?,?,?,?::jsonb,?::jsonb,?,?)
""",
                        id,
                        request.requestId,
                        requestJson,
                        request.employeeId,
                        request.periodId,
                        c.revisionId,
                        request.policyId,
                        store.json(snapshot),
                        store.json(result),
                        SetupAccess.actor(),
                        request.reason.trim());
        store.audit(
                request.employeeId,
                "PAYROLL_CALCULATED",
                "PAYROLL_CALCULATION",
                id,
                request.reason.trim(),
                List.of("period", "inputs", "result"));
        return detail(id);
    }

    public List<Map<String, Object>> history(UUID periodId) {
        viewAccess();
        return store.jdbc()
                .query(
                        """
SELECT c.id,c.employee_id,e.employee_number,e.display_name,c.created_at,c.created_by,c.result,
       NOT EXISTS(SELECT 1 FROM payroll_calculations n WHERE n.employee_id=c.employee_id AND n.period_id=c.period_id
         AND (n.created_at,n.id)>(c.created_at,c.id)) AS latest
FROM payroll_calculations c JOIN employees e ON e.id=c.employee_id
WHERE c.period_id=? ORDER BY c.created_at DESC,c.id DESC LIMIT 1000
""",
                        (r, i) ->
                                Map.<String, Object>of(
                                        "id",
                                        r.getObject("id").toString(),
                                        "employeeId",
                                        r.getObject("employee_id").toString(),
                                        "employeeNumber",
                                        r.getString("employee_number"),
                                        "name",
                                        Objects.toString(
                                                r.getString("display_name"),
                                                r.getString("employee_number")),
                                        "createdAt",
                                        r.getTimestamp("created_at").toInstant().toString(),
                                        "actor",
                                        r.getString("created_by"),
                                        "latest",
                                        r.getBoolean("latest"),
                                        "result",
                                        store.object(r.getString("result"))),
                        periodId);
    }

    public Map<String, Object> detail(UUID id) {
        viewAccess();
        var rows =
                store.jdbc()
                        .query(
                                "SELECT * FROM payroll_calculations WHERE id=?",
                                (r, i) ->
                                        Map.<String, Object>of(
                                                "id",
                                                r.getObject("id").toString(),
                                                "employeeId",
                                                r.getObject("employee_id").toString(),
                                                "periodId",
                                                r.getObject("period_id").toString(),
                                                "result",
                                                store.object(r.getString("result")),
                                                "snapshot",
                                                store.object(r.getString("snapshot")),
                                                "createdAt",
                                                r.getTimestamp("created_at").toInstant().toString(),
                                                "actor",
                                                r.getString("created_by"),
                                                "reason",
                                                r.getString("reason")),
                                id);
        if (rows.isEmpty()) throw SetupException.missing();
        return rows.getFirst();
    }

    public Map<String, Object> equations(UUID structureId, UUID policyId, LocalDate at) {
        viewAccess();
        if (at == null) throw new SetupException("Select an effective date.");
        var structure = active("SALARY_STRUCTURE", structureId, at);
        var policy = active("CALCULATION_POLICY", policyId, at);
        if (!Objects.equals(structure.data().get("companyId"), policy.data().get("companyId")))
            throw new SetupException("Structure and policy must belong to the same company.");
        List<SetupStore.Item> components = components(structure, at);
        List<SetupStore.Item> rules =
                store.list("STATUTORY_RULE").stream()
                        .filter(item -> existsAt(item.id(), at))
                        .map(item -> store.get("STATUTORY_RULE", item.id(), at))
                        .filter(SetupStore.Item::active)
                        .toList();
        return Map.of(
                "at",
                at.toString(),
                "structure",
                structure,
                "policy",
                policy,
                "components",
                components,
                "rules",
                rules,
                "variables",
                new TreeSet<>(PayrollFormula.VARIABLES),
                "notes",
                List.of(
                        "Basic is basic pay plus calculated BRA1 and BRA2.",
                        "Earnings are calculated before deductions. Employer contributions do not"
                                + " reduce net pay.",
                        "Structure values override component defaults; published employee values"
                                + " override structure values.",
                        "Only the statutory rules selected in each employee's published profile are"
                                + " applied.",
                        "Component eligibility uses published employee flags. Period inputs must be"
                                + " reviewed before saving.",
                        "Tiered rules use progressive employee bands; the employer rate is a"
                                + " percentage of the selected basis.",
                        "Every line is rounded to two decimals before totals are added."));
    }

    private boolean existsAt(UUID id, LocalDate at) {
        return Boolean.TRUE.equals(
                store.jdbc()
                        .queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM setup_item_revisions WHERE item_id=?"
                                        + " AND effective_from<=?)",
                                Boolean.class,
                                id,
                                at));
    }

    private SetupStore.Item period(UUID id) {
        if (id == null) throw new SetupException("Select a payroll period.");
        var latest = store.get("PAY_PERIOD", id, null);
        var period = active("PAY_PERIOD", id, date(latest.data(), "startDate"));
        if (!period.data().equals(latest.data()))
            throw new SetupException(
                    "Period dates changed after its start. Configure a period effective on or"
                            + " before its start date.");
        return period;
    }

    private SetupStore.Item active(String kind, UUID id, LocalDate at) {
        if (id == null)
            throw new SetupException(
                    "Select " + kind.toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
        var item = store.get(kind, id, at);
        if (!item.active()) throw new SetupException(item.name() + " is inactive for this period.");
        return item;
    }

    private List<SetupStore.Item> components(SetupStore.Item structure, LocalDate at) {
        var result = new ArrayList<SetupStore.Item>();
        var ids = new HashSet<UUID>();
        for (var row : PayrollCalculator.rows(structure.data().get("components"))) {
            UUID id = uuid(row, "componentId");
            if (!ids.add(id))
                throw new SetupException("The salary structure contains a duplicate component.");
            result.add(active("COMPONENT", id, at));
        }
        return result;
    }

    private Context context(UUID employee, UUID periodId, UUID policyId, boolean recheckSource) {
        var period = period(periodId);
        LocalDate start = date(period.data(), "startDate"), end = date(period.data(), "endDate");
        var policy = active("CALCULATION_POLICY", policyId, start);
        var found =
                store.jdbc()
                        .queryForList(
                                """
SELECT e.employee_number,e.display_name,e.payroll_status,e.employment_status,e.cadre_status,p.registration_status,
       p.linematrix_employee_id,p.source_employee_number
FROM employees e JOIN employee_payroll_profiles p ON p.employee_id=e.id WHERE e.id=?
""",
                                employee);
        if (found.isEmpty()) throw SetupException.missing();
        var state = found.getFirst();
        if (state.get("linematrix_employee_id") == null
                || state.get("source_employee_number") == null)
            throw new SetupException("Link a LineMatrix employee before payroll calculation.");
        if (!"ACTIVE".equals(state.get("payroll_status"))
                || "HOLD".equals(state.get("registration_status"))
                || !"ACTIVE".equals(state.get("employment_status"))
                || !"ACTIVE".equals(state.get("cadre_status")))
            throw new SetupException(
                    "Only active employees with published payroll setup can be calculated. Resolve"
                            + " payroll holds first.");
        var revisions =
                store.jdbc()
                        .queryForList(
                                "SELECT * FROM payroll_profile_revisions WHERE employee_id=? AND"
                                        + " effective_from<=? ORDER BY effective_from DESC LIMIT 1",
                                employee,
                                start);
        if (revisions.isEmpty())
            throw new SetupException(
                    "Publish employee setup effective on or before the period start.");
        var r = revisions.getFirst();
        var general = store.object(r.get("general_data").toString());
        var financial = store.object(r.get("financial_data").toString());
        if (!"PERMANENT".equals(general.get("employmentType")))
            throw new SetupException(
                    "Only permanent employees are eligible for payroll calculation.");
        if (date(general, "joinedDate").isAfter(start))
            throw new SetupException(
                    "Joining date falls after period start. A split-period calculation is"
                            + " required.");
        if (Boolean.TRUE.equals(
                store.jdbc()
                        .queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM payroll_profile_revisions WHERE"
                                    + " employee_id=? AND effective_from>? AND effective_from<=?)",
                                Boolean.class,
                                employee,
                                start,
                                end)))
            throw new SetupException(
                    "Salary or employment changes during this period require a split-period"
                            + " calculation.");
        if (!Objects.equals(general.get("companyId"), period.data().get("companyId"))
                || !Objects.equals(general.get("companyId"), policy.data().get("companyId")))
            throw new SetupException(
                    "Employee, period and calculation policy must belong to the same company.");
        if (recheckSource) {
            var source = lookup.lookup(Objects.toString(state.get("source_employee_number"), ""));
            if (!source.active()
                    || !source.isPermanent()
                    || !"active".equalsIgnoreCase(source.employmentStatus())
                    || !Objects.equals(
                            source.sourceId(), state.get("linematrix_employee_id").toString()))
                throw new SetupException(
                        "The linked employee is no longer active and permanent. Refresh the"
                                + " employee profile.");
        }
        var structure = active("SALARY_STRUCTURE", uuid(financial, "salaryStructureId"), start);
        if (!Objects.equals(structure.data().get("companyId"), general.get("companyId"))
                || !Objects.equals(structure.data().get("payBasis"), financial.get("payBasis")))
            throw new SetupException(
                    "Salary structure company or pay basis does not match the published employee"
                            + " setup.");
        var components = components(structure, start);
        Set<String> ids = new HashSet<>();
        components.forEach(c -> ids.add(c.id().toString()));
        var overrides = new LinkedHashMap<String, Object>();
        for (var assignments :
                List.of(
                        PayrollCalculator.rows(structure.data().get("components")),
                        PayrollCalculator.rows(financial.get("components")))) {
            for (var row : assignments) {
                String id = text(row, "componentId");
                if (!ids.contains(id))
                    throw new SetupException(
                            "A published employee override is no longer in the effective salary"
                                    + " structure. Publish reviewed employee setup.");
                if (row.get("value") != null && !"".equals(row.get("value")))
                    overrides.put(id, row.get("value"));
            }
        }
        var rules = new ArrayList<SetupStore.Item>();
        var ruleIds = new HashSet<UUID>();
        if (financial.get("statutoryRules") instanceof List<?> values)
            for (var value : values) {
                UUID id = UUID.fromString(value.toString());
                if (!ruleIds.add(id)) throw new SetupException("Duplicate statutory rule.");
                rules.add(active("STATUTORY_RULE", id, start));
            }
        return new Context(
                period,
                policy,
                (UUID) r.get("id"),
                Map.of(
                        "id",
                        employee.toString(),
                        "number",
                        state.get("employee_number"),
                        "name",
                        state.get("display_name")),
                financial,
                structure,
                components,
                rules,
                overrides);
    }

    private void validateRequest(Request r) {
        if (r == null
                || r.employeeId == null
                || r.periodId == null
                || r.policyId == null
                || r.inputs == null
                || r.componentAmounts == null)
            throw new SetupException(
                    "Employee, payroll period, policy and input maps are required.");
    }

    private String fingerprint(Map<String, Object> snapshot, PayrollCalculator.Result result) {
        // Canonical JSON avoids map iteration order differences. This is a stale-preview check,
        // not an authorization token; permissions and all calculations are rechecked on save.
        try {
            var bytes =
                    canonical(
                                    store.object(
                                            store.json(
                                                    Map.of(
                                                            "snapshot",
                                                            snapshot,
                                                            "result",
                                                            result))))
                            .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var ordered = new TreeMap<String, Object>();
            map.forEach((key, item) -> ordered.put(key.toString(), item));
            return ordered.entrySet().stream()
                    .map(e -> store.json(e.getKey()) + ":" + canonical(e.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof List<?> list)
            return list.stream()
                    .map(this::canonical)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return store.json(value);
    }

    private PayrollCalculator.Result calculate(Context c, Request r) {
        return PayrollCalculator.calculate(
                c.financial,
                c.policy.data(),
                c.components,
                c.rules,
                c.overrides,
                r.inputs,
                r.componentAmounts,
                ChronoUnit.DAYS.between(
                                date(c.period.data(), "startDate"),
                                date(c.period.data(), "endDate"))
                        + 1);
    }

    private Map<String, Object> snapshot(Context c, Request r) {
        // Deliberately store calculation inputs only; bank accounts/NIC/contact data are
        // unnecessary.
        var salary = new LinkedHashMap<>(c.financial);
        salary.keySet()
                .retainAll(
                        Set.of(
                                "payBasis",
                                "basicSalary",
                                "bra1",
                                "bra2",
                                "components",
                                "taxExempted",
                                "overtimePaid",
                                "holidayPaymentEligible",
                                "attendanceBonusEligible",
                                "overtimeAllowanceEligible",
                                "statutoryRules",
                                "statutoryExemptionReason"));
        return Map.of(
                "employee",
                c.employee,
                "period",
                c.period,
                "policy",
                c.policy,
                "profileRevisionId",
                c.revisionId,
                "salary",
                salary,
                "structure",
                c.structure,
                "components",
                c.components,
                "rules",
                c.rules,
                "inputs",
                r.inputs,
                "componentAmounts",
                r.componentAmounts);
    }
}
