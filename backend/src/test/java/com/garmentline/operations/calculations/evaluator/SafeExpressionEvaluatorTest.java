package com.garmentline.operations.calculations.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeExpressionEvaluatorTest {

  private final SafeExpressionEvaluator evaluator = new SafeExpressionEvaluator();

  @Test
  void evaluatesArithmeticExpressionsDeterministically() {
    Map<String, BigDecimal> variables = new LinkedHashMap<>();
    variables.put("planned_mo", BigDecimal.valueOf(10));
    variables.put("planned_hel", BigDecimal.valueOf(2));

    BigDecimal value =
        evaluator.evaluate(
            "(planned_mo + planned_hel) * 2",
            variables,
            new ArrayList<>(),
            BigDecimal.ZERO,
            8,
            RoundingMode.HALF_UP);

    assertThat(value).isEqualByComparingTo("24");
  }

  @Test
  void protectsDivideByZeroAndEmitsWarning() {
    Map<String, BigDecimal> variables = new LinkedHashMap<>();
    variables.put("actual_sah", BigDecimal.valueOf(25));
    variables.put("clock_hours", BigDecimal.ZERO);
    List<String> warnings = new ArrayList<>();

    BigDecimal value =
        evaluator.evaluate(
            "safe_divide(actual_sah, clock_hours)",
            variables,
            warnings,
            BigDecimal.ZERO,
            8,
            RoundingMode.HALF_UP);

    assertThat(value).isEqualByComparingTo("0");
    assertThat(warnings).contains("DIVIDE_BY_ZERO_PROTECTED");
  }
}

