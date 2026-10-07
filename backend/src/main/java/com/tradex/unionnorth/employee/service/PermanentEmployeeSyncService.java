package com.tradex.unionnorth.employee.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Imports complete permanent and new joiner roster records as payroll drafts. */
@Service
@ConditionalOnProperty(name = "app.payroll.employee-sync.enabled", havingValue = "true")
public class PermanentEmployeeSyncService {
    private static final Logger log = LoggerFactory.getLogger(PermanentEmployeeSyncService.class);
    private final JdbcTemplate jdbc;
    private final EmployeeService employees;

    public PermanentEmployeeSyncService(JdbcTemplate jdbc, EmployeeService employees) {
        this.jdbc = jdbc;
        this.employees = employees;
    }

    @Scheduled(initialDelayString = "${app.payroll.employee-sync.initial-delay-ms:15000}",
            fixedDelayString = "${app.payroll.employee-sync.interval-ms:60000}")
    public void sync() {
        List<String> numbers = jdbc.queryForList("""
            SELECT e.employee_code
            FROM public.employees e
            JOIN public.employee_master_details d ON d.employee_id = e.id
            WHERE e.employee_category IN ('permanent', 'new_joiner')
              AND e.workforce_group IS NOT NULL
              AND e.is_active = true AND e.employment_status = 'active'
              AND nullif(trim(d.first_name), '') IS NOT NULL
              AND nullif(trim(d.last_name), '') IS NOT NULL
              AND nullif(trim(d.identity_number), '') IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM payroll.employee_payroll_profiles p
                              WHERE p.linematrix_employee_id = e.id)
              AND NOT EXISTS (SELECT 1 FROM payroll.employees p
                              WHERE p.employee_number = e.employee_code)
            ORDER BY e.created_at, e.id
            LIMIT 100
            """, String.class);
        for (String number : numbers) {
            try {
                employees.registerFromLineMatrix(number);
            } catch (RuntimeException exception) {
                // One incomplete or conflicting record must not prevent other registrations.
                log.warn("Eligible employee payroll import skipped: {}", exception.getClass().getSimpleName());
            }
        }
    }
}
