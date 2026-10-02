package com.tradex.unionnorth.setup;

import com.tradex.unionnorth.security.WorkforceAccess;
import com.tradex.unionnorth.security.domain.WorkforceGroup;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each pay period has independent, audited approval for each workforce group. */
@Service
public class PayrollGroupApprovalService {
    public record Approval(UUID id, UUID periodId, WorkforceGroup workforceGroup, String status,
                           List<UUID> calculationIds, UUID submittedBy, UUID approvedBy) {}
    public record Event(UUID id, UUID periodId, WorkforceGroup workforceGroup, String action,
                        List<UUID> calculationIds, UUID actorId, String reason, String createdAt) {}
    private final JdbcTemplate jdbc;
    private final WorkforceAccess access;
    private final PayrollCalculationService calculations;

    public PayrollGroupApprovalService(JdbcTemplate jdbc, WorkforceAccess access,
                                       PayrollCalculationService calculations) {
        this.jdbc = jdbc;
        this.access = access;
        this.calculations = calculations;
    }

    public List<Approval> list(UUID periodId) {
        SetupAccess.require("PAYROLL_VIEW");
        var groups = access.groups(WorkforceAccess.Action.VIEW);
        return jdbc.query("""
            SELECT id,period_id,workforce_group,status,calculation_ids,submitted_by,approved_by
            FROM payroll.payroll_group_approvals WHERE period_id=? AND workforce_group IN (?,?)
            ORDER BY workforce_group
            """, (r, index) -> new Approval(r.getObject("id", UUID.class),
                    r.getObject("period_id", UUID.class), WorkforceGroup.valueOf(r.getString("workforce_group")),
                    r.getString("status"), ids(r.getArray("calculation_ids")),
                    r.getObject("submitted_by", UUID.class), r.getObject("approved_by", UUID.class)),
                periodId, groups.contains(WorkforceGroup.EXECUTIVE_STAFF) ? "EXECUTIVE_STAFF" : "",
                groups.contains(WorkforceGroup.GENERAL_WORKFORCE) ? "GENERAL_WORKFORCE" : "");
    }

    public List<Event> events(UUID periodId) {
        SetupAccess.require("PAYROLL_VIEW");
        var groups = access.groups(WorkforceAccess.Action.VIEW);
        return jdbc.query("""
            SELECT id,period_id,workforce_group,action,calculation_ids,actor_id,reason,created_at
            FROM payroll.payroll_group_approval_events
            WHERE period_id=? AND workforce_group IN (?,?)
            ORDER BY created_at DESC,id DESC
            """, (r, index) -> new Event(r.getObject("id", UUID.class),
                r.getObject("period_id", UUID.class), WorkforceGroup.valueOf(r.getString("workforce_group")),
                r.getString("action"), ids(r.getArray("calculation_ids")),
                r.getObject("actor_id", UUID.class), r.getString("reason"),
                r.getTimestamp("created_at").toInstant().toString()),
                periodId, groups.contains(WorkforceGroup.EXECUTIVE_STAFF) ? "EXECUTIVE_STAFF" : "",
                groups.contains(WorkforceGroup.GENERAL_WORKFORCE) ? "GENERAL_WORKFORCE" : "");
    }

    @Transactional
    public void submit(UUID periodId, WorkforceGroup group, String reason) {
        SetupAccess.require("PAYROLL_CALCULATE");
        access.require(group, WorkforceAccess.Action.EDIT);
        String checkedReason = reason(reason);
        lock(periodId, group);
        String status = status(periodId, group);
        if ("APPROVED".equals(status) || "SUBMITTED".equals(status))
            throw new SetupException("This group's payroll is already submitted or approved.");
        var saved = jdbc.queryForList("""
            SELECT DISTINCT ON (c.employee_id) c.id
            FROM payroll.payroll_calculations c
            JOIN payroll.employees e ON e.id=c.employee_id
            WHERE c.period_id=? AND c.workforce_group_snapshot=? AND e.workforce_group=?
            ORDER BY c.employee_id,c.created_at DESC,c.id DESC
            """, UUID.class, periodId, group.name(), group.name());
        if (saved.isEmpty()) throw new SetupException("Calculate at least one employee before submission.");
        Set<UUID> eligible = calculations.employees(periodId).stream()
                .filter(row -> group.name().equals(row.get("workforceGroup")))
                .filter(row -> Boolean.TRUE.equals(row.get("eligible")))
                .map(row -> UUID.fromString(row.get("id").toString()))
                .collect(Collectors.toSet());
        Set<UUID> calculated = Set.copyOf(jdbc.queryForList("""
            SELECT DISTINCT c.employee_id FROM payroll.payroll_calculations c
            JOIN payroll.employees e ON e.id=c.employee_id
            WHERE c.period_id=? AND c.workforce_group_snapshot=? AND e.workforce_group=?
            """, UUID.class, periodId, group.name(), group.name()));
        if (!calculated.equals(eligible))
            throw new SetupException("Calculate every eligible employee and resolve outdated calculations before submission.");
        UUID actor = WorkforceAccess.actor();
        UUID approvalId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO payroll.payroll_group_approvals
              (id,period_id,workforce_group,status,calculation_ids,submitted_by)
            VALUES (?, ?, ?, 'SUBMITTED', ?, ?)
            ON CONFLICT (period_id,workforce_group) DO UPDATE SET
              status='SUBMITTED',calculation_ids=excluded.calculation_ids,
              submitted_by=excluded.submitted_by,submitted_at=now(),approved_by=NULL,
              approved_at=NULL,rejection_reason=NULL
            """, approvalId, periodId, group.name(), array(saved), actor);
        UUID actualId = jdbc.queryForObject("""
            SELECT id FROM payroll.payroll_group_approvals WHERE period_id=? AND workforce_group=?
            """, UUID.class, periodId, group.name());
        event(actualId, periodId, group, "SUBMITTED", saved, actor, checkedReason);
    }

    @Transactional
    public void approve(UUID periodId, WorkforceGroup group, String reason) {
        SetupAccess.require("PAYROLL_APPROVE");
        access.require(group, WorkforceAccess.Action.APPROVE);
        String checkedReason = reason(reason);
        lock(periodId, group);
        var rows = jdbc.queryForList("""
            SELECT id,status,submitted_by,calculation_ids FROM payroll.payroll_group_approvals
            WHERE period_id=? AND workforce_group=? FOR UPDATE
            """, periodId, group.name());
        if (rows.isEmpty() || !"SUBMITTED".equals(rows.getFirst().get("status")))
            throw new SetupException("Submit this group's payroll before approval.");
        var row = rows.getFirst();
        UUID actor = WorkforceAccess.actor();
        if (actor.equals(row.get("submitted_by")))
            throw new SetupException("A different account must approve this payroll.");
        UUID id = (UUID) row.get("id");
        jdbc.update("""
            UPDATE payroll.payroll_group_approvals SET status='APPROVED',approved_by=?,approved_at=now()
            WHERE id=?
            """, actor, id);
        event(id, periodId, group, "APPROVED", ids((Array) row.get("calculation_ids")), actor, checkedReason);
    }

    @Transactional
    public void reject(UUID periodId, WorkforceGroup group, String reason) {
        SetupAccess.require("PAYROLL_APPROVE");
        access.require(group, WorkforceAccess.Action.APPROVE);
        String checkedReason = reason(reason);
        lock(periodId, group);
        var rows = jdbc.queryForList("""
            SELECT id,status,calculation_ids FROM payroll.payroll_group_approvals
            WHERE period_id=? AND workforce_group=? FOR UPDATE
            """, periodId, group.name());
        if (rows.isEmpty() || !"SUBMITTED".equals(rows.getFirst().get("status")))
            throw new SetupException("Only a submitted payroll can be rejected.");
        var row = rows.getFirst();
        UUID id = (UUID) row.get("id");
        UUID actor = WorkforceAccess.actor();
        jdbc.update("UPDATE payroll.payroll_group_approvals SET status='REJECTED',rejection_reason=? WHERE id=?",
                checkedReason, id);
        event(id, periodId, group, "REJECTED", ids((Array) row.get("calculation_ids")), actor, checkedReason);
    }

    private void event(UUID id, UUID periodId, WorkforceGroup group, String action, List<UUID> calculations,
                       UUID actor, String reason) {
        jdbc.update("""
            INSERT INTO payroll.payroll_group_approval_events
              (approval_id,period_id,workforce_group,action,calculation_ids,actor_id,reason)
            VALUES (?,?,?,?,?,?,?)
            """, id, periodId, group.name(), action, array(calculations), actor, reason);
    }

    private void lock(UUID periodId, WorkforceGroup group) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))",
                "payroll-group-" + periodId + "-" + group.name());
    }

    private String status(UUID periodId, WorkforceGroup group) {
        var rows = jdbc.queryForList("""
            SELECT status FROM payroll.payroll_group_approvals WHERE period_id=? AND workforce_group=? FOR UPDATE
            """, String.class, periodId, group.name());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static String reason(String value) {
        if (value == null || value.isBlank() || value.length() > 500)
            throw new SetupException("Enter a reason of up to 500 characters.");
        return value.trim();
    }

    private Array array(List<UUID> values) {
        return jdbc.execute((ConnectionCallback<Array>) connection -> {
            try { return connection.createArrayOf("uuid", values.toArray()); }
            catch (SQLException exception) { throw exception; }
        });
    }

    private static List<UUID> ids(Array array) {
        if (array == null) return List.of();
        try {
            var values = (Object[]) array.getArray();
            var result = new ArrayList<UUID>(values.length);
            for (Object value : values) result.add(value instanceof UUID id ? id : UUID.fromString(value.toString()));
            return List.copyOf(result);
        } catch (SQLException exception) { throw new IllegalStateException("Cannot read approval snapshot", exception); }
    }
}
