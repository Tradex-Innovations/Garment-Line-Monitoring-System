package com.tradex.unionnorth.setup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

import com.tradex.unionnorth.security.WorkforceAccess;
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

class PayrollGroupApprovalAccessTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkforceAccess access = mock(WorkforceAccess.class);
    private final PayrollCalculationService calculations = mock(PayrollCalculationService.class);
    private final PayrollGroupApprovalService service = new PayrollGroupApprovalService(
            jdbc, access, calculations);
    private final UUID period = UUID.randomUUID();

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void generalApproverCannotApproveExecutiveRun() {
        authenticate("PAYROLL_APPROVE", "PAYROLL_VIEW");
        doThrow(new AccessDeniedException("Executive group denied"))
                .when(access).require(WorkforceGroup.EXECUTIVE_STAFF, WorkforceAccess.Action.APPROVE);
        assertThatThrownBy(() -> service.approve(period, WorkforceGroup.EXECUTIVE_STAFF, "Reviewed"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void preparingHrCannotApproveEvenWithGroupGrant() {
        authenticate("PAYROLL_CALCULATE", "PAYROLL_VIEW", "SALARY_VIEW");
        assertThatThrownBy(() -> service.approve(period, WorkforceGroup.GENERAL_WORKFORCE, "Reviewed"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void groupCannotBeSubmittedWithAnEligibleEmployeeMissingFromTheRun() {
        authenticate("PAYROLL_CALCULATE", "PAYROLL_VIEW", "SALARY_VIEW");
        var included = UUID.randomUUID();
        var missing = UUID.randomUUID();
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("SELECT status FROM payroll.payroll_group_approvals")),
                eq(String.class), eq(period), eq("GENERAL_WORKFORCE"))).thenReturn(List.of());
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("DISTINCT ON (c.employee_id)")),
                eq(UUID.class), eq(period), eq("GENERAL_WORKFORCE"), eq("GENERAL_WORKFORCE")))
                .thenReturn(List.of(UUID.randomUUID()));
        when(calculations.employees(period)).thenReturn(List.of(
                java.util.Map.of("id", included.toString(), "eligible", true,
                        "workforceGroup", "GENERAL_WORKFORCE"),
                java.util.Map.of("id", missing.toString(), "eligible", true,
                        "workforceGroup", "GENERAL_WORKFORCE")));
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("SELECT DISTINCT c.employee_id")),
                eq(UUID.class), eq(period), eq("GENERAL_WORKFORCE"), eq("GENERAL_WORKFORCE")))
                .thenReturn(List.of(included));

        assertThatThrownBy(() -> service.submit(period, WorkforceGroup.GENERAL_WORKFORCE, "Reviewed"))
                .isInstanceOf(SetupException.class).hasMessageContaining("every eligible employee");
    }

    private static void authenticate(String... authorities) {
        var list = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), "test", List.copyOf(list)));
    }
}
