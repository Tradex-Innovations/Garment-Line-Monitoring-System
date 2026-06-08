package com.garmentline.operations.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.ApiException;
import com.garmentline.operations.support.JsonSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
public class ValidationService {

  private final SupabaseAdminClient supabaseAdminClient;
  private final ImportParsingSupport parsingSupport;
  private final RoleGuard roleGuard;
  private final ObjectMapper objectMapper;

  public ValidationService(
      SupabaseAdminClient supabaseAdminClient,
      ImportParsingSupport parsingSupport,
      RoleGuard roleGuard,
      ObjectMapper objectMapper) {
    this.supabaseAdminClient = supabaseAdminClient;
    this.parsingSupport = parsingSupport;
    this.roleGuard = roleGuard;
    this.objectMapper = objectMapper;
  }

  public List<Map<String, Object>> getValidationSummary(AuthenticatedUser user) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "viewer");
    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("order", "attendance_date.desc");
    ArrayNode rows = supabaseAdminClient.select("vw_validation_summary", query);
    List<Map<String, Object>> response = new ArrayList<>();
    rows.forEach(row -> response.add(mapValidationSummary(row)));
    return response;
  }

  public List<Map<String, Object>> getReconciliationRows(
      AuthenticatedUser user,
      String attendanceDate,
      String status,
      String department,
      String employeeCode,
      String importBatchId) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "viewer");

    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("order", "attendance_date.desc");
    query.add("order", "employee_code.asc");
    ArrayNode rows = supabaseAdminClient.select("attendance_reconciliation", query);

    List<Map<String, Object>> mappedRows = new ArrayList<>();
    rows.forEach(row -> mappedRows.add(mapReconciliationRow(row)));

    return mappedRows.stream()
        .filter(row -> attendanceDate == null || attendanceDate.equals(row.get("attendanceDate")))
        .filter(
            row ->
                status == null
                    || status.equals("all")
                    || status.equals(row.get("effectiveStatus")))
        .filter(
            row ->
                department == null
                    || department.equalsIgnoreCase(String.valueOf(row.get("departmentName"))))
        .filter(
            row ->
                employeeCode == null
                    || String.valueOf(row.get("employeeCode"))
                        .toLowerCase(Locale.ROOT)
                        .contains(employeeCode.toLowerCase(Locale.ROOT)))
        .filter(
            row ->
                importBatchId == null
                    || importBatchId.equals(row.get("faceImportBatchId"))
                    || importBatchId.equals(row.get("fingerprintImportBatchId")))
        .toList();
  }

  public Map<String, Object> getReconciliationDetail(AuthenticatedUser user, String reconciliationId) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "viewer");

    ObjectNode row =
        supabaseAdminClient.selectSingle(
            "attendance_reconciliation",
            supabaseAdminClient.filters(Map.of("id", "eq." + reconciliationId)));
    Map<String, Object> record = mapReconciliationRow(row);
    String employeeCode = JsonSupport.text(row, "employee_code");
    String attendanceDate = JsonSupport.text(row, "attendance_date");

    ArrayNode notes =
        supabaseAdminClient.select(
            "reconciliation_notes",
            filtersWithOrdering(
                Map.of("reconciliation_id", "eq." + reconciliationId),
                "created_at.desc"));
    ArrayNode faceEvents =
        supabaseAdminClient.select(
            "face_events",
            filtersWithOrdering(
                Map.of(
                    "employee_code", "eq." + employeeCode,
                    "event_date", "eq." + attendanceDate),
                "event_time.asc"));
    ArrayNode allFaceRawRows =
        supabaseAdminClient.select(
            "face_raw_rows",
            filtersWithOrdering(Map.of("source_employee_id", "eq." + employeeCode), "row_number.asc"));
    ArrayNode fingerprintRows =
        supabaseAdminClient.select(
            "fingerprint_daily_attendance",
            filtersWithOrdering(
                Map.of(
                    "employee_code", "eq." + employeeCode,
                    "attendance_date", "eq." + attendanceDate),
                "created_at.desc"));
    ArrayNode auditLogs =
        supabaseAdminClient.select(
            "audit_logs",
            filtersWithOrdering(
                Map.of(
                    "entity_type", "eq.attendance_reconciliation",
                    "entity_id", "eq." + reconciliationId),
                "created_at.desc"));

    List<Map<String, Object>> filteredFaceRawRows =
        JsonSupport.toList(allFaceRawRows).stream()
            .filter(
                rawRow ->
                    attendanceDate.equals(
                        parsingSupport.parseFlexibleDateText(
                            JsonSupport.text(rawRow, "source_date_text"))))
            .map(
                rawRow ->
                    objectMapper.convertValue(
                        rawRow, new TypeReference<Map<String, Object>>() {}))
            .toList();

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("record", record);
    payload.put("faceEvents", objectMapper.convertValue(faceEvents, List.class));
    payload.put("faceRawRows", filteredFaceRawRows);
    payload.put("fingerprintRows", objectMapper.convertValue(fingerprintRows, List.class));
    payload.put("auditLogs", objectMapper.convertValue(auditLogs, List.class));
    payload.put("notes", objectMapper.convertValue(notes, List.class));
    return payload;
  }

  public Map<String, Object> overrideReconciliationStatus(
      AuthenticatedUser user, String reconciliationId, String newStatus, String reason, String note) {
    roleGuard.requireAnyRole(user, "admin", "hr");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("p_reconciliation_id", reconciliationId);
    payload.put("p_new_status", newStatus);
    payload.put("p_reason", reason);
    payload.put("p_note", note == null || note.isBlank() ? null : note);

    JsonNode response =
        supabaseAdminClient.rpc("rpc_override_reconciliation", payload);

    return objectMapper.convertValue(response, Map.class);
  }

  public Map<String, Object> addReconciliationNote(
      AuthenticatedUser user, String reconciliationId, String note) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor");

    JsonNode response =
        supabaseAdminClient.rpc(
            "rpc_add_reconciliation_note",
            Map.of("p_reconciliation_id", reconciliationId, "p_note", note));

    return objectMapper.convertValue(response, Map.class);
  }

  private MultiValueMap<String, String> filtersWithOrdering(Map<String, String> filters, String ordering) {
    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    filters.forEach((key, value) -> {
      if (value != null) {
        query.add(key, value);
      }
    });
    query.add("order", ordering);
    return query;
  }

  private Map<String, Object> mapValidationSummary(JsonNode row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("attendanceDate", JsonSupport.text(row, "attendance_date"));
    payload.put("totalReconciled", JsonSupport.integer(row, "total_reconciled"));
    payload.put("validatedCount", JsonSupport.integer(row, "validated_count"));
    payload.put("faceOnlyCount", JsonSupport.integer(row, "face_only_count"));
    payload.put("fingerprintOnlyCount", JsonSupport.integer(row, "fingerprint_only_count"));
    payload.put("leaveCount", JsonSupport.integer(row, "leave_count"));
    payload.put("absentCount", JsonSupport.integer(row, "absent_count"));
    payload.put("needsReviewCount", JsonSupport.integer(row, "needs_review_count"));
    payload.put("anomalyCount", JsonSupport.integer(row, "anomaly_count"));
    return payload;
  }

  private Map<String, Object> mapReconciliationRow(JsonNode row) {
    if (row == null || row.isNull()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Reconciliation row was not found.");
    }

    String effectiveStatus =
        JsonSupport.text(row, "manual_override_status") != null
            ? JsonSupport.text(row, "manual_override_status")
            : JsonSupport.text(row, "reconciliation_status");

    List<String> ruleFlags =
        JsonSupport.toList(row.get("rule_flags")).stream()
            .map(JsonNode::asText)
            .collect(Collectors.toList());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", JsonSupport.text(row, "id"));
    payload.put("faceImportBatchId", JsonSupport.text(row, "face_import_batch_id"));
    payload.put("fingerprintImportBatchId", JsonSupport.text(row, "fingerprint_import_batch_id"));
    payload.put("employeeCode", JsonSupport.text(row, "employee_code"));
    payload.put("employeeName", JsonSupport.text(row, "employee_name"));
    payload.put("designation", JsonSupport.text(row, "designation"));
    payload.put("departmentName", JsonSupport.text(row, "department_name"));
    payload.put("attendanceDate", JsonSupport.text(row, "attendance_date"));
    payload.put("faceFirstSeen", JsonSupport.text(row, "face_first_seen"));
    payload.put("faceLastSeen", JsonSupport.text(row, "face_last_seen"));
    payload.put("faceEventCount", JsonSupport.integer(row, "face_event_count"));
    payload.put("duplicateFaceEventCount", JsonSupport.integer(row, "duplicate_face_event_count"));
    payload.put("fingerprintTimeIn", JsonSupport.text(row, "fingerprint_time_in"));
    payload.put("fingerprintTimeOut", JsonSupport.text(row, "fingerprint_time_out"));
    payload.put("lateEarlyHours", JsonSupport.decimal(row, "late_early_hours"));
    payload.put("otHours", JsonSupport.decimal(row, "ot_hours"));
    payload.put("leaveType", JsonSupport.text(row, "leave_type"));
    payload.put("reconciliationStatus", JsonSupport.text(row, "reconciliation_status"));
    payload.put("effectiveStatus", effectiveStatus);
    payload.put("exceptionReason", JsonSupport.text(row, "exception_reason"));
    payload.put("confidenceLevel", JsonSupport.text(row, "confidence_level"));
    payload.put("ruleFlags", ruleFlags);
    payload.put("manuallyOverridden", JsonSupport.bool(row, "manually_overridden"));
    payload.put("manualOverrideStatus", JsonSupport.text(row, "manual_override_status"));
    payload.put("manualOverrideReason", JsonSupport.text(row, "manual_override_reason"));
    payload.put("manualOverrideBy", JsonSupport.text(row, "manual_override_by"));
    payload.put("manualOverrideAt", JsonSupport.text(row, "manual_override_at"));
    payload.put("createdAt", JsonSupport.text(row, "created_at"));
    payload.put("updatedAt", JsonSupport.text(row, "updated_at"));
    return payload;
  }
}
