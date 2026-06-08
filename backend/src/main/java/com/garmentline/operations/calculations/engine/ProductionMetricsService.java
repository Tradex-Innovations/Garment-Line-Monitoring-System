package com.garmentline.operations.calculations.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.garmentline.operations.calculations.incentives.IncentiveCalculationService;
import com.garmentline.operations.calculations.model.CalculationExecutionResult;
import com.garmentline.operations.calculations.model.EfficiencyCalculationInput;
import com.garmentline.operations.calculations.model.EfficiencyCalculationResult;
import com.garmentline.operations.calculations.model.IncentiveCalculationResult;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.ApiException;
import com.garmentline.operations.support.JsonSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
public class ProductionMetricsService {

  private final EfficiencyCalculationService efficiencyCalculationService;
  private final IncentiveCalculationService incentiveCalculationService;
  private final SupabaseAdminClient supabaseAdminClient;
  private final RoleGuard roleGuard;
  private final ObjectMapper objectMapper;

  public ProductionMetricsService(
      EfficiencyCalculationService efficiencyCalculationService,
      IncentiveCalculationService incentiveCalculationService,
      SupabaseAdminClient supabaseAdminClient,
      RoleGuard roleGuard,
      ObjectMapper objectMapper) {
    this.efficiencyCalculationService = efficiencyCalculationService;
    this.incentiveCalculationService = incentiveCalculationService;
    this.supabaseAdminClient = supabaseAdminClient;
    this.roleGuard = roleGuard;
    this.objectMapper = objectMapper;
  }

  public CalculationExecutionResult preview(EfficiencyCalculationInput input, AuthenticatedUser user) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "ie", "viewer");
    EfficiencyCalculationResult metrics = efficiencyCalculationService.calculate(input);
    IncentiveCalculationResult incentive = incentiveCalculationService.calculate(metrics);
    return new CalculationExecutionResult(
        input,
        enrichWithIncentive(metrics, incentive),
        incentive,
        null,
        null);
  }

  public CalculationExecutionResult calculateAndPersist(
      EfficiencyCalculationInput input, AuthenticatedUser user) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "ie");

    ObjectNode lineRow = fetchLineById(input.productionLineId());
    EfficiencyCalculationResult metrics = efficiencyCalculationService.calculate(input);
    IncentiveCalculationResult incentive = incentiveCalculationService.calculate(metrics);
    EfficiencyCalculationResult combinedMetrics = enrichWithIncentive(metrics, incentive);

    String metricRecordId = upsertMetricRecord(lineRow, input, combinedMetrics);
    String incentiveRecordId = upsertIncentiveRecord(lineRow, input, combinedMetrics, incentive, metricRecordId);
    insertAuditSnapshot(input, combinedMetrics, incentive, metricRecordId, incentiveRecordId);

    return new CalculationExecutionResult(
        input,
        combinedMetrics,
        incentive,
        metricRecordId,
        incentiveRecordId);
  }

  public EfficiencyCalculationInput toInputFromStoredRow(JsonNode row) {
    return new EfficiencyCalculationInput(
        JsonSupport.text(row, "production_line_id"),
        LocalDate.parse(JsonSupport.text(row, "production_date")),
        JsonSupport.text(row, "shift_code"),
        decimal(row, "planned_mo"),
        decimal(row, "planned_hel"),
        decimal(row, "actual_mo"),
        decimal(row, "actual_hel"),
        decimal(row, "team_members"),
        decimal(row, "working_hours"),
        decimal(row, "smv"),
        decimal(row, "planned_pcs"),
        decimal(row, "forecast_pcs"),
        decimal(row, "actual_pcs"),
        JsonSupport.text(row, "remarks"),
        decimal(row, "lost_time_minutes"),
        row.get("source_metadata") == null || row.get("source_metadata").isNull()
            ? Map.of()
            : JsonSupport.toMap(objectMapper, row.get("source_metadata")));
  }

  private ObjectNode fetchLineById(String productionLineId) {
    if (productionLineId == null || productionLineId.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "productionLineId is required.");
    }

    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("id", "eq." + productionLineId);
    return supabaseAdminClient.selectSingle("production_lines", query);
  }

  private String upsertMetricRecord(
      ObjectNode lineRow,
      EfficiencyCalculationInput input,
      EfficiencyCalculationResult result) {
    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("production_line_id", "eq." + JsonSupport.text(lineRow, "id"));
    query.add("production_date", "eq." + input.productionDate());
    if (input.shiftCode() == null || input.shiftCode().isBlank()) {
      query.add("shift_code", "is.null");
    } else {
      query.add("shift_code", "eq." + input.shiftCode());
    }

    ArrayNode existingRows = supabaseAdminClient.select("production_line_daily_metrics", query);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("production_line_id", JsonSupport.text(lineRow, "id"));
    payload.put("line_code", JsonSupport.text(lineRow, "code"));
    payload.put("production_date", input.productionDate().toString());
    payload.put("metric_date", input.productionDate().toString());
    payload.put("shift_code", emptyToNull(input.shiftCode()));
    payload.put("planned_mo", input.plannedMo());
    payload.put("planned_hel", input.plannedHel());
    payload.put("actual_mo", input.actualMo());
    payload.put("actual_hel", input.actualHel());
    payload.put("team_members", input.teamMembers());
    payload.put("working_hours", input.workingHours());
    payload.put("smv", input.smv());
    payload.put("planned_pcs", input.plannedPcs());
    payload.put("forecast_pcs", input.forecastPcs());
    payload.put("actual_pcs", input.actualPcs());
    payload.put("remarks", emptyToNull(input.remarks()));
    payload.put("lost_time_minutes", input.lostTimeMinutes());
    payload.put("source_metadata", input.sourceMetadata() == null ? Map.of() : input.sourceMetadata());
    payload.put("planned_cadre_total", result.plannedCadreTotal());
    payload.put("actual_cadre_total", result.actualCadreTotal());
    payload.put("clock_hours", result.clockHours());
    payload.put("planned_sah", result.plannedSah());
    payload.put("planned_efficiency", result.plannedEfficiency());
    payload.put("forecast_sah", result.forecastSah());
    payload.put("forecast_efficiency", result.forecastEfficiency());
    payload.put("actual_sah", result.actualSah());
    payload.put("actual_efficiency", result.actualEfficiency());
    payload.put("piece_variance", result.pieceVariance());
    payload.put("sah_variance", result.sahVariance());
    payload.put("warnings", result.warnings());
    payload.put("formula_rule_set_id", result.ruleSetId());
    payload.put("formula_rule_version", result.ruleSetVersion());
    payload.put("output", integer(input.actualPcs()));
    payload.put("target_output", integer(input.plannedPcs()));
    payload.put("efficiency", percentage(result.actualEfficiency()));

    if (existingRows.isEmpty()) {
      ObjectNode created = supabaseAdminClient.insertSingle("production_line_daily_metrics", payload);
      return JsonSupport.text(created, "id");
    }

    ObjectNode updated =
        supabaseAdminClient.updateSingle(
            "production_line_daily_metrics",
            supabaseAdminClient.filters(Map.of("id", "eq." + JsonSupport.text(existingRows.get(0), "id"))),
            payload);
    return JsonSupport.text(updated, "id");
  }

  private String upsertIncentiveRecord(
      ObjectNode lineRow,
      EfficiencyCalculationInput input,
      EfficiencyCalculationResult metrics,
      IncentiveCalculationResult incentive,
      String sourceMetricRecordId) {
    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("source_metric_record_id", "eq." + sourceMetricRecordId);
    ArrayNode existingRows = supabaseAdminClient.select("incentive_records", query);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("employee_id", null);
    payload.put("month_start", input.productionDate().withDayOfMonth(1).toString());
    payload.put("amount", incentive.incentiveAmount());
    payload.put("reason", incentive.incentiveBandLabel() == null ? "line_efficiency" : incentive.incentiveBandLabel());
    payload.put("production_line_id", JsonSupport.text(lineRow, "id"));
    payload.put("production_date", input.productionDate().toString());
    payload.put("line_code", JsonSupport.text(lineRow, "code"));
    payload.put("shift_code", emptyToNull(input.shiftCode()));
    payload.put("basis_metric", incentive.basisMetric());
    payload.put("basis_value", incentive.basisValue());
    payload.put("actual_efficiency", metrics.actualEfficiency());
    payload.put("incentive_band_label", incentive.incentiveBandLabel());
    payload.put("incentive_amount", incentive.incentiveAmount());
    payload.put("incentive_rule_set_id", incentive.incentiveRuleSetId());
    payload.put("incentive_rule_version", incentive.incentiveRuleVersion());
    payload.put("warnings", incentive.warnings());
    payload.put("source_metric_record_id", sourceMetricRecordId);

    if (existingRows.isEmpty()) {
      ObjectNode created = supabaseAdminClient.insertSingle("incentive_records", payload);
      return JsonSupport.text(created, "id");
    }

    ObjectNode updated =
        supabaseAdminClient.updateSingle(
            "incentive_records",
            supabaseAdminClient.filters(Map.of("id", "eq." + JsonSupport.text(existingRows.get(0), "id"))),
            payload);
    return JsonSupport.text(updated, "id");
  }

  private void insertAuditSnapshot(
      EfficiencyCalculationInput input,
      EfficiencyCalculationResult metrics,
      IncentiveCalculationResult incentive,
      String metricRecordId,
      String incentiveRecordId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("metric_record_id", metricRecordId);
    payload.put("incentive_record_id", incentiveRecordId);
    payload.put("input_payload", objectMapper.convertValue(input, Map.class));
    payload.put("output_payload", objectMapper.convertValue(metrics, Map.class));
    payload.put("warnings", incentive.warnings());
    payload.put("formula_rule_set_id", metrics.ruleSetId());
    payload.put("formula_rule_version", metrics.ruleSetVersion());
    payload.put("incentive_rule_set_id", incentive.incentiveRuleSetId());
    payload.put("incentive_rule_version", incentive.incentiveRuleVersion());
    supabaseAdminClient.insertSingle("calculation_audit_snapshots", payload);
  }

  private EfficiencyCalculationResult enrichWithIncentive(
      EfficiencyCalculationResult metrics, IncentiveCalculationResult incentive) {
    Map<String, Object> debugSnapshot = new LinkedHashMap<>(metrics.debugSnapshot());
    debugSnapshot.put("incentive", objectMapper.convertValue(incentive, Map.class));

    return new EfficiencyCalculationResult(
        metrics.productionLineId(),
        metrics.productionDate(),
        metrics.shiftCode(),
        metrics.plannedCadreTotal(),
        metrics.actualCadreTotal(),
        metrics.clockHours(),
        metrics.plannedSah(),
        metrics.plannedEfficiency(),
        metrics.forecastSah(),
        metrics.forecastEfficiency(),
        metrics.actualSah(),
        metrics.actualEfficiency(),
        metrics.pieceVariance(),
        metrics.sahVariance(),
        incentive.incentiveAmount(),
        incentive.incentiveBandLabel(),
        metrics.ruleSetId(),
        metrics.ruleSetVersion(),
        incentive.incentiveRuleSetId(),
        incentive.incentiveRuleVersion(),
        incentive.warnings(),
        debugSnapshot);
  }

  private BigDecimal decimal(JsonNode row, String field) {
    Double value = JsonSupport.decimal(row, field);
    return value == null ? null : BigDecimal.valueOf(value);
  }

  private Integer integer(BigDecimal value) {
    return value == null ? 0 : value.intValue();
  }

  private BigDecimal percentage(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    return value.multiply(BigDecimal.valueOf(100));
  }

  private String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
