package com.tradex.unionnorth.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.employee.service.SharedEmployeeRegistrationService;
import com.tradex.unionnorth.security.WorkforceAccess;
import com.tradex.unionnorth.security.domain.WorkforceGroup;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PayrollEmployeeRegistrationServiceTest {
    private final SharedEmployeeRegistrationService shared = mock(SharedEmployeeRegistrationService.class);
    private final SetupStore store = mock(SetupStore.class);
    private final SetupCatalog catalog = mock(SetupCatalog.class);
    private final MasterDataService masters = mock(MasterDataService.class);
    private final WorkforceAccess workforce = mock(WorkforceAccess.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PayrollEmployeeRegistrationService service =
            new PayrollEmployeeRegistrationService(shared, store, catalog, masters, workforce);
    private final UUID actor = UUID.fromString("00000000-0000-4000-8000-000000000030");
    private final UUID sourceId = UUID.fromString("00000000-0000-4000-8000-000000000040");

    @BeforeEach
    void grantAccess() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actor.toString(), null, List.of(
                        new SimpleGrantedAuthority("EMPLOYEE_CREATE"),
                        new SimpleGrantedAuthority("EMPLOYEE_UPDATE"),
                        new SimpleGrantedAuthority("SALARY_EDIT"))));
        when(store.jdbc()).thenReturn(jdbc);
        when(store.json(any())).thenReturn("{}");
        when(catalog.generalFields()).thenReturn(List.of());
        when(catalog.financialFields()).thenReturn(List.of());
        when(catalog.validate(any(), any(), anyBoolean())).thenAnswer(call -> call.getArgument(1));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersNewJoinerToSharedMasterAndPayrollDraftInOneOperation() {
        when(shared.register(any(), any())).thenReturn(new SharedEmployeeRegistrationService.Result(
                sourceId, "101001", "new_joiner", WorkforceGroup.GENERAL_WORKFORCE, true));
        var result = service.register(request("new_joiner", "101001"), actor);

        assertThat(result.payrollEligible()).isTrue();
        assertThat(result.sourceId()).isEqualTo(result.employeeId());
        verify(shared).register(any(), any());
        verify(store).audit(any(), any(), any(), any(), any(), any());
    }

    @Test
    void internCannotUseFullPayrollRegistration() {
        assertThatThrownBy(() -> service.register(request("intern", "303001"), actor))
                .isInstanceOf(SetupException.class)
                .hasMessageContaining("interns");
        verifyNoInteractions(shared);
    }

    private PayrollEmployeeRegistrationService.Request request(String type, String number) {
        return new PayrollEmployeeRegistrationService.Request(type, WorkforceGroup.GENERAL_WORKFORCE,
                number, UUID.fromString("00000000-0000-4000-8000-000000000010"),
                UUID.fromString("00000000-0000-4000-8000-000000000020"),
                Map.of("firstName", "Asha", "lastName", "Perera", "identityNumber", "NIC-1",
                        "joinedDate", "2026-10-01", "employmentType", type.toUpperCase()),
                Map.of(), "Initial registration");
    }
}
