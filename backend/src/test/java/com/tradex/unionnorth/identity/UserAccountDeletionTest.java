package com.tradex.unionnorth.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.unionnorth.audit.service.AuditService;
import com.tradex.unionnorth.employee.repository.EmployeeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UserAccountDeletionTest {
  private final SupabaseIdentityClient identity = mock(SupabaseIdentityClient.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final UserAccountService service = new UserAccountService(identity, jdbc,
      mock(EmployeeRepository.class), mock(AuditService.class), new ObjectMapper());
  private final UUID actorId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  private void target(List<String> roles) throws Exception {
    when(identity.user(userId)).thenReturn(new ObjectMapper().readTree("""
        {"id":"%s","email":"person@example.com","created_at":"2026-01-01T00:00:00Z"}
        """.formatted(userId)));
    when(jdbc.queryForObject(anyString(), eq(String.class), eq(userId)))
        .thenReturn(null, "ACTIVE");
    when(jdbc.queryForList(anyString(), eq(String.class), eq(userId)))
        .thenReturn(roles);
  }

  @Test
  void deletionRevokesAccessAndSoftDeletesIdentity() throws Exception {
    target(List.of("HR_OFFICER"));

    service.deleteUser(actorId.toString(), "127.0.0.1", userId.toString());

    verify(jdbc).update(
        org.mockito.ArgumentMatchers.startsWith("INSERT INTO payroll.user_account_access"),
        eq(userId), eq("DISABLED"), eq(actorId));
    verify(jdbc).update("DELETE FROM payroll.user_role_assignments WHERE auth_user_id=?", userId);
    verify(jdbc).update("UPDATE public.profiles SET is_active=false,role='viewer',full_name='Deleted user' WHERE id=?", userId);
    verify(identity).softDelete(userId);
  }

  @Test
  void developerTargetIsNeverDeleted() throws Exception {
    target(List.of("DEVELOPER", "HR_OFFICER"));

    assertThatThrownBy(() -> service.deleteUser(actorId.toString(), "127.0.0.1", userId.toString()))
        .isInstanceOf(IdentityAdminException.class);

    verify(identity, never()).softDelete(userId);
  }

  @Test
  void finalSuperAdminCannotBeDeleted() throws Exception {
    target(List.of("SYSTEM_ADMIN"));
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq("SYSTEM_ADMIN"))).thenReturn(1L);

    assertThatThrownBy(() -> service.deleteUser(actorId.toString(), "127.0.0.1", userId.toString()))
        .isInstanceOf(IdentityAdminException.class);

    verify(identity, never()).softDelete(userId);
  }

  @Test
  void selfDeletionIsBlocked() {
    assertThatThrownBy(() -> service.deleteUser(actorId.toString(), "127.0.0.1", actorId.toString()))
        .isInstanceOf(IdentityAdminException.class);
    verify(identity, never()).softDelete(actorId);
  }

  @Test
  void deletedAuthUsersAreAbsentFromList() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode deleted = mapper.readTree("""
        {"id":"%s","deleted_at":"2026-01-02T00:00:00Z"}
        """.formatted(userId));
    when(identity.users(0, 500)).thenReturn(new SupabaseIdentityClient.UserPage(List.of(deleted), 1));

    var page = service.listUsers(null, 0, 20);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }
}
