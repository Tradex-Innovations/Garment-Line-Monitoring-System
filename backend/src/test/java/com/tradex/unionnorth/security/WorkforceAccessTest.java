package com.tradex.unionnorth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.security.domain.WorkforceGroup;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class WorkforceAccessTest {
    private static final UUID GENERAL_HR = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID EXECUTIVE_HR = UUID.fromString("00000000-0000-4000-8000-000000000102");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000103");
    private static final UUID EXECUTIVE_EMPLOYEE = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkforceAccess access = new WorkforceAccess(jdbc);

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void generalHrCannotReadExecutiveFinancialProfile() {
        authenticate(GENERAL_HR, "ROLE_HR_OFFICER");
        when(jdbc.queryForList(anyString(), eq(String.class), eq(EXECUTIVE_EMPLOYEE)))
                .thenReturn(List.of("EXECUTIVE_STAFF"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(GENERAL_HR)))
                .thenReturn(List.of("GENERAL_WORKFORCE"));

        assertThatThrownBy(() -> access.employeeGroup(EXECUTIVE_EMPLOYEE, WorkforceAccess.Action.VIEW))
                .isInstanceOf(AccessDeniedException.class);
        access.require(WorkforceGroup.GENERAL_WORKFORCE, WorkforceAccess.Action.VIEW);
    }

    @Test void executiveHrCanReadOnlyAssignedExecutiveGroup() {
        authenticate(EXECUTIVE_HR, "ROLE_HR_OFFICER");
        when(jdbc.queryForList(anyString(), eq(String.class), eq(EXECUTIVE_HR)))
                .thenReturn(List.of("EXECUTIVE_STAFF"));
        access.require(WorkforceGroup.EXECUTIVE_STAFF, WorkforceAccess.Action.VIEW);
        assertThatThrownBy(() -> access.require(WorkforceGroup.GENERAL_WORKFORCE, WorkforceAccess.Action.VIEW))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void systemAdminCanAdministerBothButCannotTreatUnclassifiedEmployeeAsPayrollReady() {
        authenticate(ADMIN, "ROLE_SYSTEM_ADMIN");
        assertThat(access.groups(WorkforceAccess.Action.EDIT))
                .containsExactlyInAnyOrder(WorkforceGroup.EXECUTIVE_STAFF, WorkforceGroup.GENERAL_WORKFORCE);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(EXECUTIVE_EMPLOYEE)))
                .thenReturn(java.util.Collections.singletonList(null));
        assertThatThrownBy(() -> access.employeeGroup(EXECUTIVE_EMPLOYEE, WorkforceAccess.Action.VIEW))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticate(UUID actor, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actor.toString(), "test", List.of(new SimpleGrantedAuthority(role))));
    }
}
