package com.tradex.unionnorth.setup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

import com.tradex.unionnorth.security.WorkforceAccess;
import com.tradex.unionnorth.security.domain.WorkforceGroup;
import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
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

    @Test void separatePreparersAndApproversCanCompleteBothWorkforceRuns() throws SQLException {
        var generalEmployee = UUID.randomUUID();
        var executiveEmployee = UUID.randomUUID();
        var generalCalculation = UUID.randomUUID();
        var executiveCalculation = UUID.randomUUID();
        var generalApproval = UUID.randomUUID();
        var executiveApproval = UUID.randomUUID();
        var generalSubmitter = UUID.randomUUID();
        var executiveSubmitter = UUID.randomUUID();
        when(jdbc.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Array>>any()))
                .thenReturn(mock(Array.class));
        when(calculations.employees(period)).thenReturn(List.of(
                Map.of("id", generalEmployee.toString(), "eligible", true,
                        "workforceGroup", "GENERAL_WORKFORCE"),
                Map.of("id", executiveEmployee.toString(), "eligible", true,
                        "workforceGroup", "EXECUTIVE_STAFF")));
        record Scenario(WorkforceGroup group, UUID employee, UUID calculation,
                        UUID approval, UUID submitter) {}
        for (var scenario : List.of(
                new Scenario(WorkforceGroup.GENERAL_WORKFORCE, generalEmployee, generalCalculation,
                        generalApproval, generalSubmitter),
                new Scenario(WorkforceGroup.EXECUTIVE_STAFF, executiveEmployee, executiveCalculation,
                        executiveApproval, executiveSubmitter))) {
            var group = scenario.group();
            var employee = scenario.employee();
            var calculation = scenario.calculation();
            var approval = scenario.approval();
            var submitter = scenario.submitter();
            var calculationIds = mock(Array.class);
            when(calculationIds.getArray()).thenReturn(new UUID[] {calculation});
            when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("SELECT status FROM payroll.payroll_group_approvals")),
                    eq(String.class), eq(period), eq(group.name()))).thenReturn(List.of());
            when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("DISTINCT ON (c.employee_id)")),
                    eq(UUID.class), eq(period), eq(group.name()), eq(group.name())))
                    .thenReturn(List.of(calculation));
            when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("SELECT DISTINCT c.employee_id")),
                    eq(UUID.class), eq(period), eq(group.name()), eq(group.name())))
                    .thenReturn(List.of(employee));
            when(jdbc.queryForObject(argThat(sql -> sql != null && sql.contains("SELECT id FROM payroll.payroll_group_approvals")),
                    eq(UUID.class), eq(period), eq(group.name()))).thenReturn(approval);
            when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("SELECT id,status,submitted_by,calculation_ids")),
                    eq(period), eq(group.name()))).thenReturn(List.of(Map.of(
                            "id", approval, "status", "SUBMITTED", "submitted_by", submitter,
                            "calculation_ids", calculationIds)));
            authenticate(submitter, "PAYROLL_CALCULATE", "PAYROLL_VIEW", "SALARY_VIEW");
            service.submit(period, group, "Synthetic payroll reviewed");
            authenticate(UUID.randomUUID(), "PAYROLL_APPROVE", "PAYROLL_VIEW", "SALARY_VIEW");
            service.approve(period, group, "Independent synthetic approval");
            verify(access).require(group, WorkforceAccess.Action.EDIT);
            verify(access).require(group, WorkforceAccess.Action.APPROVE);
        }
        verify(jdbc, org.mockito.Mockito.times(2)).update(
                argThat(sql -> sql != null && sql.contains("SET status='APPROVED'")),
                any(UUID.class), any(UUID.class));
    }

    @Test void submitterCannotApproveTheirOwnRun() {
        var submitter = UUID.randomUUID();
        authenticate(submitter, "PAYROLL_APPROVE", "PAYROLL_VIEW", "SALARY_VIEW");
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("SELECT id,status,submitted_by,calculation_ids")),
                eq(period), eq("GENERAL_WORKFORCE"))).thenReturn(List.of(Map.of(
                        "id", UUID.randomUUID(), "status", "SUBMITTED", "submitted_by", submitter,
                        "calculation_ids", mock(Array.class))));
        assertThatThrownBy(() -> service.approve(period, WorkforceGroup.GENERAL_WORKFORCE, "Self approval"))
                .isInstanceOf(SetupException.class).hasMessageContaining("different account");
    }

    private static void authenticate(String... authorities) {
        authenticate(UUID.randomUUID(), authorities);
    }

    private static void authenticate(UUID actor, String... authorities) {
        var list = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actor.toString(), "test", List.copyOf(list)));
    }
}
