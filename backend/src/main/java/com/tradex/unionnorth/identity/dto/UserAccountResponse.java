package com.tradex.unionnorth.identity.dto;

import com.tradex.unionnorth.security.domain.Role;
import java.time.Instant;
import java.util.List;

public record UserAccountResponse(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        String displayName,
        boolean emailVerified,
        UserAccountStatus status,
        String employeeId,
        List<Role> roles,
        List<String> requiredActions,
        Instant createdAt) {
}
