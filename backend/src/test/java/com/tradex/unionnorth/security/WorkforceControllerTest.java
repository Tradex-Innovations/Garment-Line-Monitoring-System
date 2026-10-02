package com.tradex.unionnorth.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.security.domain.WorkforceGroup;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class WorkforceControllerTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000104");
    private static final UUID PAYROLL_EMPLOYEE = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID SOURCE_EMPLOYEE = UUID.fromString("00000000-0000-4000-8000-000000000202");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkforceController controller = new WorkforceController(jdbc, new WorkforceAccess(jdbc));

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void developerCanClassifyAnUnassignedPayrollEmployee() {
        authenticateDeveloper();
        when(jdbc.queryForList(anyString(), eq(PAYROLL_EMPLOYEE))).thenReturn(
                List.of(Map.of("linematrix_employee_id", SOURCE_EMPLOYEE)));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(SOURCE_EMPLOYEE)))
                .thenReturn(Collections.singletonList(null));

        controller.classifyPayrollEmployee(PAYROLL_EMPLOYEE,
                new WorkforceController.Assignment(WorkforceGroup.GENERAL_WORKFORCE, "Payroll setup"));

        verify(jdbc).update("UPDATE public.employees SET workforce_group=? WHERE id=?",
                "GENERAL_WORKFORCE", SOURCE_EMPLOYEE);
    }

    @Test void developerCannotMoveAnApprovedGroupThroughPayrollProfile() {
        authenticateDeveloper();
        when(jdbc.queryForList(anyString(), eq(PAYROLL_EMPLOYEE))).thenReturn(List.of(Map.of(
                "linematrix_employee_id", SOURCE_EMPLOYEE, "workforce_group", "EXECUTIVE_STAFF")));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(ACTOR)))
                .thenReturn(List.of("EXECUTIVE_STAFF"));

        assertThatThrownBy(() -> controller.classifyPayrollEmployee(PAYROLL_EMPLOYEE,
                new WorkforceController.Assignment(WorkforceGroup.GENERAL_WORKFORCE, "Change group")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void systemAdminCanEditAnExistingGroupFromThePayrollProfile() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ACTOR.toString(), "test", List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));
        when(jdbc.queryForList(anyString(), eq(PAYROLL_EMPLOYEE))).thenReturn(List.of(Map.of(
                "linematrix_employee_id", SOURCE_EMPLOYEE, "workforce_group", "GENERAL_WORKFORCE")));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(SOURCE_EMPLOYEE)))
                .thenReturn(List.of("GENERAL_WORKFORCE"));

        controller.classifyPayrollEmployee(PAYROLL_EMPLOYEE,
                new WorkforceController.Assignment(WorkforceGroup.EXECUTIVE_STAFF, "Approved group change"));

        verify(jdbc).update("UPDATE public.employees SET workforce_group=? WHERE id=?",
                "EXECUTIVE_STAFF", SOURCE_EMPLOYEE);
    }

    @Test void developerCanEditAnExistingGroupWhenBothGroupsAreEditable() {
        authenticateDeveloper();
        when(jdbc.queryForList(anyString(), eq(PAYROLL_EMPLOYEE))).thenReturn(List.of(Map.of(
                "linematrix_employee_id", SOURCE_EMPLOYEE, "workforce_group", "GENERAL_WORKFORCE")));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(SOURCE_EMPLOYEE)))
                .thenReturn(List.of("GENERAL_WORKFORCE"));

        controller.classifyPayrollEmployee(PAYROLL_EMPLOYEE,
                new WorkforceController.Assignment(WorkforceGroup.EXECUTIVE_STAFF, "Correct workforce group"));

        verify(jdbc).update("UPDATE public.employees SET workforce_group=? WHERE id=?",
                "EXECUTIVE_STAFF", SOURCE_EMPLOYEE);
    }

    private static void authenticateDeveloper() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ACTOR.toString(), "test", List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER"))));
    }
}
