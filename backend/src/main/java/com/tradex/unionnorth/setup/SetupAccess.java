package com.tradex.unionnorth.setup;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

final class SetupAccess {
    private SetupAccess() {}

    static boolean has(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream()
                        .anyMatch(
                                a ->
                                        a.getAuthority().equals(permission)
                                                || a.getAuthority().equals("ROLE_SYSTEM_ADMIN")
                                                || a.getAuthority().equals("SYSTEM_ADMIN_ACCESS"));
    }

    static void require(String permission) {
        if (!has(permission)) throw new AccessDeniedException("Permission required");
    }

    static String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }
}
