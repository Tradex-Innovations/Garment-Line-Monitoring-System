package com.garmentline.operations.calculations.engine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.garmentline.operations.calculations.model.CalculationExecutionResult;
import com.garmentline.operations.calculations.model.EfficiencyCalculationInput;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.JsonSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
public class CalculationBatchService {

  private final ProductionMetricsService productionMetricsService;
  private final SupabaseAdminClient supabaseAdminClient;
  private final RoleGuard roleGuard;

  public CalculationBatchService(
      ProductionMetricsService productionMetricsService,
      SupabaseAdminClient supabaseAdminClient,
      RoleGuard roleGuard) {
    this.productionMetricsService = productionMetricsService;
    this.supabaseAdminClient = supabaseAdminClient;
    this.roleGuard = roleGuard;
  }

  public Map<String, Object> recalculate(
      AuthenticatedUser user, String dateFrom, String dateTo, String lineCode) {
    roleGuard.requireAnyRole(user, "admin");

    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    query.add("order", "production_date.asc");
    if (dateFrom != null && !dateFrom.isBlank() && dateTo != null && !dateTo.isBlank()) {
      query.add("and", "(production_date.gte." + dateFrom + ",production_date.lte." + dateTo + ")");
    } else if (dateFrom != null && !dateFrom.isBlank()) {
      query.add("production_date", "gte." + dateFrom);
    } else if (dateTo != null && !dateTo.isBlank()) {
      query.add("production_date", "lte." + dateTo);
    }
    if (lineCode != null && !lineCode.isBlank()) {
      query.add("line_code", "eq." + lineCode);
    }

    ArrayNode rows = supabaseAdminClient.selectAll("production_line_daily_metrics", query);
    List<Map<String, Object>> processed = new ArrayList<>();

    rows.forEach(
        row -> {
          EfficiencyCalculationInput input = productionMetricsService.toInputFromStoredRow(row);
          CalculationExecutionResult result =
              productionMetricsService.calculateAndPersist(input, user);
          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("metricRecordId", result.metricRecordId());
          summary.put("incentiveRecordId", result.incentiveRecordId());
          summary.put("productionDate", JsonSupport.text(row, "production_date"));
          summary.put("lineCode", JsonSupport.text(row, "line_code"));
          processed.add(summary);
        });

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("recalculatedCount", processed.size());
    payload.put("items", processed);
    return payload;
  }
}
