package com.garmentline.operations.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.garmentline.operations.config.SupabaseProperties;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.ApiException;
import com.garmentline.operations.support.JsonSupport;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {

  private final SupabaseAdminClient supabaseAdminClient;
  private final SupabaseProperties supabaseProperties;
  private final ImportParsingSupport parsingSupport;
  private final RoleGuard roleGuard;
  private final ObjectMapper objectMapper;

  public ImportService(
      SupabaseAdminClient supabaseAdminClient,
      SupabaseProperties supabaseProperties,
      ImportParsingSupport parsingSupport,
      RoleGuard roleGuard,
      ObjectMapper objectMapper) {
    this.supabaseAdminClient = supabaseAdminClient;
    this.supabaseProperties = supabaseProperties;
    this.parsingSupport = parsingSupport;
    this.roleGuard = roleGuard;
    this.objectMapper = objectMapper;
  }

  public List<Map<String, Object>> listImportBatches(AuthenticatedUser user) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor");

    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("order", "created_at.desc");
    query.add("limit", "50");

    ArrayNode rows = supabaseAdminClient.select("import_batches", query);
    List<Map<String, Object>> batches = new ArrayList<>();
    rows.forEach(row -> batches.add(mapBatchSummary(row)));
    return batches;
  }

  public Map<String, Object> uploadAndProcess(
      String sourceType, MultipartFile file, AuthenticatedUser user) throws IOException {
    roleGuard.requireAnyRole(user, "admin", "hr");

    if (!List.of("face", "fingerprint").contains(sourceType)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported source type: " + sourceType);
    }

    if (file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a file to upload.");
    }

    String batchId = UUID.randomUUID().toString();
    String storagePath =
        parsingSupport.buildImportsStoragePath(
            sourceType,
            batchId,
            file.getOriginalFilename() == null ? sourceType + "-import" : file.getOriginalFilename());
    String now = OffsetDateTime.now().toString();

    createImportBatch(
        batchId,
        sourceType,
        file.getOriginalFilename(),
        storagePath,
        file.getContentType(),
        file.getSize(),
        user.id());

    try {
      supabaseAdminClient.uploadObject(
          supabaseProperties.importsBucket(),
          storagePath,
          file.getBytes(),
          file.getContentType());
      updateImportBatchStatus(batchId, "processing", now, null, null, null, null, null);

      if ("face".equals(sourceType)) {
        ImportParsingSupport.FaceParseResult parsed = parsingSupport.parseFaceWorkbook(file);
        replaceFaceRawRows(batchId, parsed.rows());
        updateImportBatchStatus(
            batchId,
            "parsed",
            null,
            joinWarnings(parsed.warnings()),
            parsed.rows().size(),
            null,
            null,
            null);
        upsertEmployeesFromFace(parsed.rows());
        Map<String, Object> normalized = normalizeFaceRows(batchId);
        return finalizeImportBatch(batchId, "normalized", normalized, parsed.warnings());
      }

      ImportParsingSupport.FingerprintParseResult parsed = parsingSupport.parseFingerprintFile(file);
      replaceFingerprintImportReport(batchId, parsed.metadata());
      replaceFingerprintRawRows(batchId, parsed.rows());
      updateImportBatchStatus(
          batchId,
          "parsed",
          null,
          joinWarnings(parsed.warnings()),
          parsed.rows().size(),
          null,
          null,
          null);
      upsertEmployeesFromFingerprint(parsed.rows());
      Map<String, Object> normalized = normalizeFingerprintRows(batchId);
      return finalizeImportBatch(batchId, "normalized", normalized, parsed.warnings());
    } catch (Exception exception) {
      updateImportBatchStatus(
          batchId,
          "failed",
          null,
          exception.getMessage(),
          null,
          null,
          null,
          OffsetDateTime.now().toString());
      throw exception;
    }
  }

  public Map<String, Object> rerunNormalization(String batchId, AuthenticatedUser user) {
    roleGuard.requireAnyRole(user, "admin", "hr");

    ObjectNode batch =
        supabaseAdminClient.selectSingle(
            "import_batches",
            supabaseAdminClient.filters(Map.of("id", "eq." + batchId)));

    String sourceType = JsonSupport.text(batch, "source_type");
    Map<String, Object> normalized =
        "face".equals(sourceType) ? normalizeFaceRows(batchId) : normalizeFingerprintRows(batchId);

    return finalizeImportBatch(batchId, "normalized", normalized, List.of());
  }

  public Map<String, Object> reconcilePair(
      String faceBatchId, String fingerprintBatchId, AuthenticatedUser user) {
    roleGuard.requireAnyRole(user, "admin", "hr");

    JsonNode response =
        supabaseAdminClient.rpc(
            "rpc_reconcile_attendance",
            Map.of("face_batch_id", faceBatchId, "fingerprint_batch_id", fingerprintBatchId));

    updateImportBatchStatus(faceBatchId, "reconciled", null, null, null, null, null, null);
    updateImportBatchStatus(
        fingerprintBatchId, "reconciled", null, null, null, null, null, null);

    return objectMapper.convertValue(response, Map.class);
  }

  private Map<String, Object> finalizeImportBatch(
      String batchId, String status, Map<String, Object> normalized, List<String> warnings) {
    updateImportBatchStatus(
        batchId,
        status,
        null,
        joinWarnings(warnings),
        (Integer) normalized.get("totalRawRows"),
        (Integer) normalized.get("validRowCount"),
        (Integer) normalized.get("errorRowCount"),
        OffsetDateTime.now().toString());

    ObjectNode batch =
        supabaseAdminClient.selectSingle(
            "import_batches",
            supabaseAdminClient.filters(Map.of("id", "eq." + batchId)));
    return mapBatchSummary(batch);
  }

  private void createImportBatch(
      String batchId,
      String sourceType,
      String originalFilename,
      String storagePath,
      String contentType,
      long size,
      String uploadedBy) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", batchId);
    payload.put("source_type", sourceType);
    payload.put("original_filename", originalFilename);
    payload.put("storage_path", storagePath);
    payload.put("file_mime_type", contentType);
    payload.put("file_size_bytes", size);
    payload.put("uploaded_by", uploadedBy);
    payload.put("import_status", "uploaded");

    supabaseAdminClient.insertSingle("import_batches", payload);
  }

  private void updateImportBatchStatus(
      String batchId,
      String status,
      String startedAt,
      String notes,
      Integer totalRawRows,
      Integer totalValidRows,
      Integer totalErrorRows,
      String completedAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("import_status", status);
    if (startedAt != null) {
      payload.put("started_at", startedAt);
    }
    if (notes != null) {
      payload.put("notes", notes);
    }
    if (totalRawRows != null) {
      payload.put("total_raw_rows", totalRawRows);
    }
    if (totalValidRows != null) {
      payload.put("total_valid_rows", totalValidRows);
    }
    if (totalErrorRows != null) {
      payload.put("total_error_rows", totalErrorRows);
    }
    if (completedAt != null) {
      payload.put("completed_at", completedAt);
    }

    supabaseAdminClient.updateSingle(
        "import_batches",
        supabaseAdminClient.filters(Map.of("id", "eq." + batchId)),
        payload);
  }

  private String joinWarnings(List<String> warnings) {
    String joined =
        warnings == null ? "" : warnings.stream().filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(" | "));
    return joined.isBlank() ? null : joined;
  }

  private void replaceFaceRawRows(
      String batchId, List<ImportParsingSupport.FaceParsedRow> rows) {
    supabaseAdminClient.delete(
        "face_raw_rows", supabaseAdminClient.filters(Map.of("import_batch_id", "eq." + batchId)));

    List<Map<String, Object>> inserts = new ArrayList<>();
    for (ImportParsingSupport.FaceParsedRow row : rows) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("import_batch_id", batchId);
      payload.put("row_number", row.rowNumber());
      payload.put("source_first_name", row.firstName());
      payload.put("source_last_name", row.lastName());
      payload.put("source_employee_id", row.employeeId());
      payload.put("source_department", row.department());
      payload.put("source_date_text", row.dateText());
      payload.put("source_weekday", row.weekday());
      payload.put("source_records_text", row.recordsText());
      payload.put("raw_payload", JsonSupport.toMap(objectMapper, row.rawPayload()));
      payload.put("parse_status", row.parseStatus());
      payload.put("parse_error", row.parseError());
      inserts.add(payload);
    }
    insertChunks("face_raw_rows", inserts);
  }

  private void replaceFingerprintRawRows(
      String batchId, List<ImportParsingSupport.FingerprintParsedRow> rows) {
    supabaseAdminClient.delete(
        "fingerprint_raw_rows",
        supabaseAdminClient.filters(Map.of("import_batch_id", "eq." + batchId)));

    List<Map<String, Object>> inserts = new ArrayList<>();
    for (ImportParsingSupport.FingerprintParsedRow row : rows) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("import_batch_id", batchId);
      payload.put("row_number", row.rowNumber());
      payload.put("source_emp_no", row.empNo());
      payload.put("source_epf_no", row.epfNo());
      payload.put("source_name", row.name());
      payload.put("source_designation", row.designation());
      payload.put("source_department", row.department());
      payload.put("source_employee_category", row.employeeCategory());
      payload.put("source_date_text", row.dateText());
      payload.put("source_time_in_text", row.timeInText());
      payload.put("source_time_out_text", row.timeOutText());
      payload.put("source_late_early_text", row.lateEarlyText());
      payload.put("source_day", row.dayText());
      payload.put("source_ot_text", row.otText());
      payload.put("source_leave_type", row.leaveType());
      payload.put("source_leave_days_total_text", row.leaveDaysTotalText());
      payload.put("source_nopay_days_total_text", row.nopayDaysTotalText());
      payload.put("source_other_leave_days_text", row.otherLeaveDaysText());
      payload.put("raw_payload", JsonSupport.toMap(objectMapper, row.rawPayload()));
      payload.put("parse_status", row.parseStatus());
      payload.put("parse_error", row.parseError());
      inserts.add(payload);
    }
    insertChunks("fingerprint_raw_rows", inserts);
  }

  private void replaceFingerprintImportReport(
      String batchId, ImportParsingSupport.FingerprintImportMetadata metadata) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("import_batch_id", batchId);
    payload.put("company_name", trimToNull(metadata.companyName()));
    payload.put("company_address", trimToNull(metadata.companyAddress()));
    payload.put("company_phone", trimToNull(metadata.companyPhone()));
    payload.put("report_title", trimToNull(metadata.reportTitle()));
    payload.put("report_scope", trimToNull(metadata.reportScope()));
    payload.put(
        "report_date_from",
        parsingSupport.parseFlexibleDateText(trimToNull(metadata.reportDateFromText())));
    payload.put(
        "report_date_to",
        parsingSupport.parseFlexibleDateText(trimToNull(metadata.reportDateToText())));

    supabaseAdminClient.upsertMany(
        "fingerprint_import_reports", List.of(payload), "import_batch_id");
  }

  private Map<String, Object> normalizeFaceRows(String batchId) {
    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("import_batch_id", "eq." + batchId);
    query.add("order", "row_number.asc");
    ArrayNode rawRows = supabaseAdminClient.selectAll("face_raw_rows", query);

    List<Map<String, Object>> eventRows = new ArrayList<>();
    List<Map<String, Object>> summaryRows = new ArrayList<>();
    Map<String, FaceSummaryAccumulator> summaryMap = new LinkedHashMap<>();
    int validRowCount = 0;
    int errorRowCount = 0;

    for (JsonNode rawRow : rawRows) {
      String employeeCode = trimToNull(JsonSupport.text(rawRow, "source_employee_id"));
      String eventDate = parsingSupport.parseFlexibleDateText(JsonSupport.text(rawRow, "source_date_text"));
      if (employeeCode == null || eventDate == null) {
        errorRowCount += 1;
        continue;
      }

      validRowCount += 1;
      String key = employeeCode + "::" + eventDate;
      FaceSummaryAccumulator accumulator =
          summaryMap.computeIfAbsent(key, ignored -> new FaceSummaryAccumulator(employeeCode, eventDate));

      String sourceName =
          parsingSupport.combineSourceName(
              JsonSupport.text(rawRow, "source_first_name"),
              JsonSupport.text(rawRow, "source_last_name"));
      if (isGenericDepartment(JsonSupport.text(rawRow, "source_department"))) {
        accumulator.qualityFlags.add("generic_department");
      }
      if (!parsingSupport.isLikelyHumanName(sourceName)) {
        accumulator.qualityFlags.add("nonhuman_name_source");
      }

      List<String> tokens = splitRecordTokens(JsonSupport.text(rawRow, "source_records_text"));
      List<String> validTimes =
          tokens.stream().filter(parsingSupport::isValidTimeToken).sorted().toList();
      List<String> invalidTimes =
          tokens.stream().filter(token -> !parsingSupport.isValidTimeToken(token)).toList();

      if (!invalidTimes.isEmpty()) {
        accumulator.qualityFlags.add("invalid_time_token");
      }

      for (String time : validTimes) {
        boolean isDuplicate = accumulator.countByTime.getOrDefault(time, 0) > 0;
        accumulator.countByTime.put(time, accumulator.countByTime.getOrDefault(time, 0) + 1);
        if (isDuplicate) {
          accumulator.qualityFlags.add("duplicate_face_time");
        }

        Map<String, Object> normalizedRecord = new LinkedHashMap<>();
        normalizedRecord.put("time", time);
        normalizedRecord.put("isDuplicate", isDuplicate);
        accumulator.normalizedRecords.add(normalizedRecord);

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("import_batch_id", batchId);
        eventPayload.put("raw_row_id", JsonSupport.text(rawRow, "id"));
        eventPayload.put("employee_code", employeeCode);
        eventPayload.put("event_date", eventDate);
        eventPayload.put("event_time", parsingSupport.toDatabaseTime(time));
        eventPayload.put("event_timestamp", null);
        eventPayload.put("event_sequence", accumulator.normalizedRecords.size());
        eventPayload.put("source_records_text", JsonSupport.text(rawRow, "source_records_text"));
        eventPayload.put("is_duplicate", isDuplicate);
        eventRows.add(eventPayload);
      }
    }

    summaryMap.values().forEach(
        accumulator -> {
          List<String> allTimes =
              accumulator.normalizedRecords.stream()
                  .map(record -> String.valueOf(record.get("time")))
                  .sorted()
                  .toList();
          Map<String, Object> summaryPayload = new LinkedHashMap<>();
          summaryPayload.put("import_batch_id", batchId);
          summaryPayload.put("employee_code", accumulator.employeeCode);
          summaryPayload.put("event_date", accumulator.eventDate);
          summaryPayload.put("face_first_seen", allTimes.isEmpty() ? null : parsingSupport.toDatabaseTime(allTimes.get(0)));
          summaryPayload.put(
              "face_last_seen",
              allTimes.isEmpty()
                  ? null
                  : parsingSupport.toDatabaseTime(allTimes.get(allTimes.size() - 1)));
          summaryPayload.put("face_event_count", accumulator.normalizedRecords.size());
          summaryPayload.put(
              "duplicate_event_count",
              accumulator.normalizedRecords.stream()
                  .filter(record -> Boolean.TRUE.equals(record.get("isDuplicate")))
                  .count());
          summaryPayload.put("normalized_records", accumulator.normalizedRecords);
          summaryPayload.put("quality_flags", List.copyOf(accumulator.qualityFlags));
          summaryRows.add(summaryPayload);
        });

    supabaseAdminClient.delete(
        "face_events", supabaseAdminClient.filters(Map.of("import_batch_id", "eq." + batchId)));
    supabaseAdminClient.delete(
        "face_daily_summary",
        supabaseAdminClient.filters(Map.of("import_batch_id", "eq." + batchId)));
    insertChunks("face_events", eventRows);
    insertChunks("face_daily_summary", summaryRows);

    return Map.of(
        "totalRawRows", rawRows.size(),
        "validRowCount", validRowCount,
        "errorRowCount", errorRowCount);
  }

  private Map<String, Object> normalizeFingerprintRows(String batchId) {
    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("import_batch_id", "eq." + batchId);
    query.add("order", "row_number.asc");
    ArrayNode rawRows = supabaseAdminClient.selectAll("fingerprint_raw_rows", query);

    Map<String, String> leaveCodeMap = fetchLeaveCodeMap();
    Map<String, Map<String, Object>> normalizedRowsByKey = new LinkedHashMap<>();
    int validRowCount = 0;
    int errorRowCount = 0;

    for (JsonNode rawRow : rawRows) {
      String employeeCode =
          trimToNull(JsonSupport.text(rawRow, "source_emp_no")) != null
              ? trimToNull(JsonSupport.text(rawRow, "source_emp_no"))
              : trimToNull(JsonSupport.text(rawRow, "source_epf_no"));
      String attendanceDate =
          parsingSupport.parseFlexibleDateText(JsonSupport.text(rawRow, "source_date_text"));

      if (employeeCode == null || attendanceDate == null) {
        errorRowCount += 1;
        continue;
      }

      validRowCount += 1;

      String timeInToken = trimToNull(JsonSupport.text(rawRow, "source_time_in_text"));
      String timeOutToken = trimToNull(JsonSupport.text(rawRow, "source_time_out_text"));
      boolean zeroTimePair = "00:00".equals(timeInToken) && "00:00".equals(timeOutToken);
      String timeIn = parsingSupport.isValidTimeToken(timeInToken) ? parsingSupport.toDatabaseTime(timeInToken) : null;
      String timeOut = parsingSupport.isValidTimeToken(timeOutToken) ? parsingSupport.toDatabaseTime(timeOutToken) : null;

      Double lateEarlyHours = parsingSupport.parseDecimalHours(JsonSupport.text(rawRow, "source_late_early_text"));
      Double otHours = parsingSupport.parseDecimalHours(JsonSupport.text(rawRow, "source_ot_text"));
      Double leaveDaysTotal = parsingSupport.parseDecimalHours(JsonSupport.text(rawRow, "source_leave_days_total_text"));
      Double nopayDaysTotal = parsingSupport.parseDecimalHours(JsonSupport.text(rawRow, "source_nopay_days_total_text"));
      Double otherLeaveDays = parsingSupport.parseDecimalHours(JsonSupport.text(rawRow, "source_other_leave_days_text"));
      String leaveType = normalizeLeaveType(JsonSupport.text(rawRow, "source_leave_type"));
      String leaveClass = leaveType == null ? null : leaveCodeMap.get(leaveType);

      Set<String> qualityFlags = new LinkedHashSet<>();
      if (zeroTimePair) {
        qualityFlags.add("zero_time_pair");
      }
      if (timeInToken != null && timeIn == null) {
        qualityFlags.add("invalid_time_in");
      }
      if (timeOutToken != null && timeOut == null) {
        qualityFlags.add("invalid_time_out");
      }

      List<NumericSource> numericSources =
          List.of(
              new NumericSource(JsonSupport.text(rawRow, "source_late_early_text"), lateEarlyHours),
              new NumericSource(JsonSupport.text(rawRow, "source_ot_text"), otHours),
              new NumericSource(JsonSupport.text(rawRow, "source_leave_days_total_text"), leaveDaysTotal),
              new NumericSource(JsonSupport.text(rawRow, "source_nopay_days_total_text"), nopayDaysTotal),
              new NumericSource(JsonSupport.text(rawRow, "source_other_leave_days_text"), otherLeaveDays));

      boolean malformedNumericField =
          numericSources.stream()
              .anyMatch(source -> source.sourceValue() != null && source.parsedValue() == null);
      if (malformedNumericField) {
        qualityFlags.add("malformed_numeric_field");
      }
      if (leaveType != null && timeIn == null && timeOut == null) {
        qualityFlags.add("leave_without_time");
      }

      String attendanceState =
          buildAttendanceState(
              leaveType,
              leaveClass,
              zeroTimePair,
              timeIn,
              timeOut,
              leaveDaysTotal,
              nopayDaysTotal,
              otherLeaveDays);

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("import_batch_id", batchId);
      payload.put("raw_row_id", JsonSupport.text(rawRow, "id"));
      payload.put("employee_code", employeeCode);
      payload.put("epf_no", JsonSupport.text(rawRow, "source_epf_no"));
      payload.put(
          "employee_name",
          trimToNull(parsingSupport.normalizeWhitespace(JsonSupport.text(rawRow, "source_name"))));
      payload.put(
          "designation",
          trimToNull(parsingSupport.normalizeWhitespace(JsonSupport.text(rawRow, "source_designation"))));
      payload.put(
          "department_name",
          trimToNull(parsingSupport.normalizeWhitespace(JsonSupport.text(rawRow, "source_department"))));
      payload.put(
          "employee_category",
          trimToNull(
              parsingSupport.normalizeWhitespace(
                  JsonSupport.text(rawRow, "source_employee_category"))));
      payload.put("attendance_date", attendanceDate);
      payload.put("time_in", zeroTimePair ? parsingSupport.toDatabaseTime("00:00") : timeIn);
      payload.put("time_out", zeroTimePair ? parsingSupport.toDatabaseTime("00:00") : timeOut);
      payload.put(
          "day_label",
          trimToNull(parsingSupport.normalizeWhitespace(JsonSupport.text(rawRow, "source_day"))));
      payload.put("late_early_hours", lateEarlyHours);
      payload.put("ot_hours", otHours);
      payload.put("leave_type", leaveType);
      payload.put("leave_days_total", leaveDaysTotal);
      payload.put("nopay_days_total", nopayDaysTotal);
      payload.put("other_leave_days", otherLeaveDays);
      payload.put("attendance_state", attendanceState);
      payload.put("quality_flags", List.copyOf(qualityFlags));
      String dedupeKey = batchId + "::" + employeeCode + "::" + attendanceDate;
      Map<String, Object> existing = normalizedRowsByKey.get(dedupeKey);
      normalizedRowsByKey.put(
          dedupeKey,
          existing == null
              ? payload
              : mergeFingerprintDailyAttendanceRow(existing, payload));
    }

    List<Map<String, Object>> normalizedRows = new ArrayList<>(normalizedRowsByKey.values());

    supabaseAdminClient.delete(
        "fingerprint_daily_attendance",
        supabaseAdminClient.filters(Map.of("import_batch_id", "eq." + batchId)));
    insertChunks("fingerprint_daily_attendance", normalizedRows);

    return Map.of(
        "totalRawRows", rawRows.size(),
        "validRowCount", validRowCount,
        "errorRowCount", errorRowCount);
  }

  private void upsertEmployeesFromFace(List<ImportParsingSupport.FaceParsedRow> rows) {
    Set<String> employeeCodes =
        rows.stream()
            .map(ImportParsingSupport.FaceParsedRow::employeeId)
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (employeeCodes.isEmpty()) {
      return;
    }

    Map<String, JsonNode> existingMap = fetchEmployeesByCodes(employeeCodes);
    upsertDepartments(
        rows.stream()
            .map(ImportParsingSupport.FaceParsedRow::department)
            .filter(value -> value != null && !value.isBlank())
            .toList());

    List<Map<String, Object>> upsertRows = new ArrayList<>();
    for (String employeeCode : employeeCodes) {
      ImportParsingSupport.FaceParsedRow row =
          rows.stream().filter(candidate -> employeeCode.equals(candidate.employeeId())).findFirst().orElse(null);
      String incomingName =
          row == null ? null : parsingSupport.combineSourceName(row.firstName(), row.lastName());
      upsertRows.add(
          mergeEmployeeDisplayData(
              existingMap.get(employeeCode),
              employeeCode,
              null,
              parsingSupport.isLikelyHumanName(incomingName) ? incomingName : null,
              null,
              row == null ? null : row.department(),
              null,
              parsingSupport.isLikelyHumanName(incomingName) ? "face" : null));
    }
    supabaseAdminClient.upsertMany("employees", upsertRows, "employee_code");
  }

  private void upsertEmployeesFromFingerprint(List<ImportParsingSupport.FingerprintParsedRow> rows) {
    Map<String, ImportParsingSupport.FingerprintParsedRow> canonicalRows = new LinkedHashMap<>();
    for (ImportParsingSupport.FingerprintParsedRow row : rows) {
      String employeeCode = resolveFingerprintEmployeeCode(row);
      if (employeeCode == null || canonicalRows.containsKey(employeeCode)) {
        continue;
      }
      canonicalRows.put(employeeCode, row);
    }

    if (canonicalRows.isEmpty()) {
      return;
    }

    Set<String> employeeCodes = canonicalRows.keySet();

    Map<String, JsonNode> existingMap = fetchEmployeesByCodes(employeeCodes);
    upsertDepartments(
        canonicalRows.values().stream()
            .map(ImportParsingSupport.FingerprintParsedRow::department)
            .filter(value -> value != null && !value.isBlank())
            .toList());

    List<Map<String, Object>> upsertRows = new ArrayList<>();
    for (ImportParsingSupport.FingerprintParsedRow row : canonicalRows.values()) {
      String employeeCode = resolveFingerprintEmployeeCode(row);
      upsertRows.add(
          mergeEmployeeDisplayData(
              existingMap.get(employeeCode),
              employeeCode,
              row.epfNo(),
              trimToNull(parsingSupport.normalizeWhitespace(row.name())),
              trimToNull(parsingSupport.normalizeWhitespace(row.designation())),
              trimToNull(parsingSupport.normalizeWhitespace(row.department())),
              trimToNull(parsingSupport.normalizeWhitespace(row.employeeCategory())),
              row.name() != null && parsingSupport.isLikelyHumanName(row.name()) ? "fingerprint" : null));
    }
    supabaseAdminClient.upsertMany("employees", upsertRows, "employee_code");
  }

  private Map<String, JsonNode> fetchEmployeesByCodes(Set<String> employeeCodes) {
    if (employeeCodes.isEmpty()) {
      return Map.of();
    }

    Map<String, JsonNode> existing = new HashMap<>();
    List<String> codes = new ArrayList<>(employeeCodes);
    int chunkSize = 150;

    for (int index = 0; index < codes.size(); index += chunkSize) {
      int end = Math.min(codes.size(), index + chunkSize);
      MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
      query.add("employee_code", buildInFilter(Set.copyOf(codes.subList(index, end))));
      ArrayNode rows = supabaseAdminClient.select("employees", query);
      rows.forEach(row -> existing.put(JsonSupport.text(row, "employee_code"), row));
    }

    return existing;
  }

  private void upsertDepartments(List<String> departments) {
    List<Map<String, Object>> rows =
        departments.stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .map(value -> Map.<String, Object>of("name", value))
            .toList();
    supabaseAdminClient.upsertMany("departments", rows, "name");
  }

  private Map<String, Object> mergeEmployeeDisplayData(
      JsonNode existing,
      String employeeCode,
      String epfNo,
      String displayName,
      String designation,
      String departmentName,
      String employeeCategory,
      String sourcePriorityName) {
    String existingPriority = JsonSupport.text(existing, "source_priority_name");
    String existingDisplayName = JsonSupport.text(existing, "display_name");

    boolean shouldReplaceName =
        displayName != null
            && (existingDisplayName == null
                || !parsingSupport.isLikelyHumanName(existingDisplayName)
                || ("fingerprint".equals(sourcePriorityName) && !"fingerprint".equals(existingPriority)));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("employee_code", employeeCode);
    payload.put("epf_no", epfNo != null ? epfNo : JsonSupport.text(existing, "epf_no"));
    payload.put(
        "display_name",
        shouldReplaceName ? displayName : existingDisplayName != null ? existingDisplayName : displayName);
    payload.put(
        "designation",
        pickBetterText(JsonSupport.text(existing, "designation"), designation));
    payload.put(
        "department_name",
        pickBetterText(JsonSupport.text(existing, "department_name"), departmentName));
    payload.put(
        "employee_category",
        pickBetterText(JsonSupport.text(existing, "employee_category"), employeeCategory));
    payload.put(
        "source_priority_name",
        shouldReplaceName && sourcePriorityName != null
            ? sourcePriorityName
            : existingPriority != null ? existingPriority : sourcePriorityName);
    payload.put("is_active", JsonSupport.bool(existing, "is_active") == null || JsonSupport.bool(existing, "is_active"));
    return payload;
  }

  private Map<String, String> fetchLeaveCodeMap() {
    ArrayNode rows = supabaseAdminClient.select("leave_code_map", new LinkedMultiValueMap<>());
    Map<String, String> leaveCodeMap = new HashMap<>();
    rows.forEach(
        row -> {
          String code = JsonSupport.text(row, "code");
          String attendanceClass = JsonSupport.text(row, "attendance_class");
          if (code != null && attendanceClass != null) {
            leaveCodeMap.put(code.toUpperCase(Locale.ROOT), attendanceClass);
          }
        });
    return leaveCodeMap;
  }

  private String buildAttendanceState(
      String leaveType,
      String leaveClass,
      boolean zeroTimePair,
      String timeIn,
      String timeOut,
      Double leaveDaysTotal,
      Double nopayDaysTotal,
      Double otherLeaveDays) {
    boolean hasLeaveTotals =
        valueOrZero(leaveDaysTotal) > 0
            || valueOrZero(nopayDaysTotal) > 0
            || valueOrZero(otherLeaveDays) > 0;

    if (leaveType != null && "absent".equals(leaveClass)) {
      return "absent";
    }
    if (leaveType != null && (hasLeaveTotals || "leave".equals(leaveClass))) {
      return "leave";
    }
    if (zeroTimePair && leaveType == null) {
      return "review";
    }
    if (timeIn != null || timeOut != null) {
      return "present";
    }
    if (leaveType != null) {
      return "review";
    }
    return "no_data";
  }

  private void insertChunks(String table, List<Map<String, Object>> rows) {
    if (rows.isEmpty()) {
      return;
    }
    int chunkSize = 500;
    for (int index = 0; index < rows.size(); index += chunkSize) {
      int end = Math.min(rows.size(), index + chunkSize);
      supabaseAdminClient.insertMany(table, rows.subList(index, end));
    }
  }

  private String resolveFingerprintEmployeeCode(ImportParsingSupport.FingerprintParsedRow row) {
    return trimToNull(row.empNo()) != null ? trimToNull(row.empNo()) : trimToNull(row.epfNo());
  }

  private String normalizeLeaveType(String value) {
    if (value == null) {
      return null;
    }
    String normalized = parsingSupport.normalizeWhitespace(value).toUpperCase(Locale.ROOT);
    return normalized.isBlank() || "0".equals(normalized) || "0.0".equals(normalized) ? null : normalized;
  }

  private String buildInFilter(Set<String> values) {
    return "in.(" + values.stream().map(this::quoteFilterValue).collect(Collectors.joining(",")) + ")";
  }

  private String quoteFilterValue(String value) {
    return "\"" + value.replace("\"", "\\\"") + "\"";
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private boolean isGenericDepartment(String value) {
    if (value == null) {
      return false;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return List.of("union north", "production", "factory", "garment").contains(normalized);
  }

  private List<String> splitRecordTokens(String recordsText) {
    if (recordsText == null || recordsText.isBlank()) {
      return List.of();
    }
    return List.of(recordsText.split(";")).stream()
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();
  }

  private String pickBetterText(String existing, String incoming) {
    if (incoming == null || incoming.isBlank()) {
      return existing;
    }
    if (existing == null || existing.isBlank()) {
      return incoming;
    }
    return incoming.length() > existing.length() ? incoming : existing;
  }

  private double valueOrZero(Double value) {
    return value == null ? 0d : value;
  }

  private Map<String, Object> mergeFingerprintDailyAttendanceRow(
      Map<String, Object> existing, Map<String, Object> incoming) {
    Map<String, Object> merged = new LinkedHashMap<>(existing);

    for (String key :
        List.of(
            "epf_no",
            "employee_name",
            "designation",
            "department_name",
            "employee_category",
            "time_in",
            "time_out",
            "day_label",
            "late_early_hours",
            "ot_hours",
            "leave_type",
            "leave_days_total",
            "nopay_days_total",
            "other_leave_days",
            "attendance_state")) {
      Object current = merged.get(key);
      Object candidate = incoming.get(key);
      if (shouldReplaceFingerprintField(current, candidate)) {
        merged.put(key, candidate);
      }
    }

    Set<String> qualityFlags = new LinkedHashSet<>();
    Object existingFlags = existing.get("quality_flags");
    if (existingFlags instanceof List<?> values) {
      values.stream().map(String::valueOf).forEach(qualityFlags::add);
    }
    Object incomingFlags = incoming.get("quality_flags");
    if (incomingFlags instanceof List<?> values) {
      values.stream().map(String::valueOf).forEach(qualityFlags::add);
    }
    qualityFlags.add("duplicate_daily_attendance_row");
    merged.put("quality_flags", List.copyOf(qualityFlags));

    return merged;
  }

  private boolean shouldReplaceFingerprintField(Object current, Object candidate) {
    if (candidate == null) {
      return false;
    }
    if (current == null) {
      return true;
    }
    if (current instanceof String currentText && candidate instanceof String candidateText) {
      if (currentText.isBlank()) {
        return !candidateText.isBlank();
      }
      if ("00:00:00".equals(currentText) && !"00:00:00".equals(candidateText)) {
        return true;
      }
      return candidateText.length() > currentText.length() && !candidateText.equals(currentText);
    }
    if (current instanceof Number currentNumber && candidate instanceof Number candidateNumber) {
      return currentNumber.doubleValue() == 0d && candidateNumber.doubleValue() != 0d;
    }
    return false;
  }

  private Map<String, Object> mapBatchSummary(JsonNode row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", JsonSupport.text(row, "id"));
    payload.put("sourceType", JsonSupport.text(row, "source_type"));
    payload.put("originalFilename", JsonSupport.text(row, "original_filename"));
    payload.put("importStatus", JsonSupport.text(row, "import_status"));
    payload.put("storagePath", JsonSupport.text(row, "storage_path"));
    payload.put("fileMimeType", JsonSupport.text(row, "file_mime_type"));
    payload.put("fileSizeBytes", JsonSupport.longValue(row, "file_size_bytes"));
    payload.put("totalRawRows", JsonSupport.integer(row, "total_raw_rows"));
    payload.put("totalValidRows", JsonSupport.integer(row, "total_valid_rows"));
    payload.put("totalErrorRows", JsonSupport.integer(row, "total_error_rows"));
    payload.put("notes", JsonSupport.text(row, "notes"));
    payload.put("startedAt", JsonSupport.text(row, "started_at"));
    payload.put("completedAt", JsonSupport.text(row, "completed_at"));
    payload.put("createdAt", JsonSupport.text(row, "created_at"));
    payload.put("updatedAt", JsonSupport.text(row, "updated_at"));
    return payload;
  }

  private static final class FaceSummaryAccumulator {
    private final String employeeCode;
    private final String eventDate;
    private final List<Map<String, Object>> normalizedRecords = new ArrayList<>();
    private final Set<String> qualityFlags = new LinkedHashSet<>();
    private final Map<String, Integer> countByTime = new HashMap<>();

    private FaceSummaryAccumulator(String employeeCode, String eventDate) {
      this.employeeCode = employeeCode;
      this.eventDate = eventDate;
    }
  }

  private record NumericSource(String sourceValue, Double parsedValue) {
  }
}
