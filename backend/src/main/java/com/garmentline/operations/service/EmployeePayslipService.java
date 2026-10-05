package com.garmentline.operations.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.garmentline.operations.support.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Employee-only draft payslips after internal group approval. Never accepts an employee ID from the caller. */
@Service
public class EmployeePayslipService {
  private final ObjectProvider<JdbcTemplate> jdbcProvider;
  private final ObjectMapper mapper;

  public EmployeePayslipService(ObjectProvider<JdbcTemplate> jdbcProvider, ObjectMapper mapper) {
    this.jdbcProvider = jdbcProvider;
    this.mapper = mapper;
  }

  public List<Map<String, Object>> list(String lineMatrixEmployeeId) {
    return database().query("""
        SELECT c.id, c.period_id, c.snapshot->'period'->'data'->>'startDate' AS period_start,
               c.snapshot->'period'->'data'->>'endDate' AS period_end, c.result::text AS result,
               r.action AS review_status, r.note AS review_note, r.created_at AS reviewed_at
        FROM payroll.employee_payroll_profiles p
        JOIN payroll.employees e ON e.id=p.employee_id
        JOIN payroll.payroll_calculations c ON c.employee_id=e.id
        JOIN payroll.payroll_group_approvals a
          ON a.period_id=c.period_id AND a.workforce_group=c.workforce_group_snapshot
         AND a.workforce_group=e.workforce_group AND a.status='APPROVED'
         AND c.id=ANY(a.calculation_ids)
        LEFT JOIN payroll.employee_payslip_reviews r ON r.calculation_id=c.id
        WHERE p.linematrix_employee_id=? AND e.payroll_status='ACTIVE'
        ORDER BY c.created_at DESC, c.id DESC LIMIT 24
        """, (row, index) -> Map.<String, Object>of(
            "id", row.getObject("id").toString(),
            "periodId", row.getObject("period_id").toString(),
            "periodStart", nullable(row.getString("period_start")),
            "periodEnd", nullable(row.getString("period_end")),
            "result", result(row.getString("result")),
            "reviewStatus", nullable(row.getString("review_status")),
            "reviewNote", nullable(row.getString("review_note")),
            "reviewedAt", row.getTimestamp("reviewed_at") == null ? "" : row.getTimestamp("reviewed_at").toInstant().toString()),
        UUID.fromString(lineMatrixEmployeeId));
  }

  public List<Map<String, Object>> review(String lineMatrixEmployeeId, String calculationId,
                                           String action, String note) {
    UUID calculation;
    try {
      calculation = UUID.fromString(calculationId);
    } catch (RuntimeException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payslip ID.");
    }
    if (!"CONFIRMED".equals(action) && !"QUERY".equals(action))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Choose confirm or raise a query.");
    String cleanNote = note == null ? "" : note.trim();
    if (cleanNote.length() > 1000 || ("QUERY".equals(action) && cleanNote.isEmpty()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a query of up to 1000 characters.");
    if ("CONFIRMED".equals(action) && !cleanNote.isEmpty())
      throw new ApiException(HttpStatus.BAD_REQUEST, "Confirmation does not accept a query note.");

    // The insert is atomic and rechecks ownership and approval at write time.
    int inserted = database().update("""
        INSERT INTO payroll.employee_payslip_reviews(calculation_id,employee_id,action,note)
        SELECT c.id,e.id,?,?
        FROM payroll.employee_payroll_profiles p
        JOIN payroll.employees e ON e.id=p.employee_id
        JOIN payroll.payroll_calculations c ON c.employee_id=e.id
        JOIN payroll.payroll_group_approvals a
          ON a.period_id=c.period_id AND a.workforce_group=c.workforce_group_snapshot
         AND a.workforce_group=e.workforce_group AND a.status='APPROVED'
         AND c.id=ANY(a.calculation_ids)
        WHERE p.linematrix_employee_id=? AND c.id=? AND e.payroll_status='ACTIVE'
        ON CONFLICT (calculation_id) DO NOTHING
        """, action, "QUERY".equals(action) ? cleanNote : null,
        UUID.fromString(lineMatrixEmployeeId), calculation);
    if (inserted != 1)
      throw new ApiException(HttpStatus.CONFLICT,
          "This draft payslip is unavailable or already reviewed. Refresh the portal.");
    return list(lineMatrixEmployeeId);
  }

  private JdbcTemplate database() {
    JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
    if (jdbc == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
        "Payroll payslips are unavailable until the shared payroll backend is connected.");
    return jdbc;
  }

  private Map<String, Object> result(String json) {
    try {
      return mapper.readValue(json, new TypeReference<>() {});
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read payslip details.");
    }
  }

  private static String nullable(String value) { return value == null ? "" : value; }
}
