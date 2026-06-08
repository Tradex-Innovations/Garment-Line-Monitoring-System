package com.garmentline.operations.calculations.engine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.garmentline.operations.calculations.incentives.IncentiveCalculationService;
import com.garmentline.operations.calculations.model.EfficiencyCalculationInput;
import com.garmentline.operations.calculations.model.EfficiencyCalculationResult;
import com.garmentline.operations.calculations.model.IncentiveCalculationResult;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductionMetricsServiceTest {

  @Test
  void persistsMetricIncentiveAndAuditSnapshotTogether() {
    EfficiencyCalculationService efficiencyService = mock(EfficiencyCalculationService.class);
    IncentiveCalculationService incentiveService = mock(IncentiveCalculationService.class);
    SupabaseAdminClient supabaseAdminClient = mock(SupabaseAdminClient.class);
    RoleGuard roleGuard = mock(RoleGuard.class);
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    ProductionMetricsService service =
        new ProductionMetricsService(
            efficiencyService,
            incentiveService,
            supabaseAdminClient,
            roleGuard,
            objectMapper);

    EfficiencyCalculationInput input =
        new EfficiencyCalculationInput(
            "line-1",
            LocalDate.of(2026, 5, 1),
            "Shift A",
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.valueOf(9),
            BigDecimal.valueOf(20),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(95),
            BigDecimal.valueOf(98),
            null,
            BigDecimal.ZERO,
            Map.of());

    EfficiencyCalculationResult metrics =
        new EfficiencyCalculationResult(
            "line-1",
            input.productionDate(),
            input.shiftCode(),
            BigDecimal.valueOf(2),
            BigDecimal.valueOf(2),
            BigDecimal.valueOf(18),
            BigDecimal.valueOf(33.3),
            BigDecimal.valueOf(0.20),
            BigDecimal.valueOf(31.6),
            BigDecimal.valueOf(0.18),
            BigDecimal.valueOf(32.6),
            BigDecimal.valueOf(0.19),
            BigDecimal.valueOf(3),
            BigDecimal.ONE,
            BigDecimal.ZERO,
            null,
            "efficiency-v1",
            1,
            null,
            0,
            List.of(),
            Map.of());

    IncentiveCalculationResult incentive =
        new IncentiveCalculationResult(
            "actual_efficiency",
            BigDecimal.valueOf(0.19),
            null,
            BigDecimal.ZERO,
            "incentive-ladder-v1",
            1,
            List.of("NO_OUTPUT"));

    ObjectNode lineRow = objectMapper.createObjectNode();
    lineRow.put("id", "line-1");
    lineRow.put("code", "L-01");
    lineRow.put("name", "Line 01");

    ArrayNode emptyRows = objectMapper.createArrayNode();

    ObjectNode createdMetric = objectMapper.createObjectNode();
    createdMetric.put("id", "metric-1");
    ObjectNode createdIncentive = objectMapper.createObjectNode();
    createdIncentive.put("id", "incentive-1");
    ObjectNode createdAudit = objectMapper.createObjectNode();
    createdAudit.put("id", "audit-1");

    when(efficiencyService.calculate(input)).thenReturn(metrics);
    when(incentiveService.calculate(any(EfficiencyCalculationResult.class))).thenReturn(incentive);
    when(supabaseAdminClient.selectSingle(eq("production_lines"), any())).thenReturn(lineRow);
    when(supabaseAdminClient.select(eq("production_line_daily_metrics"), any())).thenReturn(emptyRows);
    when(supabaseAdminClient.select(eq("incentive_records"), any())).thenReturn(emptyRows);
    when(supabaseAdminClient.insertSingle(eq("production_line_daily_metrics"), any()))
        .thenReturn(createdMetric);
    when(supabaseAdminClient.insertSingle(eq("incentive_records"), any())).thenReturn(createdIncentive);
    when(supabaseAdminClient.insertSingle(eq("calculation_audit_snapshots"), any()))
        .thenReturn(createdAudit);

    service.calculateAndPersist(input, new AuthenticatedUser("user-1", "Admin", "admin"));

    verify(supabaseAdminClient, times(1)).insertSingle(eq("production_line_daily_metrics"), any());
    verify(supabaseAdminClient, times(1)).insertSingle(eq("incentive_records"), any());
    verify(supabaseAdminClient, times(1)).insertSingle(eq("calculation_audit_snapshots"), any());
  }
}
