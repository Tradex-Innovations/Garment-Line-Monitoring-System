package com.garmentline.operations.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.security.domain.RolePermissionMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class PayrollJwtAuthenticationConverterTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PayrollJwtAuthenticationConverter converter =
      new PayrollJwtAuthenticationConverter(jdbc, new RolePermissionMapper());
  private final UUID userId = UUID.randomUUID();

  @Test
  void grantsOnlyDatabaseRolesEvenWhenJwtClaimsAnAdministratorRole() {
    when(jdbc.queryForObject(any(String.class), eq(Boolean.class), eq(userId))).thenReturn(true);
    when(jdbc.queryForList(any(String.class), eq(String.class), eq(userId)))
        .thenReturn(List.of("EMPLOYEE"));
    Jwt jwt = Jwt.withTokenValue("test")
        .header("alg", "none")
        .subject(userId.toString())
        .claim("user_metadata", java.util.Map.of("role", "SYSTEM_ADMIN"))
        .build();

    var authorities = converter.convert(jwt).getAuthorities().stream()
        .map(a -> a.getAuthority()).toList();
    assertThat(authorities).contains("ROLE_EMPLOYEE", "EMPLOYEE_VIEW_SELF")
        .doesNotContain("ROLE_SYSTEM_ADMIN", "PRIVILEGED_ACCOUNT_MANAGE");
  }

  @Test
  void disabledAccountCannotUseAnyModule() {
    when(jdbc.queryForObject(any(String.class), eq(Boolean.class), eq(userId))).thenReturn(false);
    Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
        .subject(userId.toString()).build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void nonUuidSubjectIsRejectedAsBadCredentials() {
    Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
        .subject("not-a-user-id").build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void roleUpgradeImmediatelyRequiresMfaEvenWithAnOldAal1Token() {
    when(jdbc.queryForObject(any(String.class), eq(Boolean.class), eq(userId))).thenReturn(true);
    when(jdbc.queryForList(any(String.class), eq(String.class), eq(userId)))
        .thenReturn(List.of("EMPLOYEE"), List.of("EMPLOYEE", "SYSTEM_ADMIN"));
    Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
        .subject(userId.toString()).claim("aal", "aal1").build();

    assertThat(PayrollMfaPolicy.allowed(converter.convert(jwt))).isTrue();
    assertThat(PayrollMfaPolicy.allowed(converter.convert(jwt))).isFalse();
  }
}
