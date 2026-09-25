package com.garmentline.operations.config;

import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Payroll MFA policy based only on server-granted roles and the verified JWT. */
public final class PayrollMfaPolicy {
  private static final Set<String> PRIVILEGED_ROLES =
      Set.of("ROLE_ADMIN", "ROLE_SYSTEM_ADMIN", "ROLE_DEVELOPER");

  private PayrollMfaPolicy() {}

  public static boolean required(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> PRIVILEGED_ROLES.contains(authority.getAuthority()));
  }

  public static boolean verified(Authentication authentication) {
    return authentication instanceof JwtAuthenticationToken token
        && "aal2".equals(token.getToken().getClaimAsString("aal"));
  }

  public static boolean allowed(Authentication authentication) {
    return !required(authentication) || verified(authentication);
  }
}
