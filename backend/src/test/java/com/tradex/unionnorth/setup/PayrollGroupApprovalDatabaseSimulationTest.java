package com.tradex.unionnorth.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.security.WorkforceAccess;
import com.tradex.unionnorth.security.domain.WorkforceGroup;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in, rollback-only simulation against an isolated local payroll_sim_ database. */
class PayrollGroupApprovalDatabaseSimulationTest {
    private record SyntheticEmployee(UUID id, String net) {}

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void separateWorkforceGroupsSubmitAndApproveWithPersistedHistory() {
        String url = System.getenv("PAYROLL_SIM_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Set PAYROLL_SIM_DB_URL to opt in");
        Assumptions.assumeTrue(url.matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/payroll_sim_[a-zA-Z0-9_]+"),
                "Only an isolated local payroll_sim_ database is permitted");
        var source = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("PAYROLL_SIM_DB_USER", "postgres"),
                System.getenv().getOrDefault("PAYROLL_SIM_DB_PASSWORD", "postgres"));
        var jdbc = new JdbcTemplate(source);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(rollback -> {
            UUID period = UUID.randomUUID();
            UUID policy = UUID.randomUUID();
            jdbc.update("INSERT INTO payroll.setup_items(id,kind,code) VALUES (?,'PAY_PERIOD',?)," +
                    "(?,'CALCULATION_POLICY',?)", period, "SIM-" + period,
                    policy, "SIM-" + policy);
            var access = mock(WorkforceAccess.class);
            var calculations = mock(PayrollCalculationService.class);
            var service = new PayrollGroupApprovalService(jdbc, access, calculations);
            when(access.groups(WorkforceAccess.Action.VIEW)).thenReturn(List.of(
                    WorkforceGroup.GENERAL_WORKFORCE, WorkforceGroup.EXECUTIVE_STAFF));
            var general = seedCalculation(jdbc, period, policy, WorkforceGroup.GENERAL_WORKFORCE);
            var executive = seedCalculation(jdbc, period, policy, WorkforceGroup.EXECUTIVE_STAFF);
            when(calculations.employees(period)).thenReturn(List.of(
                    Map.of("id", general.id().toString(), "eligible", true,
                            "workforceGroup", "GENERAL_WORKFORCE"),
                    Map.of("id", executive.id().toString(), "eligible", true,
                            "workforceGroup", "EXECUTIVE_STAFF")));
            for (var employee : List.of(general, executive)) {
                assertThat(jdbc.queryForObject("""
                        SELECT c.result->>'net' FROM payroll.payroll_calculations c
                        WHERE c.employee_id=? AND c.period_id=?
                        """, String.class, employee.id(), period)).isEqualTo(employee.net());
            }

            for (var group : List.of(WorkforceGroup.GENERAL_WORKFORCE,
                    WorkforceGroup.EXECUTIVE_STAFF)) {
                UUID preparer = UUID.randomUUID();
                UUID approver = UUID.randomUUID();
                authenticate(preparer, "PAYROLL_CALCULATE", "PAYROLL_VIEW", "SALARY_VIEW");
                service.submit(period, group, "Synthetic preparation");
                authenticate(approver, "PAYROLL_APPROVE", "PAYROLL_VIEW", "SALARY_VIEW");
                service.approve(period, group, "Independent synthetic approval");
                var approval = service.list(period).stream()
                        .filter(item -> item.workforceGroup() == group).findFirst().orElseThrow();
                assertThat(approval.status()).isEqualTo("APPROVED");
                assertThat(approval.submittedBy()).isEqualTo(preparer);
                assertThat(approval.approvedBy()).isEqualTo(approver);
                assertThat(approval.calculationIds()).hasSize(1);
                assertThat(service.events(period).stream()
                        .filter(item -> item.workforceGroup() == group)
                        .map(PayrollGroupApprovalService.Event::action))
                        .containsExactlyInAnyOrder("SUBMITTED", "APPROVED");
            }
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM payroll.payroll_group_approvals WHERE period_id=?",
                    Integer.class, period)).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM payroll.payroll_group_approval_events WHERE period_id=?",
                    Integer.class, period)).isEqualTo(4);
            rollback.setRollbackOnly();
        });
    }

    private static SyntheticEmployee seedCalculation(JdbcTemplate jdbc, UUID period, UUID policy,
                                                     WorkforceGroup group) {
        UUID employee = UUID.randomUUID();
        UUID revision = UUID.randomUUID();
        String number = "SIM-" + employee;
        String baseSalary = group == WorkforceGroup.EXECUTIVE_STAFF ? "20000" : "10000";
        var overtime = new SetupStore.Item(UUID.randomUUID(), "COMPONENT", "SIM-OT",
                "Synthetic overtime", true, 0, LocalDate.of(2026, 1, 1),
                Map.of("category", "EARNING", "method", "OVERTIME", "value", "1.5",
                        "hoursDivisor", "200", "eligibility", "OVERTIME"));
        var allowance = new SetupStore.Item(UUID.randomUUID(), "COMPONENT", "SIM-ALLOWANCE",
                "Synthetic allowance", true, 0, LocalDate.of(2026, 1, 1),
                Map.of("category", "EARNING", "method", "FIXED", "value", "500"));
        var deduction = new SetupStore.Item(UUID.randomUUID(), "COMPONENT", "SIM-DEDUCTION",
                "Synthetic deduction", true, 0, LocalDate.of(2026, 1, 1),
                Map.of("category", "DEDUCTION", "method", "FIXED", "value", "100"));
        var result = PayrollCalculator.calculate(
                Map.of("payBasis", "MONTHLY", "basicSalary", baseSalary, "bra1", "100",
                        "bra2", "50", "overtimePaid", true),
                Map.of("monthlyProration", "FULL", "dayDivisor", "CALENDAR_DAYS",
                        "braTreatment", "FIXED", "rounding", "HALF_UP",
                        "basicTaxable", true, "basicStatutoryEligible", true),
                List.of(allowance, overtime, deduction), List.of(), Map.of(), Map.of(),
                Map.of(overtime.id().toString(), new BigDecimal("4")), 30);
        assertThat(result.net()).isEqualTo(
                group == WorkforceGroup.EXECUTIVE_STAFF ? "21150.00" : "10850.00");
        jdbc.update("""
                INSERT INTO payroll.employees(id,employee_number,first_name,last_name,display_name,
                  identity_number,employment_status,cadre_status,payroll_status,workforce_group)
                VALUES (?,?,?,?,? ,?,'ACTIVE','ACTIVE','ACTIVE',?)
                """, employee, number, "Synthetic", group.name(), "Synthetic " + group.name(),
                number, group.name());
        jdbc.update("""
                INSERT INTO payroll.employee_payroll_profiles(employee_id,registration_status)
                VALUES (?,'ACTIVE')
                """, employee);
        jdbc.update("""
                INSERT INTO payroll.payroll_profile_revisions
                  (id,employee_id,effective_from,general_data,financial_data,master_snapshot,created_by,reason)
                VALUES (?,?,DATE '2026-01-01','{}','{}','{}','simulation','Synthetic profile')
                """, revision, employee);
        jdbc.update("""
                INSERT INTO payroll.payroll_calculations
                  (id,request_id,request_data,employee_id,period_id,profile_revision_id,policy_id,
                   snapshot,result,created_by,reason,workforce_group_snapshot)
                VALUES (?,?,?::jsonb,?,?,?,?,'{}',?::jsonb,'simulation','Synthetic calculation',?)
                """, UUID.randomUUID(), UUID.randomUUID(), "{}", employee, period, revision,
                policy, new SetupStore(jdbc, new ObjectMapper()).json(result), group.name());
        return new SyntheticEmployee(employee, result.net());
    }

    private static void authenticate(UUID actor, String... permissions) {
        var authorities = java.util.Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.toString(), "simulation", authorities));
    }
}
