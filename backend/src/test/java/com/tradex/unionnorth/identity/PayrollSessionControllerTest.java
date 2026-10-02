package com.tradex.unionnorth.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradex.unionnorth.security.domain.Permission;
import com.tradex.unionnorth.security.domain.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class PayrollSessionControllerTest {
  @Test
  void returnsOnlyServerGrantedRolesAndPermissionsForTheAuthenticatedSubject() {
    var jwt = new Jwt("test", Instant.now(), Instant.now().plusSeconds(3600),
        java.util.Map.of("alg", "none"),
        java.util.Map.of("sub", "account-123", "role", "SYSTEM_ADMIN"));
    var authentication = new JwtAuthenticationToken(jwt, List.of(
        new SimpleGrantedAuthority("ROLE_HR_OFFICER"),
        new SimpleGrantedAuthority("EMPLOYEE_CREATE")), "account-123");

    var response = new PayrollSessionController().me(authentication);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().userId()).isEqualTo("account-123");
    assertThat(response.getBody().roles()).containsExactly(Role.HR_OFFICER);
    assertThat(response.getBody().permissions()).containsExactly(Permission.EMPLOYEE_CREATE);
    assertThat(response.getBody().mfaRequired()).isFalse();
    assertThat(response.getBody().mfaVerified()).isFalse();
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
  }
}
