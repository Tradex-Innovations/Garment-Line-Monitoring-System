package com.tradex.unionnorth.setup;

import com.tradex.unionnorth.security.domain.WorkforceGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/approvals")
public class PayrollGroupApprovalController {
    public record Decision(@NotBlank @Size(max = 500) String reason) {}

    private final PayrollGroupApprovalService service;

    public PayrollGroupApprovalController(PayrollGroupApprovalService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam UUID periodId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(periodId));
    }

    @GetMapping("/events")
    public ResponseEntity<?> events(@RequestParam UUID periodId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.events(periodId));
    }

    @PostMapping("/{periodId}/{group}/submit")
    public ResponseEntity<?> submit(@PathVariable UUID periodId, @PathVariable WorkforceGroup group,
                                    @Valid @RequestBody Decision decision) {
        service.submit(periodId, group, decision.reason());
        return list(periodId);
    }

    @PostMapping("/{periodId}/{group}/approve")
    public ResponseEntity<?> approve(@PathVariable UUID periodId, @PathVariable WorkforceGroup group,
                                     @Valid @RequestBody Decision decision) {
        service.approve(periodId, group, decision.reason());
        return list(periodId);
    }

    @PostMapping("/{periodId}/{group}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID periodId, @PathVariable WorkforceGroup group,
                                    @Valid @RequestBody Decision decision) {
        service.reject(periodId, group, decision.reason());
        return list(periodId);
    }
}
