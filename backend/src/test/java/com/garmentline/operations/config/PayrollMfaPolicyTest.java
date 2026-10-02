package com.garmentline.operations.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class PayrollMfaPolicyTest {
  private JwtAuthenticationToken authentication(String role, String aal) {
    Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(300),
        Map.of("alg", "none"), Map.of("sub", "user-1", "aal", aal));
    return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  @Test
  void privilegedRolesNeedVerifiedMfa() {
    for (String role : List.of("ADMIN", "SYSTEM_ADMIN", "DEVELOPER")) {
      assertThat(PayrollMfaPolicy.required(authentication(role, "aal1"))).isTrue();
      assertThat(PayrollMfaPolicy.allowed(authentication(role, "aal1"))).isFalse();
      assertThat(PayrollMfaPolicy.allowed(authentication(role, "aal2"))).isTrue();
    }
  }

  @Test
  void ordinaryRolesCanAccessWithSingleFactor() {
    assertThat(PayrollMfaPolicy.required(authentication("EMPLOYEE", "aal1"))).isFalse();
    assertThat(PayrollMfaPolicy.allowed(authentication("EMPLOYEE", "aal1"))).isTrue();
  }
}
