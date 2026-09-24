package com.tradex.unionnorth.security.domain;

import static com.tradex.unionnorth.security.domain.Permission.EMPLOYEE_CREATE;
import static com.tradex.unionnorth.security.domain.Permission.EMPLOYEE_UPDATE;
import static com.tradex.unionnorth.security.domain.Permission.EMPLOYEE_VIEW_ALL;
import static com.tradex.unionnorth.security.domain.Permission.EMPLOYEE_VIEW_SELF;
import static com.tradex.unionnorth.security.domain.Permission.LEAVE_APPROVE_LEVEL_1;
import static com.tradex.unionnorth.security.domain.Permission.LEAVE_APPROVE_LEVEL_2;
import static com.tradex.unionnorth.security.domain.Permission.LEAVE_APPROVE_LEVEL_3;
import static com.tradex.unionnorth.security.domain.Permission.LEAVE_REQUEST;
import static com.tradex.unionnorth.security.domain.Permission.PAYMENT_EXPORT;
import static com.tradex.unionnorth.security.domain.Permission.PAYROLL_ADJUST;
import static com.tradex.unionnorth.security.domain.Permission.PAYROLL_APPROVE;
import static com.tradex.unionnorth.security.domain.Permission.PAYROLL_CALCULATE;
import static com.tradex.unionnorth.security.domain.Permission.PAYROLL_LOCK;
import static com.tradex.unionnorth.security.domain.Permission.PAYROLL_REVIEW;
import static com.tradex.unionnorth.security.domain.Permission.PAYROLL_VIEW;
import static com.tradex.unionnorth.security.domain.Permission.PRIVILEGED_ACCOUNT_MANAGE;
import static com.tradex.unionnorth.security.domain.Permission.SALARY_EDIT;
import static com.tradex.unionnorth.security.domain.Permission.SALARY_VIEW;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

@Component
public class RolePermissionMapper {

    private final EnumMap<Role, Set<Permission>> permissionsByRole = new EnumMap<>(Role.class);

    public RolePermissionMapper() {
        permissionsByRole.put(Role.EMPLOYEE, EnumSet.of(EMPLOYEE_VIEW_SELF, LEAVE_REQUEST));
        permissionsByRole.put(
                Role.LINE_SUPERVISOR, EnumSet.of(EMPLOYEE_VIEW_ALL, LEAVE_APPROVE_LEVEL_1));
        permissionsByRole.put(
                Role.PRODUCTION_MANAGER,
                EnumSet.of(EMPLOYEE_VIEW_ALL, LEAVE_APPROVE_LEVEL_1, LEAVE_APPROVE_LEVEL_2));
        permissionsByRole.put(
                Role.HR_OFFICER,
                EnumSet.of(
                        EMPLOYEE_VIEW_ALL,
                        EMPLOYEE_CREATE,
                        EMPLOYEE_UPDATE,
                        LEAVE_APPROVE_LEVEL_1));
        permissionsByRole.put(
                Role.HR_MANAGER,
                EnumSet.of(
                        EMPLOYEE_VIEW_ALL,
                        EMPLOYEE_CREATE,
                        EMPLOYEE_UPDATE,
                        LEAVE_APPROVE_LEVEL_2,
                        LEAVE_APPROVE_LEVEL_3,
                        PAYROLL_VIEW,
                        SALARY_VIEW));
        permissionsByRole.get(Role.HR_MANAGER).add(Permission.ORGANIZATION_MANAGE);
        for (Role hr : Set.of(Role.HR_OFFICER, Role.HR_MANAGER)) {
            permissionsByRole
                    .get(hr)
                    .addAll(
                            EnumSet.of(
                                    PAYROLL_VIEW,
                                    PAYROLL_CALCULATE,
                                    SALARY_VIEW,
                                    SALARY_EDIT,
                                    Permission.PAYROLL_SETUP_EDIT,
                                    Permission.ORGANIZATION_MANAGE));
        }
        permissionsByRole.put(
                Role.PAYROLL_OFFICER,
                EnumSet.of(
                        EMPLOYEE_VIEW_ALL,
                        PAYROLL_VIEW,
                        PAYROLL_CALCULATE,
                        PAYROLL_ADJUST,
                        SALARY_VIEW,
                        SALARY_EDIT));
        permissionsByRole.get(Role.PAYROLL_OFFICER).add(Permission.PAYROLL_SETUP_EDIT);
        permissionsByRole.put(
                Role.PAYROLL_REVIEWER, EnumSet.of(PAYROLL_VIEW, PAYROLL_REVIEW, SALARY_VIEW));
        permissionsByRole.put(
                Role.FINANCE,
                EnumSet.of(PAYROLL_VIEW, PAYROLL_APPROVE, PAYMENT_EXPORT, SALARY_VIEW));
        permissionsByRole.put(
                Role.MANAGEMENT_APPROVER,
                EnumSet.of(PAYROLL_VIEW, PAYROLL_APPROVE, PAYROLL_LOCK, LEAVE_APPROVE_LEVEL_3));
        permissionsByRole.put(Role.SECURITY_GUARD, EnumSet.of(EMPLOYEE_VIEW_ALL));
        EnumSet<Permission> adminPermissions = EnumSet.allOf(Permission.class);
        adminPermissions.remove(PRIVILEGED_ACCOUNT_MANAGE);
        adminPermissions.remove(Permission.DEVELOPER_ACCESS);
        permissionsByRole.put(Role.ADMIN, adminPermissions);
        EnumSet<Permission> systemPermissions = EnumSet.allOf(Permission.class);
        systemPermissions.remove(Permission.DEVELOPER_ACCESS);
        permissionsByRole.put(Role.SYSTEM_ADMIN, systemPermissions);
        permissionsByRole.put(Role.DEVELOPER, EnumSet.allOf(Permission.class));
    }

    public Set<Permission> permissionsFor(Role role) {
        return permissionsByRole.getOrDefault(role, Set.of());
    }

    public Set<Permission> permissionsForRoleName(String roleName) {
        try {
            return permissionsFor(Role.valueOf(roleName));
        } catch (IllegalArgumentException exception) {
            return Set.of();
        }
    }

    public boolean isSupportedRole(String roleName) {
        try {
            Role.valueOf(roleName);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
