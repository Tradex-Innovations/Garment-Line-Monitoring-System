package com.tradex.unionnorth.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.employee.dto.SharedEmployeeRegistrationRequest;
import com.tradex.unionnorth.security.WorkforceAccess;
import com.tradex.unionnorth.security.domain.WorkforceGroup;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SharedEmployeeRegistrationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkforceAccess access = mock(WorkforceAccess.class);
    private final SharedEmployeeRegistrationService service = new SharedEmployeeRegistrationService(jdbc, access);
    private static final UUID DEPARTMENT = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID DESIGNATION = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000030");

    @Test
    void rejectsWrongTemporaryNumberBeforeWriting() {
        assertThatThrownBy(() -> service.register(request("intern", "101001"), ACTOR))
                .isInstanceOf(EmployeeRegistrationException.class)
                .hasMessageContaining("303");
        verifyNoInteractions(jdbc);
    }

    @Test
    void registersInternIntoSharedMasterWithoutPayrollEligibility() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(UUID.class))).thenReturn(1L);

        var result = service.register(request("intern", "303001"), ACTOR);

        assertThat(result.employeeType()).isEqualTo("intern");
        assertThat(result.payrollEligible()).isFalse();
        assertThat(result.employeeNumber()).isEqualTo("303001");
    }

    @Test
    void permanentEmployeeIsMarkedForPayrollDraft() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(UUID.class))).thenReturn(1L);

        var result = service.register(request("permanent", "22541"), ACTOR);

        assertThat(result.payrollEligible()).isTrue();
        assertThat(result.employeeNumber()).isEqualTo("22541");
    }

    private SharedEmployeeRegistrationRequest request(String type, String number) {
        return new SharedEmployeeRegistrationRequest(type, WorkforceGroup.GENERAL_WORKFORCE, number, "Asha", "Perera", null, "NIC-1",
                null, null, DEPARTMENT, DESIGNATION, LocalDate.of(2026, 9, 28),
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
}
