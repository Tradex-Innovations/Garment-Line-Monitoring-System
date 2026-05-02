package com.garmentline.operations.calculations.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

public record FormulaRuleSet(
    String ruleSetId,
    String description,
    int version,
    BigDecimal safeDivideDefault,
    LinkedHashMap<String, FormulaDefinition> formulas) {
}

