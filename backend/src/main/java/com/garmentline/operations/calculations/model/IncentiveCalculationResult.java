package com.garmentline.operations.calculations.model;

import java.math.BigDecimal;
import java.util.List;

public record IncentiveCalculationResult(
    String basisMetric,
    BigDecimal basisValue,
    String incentiveBandLabel,
    BigDecimal incentiveAmount,
    String incentiveRuleSetId,
    int incentiveRuleVersion,
    List<String> warnings) {
}

