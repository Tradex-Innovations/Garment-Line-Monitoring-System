package com.tradex.unionnorth.setup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployeeLookup;
import com.tradex.unionnorth.security.WorkforceAccess;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PayrollProfileGroupAccessTest {
    private final SetupStore store = mock(SetupStore.class);
    private final LineMatrixEmployeeLookup lookup = mock(LineMatrixEmployeeLookup.class);
    private final WorkforceAccess access = mock(WorkforceAccess.class);
    private final PayrollProfileService service = new PayrollProfileService(store, mock(SetupCatalog.class),
            mock(MasterDataService.class), lookup, access);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void generalHrCannotRelinkAnExecutivePayrollRecordToAVisibleSource() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), "test", List.of(new SimpleGrantedAuthority("EMPLOYEE_UPDATE"))));
        UUID executivePayrollId = UUID.randomUUID();
        when(access.employeeGroup(executivePayrollId, WorkforceAccess.Action.EDIT))
                .thenThrow(new AccessDeniedException("Executive payroll denied"));

        assertThatThrownBy(() -> service.link(executivePayrollId,
                new PayrollProfileService.Link(1, "22541", "Link source")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(lookup, store);
    }
}
