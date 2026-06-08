package com.garmentline.operations.calculations.model;

public record CalculationRuleCatalogEntry(
    String ruleType,
    String ruleSetId,
    int version,
    String description,
    String sourcePath,
    boolean active,
    String checksum,
    String rawYaml) {
}

