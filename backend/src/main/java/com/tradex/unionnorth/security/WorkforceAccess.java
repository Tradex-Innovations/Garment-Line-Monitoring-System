package com.tradex.unionnorth.security;

import com.tradex.unionnorth.security.domain.WorkforceGroup;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Server-owned group grants are checked in addition to action permissions. */
@Service
public class WorkforceAccess {
    public enum Action { VIEW, EDIT, APPROVE }

    private final JdbcTemplate jdbc;

    public WorkforceAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static UUID actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("Authentication required");
        try { return UUID.fromString(auth.getName()); }
        catch (IllegalArgumentException exception) { throw new AccessDeniedException("Invalid account"); }
    }

    public static boolean systemAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN"));
    }

    private static boolean developer() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DEVELOPER"));
    }

    public List<WorkforceGroup> groups(Action action) {
        if (systemAdmin()) return Arrays.asList(WorkforceGroup.values());
        if (developer()) {
            if (action == Action.VIEW) return Arrays.asList(WorkforceGroup.values());
            if (action == Action.EDIT) {
                var approved = grantedGroups(Action.APPROVE);
                return Arrays.stream(WorkforceGroup.values()).filter(group -> !approved.contains(group)).toList();
            }
        }
        return grantedGroups(action);
    }

    private List<WorkforceGroup> grantedGroups(Action action) {
        String column = column(action);
        return jdbc.queryForList("SELECT workforce_group FROM payroll.user_workforce_grants WHERE auth_user_id=? AND "
                + column + "=true ORDER BY workforce_group", String.class, actor()).stream()
                .map(WorkforceGroup::valueOf).toList();
    }

    public void require(WorkforceGroup group, Action action) {
        if (systemAdmin()) return;
        if (group == null || !groups(action).contains(group))
            throw new AccessDeniedException("Workforce payroll access denied");
    }

    public WorkforceGroup employeeGroup(UUID employeeId, Action action) {
        var rows = jdbc.queryForList("SELECT workforce_group FROM payroll.employees WHERE id=?", String.class, employeeId);
        if (rows.isEmpty()) throw new AccessDeniedException("Workforce payroll access denied");
        WorkforceGroup group = parse(rows.getFirst());
        require(group, action);
        return group;
    }

    public WorkforceGroup sourceGroup(UUID sourceId, Action action) {
        WorkforceGroup group = sourceGroupForImport(sourceId);
        require(group, action);
        return group;
    }

    /** Background sync checks the classification, but has no interactive grant. */
    public WorkforceGroup sourceGroupForImport(UUID sourceId) {
        var rows = jdbc.queryForList("SELECT workforce_group FROM public.employees WHERE id=?", String.class, sourceId);
        if (rows.isEmpty()) throw new AccessDeniedException("Workforce payroll access denied");
        return parse(rows.getFirst());
    }

    public static WorkforceGroup parse(String value) {
        if (value == null) throw new AccessDeniedException("Workforce group has not been assigned");
        try { return WorkforceGroup.valueOf(value); }
        catch (IllegalArgumentException exception) { throw new AccessDeniedException("Invalid workforce group"); }
    }

    private static String column(Action action) {
        return switch (action) {
            case VIEW -> "can_view";
            case EDIT -> "can_edit";
            case APPROVE -> "can_approve";
        };
    }
}
