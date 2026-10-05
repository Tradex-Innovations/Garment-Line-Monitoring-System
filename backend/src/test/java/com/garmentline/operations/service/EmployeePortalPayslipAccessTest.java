package com.garmentline.operations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.garmentline.operations.config.EmployeePortalProperties;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

class EmployeePortalPayslipAccessTest {
  private final SupabaseAdminClient supabase = mock(SupabaseAdminClient.class);
  private final EmployeePayslipService payslips = mock(EmployeePayslipService.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final EmployeePortalService portal = new EmployeePortalService(supabase,
      new EmployeePortalProperties("Asia/Colombo", 0, 0, 0, false, 0, 0), payslips);
  private final UUID employeeId = UUID.randomUUID();

  @Test
  void kioskTokenCannotOpenSalary() {
    session("KIOSK");
    ApiException error = assertThrows(ApiException.class, () -> portal.payslips("test-token"));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    verifyNoInteractions(payslips);
  }

  @Test
  void privatePortalSessionCanOpenOwnSalaryOnly() {
    session("STANDARD");
    when(payslips.list(employeeId.toString())).thenReturn(List.of());
    assertEquals(List.of(), portal.payslips("test-token"));
  }

  @Test
  void monthlyCalendarUsesOnlyTheSignedInEmployee() {
    session("STANDARD");
    when(supabase.selectAll(eq("attendance_reconciliation"), any()))
        .thenReturn(mapper.createArrayNode());
    when(supabase.selectAll(eq("employee_leave_requests"), any()))
        .thenReturn(mapper.createArrayNode());
    assertEquals("2026-10", portal.calendar("test-token", "2026-10").get("month"));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<MultiValueMap<String, String>> filters = ArgumentCaptor.forClass(MultiValueMap.class);
    verify(supabase).selectAll(eq("employee_leave_requests"), filters.capture());
    assertEquals("eq." + employeeId, filters.getValue().getFirst("employee_id"));
    assertEquals("eq.approved", filters.getValue().getFirst("status"));
  }

  @Test
  void kioskTokenCannotBrowseMonthlyHistory() {
    session("KIOSK");
    ApiException error = assertThrows(ApiException.class,
        () -> portal.calendar("test-token", "2026-10"));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
  }

  private void session(String scope) {
    ArrayNode sessions = mapper.createArrayNode();
    sessions.add(mapper.createObjectNode()
        .put("id", UUID.randomUUID().toString())
        .put("employee_id", employeeId.toString())
        .put("session_scope", scope)
        .put("expires_at", Instant.now().plusSeconds(300).toString()));
    ArrayNode employees = mapper.createArrayNode();
    employees.add(mapper.createObjectNode().put("id", employeeId.toString())
        .put("employee_code", "DEMO001").put("is_active", true));
    when(supabase.selectAll(eq("employee_portal_sessions"), any())).thenReturn(sessions);
    when(supabase.selectAll(eq("employees"), any())).thenReturn(employees);
  }
}
