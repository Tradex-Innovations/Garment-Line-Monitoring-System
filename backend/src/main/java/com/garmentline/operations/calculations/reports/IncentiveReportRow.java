package com.garmentline.operations.calculations.reports;

import java.math.BigDecimal;
import java.util.List;

public record IncentiveReportRow(
    String id,
    String sourceMetricRecordId,
    String productionLineId,
    String lineCode,
    String lineName,
    String productionDate,
    String shiftCode,
    String basisMetric,
    BigDecimal basisValue,
    BigDecimal actualEfficiency,
    String incentiveBandLabel,
    BigDecimal incentiveAmount,
    String incentiveRuleSetId,
    Integer incentiveRuleVersion,
    List<String> warnings) {
}

