package com.garmentline.operations.config;

import com.tradex.unionnorth.security.domain.RolePermissionMapper;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

/** Payroll grants come from server-owned records, never editable user JWT metadata. */
@Component
@Profile("payroll")
public class PayrollJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {
  private final JdbcTemplate jdbc;
  private final RolePermissionMapper permissions;

  public PayrollJwtAuthenticationConverter(JdbcTemplate jdbc, RolePermissionMapper permissions) {
    this.jdbc = jdbc;
    this.permissions = permissions;
  }

  @Override
  public JwtAuthenticationToken convert(Jwt jwt) {
    UUID userId;
    try {
      userId = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new BadCredentialsException("Invalid account identifier");
    }
    Boolean active = jdbc.queryForObject(
        "SELECT NOT EXISTS (SELECT 1 FROM payroll.user_account_access WHERE auth_user_id = ? AND status <> 'ACTIVE')",
        Boolean.class,
        userId);
    if (!Boolean.TRUE.equals(active)) throw new BadCredentialsException("Account access disabled");
    Set<String> authorities = new LinkedHashSet<>();
    jdbc.queryForList(
            "SELECT role FROM payroll.user_role_assignments WHERE auth_user_id = ?",
            String.class,
            userId)
        .forEach(role -> {
          if (!permissions.isSupportedRole(role)) return;
          authorities.add("ROLE_" + role);
          permissions.permissionsForRoleName(role)
              .forEach(permission -> authorities.add(permission.name()));
        });
    return new JwtAuthenticationToken(
        jwt,
        authorities.stream().map(SimpleGrantedAuthority::new).toList(),
        jwt.getSubject());
  }
}
