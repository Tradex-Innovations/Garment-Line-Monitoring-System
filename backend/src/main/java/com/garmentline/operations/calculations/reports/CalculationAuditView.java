package com.garmentline.operations.calculations.reports;

import java.util.List;
import java.util.Map;

public record CalculationAuditView(
    String id,
    String metricRecordId,
    String incentiveRecordId,
    Map<String, Object> inputPayload,
    Map<String, Object> outputPayload,
    List<String> warnings,
    String formulaRuleSetId,
    Integer formulaRuleVersion,
    String incentiveRuleSetId,
    Integer incentiveRuleVersion,
    String createdAt) {
}

