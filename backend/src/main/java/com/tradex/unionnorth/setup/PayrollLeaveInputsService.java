package com.tradex.unionnorth.setup;

import com.tradex.unionnorth.security.WorkforceAccess;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Approved LineMatrix leave, scoped to an employee and payroll period. No pay rule is inferred. */
@Service
@Transactional(readOnly = true)
public class PayrollLeaveInputsService {
    private final SetupStore store;
    private final WorkforceAccess workforceAccess;

    public PayrollLeaveInputsService(SetupStore store, WorkforceAccess workforceAccess) {
        this.store = store;
        this.workforceAccess = workforceAccess;
    }

    public Map<String, Object> approved(UUID employeeId, UUID periodId) {
        SetupAccess.require("PAYROLL_VIEW");
        SetupAccess.require("SALARY_VIEW");
        workforceAccess.employeeGroup(employeeId, WorkforceAccess.Action.VIEW);
        var period = store.get("PAY_PERIOD", periodId, null);
        LocalDate from = LocalDate.parse(period.data().get("startDate").toString());
        LocalDate to = LocalDate.parse(period.data().get("endDate").toString());
        if (to.isBefore(from)) throw new SetupException("Invalid payroll period dates.");
        var source = store.jdbc().queryForList(
                "SELECT linematrix_employee_id FROM employee_payroll_profiles WHERE employee_id=?",
                UUID.class, employeeId);
        if (source.isEmpty()) throw SetupException.missing();
        UUID sourceId = source.getFirst();
        List<Map<String, Object>> requests = sourceId == null ? List.of() : store.jdbc().query(
                """
                SELECT id, leave_type, leave_category, start_date, end_date, start_time, end_time,
                       half_day_session, reviewed_at
                FROM public.employee_leave_requests
                WHERE employee_id=? AND status='approved' AND start_date<=? AND end_date>=?
                ORDER BY start_date, id
                """,
                (row, index) -> {
                    String type = row.getString("leave_type");
                    String category = row.getString("leave_category");
                    LocalDate start = row.getDate("start_date").toLocalDate();
                    LocalDate end = row.getDate("end_date").toLocalDate();
                    var item = new LinkedHashMap<String, Object>();
                    item.put("id", row.getObject("id").toString());
                    item.put("type", type);
                    item.put("category", category);
                    item.put("startDate", start.toString());
                    item.put("endDate", end.toString());
                    item.put("startTime", row.getString("start_time"));
                    item.put("endTime", row.getString("end_time"));
                    item.put("halfDaySession", row.getString("half_day_session"));
                    item.put("daysInPeriod", overlapDays(type, start, end, from, to));
                    item.put("reviewedAt", row.getTimestamp("reviewed_at") == null ? null
                            : row.getTimestamp("reviewed_at").toInstant().toString());
                    return item;
                }, sourceId, to, from);
        BigDecimal noPayDays = requests.stream()
                .filter(row -> "no_pay".equals(row.get("category")))
                .map(row -> (BigDecimal) row.get("daysInPeriod"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var result = new LinkedHashMap<String, Object>();
        result.put("employeeId", employeeId);
        result.put("periodId", periodId);
        result.put("sourceLinked", sourceId != null);
        result.put("approvedNoPayCalendarDays", noPayDays);
        result.put("requests", requests);
        result.put("note", "Calendar-day overlap only. HR must review period inputs against approved counting, holiday and payroll policies before calculating pay.");
        return result;
    }

    static BigDecimal overlapDays(String type, LocalDate start, LocalDate end,
                                  LocalDate periodStart, LocalDate periodEnd) {
        LocalDate first = start.isAfter(periodStart) ? start : periodStart;
        LocalDate last = end.isBefore(periodEnd) ? end : periodEnd;
        if (last.isBefore(first) || "short_leave".equals(type)) return BigDecimal.ZERO;
        if ("half_day".equals(type)) return new BigDecimal("0.5");
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(first, last) + 1);
    }
}
