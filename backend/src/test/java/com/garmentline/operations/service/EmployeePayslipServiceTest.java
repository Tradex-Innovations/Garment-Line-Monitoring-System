package com.garmentline.operations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garmentline.operations.support.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class EmployeePayslipServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
  private final EmployeePayslipService service = new EmployeePayslipService(provider, new ObjectMapper());

  @Test
  void rejectsQueriesWithoutExplanationBeforeDatabaseAccess() {
    ApiException error = assertThrows(ApiException.class, () -> service.review(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "QUERY", " "));
    assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsPayslipNotOwnedByEmployeeOrNotApproved() {
    when(provider.getIfAvailable()).thenReturn(jdbc);
    UUID employee = UUID.randomUUID();
    UUID calculation = UUID.randomUUID();
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
    ApiException error = assertThrows(ApiException.class, () -> service.review(
        employee.toString(), calculation.toString(), "CONFIRMED", null));
    assertEquals(HttpStatus.CONFLICT, error.getStatus());
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), any(Object[].class));
    assertTrue(sql.getValue().contains("p.linematrix_employee_id=?"));
    assertTrue(sql.getValue().contains("a.status='APPROVED'"));
    assertTrue(sql.getValue().contains("ON CONFLICT (calculation_id) DO NOTHING"));
  }

  @Test
  void requiresConnectedPayrollDatabase() {
    ApiException error = assertThrows(ApiException.class, () -> service.list(UUID.randomUUID().toString()));
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
  }
}
