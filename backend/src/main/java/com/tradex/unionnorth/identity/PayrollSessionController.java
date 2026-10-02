package com.tradex.unionnorth.identity;

import com.tradex.unionnorth.security.domain.Permission;
import com.tradex.unionnorth.security.domain.Role;
import com.garmentline.operations.config.PayrollMfaPolicy;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Browser visibility uses the same server-side grants that protect Payroll APIs. */
@RestController
@RequestMapping("/api/v1/auth")
public class PayrollSessionController {
  public record SessionPermissions(String userId, List<Role> roles, List<Permission> permissions,
      boolean mfaRequired, boolean mfaVerified) {}

  @GetMapping("/me")
  public ResponseEntity<SessionPermissions> me(JwtAuthenticationToken authentication) {
    var granted = authentication.getAuthorities().stream()
        .map(authority -> authority.getAuthority()).collect(java.util.stream.Collectors.toSet());
    var roles = Arrays.stream(Role.values())
        .filter(role -> granted.contains("ROLE_" + role.name())).toList();
    var permissions = Arrays.stream(Permission.values())
        .filter(permission -> granted.contains(permission.name())).toList();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new SessionPermissions(authentication.getName(), roles, permissions,
            PayrollMfaPolicy.required(authentication), PayrollMfaPolicy.verified(authentication)));
  }
}
