package com.garmentline.operations.api;

import com.garmentline.operations.calculations.engine.CalculationBatchService;
import com.garmentline.operations.calculations.engine.ProductionMetricsService;
import com.garmentline.operations.calculations.model.CalculationExecutionResult;
import com.garmentline.operations.calculations.model.EfficiencyCalculationInput;
import com.garmentline.operations.calculations.reports.CalculationAuditView;
import com.garmentline.operations.calculations.reports.CalculationReportService;
import com.garmentline.operations.calculations.reports.IncentiveReportRow;
import com.garmentline.operations.calculations.reports.LineMetricReportRow;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.UserContextService;
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
@RequestMapping("/api/calculations")
@Validated
public class CalculationMetricsController {

  private final ProductionMetricsService productionMetricsService;
  private final CalculationBatchService calculationBatchService;
  private final CalculationReportService calculationReportService;
  private final UserContextService userContextService;

  public CalculationMetricsController(
      ProductionMetricsService productionMetricsService,
      CalculationBatchService calculationBatchService,
      CalculationReportService calculationReportService,
      UserContextService userContextService) {
    this.productionMetricsService = productionMetricsService;
    this.calculationBatchService = calculationBatchService;
    this.calculationReportService = calculationReportService;
    this.userContextService = userContextService;
  }

  @PostMapping("/preview")
  public CalculationExecutionResult previewCalculation(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EfficiencyCalculationInput input) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return productionMetricsService.preview(input, user);
  }

  @PostMapping("/metrics")
  public CalculationExecutionResult calculateAndPersist(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EfficiencyCalculationInput input) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return productionMetricsService.calculateAndPersist(input, user);
  }

  @GetMapping("/metrics")
  public List<LineMetricReportRow> listMetrics(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String dateFrom,
      @RequestParam(required = false) String dateTo,
      @RequestParam(required = false) String lineCode,
      @RequestParam(required = false) String shiftCode) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return calculationReportService.listMetricReport(user, dateFrom, dateTo, lineCode, shiftCode);
  }

  @GetMapping("/incentives")
  public List<IncentiveReportRow> listIncentives(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String dateFrom,
      @RequestParam(required = false) String dateTo,
      @RequestParam(required = false) String lineCode,
      @RequestParam(required = false) String shiftCode) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return calculationReportService.listIncentiveReport(user, dateFrom, dateTo, lineCode, shiftCode);
  }

  @GetMapping("/metrics/{metricId}/audit")
  public CalculationAuditView getMetricAudit(
      @AuthenticationPrincipal Jwt jwt, @PathVariable @NotBlank String metricId) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return calculationReportService.getLatestAuditForMetric(user, metricId);
  }

  @PostMapping("/recalculate")
  public Map<String, Object> recalculate(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RecalculateRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return calculationBatchService.recalculate(user, request.dateFrom(), request.dateTo(), request.lineCode());
  }

  public record RecalculateRequest(String dateFrom, String dateTo, String lineCode) {
  }
}

