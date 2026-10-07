package com.tradex.unionnorth.setup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

class SalaryStructureDeletionTest {
    private final SetupStore store = mock(SetupStore.class);
    private final MasterDataService service = new MasterDataService(store, new SetupCatalog());
    private final UUID id = UUID.randomUUID();

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void allowEdit() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "setup-editor", "unused", java.util.List.of(
                                new SimpleGrantedAuthority("PAYROLL_SETUP_EDIT"))));
    }

    @Test
    void deletesOnlyAnUnchangedSalaryStructureAndRecordsAnAudit() {
        allowEdit();
        when(store.get("SALARY_STRUCTURE", id, null))
                .thenReturn(new SetupStore.Item(
                        id, "SALARY_STRUCTURE", "MONTHLY", "Monthly", true, 3,
                        LocalDate.of(2026, 1, 1), Map.of()));

        service.delete("SALARY_STRUCTURE", id, new MasterDataService.Delete(3L, "Created in error"));

        verify(store).lockSalaryStructure(id, true);
        verify(store).deleteUnusedSalaryStructure(id);
        verify(store).audit(null, "MASTER_DELETED", "SALARY_STRUCTURE", id,
                "Created in error", java.util.List.of("salaryStructure"));
    }

    @Test
    void refusesStaleVersionAndOtherMasterKinds() {
        allowEdit();
        when(store.get("SALARY_STRUCTURE", id, null))
                .thenReturn(new SetupStore.Item(
                        id, "SALARY_STRUCTURE", "MONTHLY", "Monthly", true, 3,
                        LocalDate.of(2026, 1, 1), Map.of()));

        assertThatThrownBy(() -> service.delete("SALARY_STRUCTURE", id,
                new MasterDataService.Delete(2L, "Old form")))
                .isInstanceOf(SetupException.class).hasMessageContaining("changed");
        assertThatThrownBy(() -> service.delete("COMPONENT", id,
                new MasterDataService.Delete(3L, "Wrong kind")))
                .isInstanceOf(SetupException.class).hasMessageContaining("Only salary structures");
        verify(store, never()).deleteUnusedSalaryStructure(id);
    }

    @Test
    void refusesUsersWithoutSetupEditPermission() {
        assertThatThrownBy(() -> service.delete("SALARY_STRUCTURE", id,
                new MasterDataService.Delete(3L, "Not permitted")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(store);
    }

    @Test
    void storePreservesAnAssignedStructureBeforeDeletingAnyRevision() {
        var jdbc = mock(JdbcTemplate.class);
        var databaseStore = new SetupStore(jdbc, new ObjectMapper());
        String key = id.toString();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(key), eq(key), eq(key), eq(id)))
                .thenReturn(true);

        assertThatThrownBy(() -> databaseStore.deleteUnusedSalaryStructure(id))
                .isInstanceOf(SetupException.class)
                .hasMessageContaining("assigned to an employee");
        verify(jdbc, never()).update("DELETE FROM setup_item_revisions WHERE item_id=?", id);
    }
}
