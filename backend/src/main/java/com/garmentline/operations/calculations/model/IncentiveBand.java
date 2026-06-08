package com.garmentline.operations.calculations.model;

import java.math.BigDecimal;

public record IncentiveBand(
    String label,
    BigDecimal minEfficiency,
    BigDecimal maxEfficiencyExclusive,
    BigDecimal incentiveAmount) {
}

