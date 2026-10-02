package com.tradex.unionnorth.security;

import com.tradex.unionnorth.security.domain.WorkforceGroup;
import com.tradex.unionnorth.setup.SetupException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workforce")
public class WorkforceController {
    public record Grant(boolean view, boolean edit, boolean approve, @NotBlank @Size(max=500) String reason) {}
    public record Assignment(WorkforceGroup group, @NotBlank @Size(max=500) String reason) {}
    public record MyAccess(List<WorkforceGroup> view, List<WorkforceGroup> edit, List<WorkforceGroup> approve,
                           boolean admin) {}

    private final JdbcTemplate jdbc;
    private final WorkforceAccess access;

    public WorkforceController(JdbcTemplate jdbc, WorkforceAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping("/mine")
    public ResponseEntity<MyAccess> mine() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MyAccess(
                access.groups(WorkforceAccess.Action.VIEW), access.groups(WorkforceAccess.Action.EDIT),
                access.groups(WorkforceAccess.Action.APPROVE), WorkforceAccess.systemAdmin()));
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<Map<String,Object>>> unassigned() {
        var rows = jdbc.queryForList("""
            SELECT id,employee_code,display_name,employee_category
            FROM public.employees WHERE workforce_group IS NULL ORDER BY employee_code LIMIT 500
            """);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rows);
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<Map<String,Object>>> assignments(@RequestParam(defaultValue = "") String search) {
        var rows = jdbc.queryForList("""
            SELECT id,employee_code,display_name,employee_category,workforce_group
            FROM public.employees
            WHERE employee_code ILIKE ? OR display_name ILIKE ?
            ORDER BY employee_code LIMIT 100
            """, "%" + search.trim() + "%", "%" + search.trim() + "%");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rows);
    }

    @GetMapping("/users/{userId}/grants")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<Map<String,Object>>> grants(@PathVariable UUID userId) {
        var rows = jdbc.queryForList("""
            SELECT workforce_group,can_view,can_edit,can_approve,assigned_at
            FROM payroll.user_workforce_grants WHERE auth_user_id=? ORDER BY workforce_group
            """, userId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rows);
    }

    @PutMapping("/users/{userId}/grants/{group}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    public ResponseEntity<Void> grant(@PathVariable UUID userId, @PathVariable WorkforceGroup group,
            @Valid @RequestBody Grant grant) {
        if ((grant.edit() || grant.approve()) && !grant.view())
            throw new SetupException("View access is required for editing or approval");
        if (grant.edit() && grant.approve())
            throw new SetupException("Preparation and approval must be assigned to different accounts");
        UUID actor = WorkforceAccess.actor();
        var previous = jdbc.queryForList("""
            SELECT can_view,can_edit,can_approve FROM payroll.user_workforce_grants
            WHERE auth_user_id=? AND workforce_group=?
            """, userId, group.name());
        jdbc.update("""
            INSERT INTO payroll.user_workforce_grants
              (auth_user_id,workforce_group,can_view,can_edit,can_approve,assigned_by)
            VALUES (?,?,?,?,?,?) ON CONFLICT (auth_user_id,workforce_group) DO UPDATE SET
              can_view=excluded.can_view,can_edit=excluded.can_edit,can_approve=excluded.can_approve,
              assigned_by=excluded.assigned_by,assigned_at=now()
            """, userId, group.name(), grant.view(), grant.edit(), grant.approve(), actor);
        audit(actor, userId, "WORKFORCE_GRANT_CHANGED", previous.toString(),
                "view=" + grant.view() + ",edit=" + grant.edit() + ",approve=" + grant.approve(), grant.reason());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/employees/{sourceId}/group")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    public ResponseEntity<Void> assign(@PathVariable UUID sourceId, @Valid @RequestBody Assignment assignment) {
        return assignGroup(sourceId, assignment, false);
    }

    @PutMapping("/payroll-employees/{payrollId}/group")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','DEVELOPER')")
    @Transactional
    public ResponseEntity<Void> classifyPayrollEmployee(
            @PathVariable UUID payrollId, @Valid @RequestBody Assignment assignment) {
        var profiles = jdbc.queryForList("""
            SELECT p.linematrix_employee_id,e.workforce_group FROM payroll.employee_payroll_profiles p
            JOIN payroll.employees e ON e.id=p.employee_id WHERE e.id=?
            """, payrollId);
        if (profiles.isEmpty() || profiles.getFirst().get("linematrix_employee_id") == null)
            throw new SetupException("Link this payroll profile to a LineMatrix employee before assigning a group.");
        boolean checkGrants = !WorkforceAccess.systemAdmin();
        if (checkGrants && profiles.getFirst().get("workforce_group") != null)
            access.require(WorkforceAccess.parse(profiles.getFirst().get("workforce_group").toString()),
                    WorkforceAccess.Action.EDIT);
        return assignGroup((UUID) profiles.getFirst().get("linematrix_employee_id"), assignment, checkGrants);
    }

    private ResponseEntity<Void> assignGroup(UUID sourceId, Assignment assignment, boolean checkGrants) {
        if (assignment.group() == null) throw new SetupException("Workforce group is required");
        UUID actor = WorkforceAccess.actor();
        var previous = jdbc.queryForList("SELECT workforce_group FROM public.employees WHERE id=? FOR UPDATE",
                String.class, sourceId);
        if (previous.isEmpty()) throw SetupException.missing();
        String before = previous.getFirst();
        if (checkGrants) {
            if (before != null) access.require(WorkforceAccess.parse(before), WorkforceAccess.Action.EDIT);
            access.require(assignment.group(), WorkforceAccess.Action.EDIT);
        }
        var periods = jdbc.queryForList("""
            SELECT DISTINCT c.period_id FROM payroll.payroll_calculations c
            JOIN payroll.employee_payroll_profiles p ON p.employee_id=c.employee_id
            WHERE p.linematrix_employee_id=? ORDER BY c.period_id
            """, UUID.class, sourceId);
        for (UUID period : periods) {
            var groups = new java.util.TreeSet<String>();
            if (before != null) groups.add(before);
            groups.add(assignment.group().name());
            for (String group : groups) jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))",
                    "payroll-group-" + period + "-" + group);
        }
        Long pending = jdbc.queryForObject("""
            SELECT count(*) FROM payroll.payroll_group_approvals a
            JOIN payroll.payroll_calculations c ON c.id=ANY(a.calculation_ids)
            JOIN payroll.employee_payroll_profiles p ON p.employee_id=c.employee_id
            WHERE a.status='SUBMITTED' AND p.linematrix_employee_id=?
            """, Long.class, sourceId);
        if (pending != null && pending > 0) throw new SetupException(
                "Resolve the submitted payroll review before moving this employee");
        jdbc.update("UPDATE public.employees SET workforce_group=? WHERE id=?", assignment.group().name(), sourceId);
        jdbc.update("""
            UPDATE payroll.employees e SET workforce_group=?
            FROM payroll.employee_payroll_profiles p
            WHERE p.employee_id=e.id AND p.linematrix_employee_id=?
            """, assignment.group().name(), sourceId);
        audit(actor, sourceId, "WORKFORCE_GROUP_CHANGED", before, assignment.group().name(), assignment.reason());
        return ResponseEntity.noContent().build();
    }

    private void audit(UUID actor, UUID subject, String action, String before, String after, String reason) {
        jdbc.update("""
            INSERT INTO payroll.workforce_access_audit(actor_id,subject_id,action,old_value,new_value,reason)
            VALUES (?,?,?,?,?,?)
            """, actor, subject, action, before, after, reason.trim());
    }
}
