package com.tradex.unionnorth.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.employee.domain.Employee;
import com.tradex.unionnorth.employee.domain.PayrollStatus;
import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployee;
import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployeeLookup;
import com.tradex.unionnorth.employee.mapper.EmployeeMapper;
import com.tradex.unionnorth.employee.repository.EmployeeRepository;
import com.tradex.unionnorth.setup.PayrollProfileService;
import com.tradex.unionnorth.setup.SetupException;
import com.tradex.unionnorth.security.WorkforceAccess;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class EmployeeRegistrationSyncTest {
    private final EmployeeRepository repository = mock(EmployeeRepository.class);
    private final PayrollProfileService profiles = mock(PayrollProfileService.class);
    private final LineMatrixEmployeeLookup lookup = mock(LineMatrixEmployeeLookup.class);
    private final EmployeeService service = new EmployeeService(repository, new EmployeeMapper(), profiles, lookup,
            mock(JdbcTemplate.class), mock(WorkforceAccess.class));

    @Test
    void importsCompletePermanentEmployeeAsHoldDraft() {
        when(repository.findByEmployeeNumber("22541")).thenReturn(Optional.empty());
        when(lookup.lookup("22541")).thenReturn(source(Map.of(
                "first_name", "Asha", "last_name", "Perera", "identity_number", "NIC-1")));
        when(repository.saveAndFlush(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerFromLineMatrix("22541");

        var saved = ArgumentCaptor.forClass(Employee.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPayrollStatus()).isEqualTo(PayrollStatus.HOLD);
        assertThat(saved.getValue().getIdentityNumber()).isEqualTo("NIC-1");
        verify(profiles).initialize(any(), eq("22541"));
    }

    @Test
    void incompleteIdentityCannotCreatePayrollRecord() {
        when(repository.findByEmployeeNumber("22541")).thenReturn(Optional.empty());
        when(lookup.lookup("22541")).thenReturn(source(Map.of("first_name", "Asha", "last_name", "Perera")));

        assertThatThrownBy(() -> service.registerFromLineMatrix("22541")).isInstanceOf(SetupException.class);
        verify(repository, never()).saveAndFlush(any(Employee.class));
    }

    @Test
    void importsCompleteNewJoinerAsHoldDraft() {
        when(repository.findByEmployeeNumber("101001")).thenReturn(Optional.empty());
        var joiner = new LineMatrixEmployee("101001", "Asha Perera", null, null, "Production", "Worker",
                "active", true, "7286b623-9d9f-4ecb-aa1c-3a938850c922", null, null, "new_joiner",
                null, null, null, null, null,
                Map.of("first_name", "Asha", "last_name", "Perera", "identity_number", "NIC-1"));
        when(lookup.lookup("101001")).thenReturn(joiner);
        when(repository.saveAndFlush(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerFromLineMatrix("101001");

        verify(repository).saveAndFlush(any(Employee.class));
        verify(profiles).initialize(any(), eq("101001"));
    }

    @Test
    void internCannotBeImportedEvenIfLookupReturnsIt() {
        when(repository.findByEmployeeNumber("303001")).thenReturn(Optional.empty());
        var intern = new LineMatrixEmployee("303001", "Asha Perera", null, null, "Production", "Intern",
                "active", true, "7286b623-9d9f-4ecb-aa1c-3a938850c922", null, null, "intern",
                null, null, null, null, null,
                Map.of("first_name", "Asha", "last_name", "Perera", "identity_number", "NIC-1"));
        when(lookup.lookup("303001")).thenReturn(intern);

        assertThatThrownBy(() -> service.registerFromLineMatrix("303001")).isInstanceOf(SetupException.class);
        verify(repository, never()).saveAndFlush(any(Employee.class));
    }

    private LineMatrixEmployee source(Map<String, Object> details) {
        return new LineMatrixEmployee("22541", "Asha Perera", null, "22541", "Production", "Worker",
                "active", true, "7286b623-9d9f-4ecb-aa1c-3a938850c922", null, null, "permanent",
                null, null, null, null, null, details);
    }
}
