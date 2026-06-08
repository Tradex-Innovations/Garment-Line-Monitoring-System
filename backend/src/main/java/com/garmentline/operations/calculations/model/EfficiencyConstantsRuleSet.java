package com.garmentline.operations.calculations.model;

import java.math.BigDecimal;
import java.util.Map;

public record EfficiencyConstantsRuleSet(
    String ruleSetId,
    String description,
    int version,
    int calculationScale,
    String calculationRoundingMode,
    Map<String, BigDecimal> numericConstants) {
}

