package com.tradex.unionnorth.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.unionnorth.audit.service.AuditService;
import com.tradex.unionnorth.employee.repository.EmployeeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UserAccountRecoveryTest {
  private final SupabaseIdentityClient identity = mock(SupabaseIdentityClient.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuditService audit = mock(AuditService.class);
  private final UserAccountService service = new UserAccountService(identity, jdbc,
      mock(EmployeeRepository.class), audit, new ObjectMapper());
  private final UUID userId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();

  private void userExists() throws Exception {
    when(identity.user(userId)).thenReturn(new ObjectMapper().readTree("""
        {"id":"%s","email":"person@example.com","created_at":"2026-01-01T00:00:00Z"}
        """.formatted(userId)));
    when(jdbc.queryForObject(anyString(), eq(String.class), eq(userId)))
        .thenReturn(null, "ACTIVE", null, "ACTIVE");
    when(jdbc.queryForList(anyString(), eq(String.class), eq(userId)))
        .thenReturn(List.of());
  }

  @Test
  void recoveryRequestLeavesExistingPasswordIntact() throws Exception {
    userExists();
    var result = service.forcePasswordChange(actorId.toString(), "127.0.0.1",
        userId.toString(), true);
    assertThat(result.emailSent()).isTrue();
    verify(identity).sendRecovery("person@example.com");
    verify(identity, never()).update(eq(userId), any());
  }

  @Test
  void failedRecoveryEmailDoesNotChangeExistingPassword() throws Exception {
    userExists();
    doThrow(new IdentityAdminException("IDENTITY_UNAVAILABLE", "Unavailable",
        org.springframework.http.HttpStatus.BAD_GATEWAY))
        .when(identity).sendRecovery("person@example.com");
    assertThatThrownBy(() -> service.forcePasswordChange(actorId.toString(), "127.0.0.1",
        userId.toString(), true)).isInstanceOf(IdentityAdminException.class);
    verify(identity, never()).update(eq(userId), any());
  }
}
