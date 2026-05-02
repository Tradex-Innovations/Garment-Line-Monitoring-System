package com.garmentline.operations.calculations.model;

public record CalculationRuleManifest(
    String formulaRuleSet,
    String constantsRuleSet,
    String incentiveLadderRuleSet,
    String incentivePolicyRuleSet,
    String aggregationRuleSet) {
}

