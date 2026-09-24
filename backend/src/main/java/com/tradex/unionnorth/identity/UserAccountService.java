package com.tradex.unionnorth.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.unionnorth.audit.service.AuditEvent;
import com.tradex.unionnorth.audit.service.AuditService;
import com.tradex.unionnorth.employee.repository.EmployeeRepository;
import com.tradex.unionnorth.identity.dto.ChangeUserStatusRequest;
import com.tradex.unionnorth.identity.dto.CreateUserAccountRequest;
import com.tradex.unionnorth.identity.dto.UpdateUserAccountRequest;
import com.tradex.unionnorth.identity.dto.UserAccountPage;
import com.tradex.unionnorth.identity.dto.UserAccountResponse;
import com.tradex.unionnorth.identity.dto.UserAccountStatus;
import com.tradex.unionnorth.identity.dto.UserActionResponse;
import com.tradex.unionnorth.security.domain.Role;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin API for the shared Supabase identity and payroll-owned role grants. */
@Service
public class UserAccountService {
  private static final Set<Role> PRIVILEGED = Set.of(Role.ADMIN, Role.SYSTEM_ADMIN, Role.DEVELOPER);
  private static final int SEARCH_PAGE_SIZE = 500;
  private static final int MAX_SEARCH_PAGES = 20;

  private final SupabaseIdentityClient identity;
  private final JdbcTemplate jdbc;
  private final EmployeeRepository employees;
  private final AuditService audit;
  private final ObjectMapper mapper;
  private final SecureRandom random = new SecureRandom();

  public UserAccountService(
      SupabaseIdentityClient identity,
      JdbcTemplate jdbc,
      EmployeeRepository employees,
      AuditService audit,
      ObjectMapper mapper) {
    this.identity = identity;
    this.jdbc = jdbc;
    this.employees = employees;
    this.audit = audit;
    this.mapper = mapper;
  }

  public UserAccountPage listUsers(String search, int page, int size) {
    List<JsonNode> users;
    long total;
    if (search == null || search.isBlank()) {
      var response = identity.users(page, size);
      users = response.users();
      total = response.total();
    } else {
      String needle = search.trim().toLowerCase(Locale.ROOT);
      List<JsonNode> matches = new ArrayList<>();
      for (int index = 0; index < MAX_SEARCH_PAGES; index++) {
        var response = identity.users(index, SEARCH_PAGE_SIZE);
        response.users().stream().filter(user -> searchable(user).contains(needle)).forEach(matches::add);
        if (response.users().size() < SEARCH_PAGE_SIZE) break;
        if (index == MAX_SEARCH_PAGES - 1) throw rejected("USER_SEARCH_LIMIT", "Narrow the user search", HttpStatus.BAD_REQUEST);
      }
      total = matches.size();
      users = matches.stream().skip((long) page * size).limit(size).toList();
    }
    int pages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
    return new UserAccountPage(users.stream().map(this::toResponse).toList(), page, size,
        total, pages, page == 0, page + 1 >= pages);
  }

  public List<Role> listAssignableRoles(boolean canManagePrivilegedAccounts) {
    return Arrays.stream(Role.values())
        .filter(role -> canManagePrivilegedAccounts || !PRIVILEGED.contains(role))
        .toList();
  }

  @Transactional
  public UserActionResponse createUser(String actorId, String ipAddress,
      boolean canManagePrivilegedAccounts, CreateUserAccountRequest request) {
    UUID employeeId = checkedEmployee(request.employeeId());
    Set<Role> roles = request.roles() == null ? Set.of() : request.roles();
    requireRoleAccess(canManagePrivilegedAccounts, roles);
    String email = request.workEmail().trim().toLowerCase(Locale.ROOT);
    JsonNode invited = identity.invite(email,
        Map.of("first_name", request.firstName().trim(), "last_name", request.lastName().trim()));
    UUID userId = UUID.fromString(invited.path("id").asText());
    saveAccess(userId, employeeId, UserAccountStatus.ACTIVE, actorId);
    roles.forEach(role -> grant(userId, role, actorId));
    record(actorId, ipAddress, "USER_CREATED", userId, "Created invited account");
    return new UserActionResponse(toResponse(invited), true, "User invited by email");
  }

  @Transactional
  public UserAccountResponse updateUser(String actorId, String ipAddress,
      String userId, boolean canManagePrivilegedAccounts, UpdateUserAccountRequest request) {
    UUID id = parseId(userId);
    UserAccountResponse before = toResponse(identity.user(id));
    requireTargetAccess(canManagePrivilegedAccounts, before);
    UUID employeeId = checkedEmployee(request.employeeId());
    JsonNode current = identity.user(id);
    Map<String, Object> metadata = metadata(current);
    metadata.put("first_name", request.firstName().trim());
    metadata.put("last_name", request.lastName().trim());
    JsonNode updated = identity.update(id,
        Map.of("email", request.workEmail().trim().toLowerCase(Locale.ROOT), "user_metadata", metadata));
    saveAccess(id, employeeId, before.status(), actorId);
    record(actorId, ipAddress, "USER_UPDATED", id, "Updated account details");
    return toResponse(updated);
  }

  @Transactional
  public UserAccountResponse changeStatus(String actorId, String ipAddress,
      String userId, boolean canManagePrivilegedAccounts, ChangeUserStatusRequest request) {
    UUID id = parseId(userId);
    UserAccountResponse before = toResponse(identity.user(id));
    requireTargetAccess(canManagePrivilegedAccounts, before);
    if (actorId.equals(userId) && request.status() != UserAccountStatus.ACTIVE)
      throw rejected("SELF_LOCKOUT_BLOCKED", "You cannot disable your own account", HttpStatus.CONFLICT);
    if (request.status() != UserAccountStatus.ACTIVE) requireNotFinalAdministrator(before);
    jdbc.update("INSERT INTO payroll.user_account_access(auth_user_id,status,updated_by) VALUES (?,?,?) "
        + "ON CONFLICT (auth_user_id) DO UPDATE SET status=EXCLUDED.status,updated_by=EXCLUDED.updated_by,updated_at=now()",
        id, request.status().name(), parseId(actorId));
    identity.update(id, Map.of("ban_duration", request.status() == UserAccountStatus.ACTIVE ? "none" : "876000h"));
    record(actorId, ipAddress, "USER_STATUS_CHANGED", id, safeReason(request.reason(), "Changed account status"));
    return toResponse(identity.user(id));
  }

  public UserAccountResponse unlockUser(String actorId, String ipAddress,
      String userId, boolean canManagePrivilegedAccounts) {
    UUID id = parseId(userId);
    UserAccountResponse before = toResponse(identity.user(id));
    requireTargetAccess(canManagePrivilegedAccounts, before);
    if (before.status() != UserAccountStatus.ACTIVE)
      throw rejected("USER_NOT_ACTIVE", "Enable the account before unlocking it", HttpStatus.CONFLICT);
    identity.update(id, Map.of("ban_duration", "none"));
    record(actorId, ipAddress, "USER_UNLOCKED", id, "Cleared account ban");
    return toResponse(identity.user(id));
  }

  @Transactional
  public UserAccountResponse assignRole(String actorId, String ipAddress,
      String userId, boolean canManagePrivilegedAccounts, Role role) {
    UUID id = parseId(userId);
    UserAccountResponse before = toResponse(identity.user(id));
    requireTargetAccess(canManagePrivilegedAccounts, before);
    requireRoleAccess(canManagePrivilegedAccounts, Set.of(role));
    grant(id, role, actorId);
    record(actorId, ipAddress, "USER_ROLE_ASSIGNED", id, "Assigned " + role.name());
    return toResponse(identity.user(id));
  }

  @Transactional
  public UserAccountResponse revokeRole(String actorId, String ipAddress,
      String userId, boolean canManagePrivilegedAccounts, Role role) {
    UUID id = parseId(userId);
    UserAccountResponse before = toResponse(identity.user(id));
    requireTargetAccess(canManagePrivilegedAccounts, before);
    requireRoleAccess(canManagePrivilegedAccounts, Set.of(role));
    if (before.roles().contains(role) && (role == Role.SYSTEM_ADMIN || role == Role.DEVELOPER))
      requireNotFinalRole(role);
    jdbc.update("DELETE FROM payroll.user_role_assignments WHERE auth_user_id=? AND role=?", id, role.name());
    record(actorId, ipAddress, "USER_ROLE_REVOKED", id, "Revoked " + role.name());
    return toResponse(identity.user(id));
  }

  public UserActionResponse forcePasswordChange(String actorId, String ipAddress,
      String userId, boolean canManagePrivilegedAccounts) {
    UUID id = parseId(userId);
    UserAccountResponse before = toResponse(identity.user(id));
    requireTargetAccess(canManagePrivilegedAccounts, before);
    byte[] password = new byte[48];
    random.nextBytes(password);
    identity.update(id, Map.of("password", Base64.getUrlEncoder().withoutPadding().encodeToString(password)));
    identity.sendRecovery(before.email());
    record(actorId, ipAddress, "USER_PASSWORD_RESET", id, "Required password recovery by email");
    return new UserActionResponse(toResponse(identity.user(id)), true, "Password recovery email sent");
  }

  public UserActionResponse resendInvite(String actorId, String ipAddress,
      String userId, boolean canManagePrivilegedAccounts) {
    UUID id = parseId(userId);
    UserAccountResponse before = toResponse(identity.user(id));
    requireTargetAccess(canManagePrivilegedAccounts, before);
    identity.sendRecovery(before.email());
    record(actorId, ipAddress, "USER_INVITE_RESENT", id, "Sent password setup email");
    return new UserActionResponse(before, true, "Password setup email sent");
  }

  private UserAccountResponse toResponse(JsonNode user) {
    UUID id = UUID.fromString(user.path("id").asText());
    JsonNode data = user.path("user_metadata");
    String first = value(data, "first_name");
    String last = value(data, "last_name");
    String email = value(user, "email");
    String display = (first + " " + last).trim();
    String created = value(user, "created_at");
    String employeeId = jdbc.queryForObject(
        "SELECT (SELECT employee_id::text FROM payroll.user_account_access WHERE auth_user_id=?)",
        String.class, id);
    String status = jdbc.queryForObject(
        "SELECT COALESCE((SELECT status FROM payroll.user_account_access WHERE auth_user_id=?),'ACTIVE')",
        String.class, id);
    List<Role> roles = jdbc.queryForList(
        "SELECT role FROM payroll.user_role_assignments WHERE auth_user_id=? ORDER BY role",
        String.class, id).stream().map(Role::valueOf).toList();
    return new UserAccountResponse(
        id.toString(), email, email, first, last, display,
        user.hasNonNull("email_confirmed_at"), UserAccountStatus.valueOf(status),
        employeeId, roles, List.of(), created.isBlank() ? Instant.EPOCH : Instant.parse(created));
  }

  private void saveAccess(UUID userId, UUID employeeId, UserAccountStatus status, String actorId) {
    jdbc.update("INSERT INTO payroll.user_account_access(auth_user_id,employee_id,status,updated_by) VALUES (?,?,?,?) "
            + "ON CONFLICT (auth_user_id) DO UPDATE SET employee_id=EXCLUDED.employee_id,status=EXCLUDED.status,updated_by=EXCLUDED.updated_by,updated_at=now()",
        userId, employeeId, status.name(), parseId(actorId));
  }

  private void grant(UUID userId, Role role, String actorId) {
    jdbc.update("INSERT INTO payroll.user_role_assignments(auth_user_id,role,assigned_by) VALUES (?,?,?) "
            + "ON CONFLICT (auth_user_id,role) DO NOTHING",
        userId, role.name(), parseId(actorId));
  }

  private UUID checkedEmployee(String text) {
    if (text == null || text.isBlank()) return null;
    UUID id = parseId(text);
    if (!employees.existsById(id))
      throw rejected("EMPLOYEE_NOT_FOUND", "Employee record was not found", HttpStatus.BAD_REQUEST);
    return id;
  }

  private void requireTargetAccess(boolean privileged, UserAccountResponse target) {
    if (!privileged && target.roles().stream().anyMatch(PRIVILEGED::contains))
      throw rejected("PRIVILEGED_ACCOUNT_REQUIRED", "Privileged account permission required", HttpStatus.FORBIDDEN);
  }

  private void requireRoleAccess(boolean privileged, Set<Role> roles) {
    if (!privileged && roles.stream().anyMatch(PRIVILEGED::contains))
      throw rejected("PRIVILEGED_ACCOUNT_REQUIRED", "Privileged account permission required", HttpStatus.FORBIDDEN);
  }

  private void requireNotFinalAdministrator(UserAccountResponse target) {
    for (Role role : List.of(Role.SYSTEM_ADMIN, Role.DEVELOPER))
      if (target.roles().contains(role)) requireNotFinalRole(role);
  }

  private void requireNotFinalRole(Role role) {
    Long active = jdbc.queryForObject(
        "SELECT count(*) FROM payroll.user_role_assignments r "
            + "LEFT JOIN payroll.user_account_access a ON a.auth_user_id=r.auth_user_id "
            + "WHERE r.role=? AND COALESCE(a.status,'ACTIVE')='ACTIVE'",
        Long.class, role.name());
    if (active == null || active <= 1)
      throw rejected("LAST_ADMIN_REQUIRED", "The final administrator role must remain active", HttpStatus.CONFLICT);
  }

  private String searchable(JsonNode user) {
    return (value(user, "email") + " " + value(user.path("user_metadata"), "first_name")
        + " " + value(user.path("user_metadata"), "last_name")).toLowerCase(Locale.ROOT);
  }

  private Map<String, Object> metadata(JsonNode user) {
    JsonNode data = user.path("user_metadata");
    return data.isObject() ? new LinkedHashMap<>(mapper.convertValue(data, new TypeReference<>() {}))
        : new LinkedHashMap<>();
  }

  private String value(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? value.asText() : "";
  }

  private UUID parseId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw rejected("INVALID_USER_ID", "Invalid user identifier", HttpStatus.BAD_REQUEST);
    }
  }

  private String safeReason(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private void record(String actorId, String ipAddress, String action, UUID userId, String reason) {
    audit.record(new AuditEvent(actorId, null, action, "USER_ACCOUNT", userId,
        null, null, reason, ipAddress));
  }

  private IdentityAdminException rejected(String code, String message, HttpStatus status) {
    return new IdentityAdminException(code, message, status);
  }
}
