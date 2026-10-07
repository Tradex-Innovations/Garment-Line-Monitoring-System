package com.tradex.unionnorth.setup;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
public class PayrollEmployeeRegistrationController {
    private final PayrollEmployeeRegistrationService registration;

    public PayrollEmployeeRegistrationController(PayrollEmployeeRegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/register-payroll")
    @PreAuthorize("hasAuthority('EMPLOYEE_CREATE') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<PayrollEmployeeRegistrationService.Result> register(
            @Valid @RequestBody PayrollEmployeeRegistrationService.Request request,
            JwtAuthenticationToken authentication) {
        var result = registration.register(request, UUID.fromString(authentication.getName()));
        return ResponseEntity.created(URI.create("/api/v1/employees/" + result.employeeId())).body(result);
    }
}
