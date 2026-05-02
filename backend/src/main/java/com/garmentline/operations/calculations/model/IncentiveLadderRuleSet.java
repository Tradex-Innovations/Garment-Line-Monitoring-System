package com.garmentline.operations.calculations.model;

import java.util.List;

public record IncentiveLadderRuleSet(
    String ruleSetId,
    String description,
    int version,
    String currency,
    List<IncentiveBand> bands) {
}

