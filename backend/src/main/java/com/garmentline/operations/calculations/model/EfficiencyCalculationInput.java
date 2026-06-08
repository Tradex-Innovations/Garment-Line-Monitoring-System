package com.garmentline.operations.calculations.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record EfficiencyCalculationInput(
    String productionLineId,
    LocalDate productionDate,
    String shiftCode,
    BigDecimal plannedMo,
    BigDecimal plannedHel,
    BigDecimal actualMo,
    BigDecimal actualHel,
    BigDecimal teamMembers,
    BigDecimal workingHours,
    BigDecimal smv,
    BigDecimal plannedPcs,
    BigDecimal forecastPcs,
    BigDecimal actualPcs,
    String remarks,
    BigDecimal lostTimeMinutes,
    Map<String, Object> sourceMetadata) {

  public static final BigDecimal ZERO = BigDecimal.ZERO;

  public BigDecimal numberOrZero(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  public boolean isEffectivelyEmpty() {
    return numberOrZero(plannedMo).compareTo(ZERO) == 0
        && numberOrZero(plannedHel).compareTo(ZERO) == 0
        && numberOrZero(actualMo).compareTo(ZERO) == 0
        && numberOrZero(actualHel).compareTo(ZERO) == 0
        && numberOrZero(teamMembers).compareTo(ZERO) == 0
        && numberOrZero(workingHours).compareTo(ZERO) == 0
        && numberOrZero(smv).compareTo(ZERO) == 0
        && numberOrZero(plannedPcs).compareTo(ZERO) == 0
        && numberOrZero(forecastPcs).compareTo(ZERO) == 0
        && numberOrZero(actualPcs).compareTo(ZERO) == 0
        && numberOrZero(lostTimeMinutes).compareTo(ZERO) == 0;
  }
}

