package com.tradex.unionnorth.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.employee.domain.Employee;
import com.tradex.unionnorth.employee.domain.EmploymentStatus;
import com.tradex.unionnorth.employee.dto.CreateEmployeeRequest;
import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployee;
import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployeeLookup;
import com.tradex.unionnorth.employee.mapper.EmployeeMapper;
import com.tradex.unionnorth.employee.repository.EmployeeRepository;
import com.tradex.unionnorth.setup.PayrollProfileService;
import com.tradex.unionnorth.setup.SetupException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class EmployeeServiceSharedMasterTest {
  private final EmployeeRepository employees = mock(EmployeeRepository.class);
  private final LineMatrixEmployeeLookup lookup = mock(LineMatrixEmployeeLookup.class);
  private final PayrollProfileService profiles = mock(PayrollProfileService.class);
  private final EmployeeService service = new EmployeeService(employees, new EmployeeMapper(),
      profiles, lookup, mock(JdbcTemplate.class));

  @Test
  void unlinkedPayrollEmployeeCannotBeCreated() {
    assertThatThrownBy(() -> service.createEmployee(request(null, "A123")))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("LineMatrix");
    verify(employees, never()).saveAndFlush(any(Employee.class));
  }

  @Test
  void changedMasterIdentityMustBeRefreshedBeforeCreation() {
    when(lookup.lookup("0012")).thenReturn(source());
    assertThatThrownBy(() -> service.createEmployee(request("0012", "wrong")))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("Refresh");
    verify(employees, never()).saveAndFlush(any(Employee.class));
  }

  @Test
  void linkedPayrollRecordUsesAuthoritativeMasterValues() {
    when(lookup.lookup("0012")).thenReturn(source());
    when(employees.saveAndFlush(any(Employee.class))).thenAnswer(invocation -> {
      Employee employee = invocation.getArgument(0);
      ReflectionTestUtils.setField(employee, "id", UUID.randomUUID());
      return employee;
    });
    var created = service.createEmployee(request("0012", "A123"));
    assertThat(created.employeeNumber()).isEqualTo("0012");
    assertThat(created.identityNumber()).isEqualTo("A123");
    assertThat(created.payrollStatus().name()).isEqualTo("HOLD");
    verify(profiles).initialize(created.id(), "0012", null, null, null);
  }

  private CreateEmployeeRequest request(String link, String identity) {
    return new CreateEmployeeRequest("0012", "Jane", "Doe", "Jane Doe", identity,
        "jane@example.com", "0771234567", EmploymentStatus.ACTIVE, null, null, link);
  }

  private LineMatrixEmployee source() {
    return new LineMatrixEmployee("0012", "Jane Doe", "0771234567", null,
        "Production", "Operator", "active", true, UUID.randomUUID().toString(),
        "2024-01-01", null, "permanent", null, null, null, null, null,
        Map.of("first_name", "Jane", "last_name", "Doe", "identity_number", "A123",
            "email", "jane@example.com"));
  }
}
