package com.garmentline.operations.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.garmentline.operations.config.EmployeePortalProperties;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.ApiException;
import com.garmentline.operations.support.JsonSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
public class EmployeePortalService {

  private static final Logger log = LoggerFactory.getLogger(EmployeePortalService.class);
  private static final double DEFAULT_LEAVE_ALLOWANCE = 14;
  private static final int PASSWORD_ITERATIONS = 120_000;
  private static final int PASSWORD_KEY_LENGTH = 256;

  private final SupabaseAdminClient supabaseAdminClient;
  private final EmployeePortalProperties portalProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public EmployeePortalService(
      SupabaseAdminClient supabaseAdminClient, EmployeePortalProperties portalProperties) {
    this.supabaseAdminClient = supabaseAdminClient;
    this.portalProperties = portalProperties;
  }

  public Map<String, Object> setupPassword(PortalPasswordSetupRequest request) {
    String employeeCode = requireText(request.employeeCode(), "Employee number is required.");
    String phone = requireText(request.phoneNumber(), "Registered phone number is required.");
    String password = requirePassword(request.password());
    String normalizedPhone = normalizePhone(phone);

    JsonNode employee = findEmployeeByCode(employeeCode);
    if (employee == null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "No active employee found for employee number " + employeeCode + ".");
    }

    String employeeId = JsonSupport.text(employee, "id");
    JsonNode employeeProfile = employeeProfile(employeeId);
    String registeredPhone = JsonSupport.text(employeeProfile, "phone");
    if (!hasText(registeredPhone)) {
      throw new ApiException(HttpStatus.CONFLICT, "This employee does not have a registered phone number. Ask HR to update the profile.");
    }
    if (!normalizePhone(registeredPhone).equals(normalizedPhone)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "The phone number does not match the employee's registered number.");
    }

    if (credentialByEmployeeId(employeeId) != null || credentialByPhone(normalizedPhone) != null) {
      throw new ApiException(HttpStatus.CONFLICT, "A portal password is already configured for this employee.");
    }

    SecretHash passwordHash = hashSecret(password);
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("employee_id", employeeId);
    row.put("phone", registeredPhone);
    row.put("phone_normalized", normalizedPhone);
    row.put("password_hash", passwordHash.hash());
    row.put("password_salt", passwordHash.salt());
    row.put("is_active", true);
    supabaseAdminClient.insertSingle("employee_portal_credentials", row);

    return otpRequiredResponse(employee, registeredPhone, normalizedPhone, "Portal password created. Enter the OTP sent to the registered phone.");
  }

  public Map<String, Object> login(PortalLoginRequest request) {
    String phone = requireText(request.phoneNumber(), "Phone number is required.");
    String password = requireText(request.password(), "Password is required.");
    String normalizedPhone = normalizePhone(phone);

    JsonNode credential = credentialByPhone(normalizedPhone);
    if (credential == null || !Boolean.TRUE.equals(JsonSupport.bool(credential, "is_active"))) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid phone number or password.");
    }

    if (!verifySecret(password, JsonSupport.text(credential, "password_salt"), JsonSupport.text(credential, "password_hash"))) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid phone number or password.");
    }

    String employeeId = JsonSupport.text(credential, "employee_id");
    JsonNode employee = findEmployeeById(employeeId);
    if (employee == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "The linked employee record is inactive or missing.");
    }

    if (requiresWeeklyOtp(credential)) {
      return otpRequiredResponse(
          employee,
          JsonSupport.text(credential, "phone"),
          normalizedPhone,
          "Weekly Monday OTP validation is required for this employee portal session.");
    }

    return authenticatedResponse(employee, issueSession(employeeId));
  }

  public Map<String, Object> verifyOtp(PortalOtpVerifyRequest request) {
    String challengeId = requireText(request.challengeId(), "OTP challenge is required.");
    String code = requireText(request.code(), "OTP code is required.").replaceAll("\\s+", "");

    JsonNode challenge =
        firstOrNull(select("employee_portal_otp_challenges", Map.of("id", "eq." + challengeId), null, 1));
    if (challenge == null || JsonSupport.text(challenge, "verified_at") != null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP challenge is invalid or already used.");
    }

    if (expiresAt(challenge).isBefore(Instant.now())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP challenge has expired. Sign in again to request a new code.");
    }

    int attempts = JsonSupport.integer(challenge, "attempts") == null ? 0 : JsonSupport.integer(challenge, "attempts");
    if (attempts >= portalProperties.resolvedOtpMaxAttempts()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Too many OTP attempts. Sign in again to request a new code.");
    }

    String employeeId = JsonSupport.text(challenge, "employee_id");
    JsonNode credential = credentialByEmployeeId(employeeId);
    if (credential == null || !Boolean.TRUE.equals(JsonSupport.bool(credential, "is_active"))) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Portal credentials are inactive.");
    }

    if (!verifySecret(code, JsonSupport.text(challenge, "code_salt"), JsonSupport.text(challenge, "code_hash"))) {
      updateById("employee_portal_otp_challenges", challengeId, Map.of("attempts", attempts + 1));
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid OTP code.");
    }

    Instant now = Instant.now();
    String weekStart = currentWeekStart().toString();
    updateById(
        "employee_portal_otp_challenges",
        challengeId,
        Map.of("attempts", attempts + 1, "verified_at", now.toString()));
    updateById(
        "employee_portal_credentials",
        JsonSupport.text(credential, "id"),
        Map.of(
            "weekly_otp_verified_week_start",
            weekStart,
            "weekly_otp_verified_at",
            now.toString()));

    JsonNode employee = findEmployeeById(employeeId);
    if (employee == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "The linked employee record is inactive or missing.");
    }

    return authenticatedResponse(employee, issueSession(employeeId));
  }

  public Map<String, Object> getPortal(String token) {
    PortalSession session = requireSession(token);
    updateById("employee_portal_sessions", session.sessionId(), Map.of("last_used_at", Instant.now().toString()));
    return snapshot(session.employeeId());
  }

  public Map<String, Object> createLeaveRequest(String token, EmployeeLeaveRequest request) {
    PortalSession session = requireSession(token);
    JsonNode employee = findEmployeeById(session.employeeId());
    if (employee == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "The linked employee record is inactive or missing.");
    }

    String leaveType = leaveType(request.leaveType());
    String startDate = requireText(request.startDate(), "Start date is required.");
    String endDate = requireText(request.endDate(), "End date is required.");
    validateDateRange(startDate, endDate);

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("employee_id", session.employeeId());
    row.put("leave_type", leaveType);
    row.put("leave_category", leaveCategory(request.leaveCategory()));
    row.put("start_date", startDate);
    row.put("end_date", endDate);
    row.put("start_time", timeOrNull(request.startTime()));
    row.put("end_time", timeOrNull(request.endTime()));
    row.put("half_day_session", halfDaySession(request.halfDaySession()));
    row.put("reason", blankToNull(request.reason()));
    row.put("status", "pending");
    row.put("requested_by", null);

    supabaseAdminClient.insertSingle("employee_leave_requests", row);
    updateById("employee_portal_sessions", session.sessionId(), Map.of("last_used_at", Instant.now().toString()));
    return snapshot(session.employeeId());
  }

  public Map<String, Object> latestKioskRecognition(String lastEventId) {
    String cleanLastEventId = blankToNull(lastEventId);
    ArrayNode events = latestMatchedRecognitionEvents();
    Instant cutoff =
        Instant.now()
            .minus(
                portalProperties.resolvedKioskRecognitionWindowSeconds(),
                ChronoUnit.SECONDS);

    for (JsonNode event : events) {
      String eventId = recognitionEventId(event);
      if (hasText(cleanLastEventId) && cleanLastEventId.equals(eventId)) {
        return kioskIdle(cleanLastEventId, "Waiting for the next face recognition event.");
      }

      Instant eventTime = timestamp(event, "event_time");
      if (eventTime.isBefore(cutoff)) {
        return kioskIdle(cleanLastEventId, "Waiting for a current face recognition event.");
      }

      JsonNode employee = employeeForRecognitionEvent(event);
      if (employee == null) {
        continue;
      }

      String employeeId = JsonSupport.text(employee, "id");
      IssuedSession session = issueKioskSession(employeeId);

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("status", "recognized");
      payload.put("eventId", eventId);
      payload.put("eventTime", JsonSupport.text(event, "event_time"));
      payload.put("expiresAt", session.expiresAt().toString());
      payload.put("token", session.token());
      payload.put("recognition", recognitionPayload(event, employee));
      payload.put("snapshot", snapshot(employeeId));
      return payload;
    }

    return kioskIdle(cleanLastEventId, "Waiting for face recognition.");
  }

  public Map<String, Object> logout(String token) {
    PortalSession session = requireSession(token);
    updateById("employee_portal_sessions", session.sessionId(), Map.of("revoked_at", Instant.now().toString()));
    return Map.of("ok", true);
  }

  private Map<String, Object> authenticatedResponse(JsonNode employee, IssuedSession session) {
    String employeeId = JsonSupport.text(employee, "id");
    JsonNode credential = credentialByEmployeeId(employeeId);
    if (credential != null) {
      updateById("employee_portal_credentials", JsonSupport.text(credential, "id"), Map.of("last_login_at", Instant.now().toString()));
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", "authenticated");
    payload.put("token", session.token());
    payload.put("expiresAt", session.expiresAt().toString());
    payload.put("snapshot", snapshot(employeeId));
    return payload;
  }

  private Map<String, Object> otpRequiredResponse(
      JsonNode employee, String phone, String normalizedPhone, String message) {
    String employeeId = JsonSupport.text(employee, "id");
    String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    SecretHash codeHash = hashSecret(code);
    Instant expiresAt = Instant.now().plus(portalProperties.resolvedOtpMinutes(), ChronoUnit.MINUTES);
    String weekStart = currentWeekStart().toString();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("employee_id", employeeId);
    row.put("phone", phone);
    row.put("phone_normalized", normalizedPhone);
    row.put("purpose", "weekly_revalidation");
    row.put("week_start", weekStart);
    row.put("code_hash", codeHash.hash());
    row.put("code_salt", codeHash.salt());
    row.put("expires_at", expiresAt.toString());
    JsonNode challenge = supabaseAdminClient.insertSingle("employee_portal_otp_challenges", row);

    log.info(
        "Employee portal OTP for employee {} phone {} week {} is {}. Configure SMS delivery before production use.",
        JsonSupport.text(employee, "employee_code"),
        maskPhone(phone),
        weekStart,
        code);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", "otp_required");
    payload.put("challengeId", JsonSupport.text(challenge, "id"));
    payload.put("maskedPhone", maskPhone(phone));
    payload.put("weekStart", weekStart);
    payload.put("expiresAt", expiresAt.toString());
    payload.put("message", message);
    payload.put("deliveryMode", "backend_log");
    if (portalProperties.exposeDevelopmentOtp()) {
      payload.put("developmentOtp", code);
    }
    return payload;
  }

  private IssuedSession issueSession(String employeeId) {
    return issueSession(employeeId, portalProperties.resolvedSessionHours(), ChronoUnit.HOURS);
  }

  private IssuedSession issueKioskSession(String employeeId) {
    return issueSession(employeeId, portalProperties.resolvedKioskSessionMinutes(), ChronoUnit.MINUTES);
  }

  private IssuedSession issueSession(String employeeId, long amount, ChronoUnit unit) {
    byte[] tokenBytes = new byte[32];
    secureRandom.nextBytes(tokenBytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    String tokenHash = sha256(token);
    Instant expiresAt = Instant.now().plus(amount, unit);

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("employee_id", employeeId);
    row.put("token_hash", tokenHash);
    row.put("expires_at", expiresAt.toString());
    supabaseAdminClient.insertSingle("employee_portal_sessions", row);
    return new IssuedSession(token, expiresAt);
  }

  private PortalSession requireSession(String token) {
    String cleanToken = requireText(token, "Employee portal session is required.");
    JsonNode session =
        firstOrNull(select("employee_portal_sessions", Map.of("token_hash", "eq." + sha256(cleanToken)), null, 1));
    if (session == null || JsonSupport.text(session, "revoked_at") != null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Employee portal session is invalid. Sign in again.");
    }
    if (expiresAt(session).isBefore(Instant.now())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Employee portal session has expired. Sign in again.");
    }
    String employeeId = JsonSupport.text(session, "employee_id");
    if (findEmployeeById(employeeId) == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "The linked employee record is inactive or missing.");
    }
    return new PortalSession(JsonSupport.text(session, "id"), employeeId);
  }

  private Map<String, Object> snapshot(String employeeId) {
    JsonNode employee = findEmployeeById(employeeId);
    if (employee == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "The linked employee record is inactive or missing.");
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("linked", true);
    payload.put("profile", profilePayload(employee));

    String employeeCode = JsonSupport.text(employee, "employee_code");
    JsonNode employeeProfile = employeeProfile(employeeId);
    JsonNode assignment =
        firstOrNull(
            select(
                "line_assignments",
                Map.of("employee_id", "eq." + employeeId, "status", "eq.Active"),
                "assigned_at.desc",
                1));
    JsonNode line = null;
    if (assignment != null) {
      line =
          firstOrNull(
              select(
                  "production_lines",
                  Map.of("id", "eq." + JsonSupport.text(assignment, "production_line_id")),
                  null,
                  1));
    }

    ArrayNode attendanceRows =
        select("attendance_reconciliation", Map.of("employee_code", "eq." + employeeCode), "attendance_date.desc", 60);
    ArrayNode leaveRows =
        select("employee_leave_requests", Map.of("employee_id", "eq." + employeeId), "requested_at.desc", 50);
    ArrayNode incentiveRows =
        select("incentive_records", Map.of("employee_id", "eq." + employeeId), "month_start.desc", 12);

    List<Map<String, Object>> leaveRequests = leaveRows.valueStream().map(this::leaveRequestPayload).toList();

    payload.put("employee", employeePayload(employee, employeeProfile));
    payload.put("currentLine", line == null ? null : linePayload(line, assignment));
    payload.put("attendanceHistory", attendanceRows.valueStream().map(this::attendancePayload).toList());
    payload.put("leaveRequests", leaveRequests);
    payload.put("incentives", incentiveRows.valueStream().map(this::incentivePayload).toList());
    payload.put("leaveBalance", leaveBalance(leaveRequests));
    return payload;
  }

  private boolean requiresWeeklyOtp(JsonNode credential) {
    String verifiedWeekStart = JsonSupport.text(credential, "weekly_otp_verified_week_start");
    return !currentWeekStart().toString().equals(verifiedWeekStart);
  }

  private LocalDate currentWeekStart() {
    ZoneId zone = ZoneId.of(portalProperties.resolvedTimeZone());
    return LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private JsonNode credentialByPhone(String normalizedPhone) {
    return firstOrNull(
        select(
            "employee_portal_credentials",
            Map.of("phone_normalized", "eq." + normalizedPhone, "is_active", "eq.true"),
            null,
            1));
  }

  private JsonNode credentialByEmployeeId(String employeeId) {
    return firstOrNull(
        select(
            "employee_portal_credentials",
            Map.of("employee_id", "eq." + employeeId, "is_active", "eq.true"),
            null,
            1));
  }

  private JsonNode findEmployeeByCode(String employeeCode) {
    return firstOrNull(
        select(
            "employees",
            Map.of("employee_code", "eq." + employeeCode.trim(), "is_active", "eq.true"),
            null,
            1));
  }

  private JsonNode findEmployeeById(String employeeId) {
    if (!hasText(employeeId)) return null;
    return firstOrNull(select("employees", Map.of("id", "eq." + employeeId, "is_active", "eq.true"), null, 1));
  }

  private JsonNode employeeProfile(String employeeId) {
    if (!hasText(employeeId)) return null;
    return firstOrNull(select("employee_profiles", Map.of("employee_id", "eq." + employeeId), null, 1));
  }

  private ArrayNode latestMatchedRecognitionEvents() {
    LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add(
        "select",
        "id,camera_event_id,camera_serial_no,employee_code,employee_id,device_person_name,matched_employee_name,matched_department,match_status,event_time,received_at,verify_mode,attendance_status,access_decision,picture_url,visible_light_pic_url");
    query.add("match_status", "eq.matched");
    query.add("order", "event_time.desc");
    query.add("limit", "10");
    return supabaseAdminClient.select("hikvision_face_events", query);
  }

  private JsonNode employeeForRecognitionEvent(JsonNode event) {
    String employeeId = JsonSupport.text(event, "employee_id");
    JsonNode employee = findEmployeeById(employeeId);
    if (employee != null) {
      return employee;
    }

    String employeeCode = JsonSupport.text(event, "employee_code");
    return hasText(employeeCode) ? findEmployeeByCode(employeeCode) : null;
  }

  private String recognitionEventId(JsonNode event) {
    return fallback(JsonSupport.text(event, "camera_event_id"), JsonSupport.text(event, "id"));
  }

  private Map<String, Object> kioskIdle(String lastEventId, String message) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", "idle");
    payload.put("lastEventId", lastEventId);
    payload.put("message", message);
    return payload;
  }

  private Map<String, Object> recognitionPayload(JsonNode event, JsonNode employee) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", recognitionEventId(event));
    payload.put("eventTime", JsonSupport.text(event, "event_time"));
    payload.put("cameraSerialNo", JsonSupport.text(event, "camera_serial_no"));
    payload.put("employeeCode", JsonSupport.text(event, "employee_code"));
    payload.put("employeeName", fallback(JsonSupport.text(event, "matched_employee_name"), JsonSupport.text(employee, "display_name")));
    payload.put("department", fallback(JsonSupport.text(event, "matched_department"), JsonSupport.text(employee, "department_name")));
    payload.put("devicePersonName", JsonSupport.text(event, "device_person_name"));
    payload.put("verifyMode", JsonSupport.text(event, "verify_mode"));
    payload.put("attendanceStatus", JsonSupport.text(event, "attendance_status"));
    payload.put("accessDecision", JsonSupport.text(event, "access_decision"));
    payload.put("pictureUrl", fallback(JsonSupport.text(event, "visible_light_pic_url"), JsonSupport.text(event, "picture_url")));
    return payload;
  }

  private ArrayNode select(String table, Map<String, String> filters, String order, Integer limit) {
    LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    filters.forEach(query::add);
    if (hasText(order)) {
      query.add("order", order);
    }
    if (limit != null) {
      query.add("limit", String.valueOf(limit));
    }
    return supabaseAdminClient.selectAll(table, query);
  }

  private void updateById(String table, String id, Map<String, Object> row) {
    supabaseAdminClient.updateSingle(table, supabaseAdminClient.filters(Map.of("id", "eq." + id)), row);
  }

  private JsonNode firstOrNull(ArrayNode rows) {
    return rows == null || rows.isEmpty() ? null : rows.get(0);
  }

  private Map<String, Object> profilePayload(JsonNode employee) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", JsonSupport.text(employee, "id"));
    payload.put("employeeId", JsonSupport.text(employee, "id"));
    payload.put("employeeCode", JsonSupport.text(employee, "employee_code"));
    payload.put("fullName", fallback(JsonSupport.text(employee, "display_name"), JsonSupport.text(employee, "employee_code")));
    payload.put("role", "employee");
    return payload;
  }

  private Map<String, Object> employeePayload(JsonNode employee, JsonNode profile) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", JsonSupport.text(employee, "id"));
    payload.put("employeeCode", JsonSupport.text(employee, "employee_code"));
    payload.put("fullName", fallback(JsonSupport.text(employee, "display_name"), JsonSupport.text(employee, "employee_code")));
    payload.put("designation", JsonSupport.text(employee, "designation"));
    payload.put("department", JsonSupport.text(employee, "department_name"));
    payload.put("photoUrl", profile == null ? null : JsonSupport.text(profile, "photo_url"));
    payload.put("shift", profile == null ? null : JsonSupport.text(profile, "shift_name"));
    payload.put("phone", profile == null ? null : JsonSupport.text(profile, "phone"));
    return payload;
  }

  private Map<String, Object> linePayload(JsonNode line, JsonNode assignment) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", JsonSupport.text(line, "id"));
    payload.put("code", JsonSupport.text(line, "code"));
    payload.put("name", JsonSupport.text(line, "name"));
    payload.put("department", JsonSupport.text(line, "department_name"));
    payload.put("shift", JsonSupport.text(line, "shift_name"));
    payload.put("supervisor", JsonSupport.text(line, "supervisor_name"));
    payload.put("assignedAt", assignment == null ? null : JsonSupport.text(assignment, "assigned_at"));
    return payload;
  }

  private Map<String, Object> attendancePayload(JsonNode row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", JsonSupport.text(row, "id"));
    payload.put("date", JsonSupport.text(row, "attendance_date"));
    payload.put("status", JsonSupport.text(row, "reconciliation_status"));
    payload.put("timeIn", JsonSupport.text(row, "fingerprint_time_in"));
    payload.put("timeOut", JsonSupport.text(row, "fingerprint_time_out"));
    payload.put("faceFirstSeen", JsonSupport.text(row, "face_first_seen"));
    payload.put("faceLastSeen", JsonSupport.text(row, "face_last_seen"));
    payload.put("otHours", JsonSupport.decimal(row, "ot_hours"));
    payload.put("lateEarlyHours", JsonSupport.decimal(row, "late_early_hours"));
    payload.put("leaveType", JsonSupport.text(row, "leave_type"));
    payload.put("exceptionReason", JsonSupport.text(row, "exception_reason"));
    return payload;
  }

  private Map<String, Object> leaveRequestPayload(JsonNode row) {
    String startDate = JsonSupport.text(row, "start_date");
    String endDate = JsonSupport.text(row, "end_date");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", JsonSupport.text(row, "id"));
    payload.put("leaveType", JsonSupport.text(row, "leave_type"));
    payload.put("leaveCategory", JsonSupport.text(row, "leave_category"));
    payload.put("startDate", startDate);
    payload.put("endDate", endDate);
    payload.put("startTime", JsonSupport.text(row, "start_time"));
    payload.put("endTime", JsonSupport.text(row, "end_time"));
    payload.put("halfDaySession", JsonSupport.text(row, "half_day_session"));
    payload.put("reason", JsonSupport.text(row, "reason"));
    payload.put("status", JsonSupport.text(row, "status"));
    payload.put("reviewNote", JsonSupport.text(row, "review_note"));
    payload.put("requestedAt", JsonSupport.text(row, "requested_at"));
    payload.put("reviewedAt", JsonSupport.text(row, "reviewed_at"));
    payload.put("dayCount", dayCount(JsonSupport.text(row, "leave_type"), startDate, endDate));
    return payload;
  }

  private Map<String, Object> incentivePayload(JsonNode row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", JsonSupport.text(row, "id"));
    payload.put("monthStart", JsonSupport.text(row, "month_start"));
    payload.put("amount", JsonSupport.decimal(row, "amount"));
    payload.put("reason", JsonSupport.text(row, "reason"));
    return payload;
  }

  private Map<String, Object> leaveBalance(List<Map<String, Object>> leaveRequests) {
    int currentYear = LocalDate.now().getYear();
    double used =
        leaveRequests.stream()
            .filter(row -> "approved".equals(row.get("status")))
            .filter(row -> row.get("startDate") instanceof String date && date.startsWith(String.valueOf(currentYear)))
            .mapToDouble(row -> row.get("dayCount") instanceof Number number ? number.doubleValue() : 0)
            .sum();

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("allowanceDays", DEFAULT_LEAVE_ALLOWANCE);
    payload.put("usedDays", used);
    payload.put("remainingDays", Math.max(0, DEFAULT_LEAVE_ALLOWANCE - used));
    return payload;
  }

  private double dayCount(String leaveType, String startDate, String endDate) {
    if ("short_leave".equals(leaveType)) return 0;
    if ("half_day".equals(leaveType)) return 0.5;
    try {
      return ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + 1;
    } catch (RuntimeException exception) {
      return 0;
    }
  }

  private void validateDateRange(String startDate, String endDate) {
    try {
      if (LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate))) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "End date must be same as or after start date.");
      }
    } catch (ApiException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Leave dates must use yyyy-MM-dd format.");
    }
  }

  private String leaveType(String value) {
    String normalized = normalize(value);
    if (List.of("full_day", "half_day", "short_leave").contains(normalized)) {
      return normalized;
    }
    throw new ApiException(HttpStatus.BAD_REQUEST, "Leave type must be full_day, half_day, or short_leave.");
  }

  private String leaveCategory(String value) {
    String normalized = normalize(value);
    if (!hasText(normalized)) return "casual";
    if (List.of("annual", "casual", "sick", "no_pay", "emergency", "personal", "medical", "other").contains(normalized)) {
      return normalized;
    }
    return "other";
  }

  private String halfDaySession(String value) {
    String normalized = normalize(value);
    if (!hasText(normalized)) return null;
    if (List.of("first_half", "second_half").contains(normalized)) {
      return normalized;
    }
    return null;
  }

  private String timeOrNull(String value) {
    if (!hasText(value)) return null;
    return value.length() == 5 ? value + ":00" : value;
  }

  private String requirePassword(String value) {
    String password = requireText(value, "Password is required.");
    if (password.length() < 8) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Use at least 8 characters for the employee portal password.");
    }
    return password;
  }

  private SecretHash hashSecret(String value) {
    byte[] saltBytes = new byte[16];
    secureRandom.nextBytes(saltBytes);
    String salt = Base64.getEncoder().encodeToString(saltBytes);
    return new SecretHash(salt, hashSecret(value, salt));
  }

  private boolean verifySecret(String value, String salt, String expectedHash) {
    if (!hasText(value) || !hasText(salt) || !hasText(expectedHash)) {
      return false;
    }
    return MessageDigest.isEqual(
        hashSecret(value, salt).getBytes(StandardCharsets.UTF_8),
        expectedHash.getBytes(StandardCharsets.UTF_8));
  }

  private String hashSecret(String value, String salt) {
    try {
      byte[] saltBytes = Base64.getDecoder().decode(salt);
      KeySpec spec = new PBEKeySpec(value.toCharArray(), saltBytes, PASSWORD_ITERATIONS, PASSWORD_KEY_LENGTH);
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process secure employee portal credentials.");
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process employee portal session.");
    }
  }

  private Instant expiresAt(JsonNode row) {
    return timestamp(row, "expires_at");
  }

  private Instant timestamp(JsonNode row, String fieldName) {
    String value = JsonSupport.text(row, fieldName);
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (RuntimeException exception) {
      return Instant.EPOCH;
    }
  }

  private String normalizePhone(String value) {
    String digits = value == null ? "" : value.replaceAll("\\D", "");
    if (digits.startsWith("00")) {
      digits = digits.substring(2);
    }
    if (digits.startsWith("0") && digits.length() == 10) {
      digits = "94" + digits.substring(1);
    }
    if (!hasText(digits) || digits.length() < 8) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid registered phone number.");
    }
    return digits;
  }

  private String maskPhone(String value) {
    String digits = value == null ? "" : value.replaceAll("\\D", "");
    if (digits.length() <= 3) return "***";
    return "******" + digits.substring(digits.length() - 3);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
  }

  private String requireText(String value, String message) {
    if (!hasText(value)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, message);
    }
    return value.trim();
  }

  private String blankToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String fallback(String first, String second) {
    return hasText(first) ? first : second;
  }

  private record SecretHash(String salt, String hash) {
  }

  private record IssuedSession(String token, Instant expiresAt) {
  }

  private record PortalSession(String sessionId, String employeeId) {
  }

  public record PortalPasswordSetupRequest(String employeeCode, String phoneNumber, String password) {
  }

  public record PortalLoginRequest(String phoneNumber, String password) {
  }

  public record PortalOtpVerifyRequest(String challengeId, String code) {
  }

  public record EmployeeLeaveRequest(
      String leaveType,
      String leaveCategory,
      String startDate,
      String endDate,
      String startTime,
      String endTime,
      String halfDaySession,
      String reason) {
  }
}
