package com.tradex.unionnorth.employee.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.tradex.unionnorth.security.WorkforceAccess;
import com.tradex.unionnorth.setup.SetupStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class SharedEmployeePhotoLinkerTest {
  private final SetupStore store = mock(SetupStore.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final SupabaseAdminClient supabase = mock(SupabaseAdminClient.class);
  private final WorkforceAccess workforce = mock(WorkforceAccess.class);
  private final SharedEmployeePhotoLinker linker = new SharedEmployeePhotoLinker(store, supabase, workforce);
  private final UUID payrollId = UUID.randomUUID();

  @Test
  void payrollUploadLinksOnlyItsLineMatrixEmployee() {
    UUID sourceId = UUID.randomUUID();
    when(store.jdbc()).thenReturn(jdbc);
    when(jdbc.query(anyString(), any(RowMapper.class), eq(payrollId)))
        .thenReturn(List.of(sourceId));

    linker.link(payrollId, "employee-photos/photo.jpg");

    verify(workforce).requireProfile(payrollId, WorkforceAccess.Action.EDIT);
    verify(supabase).filters(Map.of("employee_id", "eq." + sourceId));
    verify(supabase).updateSingle(eq("employee_profiles"), any(),
        eq(Map.of("photo_url", "employee-photos/photo.jpg")));
  }

  @Test
  void unlinkedPayrollEmployeeCannotChangeSharedPhoto() {
    when(store.jdbc()).thenReturn(jdbc);
    when(jdbc.query(anyString(), any(RowMapper.class), eq(payrollId)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> linker.link(payrollId, "employee-photos/photo.jpg"))
        .isInstanceOf(EmployeePhotoException.class)
        .hasMessageContaining("LineMatrix");
    verifyNoInteractions(supabase);
  }
}
