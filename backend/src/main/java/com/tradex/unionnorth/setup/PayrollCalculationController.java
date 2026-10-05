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
    private final PayrollLeaveInputsService leaveInputs;

    public PayrollCalculationController(PayrollCalculationService service, PayrollLeaveInputsService leaveInputs) {
        this.service = service;
        this.leaveInputs = leaveInputs;
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

    @GetMapping("/approved-leave")
    public ResponseEntity<?> approvedLeave(@RequestParam UUID employeeId, @RequestParam UUID periodId) {
        return response(leaveInputs.approved(employeeId, periodId));
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

    @GetMapping("/payslip-reviews")
    public ResponseEntity<?> payslipReviews(@RequestParam UUID periodId) {
        return response(service.payslipReviews(periodId));
    }

    @GetMapping("/calculations/{id}")
    public ResponseEntity<?> detail(@PathVariable UUID id) {
        return response(service.detail(id));
    }

    private ResponseEntity<?> response(Object value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
