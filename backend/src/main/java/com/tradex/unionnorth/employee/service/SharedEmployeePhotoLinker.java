package com.tradex.unionnorth.employee.service;

import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.tradex.unionnorth.setup.SetupStore;
import com.tradex.unionnorth.security.WorkforceAccess;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Links a Payroll upload to the shared LineMatrix employee profile. */
@Service
@Profile("payroll")
public class SharedEmployeePhotoLinker {
    private final SetupStore store;
    private final SupabaseAdminClient supabase;
    private final WorkforceAccess workforce;

    public SharedEmployeePhotoLinker(
            SetupStore store, SupabaseAdminClient supabase, WorkforceAccess workforce) {
        this.store = store;
        this.supabase = supabase;
        this.workforce = workforce;
    }

    public void link(UUID payrollEmployeeId, String storageKey) {
        workforce.requireProfile(payrollEmployeeId, WorkforceAccess.Action.EDIT);
        var sourceIds = store.jdbc().query(
                "SELECT linematrix_employee_id FROM employee_payroll_profiles WHERE employee_id=?",
                (row, index) -> row.getObject(1, UUID.class),
                payrollEmployeeId);
        if (sourceIds.size() != 1 || sourceIds.getFirst() == null) {
            throw new EmployeePhotoException(
                    "EMPLOYEE_PHOTO_SOURCE_MISSING",
                    "Link the employee to LineMatrix before uploading a photograph",
                    HttpStatus.CONFLICT);
        }
        UUID sourceId = sourceIds.getFirst();
        var filter = supabase.filters(Map.of("employee_id", "eq." + sourceId));
        supabase.updateSingle("employee_profiles", filter, Map.of("photo_url", storageKey));
    }
}
