package com.tradex.unionnorth.setup;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollCalculationController {
    private final PayrollCalculationService service;

    public PayrollCalculationController(PayrollCalculationService service) {
        this.service = service;
    }

    @GetMapping("/employees")
    public ResponseEntity<?> employees(@RequestParam UUID periodId) {
        return response(service.employees(periodId));
    }

    @GetMapping("/configuration")
    public ResponseEntity<?> configuration(
            @RequestParam UUID employeeId,
            @RequestParam UUID periodId,
            @RequestParam UUID policyId) {
        return response(service.configuration(employeeId, periodId, policyId));
    }

    @GetMapping("/equations")
    public ResponseEntity<?> equations(
            @RequestParam UUID structureId,
            @RequestParam UUID policyId,
            @RequestParam LocalDate at) {
        return response(service.equations(structureId, policyId, at));
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody PayrollCalculationService.Request request) {
        return response(service.preview(request));
    }

    @PostMapping("/calculations")
    public ResponseEntity<?> save(@RequestBody PayrollCalculationService.Request request) {
        return response(service.save(request));
    }

    @GetMapping("/calculations")
    public ResponseEntity<?> history(@RequestParam UUID periodId) {
        return response(service.history(periodId));
    }

    @GetMapping("/calculations/{id}")
    public ResponseEntity<?> detail(@PathVariable UUID id) {
        return response(service.detail(id));
    }

    private ResponseEntity<?> response(Object value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
