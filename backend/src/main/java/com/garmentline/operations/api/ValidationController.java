package com.garmentline.operations.api;

import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.UserContextService;
import com.garmentline.operations.service.ImportService;
import com.garmentline.operations.service.ValidationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation")
@Validated
public class ValidationController {

  private final ImportService importService;
  private final ValidationService validationService;
  private final UserContextService userContextService;

  public ValidationController(
      ImportService importService,
      ValidationService validationService,
      UserContextService userContextService) {
    this.importService = importService;
    this.validationService = validationService;
    this.userContextService = userContextService;
  }

  @GetMapping("/summary")
  public List<Map<String, Object>> getValidationSummary(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return validationService.getValidationSummary(user);
  }

  @GetMapping
  public List<Map<String, Object>> getReconciliationRows(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String attendanceDate,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String department,
      @RequestParam(required = false) String employeeCode,
      @RequestParam(required = false) String importBatchId) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return validationService.getReconciliationRows(
        user, attendanceDate, status, department, employeeCode, importBatchId);
  }

  @GetMapping("/{reconciliationId}")
  public Map<String, Object> getReconciliationDetail(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String reconciliationId) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return validationService.getReconciliationDetail(user, reconciliationId);
  }

  @PostMapping("/pairs")
  public Map<String, Object> reconcilePair(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReconcilePairRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return importService.reconcilePair(request.faceBatchId(), request.fingerprintBatchId(), user);
  }

  @PostMapping("/{reconciliationId}/override")
  public Map<String, Object> overrideReconciliation(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String reconciliationId,
      @Valid @RequestBody OverrideRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return validationService.overrideReconciliationStatus(
        user, reconciliationId, request.newStatus(), request.reason(), request.note());
  }

  @PostMapping("/{reconciliationId}/notes")
  public Map<String, Object> addReconciliationNote(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String reconciliationId,
      @Valid @RequestBody NoteRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return validationService.addReconciliationNote(user, reconciliationId, request.note());
  }

  public record ReconcilePairRequest(
      @NotBlank String faceBatchId,
      @NotBlank String fingerprintBatchId) {
  }

  public record OverrideRequest(
      @NotBlank String newStatus,
      @NotBlank String reason,
      String note) {
  }

  public record NoteRequest(@NotBlank String note) {
  }
}
