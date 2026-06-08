package com.garmentline.operations.calculations.reports;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record LineMetricReportRow(
    String id,
    String productionLineId,
    String lineCode,
    String lineName,
    String productionDate,
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
    List<String> warnings,
    String formulaRuleSetId,
    Integer formulaRuleVersion,
    String linkedIncentiveRecordId,
    BigDecimal linkedIncentiveAmount,
    String linkedIncentiveBand,
    String incentiveRuleSetId,
    Integer incentiveRuleVersion,
    Map<String, Object> latestAuditSnapshot) {
}

