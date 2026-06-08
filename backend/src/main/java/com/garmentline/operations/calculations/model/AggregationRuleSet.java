package com.garmentline.operations.calculations.model;

import java.math.BigDecimal;

public record AggregationRuleSet(
    String ruleSetId,
    String description,
    int version,
    boolean useRealRowTotals,
    boolean useConfiguredWorkingHoursForTotals,
    BigDecimal configuredWorkingHours,
    String rollupStrategy) {
}

