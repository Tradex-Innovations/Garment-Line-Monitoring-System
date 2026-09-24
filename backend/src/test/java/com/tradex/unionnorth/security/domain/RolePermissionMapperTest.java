package com.tradex.unionnorth.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class RolePermissionMapperTest {

    private final RolePermissionMapper mapper = new RolePermissionMapper();

    @Test
    void adminKeepsEveryPermissionExceptPrivilegedAccountManagement() {
        EnumSet<Permission> expected = EnumSet.allOf(Permission.class);
        expected.remove(Permission.PRIVILEGED_ACCOUNT_MANAGE);
        expected.remove(Permission.DEVELOPER_ACCESS);

        assertThat(mapper.permissionsFor(Role.ADMIN))
                .containsExactlyInAnyOrderElementsOf(expected)
                .doesNotContain(Permission.PRIVILEGED_ACCOUNT_MANAGE);
    }

    @Test
    void onlySystemAdminReceivesPrivilegedAccountManagement() {
        var expected = EnumSet.allOf(Permission.class);
        expected.remove(Permission.DEVELOPER_ACCESS);
        assertThat(mapper.permissionsFor(Role.SYSTEM_ADMIN)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(mapper.permissionsFor(Role.DEVELOPER)).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));

        assertThat(EnumSet.allOf(Role.class).stream()
                .filter(role -> mapper.permissionsFor(role).contains(Permission.PRIVILEGED_ACCOUNT_MANAGE)))
                .containsExactlyInAnyOrder(Role.SYSTEM_ADMIN, Role.DEVELOPER);
    }
}
