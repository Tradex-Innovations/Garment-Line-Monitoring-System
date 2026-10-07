package com.tradex.unionnorth.employee.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.employee.dto.SharedEmployeeRegistrationRequest;
import com.tradex.unionnorth.employee.service.EmployeePhotoService;
import com.tradex.unionnorth.employee.service.EmployeeRegistrationException;
import com.tradex.unionnorth.employee.service.EmployeeService;
import com.tradex.unionnorth.employee.service.SharedEmployeeRegistrationService;
import org.junit.jupiter.api.Test;

class EmployeeControllerRegistrationRoutingTest {
    @Test
    void basicRegistrationCannotBypassTheFullPayrollForm() {
        var shared = mock(SharedEmployeeRegistrationService.class);
        var controller = new EmployeeController(mock(EmployeeService.class), mock(EmployeePhotoService.class), shared);
        var request = mock(SharedEmployeeRegistrationRequest.class);
        when(request.employeeType()).thenReturn("new_joiner");

        assertThatThrownBy(() -> controller.registerSharedEmployee(request, null))
                .isInstanceOf(EmployeeRegistrationException.class)
                .hasMessageContaining("full payroll registration form");
        verifyNoInteractions(shared);
    }
}
