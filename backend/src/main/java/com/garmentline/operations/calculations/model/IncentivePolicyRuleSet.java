package com.garmentline.operations.calculations.model;

import java.util.List;

public record IncentivePolicyRuleSet(
    String ruleSetId,
    String description,
    int version,
    String basis,
    RoundingSettings rounding,
    PayoutRules payoutRules,
    List<String> flags) {

  public record RoundingSettings(int scale, String mode) {
  }

  public record PayoutRules(
      boolean allowZeroWhenNoCadre,
      boolean allowZeroWhenNoActualPcs,
      boolean produceWarningWhenClockHoursZero) {
  }
}

