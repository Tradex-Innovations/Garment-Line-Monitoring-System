package com.garmentline.operations.calculations.reports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.ApiException;
import com.garmentline.operations.support.JsonSupport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
public class CalculationReportService {

  private final SupabaseAdminClient supabaseAdminClient;
  private final RoleGuard roleGuard;
  private final ObjectMapper objectMapper;

  public CalculationReportService(
      SupabaseAdminClient supabaseAdminClient, RoleGuard roleGuard, ObjectMapper objectMapper) {
    this.supabaseAdminClient = supabaseAdminClient;
    this.roleGuard = roleGuard;
    this.objectMapper = objectMapper;
  }

  public List<LineMetricReportRow> listMetricReport(
      AuthenticatedUser user,
      String dateFrom,
      String dateTo,
      String lineCode,
      String shiftCode) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "ie", "viewer");

    ArrayNode metrics = supabaseAdminClient.selectAll("production_line_daily_metrics", metricFilters(dateFrom, dateTo, lineCode, shiftCode));
    ArrayNode lines = supabaseAdminClient.selectAll("production_lines", new LinkedMultiValueMap<>());
    ArrayNode incentives = supabaseAdminClient.selectAll("incentive_records", incentiveFilters(dateFrom, dateTo, lineCode, shiftCode));
    ArrayNode snapshots =
        supabaseAdminClient.selectAll(
            "calculation_audit_snapshots",
            supabaseAdminClient.filters(Map.of("order", "created_at.desc", "limit", "500")));

    Map<String, String> lineNameById = new LinkedHashMap<>();
    lines.forEach(row -> lineNameById.put(JsonSupport.text(row, "id"), JsonSupport.text(row, "name")));

    Map<String, ObjectNode> incentiveByMetricId = new LinkedHashMap<>();
    incentives.forEach(row -> {
      String metricId = JsonSupport.text(row, "source_metric_record_id");
      if (metricId != null && !metricId.isBlank() && !incentiveByMetricId.containsKey(metricId)) {
        incentiveByMetricId.put(metricId, (ObjectNode) row);
      }
    });

    Map<String, ObjectNode> latestAuditByMetricId = new LinkedHashMap<>();
    snapshots.forEach(row -> {
      String metricId = JsonSupport.text(row, "metric_record_id");
      if (metricId != null && !metricId.isBlank() && !latestAuditByMetricId.containsKey(metricId)) {
        latestAuditByMetricId.put(metricId, (ObjectNode) row);
      }
    });

    List<LineMetricReportRow> results = new ArrayList<>();
    metrics.forEach(
        row -> {
          String metricId = JsonSupport.text(row, "id");
          ObjectNode incentive = incentiveByMetricId.get(metricId);
          ObjectNode snapshot = latestAuditByMetricId.get(metricId);
          results.add(
              new LineMetricReportRow(
                  metricId,
                  JsonSupport.text(row, "production_line_id"),
                  JsonSupport.text(row, "line_code"),
                  lineNameById.get(JsonSupport.text(row, "production_line_id")),
                  JsonSupport.text(row, "production_date"),
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
                  decimal(row, "planned_cadre_total"),
                  decimal(row, "actual_cadre_total"),
                  decimal(row, "clock_hours"),
                  decimal(row, "planned_sah"),
                  decimal(row, "planned_efficiency"),
                  decimal(row, "forecast_sah"),
                  decimal(row, "forecast_efficiency"),
                  decimal(row, "actual_sah"),
                  decimal(row, "actual_efficiency"),
                  decimal(row, "piece_variance"),
                  decimal(row, "sah_variance"),
                  stringList(row.get("warnings")),
                  JsonSupport.text(row, "formula_rule_set_id"),
                  JsonSupport.integer(row, "formula_rule_version"),
                  incentive == null ? null : JsonSupport.text(incentive, "id"),
                  incentive == null ? null : decimal(incentive, "incentive_amount"),
                  incentive == null ? null : JsonSupport.text(incentive, "incentive_band_label"),
                  incentive == null ? null : JsonSupport.text(incentive, "incentive_rule_set_id"),
                  incentive == null ? null : JsonSupport.integer(incentive, "incentive_rule_version"),
                  snapshot == null
                      ? Map.of()
                      : Map.of(
                          "inputPayload",
                          JsonSupport.toMap(objectMapper, snapshot.get("input_payload")),
                          "outputPayload",
                          JsonSupport.toMap(objectMapper, snapshot.get("output_payload")),
                          "warnings",
                          stringList(snapshot.get("warnings")))));
        });
    return results;
  }

  public List<IncentiveReportRow> listIncentiveReport(
      AuthenticatedUser user,
      String dateFrom,
      String dateTo,
      String lineCode,
      String shiftCode) {
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "ie", "viewer");

    ArrayNode incentives =
        supabaseAdminClient.selectAll("incentive_records", incentiveFilters(dateFrom, dateTo, lineCode, shiftCode));
    ArrayNode metrics =
        supabaseAdminClient.selectAll("production_line_daily_metrics", metricFilters(dateFrom, dateTo, lineCode, shiftCode));
    ArrayNode lines = supabaseAdminClient.selectAll("production_lines", new LinkedMultiValueMap<>());

    Map<String, ObjectNode> metricById = new LinkedHashMap<>();
    metrics.forEach(row -> metricById.put(JsonSupport.text(row, "id"), (ObjectNode) row));
    Map<String, String> lineNameById = new LinkedHashMap<>();
    lines.forEach(row -> lineNameById.put(JsonSupport.text(row, "id"), JsonSupport.text(row, "name")));

    List<IncentiveReportRow> results = new ArrayList<>();
    incentives.forEach(
        row -> {
          ObjectNode metric = metricById.get(JsonSupport.text(row, "source_metric_record_id"));
          String lineId = metric == null ? JsonSupport.text(row, "production_line_id") : JsonSupport.text(metric, "production_line_id");
          results.add(
              new IncentiveReportRow(
                  JsonSupport.text(row, "id"),
                  JsonSupport.text(row, "source_metric_record_id"),
                  lineId,
                  JsonSupport.text(row, "line_code"),
                  lineNameById.get(lineId),
                  JsonSupport.text(row, "production_date"),
                  JsonSupport.text(row, "shift_code"),
                  JsonSupport.text(row, "basis_metric"),
                  decimal(row, "basis_value"),
                  metric == null ? decimal(row, "actual_efficiency") : decimal(metric, "actual_efficiency"),
                  JsonSupport.text(row, "incentive_band_label"),
                  decimal(row, "incentive_amount"),
                  JsonSupport.text(row, "incentive_rule_set_id"),
                  JsonSupport.integer(row, "incentive_rule_version"),
                  stringList(row.get("warnings"))));
        });
    return results;
  }

  public CalculationAuditView getLatestAuditForMetric(AuthenticatedUser user, String metricId) {
    roleGuard.requireAnyRole(user, "admin");

    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("metric_record_id", "eq." + metricId);
    query.add("order", "created_at.desc");
    query.add("limit", "1");

    ArrayNode rows = supabaseAdminClient.select("calculation_audit_snapshots", query);
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "No calculation audit snapshot was found for this metric.");
    }

    JsonNode row = rows.get(0);
    return new CalculationAuditView(
        JsonSupport.text(row, "id"),
        JsonSupport.text(row, "metric_record_id"),
        JsonSupport.text(row, "incentive_record_id"),
        JsonSupport.toMap(objectMapper, row.get("input_payload")),
        JsonSupport.toMap(objectMapper, row.get("output_payload")),
        stringList(row.get("warnings")),
        JsonSupport.text(row, "formula_rule_set_id"),
        JsonSupport.integer(row, "formula_rule_version"),
        JsonSupport.text(row, "incentive_rule_set_id"),
        JsonSupport.integer(row, "incentive_rule_version"),
        JsonSupport.text(row, "created_at"));
  }

  private MultiValueMap<String, String> metricFilters(
      String dateFrom, String dateTo, String lineCode, String shiftCode) {
    LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("order", "production_date.desc");
    addDateRange(query, "production_date", dateFrom, dateTo);
    if (lineCode != null && !lineCode.isBlank()) {
      query.add("line_code", "eq." + lineCode);
    }
    if (shiftCode != null && !shiftCode.isBlank()) {
      query.add("shift_code", "eq." + shiftCode);
    }
    return query;
  }

  private MultiValueMap<String, String> incentiveFilters(
      String dateFrom, String dateTo, String lineCode, String shiftCode) {
    LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("order", "production_date.desc");
    addDateRange(query, "production_date", dateFrom, dateTo);
    if (lineCode != null && !lineCode.isBlank()) {
      query.add("line_code", "eq." + lineCode);
    }
    if (shiftCode != null && !shiftCode.isBlank()) {
      query.add("shift_code", "eq." + shiftCode);
    }
    return query;
  }

  private BigDecimal decimal(JsonNode row, String field) {
    Double value = JsonSupport.decimal(row, field);
    return value == null ? null : BigDecimal.valueOf(value);
  }

  private List<String> stringList(JsonNode node) {
    if (node == null || node.isNull() || !node.isArray()) {
      return List.of();
    }
    List<String> items = new ArrayList<>();
    node.forEach(item -> items.add(item.asText()));
    return items;
  }

  private void addDateRange(
      LinkedMultiValueMap<String, String> query, String column, String dateFrom, String dateTo) {
    if (dateFrom != null && !dateFrom.isBlank() && dateTo != null && !dateTo.isBlank()) {
      query.add("and", "(" + column + ".gte." + dateFrom + "," + column + ".lte." + dateTo + ")");
      return;
    }
    if (dateFrom != null && !dateFrom.isBlank()) {
      query.add(column, "gte." + dateFrom);
    }
    if (dateTo != null && !dateTo.isBlank()) {
      query.add(column, "lte." + dateTo);
    }
  }
}
