package com.garmentline.operations.calculations.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EfficiencyCalculationResult(
    String productionLineId,
    LocalDate productionDate,
    String shiftCode,
    BigDecimal plannedCadreTotal,
    BigDecimal actualCadreTotal,
    BigDecimal clockHours,
    BigDecimal plannedSah,
    BigDecimal plannedEfficiency,
    BigDecimal forecastSah,
    BigDecimal forecastEfficiency,
    BigDecimal actualSah,
    BigDecimal actualEfficiency,
    BigDecimal pieceVariance,
    BigDecimal sahVariance,
    BigDecimal incentiveAmount,
    String incentiveBand,
    String ruleSetId,
    int ruleSetVersion,
    String incentiveRuleSetId,
    int incentiveRuleVersion,
    List<String> warnings,
    Map<String, Object> debugSnapshot) {
}

