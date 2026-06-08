package com.garmentline.operations.calculations.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentline.operations.calculations.loader.CalculationRuleLoaderService;
import com.garmentline.operations.calculations.model.EfficiencyCalculationInput;
import com.garmentline.operations.calculations.model.EfficiencyCalculationResult;
import com.garmentline.operations.calculations.validation.CalculationRuleValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EfficiencyCalculationServiceTest {

  private EfficiencyCalculationService service;

  @BeforeEach
  void setUp() {
    CalculationRuleLoaderService loader =
        new CalculationRuleLoaderService(new CalculationRuleValidator());
    loader.initialize();
    service = new EfficiencyCalculationService(loader);
  }

  @Test
  void computesWorkbookMetricsFromTypedInput() {
    EfficiencyCalculationResult result =
        service.calculate(
            new EfficiencyCalculationInput(
                "line-1",
                LocalDate.of(2026, 5, 1),
                "Shift A",
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(8),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.valueOf(9),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(950),
                BigDecimal.valueOf(980),
                "sample",
                BigDecimal.ZERO,
                Map.of("source", "test")));

    assertThat(result.plannedCadreTotal()).isEqualByComparingTo("12");
    assertThat(result.actualCadreTotal()).isEqualByComparingTo("10");
    assertThat(result.clockHours()).isEqualByComparingTo("90");
    assertThat(result.pieceVariance()).isEqualByComparingTo("30");
    assertThat(result.sahVariance()).isGreaterThan(BigDecimal.ZERO);
  }

  @Test
  void returnsSafeOutputsForZeroHourInput() {
    EfficiencyCalculationResult result =
        service.calculate(
            new EfficiencyCalculationInput(
                "line-1",
                LocalDate.of(2026, 5, 1),
                "Shift A",
                BigDecimal.valueOf(10),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(20),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                Map.of()));

    assertThat(result.plannedEfficiency()).isEqualByComparingTo("0");
    assertThat(result.actualEfficiency()).isEqualByComparingTo("0");
    assertThat(result.warnings()).contains("NO_WORKING_HOURS", "NO_OUTPUT");
  }

  @Test
  void emitsEmptyInputWarningWhenRowHasNoValues() {
    EfficiencyCalculationResult result =
        service.calculate(
            new EfficiencyCalculationInput(
                "line-1",
                LocalDate.of(2026, 5, 1),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of()));

    assertThat(result.warnings()).contains("EMPTY_LINE_INPUT");
  }
}

