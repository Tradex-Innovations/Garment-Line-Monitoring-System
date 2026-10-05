package com.garmentline.operations.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.ApiException;
import com.garmentline.operations.support.JsonSupport;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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

@Service
public class LeaveManagementService {

  private final SupabaseAdminClient supabaseAdminClient;
  private final RoleGuard roleGuard;

  public LeaveManagementService(SupabaseAdminClient supabaseAdminClient, RoleGuard roleGuard) {
    this.supabaseAdminClient = supabaseAdminClient;
    this.roleGuard = roleGuard;
  }

  public Map<String, Object> getLeaveManagement(
      AuthenticatedUser user, String status, String employeeId, String dateFrom, String dateTo) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor");
    return snapshot(user, status, employeeId, dateFrom, dateTo);
  }

  public Map<String, Object> createLeaveRequest(AuthenticatedUser user, LeaveRequest request) {
    roleGuard.requireAnyRole(user, "admin", "hr");

    String employeeId = requireText(request.employeeId(), "Employee is required.");
    validUuid(employeeId, "Employee ID");
    String leaveType = leaveType(request.leaveType());
    String leaveCategory = normalize(request.leaveCategory());
    String startDate = requireText(request.startDate(), "Start date is required.");
    String endDate = requireText(request.endDate(), "End date is required.");
    LeaveRequestRules.validate(leaveType, leaveCategory, startDate, endDate,
        request.startTime(), request.endTime(), request.halfDaySession(), request.reason());

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("employee_id", employeeId);
    row.put("leave_type", leaveType);
    row.put("leave_category", leaveCategory);
    row.put("start_date", startDate);
    row.put("end_date", endDate);
    row.put("start_time", timeOrNull(request.startTime()));
    row.put("end_time", timeOrNull(request.endTime()));
    row.put("half_day_session", halfDaySession(request.halfDaySession()));
    row.put("reason", blankToNull(request.reason()));
    row.put("status", "pending");
    row.put("requested_by", user.id());

    supabaseAdminClient.insertSingle("employee_leave_requests", row);
    return snapshot(user, "all", null, null, null);
  }

  public Map<String, Object> reviewLeaveRequest(
      AuthenticatedUser user, String id, LeaveReviewRequest request) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor");
    validUuid(id, "Request ID");
    String status = reviewStatus(request.status());
    if ("rejected".equals(status) && !hasText(request.reviewNote())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a reason for rejecting leave.");
    }
    if (request.reviewNote() != null && request.reviewNote().length() > 1000) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Review note must be 1000 characters or fewer.");
    }
    supabaseAdminClient.rpc("review_employee_leave_request", Map.of(
        "p_request_id", id, "p_actor", user.id(), "p_status", status,
        "p_note", request.reviewNote() == null ? "" : request.reviewNote()));
    return snapshot(user, "all", null, null, null);
  }

  public Map<String, Object> savePolicy(AuthenticatedUser user, LeavePolicyInput input) {
    roleGuard.requireAnyRole(user, "admin", "hr");
    if (input.policyYear() < 2000 || input.policyYear() > 2100
        || !List.of("permanent", "new_joiner", "intern").contains(input.employeeCategory())
        || !List.of("annual", "casual", "sick", "emergency", "personal", "medical", "other")
            .contains(input.leaveCategory())
        || input.entitlementDays() == null || !Double.isFinite(input.entitlementDays())
        || input.entitlementDays() < 0 || input.entitlementDays() > 366
        || !List.of("hr", "assigned_manager").contains(input.approverRole())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid leave year, category, entitlement and approver.");
    }
    supabaseAdminClient.upsertMany("employee_leave_policies", List.of(Map.of(
        "policy_year", input.policyYear(), "employee_category", input.employeeCategory(),
        "leave_category", input.leaveCategory(), "entitlement_days", input.entitlementDays(),
        "approver_role", input.approverRole(), "updated_by", user.id())),
        "policy_year,employee_category,leave_category");
    return snapshot(user, "all", null, null, null);
  }

  public Map<String, Object> assignApprover(AuthenticatedUser user, ApproverInput input) {
    roleGuard.requireAnyRole(user, "admin", "hr");
    validUuid(input.employeeId(), "Employee ID");
    validUuid(input.approverUserId(), "Manager ID");
    JsonNode manager = supabaseAdminClient.selectSingle("profiles",
        supabaseAdminClient.filters(Map.of("id", "eq." + input.approverUserId())));
    if (!"supervisor".equals(JsonSupport.text(manager, "role"))
        || !Boolean.TRUE.equals(JsonSupport.bool(manager, "is_active"))
        || input.employeeId().equals(JsonSupport.text(manager, "employee_id"))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an active supervisor other than the employee.");
    }
    supabaseAdminClient.upsertMany("employee_leave_approvers", List.of(Map.of(
        "employee_id", input.employeeId(), "approver_user_id", input.approverUserId(),
        "assigned_by", user.id())), "employee_id");
    return snapshot(user, "all", null, null, null);
  }

  private Map<String, Object> snapshot(AuthenticatedUser user, String status, String employeeId, String dateFrom, String dateTo) {
    ArrayNode employees = supabaseAdminClient.selectAll("employees", ordered("employee_code.asc"));
    ArrayNode profiles = supabaseAdminClient.selectAll("employee_profiles", new LinkedMultiValueMap<>());
    ArrayNode appUsers = supabaseAdminClient.selectAll("profiles", ordered("full_name.asc"));
    ArrayNode approvers = supabaseAdminClient.selectAll("employee_leave_approvers", new LinkedMultiValueMap<>());
    ArrayNode policies = supabaseAdminClient.selectAll("employee_leave_policies", ordered("policy_year.desc"));
    ArrayNode requests = supabaseAdminClient.selectAll("employee_leave_requests", leaveRequestQuery(status, employeeId, dateFrom, dateTo));
    Set<String> visibleEmployees = "supervisor".equals(user.role())
        ? approvers.valueStream()
            .filter(row -> user.id().equals(JsonSupport.text(row, "approver_user_id")))
            .map(row -> JsonSupport.text(row, "employee_id")).collect(Collectors.toSet())
        : employees.valueStream().map(row -> JsonSupport.text(row, "id")).collect(Collectors.toSet());

    Map<String, JsonNode> employeesById = byId(employees);
    Map<String, JsonNode> profilesByEmployeeId = byField(profiles, "employee_id");
    Map<String, JsonNode> appUsersById = byId(appUsers);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("employees", employees.valueStream()
        .filter(row -> visibleEmployees.contains(JsonSupport.text(row, "id")))
        .map(row -> employeePayload(row, profilesByEmployeeId)).toList());
    payload.put(
        "requests",
        requests.valueStream()
            .filter(row -> visibleEmployees.contains(JsonSupport.text(row, "employee_id")))
            .map(row -> leaveRequestPayload(row, employeesById, profilesByEmployeeId, appUsersById))
            .toList());
    payload.put("policies", policies.valueStream().map(row -> Map.of(
        "policyYear", row.path("policy_year").asInt(),
        "employeeCategory", JsonSupport.text(row, "employee_category"),
        "leaveCategory", JsonSupport.text(row, "leave_category"),
        "entitlementDays", row.path("entitlement_days").asDouble(),
        "approverRole", JsonSupport.text(row, "approver_role"))).toList());
    payload.put("approvers", approvers.valueStream()
        .filter(row -> visibleEmployees.contains(JsonSupport.text(row, "employee_id")))
        .map(row -> Map.of("employeeId", JsonSupport.text(row, "employee_id"),
            "approverUserId", JsonSupport.text(row, "approver_user_id"))).toList());
    payload.put("managers", "supervisor".equals(user.role()) ? List.of() : appUsers.valueStream()
        .filter(row -> "supervisor".equals(JsonSupport.text(row, "role"))
            && Boolean.TRUE.equals(JsonSupport.bool(row, "is_active")))
        .map(row -> Map.of("id", JsonSupport.text(row, "id"),
            "name", fallback(JsonSupport.text(row, "full_name"), JsonSupport.text(row, "id"))))
        .toList());
    return payload;
  }

  private MultiValueMap<String, String> leaveRequestQuery(
      String status, String employeeId, String dateFrom, String dateTo) {
    LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    if (hasText(status) && !"all".equalsIgnoreCase(status)) {
      query.add("status", "eq." + status.toLowerCase(Locale.ROOT));
    }
    if (hasText(employeeId) && !"all".equalsIgnoreCase(employeeId)) {
      query.add("employee_id", "eq." + employeeId);
    }
    if (hasText(dateFrom)) {
      query.add("end_date", "gte." + dateFrom);
    }
    if (hasText(dateTo)) {
      query.add("start_date", "lte." + dateTo);
    }
    query.add("order", "requested_at.desc");
    return query;
  }

  private MultiValueMap<String, String> ordered(String order) {
    LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("order", order);
    return query;
  }

  private Map<String, Object> employeePayload(JsonNode row, Map<String, JsonNode> profilesByEmployeeId) {
    String id = JsonSupport.text(row, "id");
    JsonNode profile = profilesByEmployeeId.get(id);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", id);
    payload.put("employeeCode", JsonSupport.text(row, "employee_code"));
    payload.put("fullName", fallback(JsonSupport.text(row, "display_name"), JsonSupport.text(row, "employee_code")));
    payload.put("designation", JsonSupport.text(row, "designation"));
    payload.put("department", JsonSupport.text(row, "department_name"));
    payload.put("photoUrl", profile == null ? null : JsonSupport.text(profile, "photo_url"));
    return payload;
  }

  private Map<String, Object> leaveRequestPayload(
      JsonNode row,
      Map<String, JsonNode> employeesById,
      Map<String, JsonNode> profilesByEmployeeId,
      Map<String, JsonNode> appUsersById) {
    String employeeId = JsonSupport.text(row, "employee_id");
    JsonNode employee = employeesById.get(employeeId);
    JsonNode employeeProfile = profilesByEmployeeId.get(employeeId);
    JsonNode requestedBy = appUsersById.get(JsonSupport.text(row, "requested_by"));
    JsonNode reviewedBy = appUsersById.get(JsonSupport.text(row, "reviewed_by"));

    String startDate = JsonSupport.text(row, "start_date");
    String endDate = JsonSupport.text(row, "end_date");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", JsonSupport.text(row, "id"));
    payload.put("employeeId", employeeId);
    payload.put("employeeCode", employee == null ? null : JsonSupport.text(employee, "employee_code"));
    payload.put("employeeName", employee == null ? "Employee" : fallback(JsonSupport.text(employee, "display_name"), JsonSupport.text(employee, "employee_code")));
    payload.put("designation", employee == null ? null : JsonSupport.text(employee, "designation"));
    payload.put("department", employee == null ? null : JsonSupport.text(employee, "department_name"));
    payload.put("photoUrl", employeeProfile == null ? null : JsonSupport.text(employeeProfile, "photo_url"));
    payload.put("leaveType", JsonSupport.text(row, "leave_type"));
    payload.put("leaveCategory", JsonSupport.text(row, "leave_category"));
    payload.put("startDate", startDate);
    payload.put("endDate", endDate);
    payload.put("startTime", JsonSupport.text(row, "start_time"));
    payload.put("endTime", JsonSupport.text(row, "end_time"));
    payload.put("halfDaySession", JsonSupport.text(row, "half_day_session"));
    payload.put("reason", JsonSupport.text(row, "reason"));
    payload.put("status", JsonSupport.text(row, "status"));
    payload.put("requestedBy", requestedBy == null ? null : JsonSupport.text(requestedBy, "full_name"));
    payload.put("requestedAt", JsonSupport.text(row, "requested_at"));
    payload.put("reviewedBy", reviewedBy == null ? null : JsonSupport.text(reviewedBy, "full_name"));
    payload.put("reviewedAt", JsonSupport.text(row, "reviewed_at"));
    payload.put("reviewNote", JsonSupport.text(row, "review_note"));
    payload.put("createdAt", JsonSupport.text(row, "created_at"));
    payload.put("updatedAt", JsonSupport.text(row, "updated_at"));
    payload.put("dayCount", dayCount(JsonSupport.text(row, "leave_type"), startDate, endDate));
    return payload;
  }

  private Map<String, JsonNode> byId(ArrayNode rows) {
    return byField(rows, "id");
  }

  private Map<String, JsonNode> byField(ArrayNode rows, String field) {
    Map<String, JsonNode> mapped = new LinkedHashMap<>();
    rows.forEach(row -> {
      String value = JsonSupport.text(row, field);
      if (hasText(value)) {
        mapped.put(value, row);
      }
    });
    return mapped;
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

  private String leaveType(String value) {
    String normalized = normalize(value);
    if (List.of("full_day", "half_day", "short_leave").contains(normalized)) {
      return normalized;
    }
    throw new ApiException(HttpStatus.BAD_REQUEST, "Leave type must be full_day, half_day, or short_leave.");
  }

  private String halfDaySession(String value) {
    String normalized = normalize(value);
    if (!hasText(normalized)) return null;
    if (List.of("first_half", "second_half").contains(normalized)) {
      return normalized;
    }
    return null;
  }

  private String reviewStatus(String value) {
    String normalized = normalize(value);
    if (List.of("approved", "rejected").contains(normalized)) {
      return normalized;
    }
    throw new ApiException(HttpStatus.BAD_REQUEST, "Review status must be approved or rejected.");
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
  }

  private String timeOrNull(String value) {
    if (!hasText(value)) return null;
    return value.length() == 5 ? value + ":00" : value;
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

  private void validUuid(String value, String name) {
    try { UUID.fromString(value); }
    catch (RuntimeException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, name + " must be a valid ID.");
    }
  }

  public record LeavePolicyInput(int policyYear, String employeeCategory,
      String leaveCategory, Double entitlementDays, String approverRole) {}
  public record ApproverInput(String employeeId, String approverUserId) {}

  public record LeaveRequest(
      String employeeId,
      String leaveType,
      String leaveCategory,
      String startDate,
      String endDate,
      String startTime,
      String endTime,
      String halfDaySession,
      String reason) {
  }

  public record LeaveReviewRequest(
      String status,
      String reviewNote) {
  }
}
