package com.tradex.unionnorth.employee.linematrix;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LineMatrixEmployeeController {
    private final LineMatrixEmployeeLookup lookup;

    public LineMatrixEmployeeController(LineMatrixEmployeeLookup lookup) {
        this.lookup = lookup;
    }

    @GetMapping("/api/v1/employees/linematrix-lookup")
    @PreAuthorize("hasAuthority('EMPLOYEE_CREATE') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<LineMatrixEmployee> lookup(@RequestParam String employeeNumber) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lookup.lookup(employeeNumber));
    }
}
