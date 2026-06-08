package com.garmentline.operations.calculations.model;

public record CalculationExecutionResult(
    EfficiencyCalculationInput input,
    EfficiencyCalculationResult metrics,
    IncentiveCalculationResult incentive,
    String metricRecordId,
    String incentiveRecordId) {
}

