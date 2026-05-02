package com.garmentline.operations.calculations.incentives;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentline.operations.calculations.loader.CalculationRuleLoaderService;
import com.garmentline.operations.calculations.model.EfficiencyCalculationResult;
import com.garmentline.operations.calculations.model.IncentiveCalculationResult;
import com.garmentline.operations.calculations.validation.CalculationRuleValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncentiveCalculationServiceTest {

  private IncentiveCalculationService service;

  @BeforeEach
  void setUp() {
    CalculationRuleLoaderService loader =
        new CalculationRuleLoaderService(new CalculationRuleValidator());
    loader.initialize();
    service = new IncentiveCalculationService(loader);
  }

  @Test
  void selectsCorrectLadderBandForActualEfficiency() {
    EfficiencyCalculationResult metrics =
        new EfficiencyCalculationResult(
            "line-1",
            LocalDate.of(2026, 5, 1),
            "Shift A",
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.valueOf(90),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.valueOf(77.40),
            BigDecimal.valueOf(0.86),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            "efficiency-v1",
            1,
            null,
            0,
            List.of(),
            Map.of());

    IncentiveCalculationResult incentive = service.calculate(metrics);

    assertThat(incentive.incentiveAmount()).isEqualByComparingTo("244");
    assertThat(incentive.incentiveBandLabel()).contains("10th - 12th Day");
  }
}
