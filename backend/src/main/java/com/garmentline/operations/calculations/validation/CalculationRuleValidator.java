package com.garmentline.operations.calculations.validation;

import com.garmentline.operations.calculations.evaluator.SafeExpressionEvaluator;
import com.garmentline.operations.calculations.model.AggregationRuleSet;
import com.garmentline.operations.calculations.model.EfficiencyConstantsRuleSet;
import com.garmentline.operations.calculations.model.FormulaRuleSet;
import com.garmentline.operations.calculations.model.IncentiveBand;
import com.garmentline.operations.calculations.model.IncentiveLadderRuleSet;
import com.garmentline.operations.calculations.model.IncentivePolicyRuleSet;
import com.garmentline.operations.support.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CalculationRuleValidator {

  private static final List<String> BASE_VARIABLES =
      List.of(
          "planned_mo",
          "planned_hel",
          "actual_mo",
          "actual_hel",
          "team_members",
          "working_hours",
          "smv",
          "planned_pcs",
          "forecast_pcs",
          "actual_pcs",
          "lost_time_minutes");

  private final SafeExpressionEvaluator evaluator = new SafeExpressionEvaluator();

  public void validate(
      FormulaRuleSet formulaRuleSet,
      EfficiencyConstantsRuleSet constantsRuleSet,
      IncentiveLadderRuleSet ladderRuleSet,
      IncentivePolicyRuleSet policyRuleSet,
      AggregationRuleSet aggregationRuleSet) {
    requireText(formulaRuleSet.ruleSetId(), "Formula rule set id is required.");
    requireText(constantsRuleSet.ruleSetId(), "Efficiency constants rule set id is required.");
    requireText(ladderRuleSet.ruleSetId(), "Incentive ladder rule set id is required.");
    requireText(policyRuleSet.ruleSetId(), "Incentive policy rule set id is required.");
    requireText(aggregationRuleSet.ruleSetId(), "Aggregation rule set id is required.");

    if (formulaRuleSet.formulas() == null || formulaRuleSet.formulas().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Formula rule set must define at least one formula.");
    }

    validateFormulas(formulaRuleSet, constantsRuleSet);
    validateIncentiveBands(ladderRuleSet);
    validatePolicy(policyRuleSet);
  }

  private void validateFormulas(
      FormulaRuleSet formulaRuleSet, EfficiencyConstantsRuleSet constantsRuleSet) {
    Map<String, BigDecimal> context = new LinkedHashMap<>();
    BASE_VARIABLES.forEach(variable -> context.put(variable, BigDecimal.ONE));
    if (constantsRuleSet.numericConstants() != null) {
      context.putAll(constantsRuleSet.numericConstants());
    }

    List<String> warnings = new ArrayList<>();
    formulaRuleSet
        .formulas()
        .forEach(
            (key, definition) -> {
              requireText(key, "Formula keys must not be blank.");
              requireText(
                  definition == null ? null : definition.expression(),
                  "Formula expression is required for " + key + ".");
              BigDecimal value =
                  evaluator.evaluate(
                      definition.expression(),
                      context,
                      warnings,
                      formulaRuleSet.safeDivideDefault() == null
                          ? BigDecimal.ZERO
                          : formulaRuleSet.safeDivideDefault(),
                      constantsRuleSet.calculationScale(),
                      RoundingMode.valueOf(
                          constantsRuleSet.calculationRoundingMode().toUpperCase(Locale.ROOT)));
              context.put(key, value);
              context.put(toSnakeCase(key), value);
            });
  }

  private void validateIncentiveBands(IncentiveLadderRuleSet ladderRuleSet) {
    if (ladderRuleSet.bands() == null || ladderRuleSet.bands().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Incentive ladder must define at least one band.");
    }

    BigDecimal previousMinimum = null;
    for (IncentiveBand band : ladderRuleSet.bands()) {
      if (band.minEfficiency() == null || band.maxEfficiencyExclusive() == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Every incentive band must define min and max values.");
      }
      if (band.incentiveAmount() == null || band.incentiveAmount().compareTo(BigDecimal.ZERO) < 0) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Every incentive band must define a non-negative amount.");
      }
      if (band.maxEfficiencyExclusive().compareTo(band.minEfficiency()) <= 0) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Incentive band max must be greater than min.");
      }
      if (previousMinimum != null && band.minEfficiency().compareTo(previousMinimum) < 0) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Incentive bands must be sorted by minEfficiency.");
      }
      previousMinimum = band.minEfficiency();
    }
  }

  private void validatePolicy(IncentivePolicyRuleSet policyRuleSet) {
    requireText(policyRuleSet.basis(), "Incentive policy basis is required.");
    if (policyRuleSet.rounding() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Incentive policy rounding settings are required.");
    }
    if (policyRuleSet.rounding().scale() < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Rounding scale must be zero or greater.");
    }
    requireText(policyRuleSet.rounding().mode(), "Rounding mode is required.");
  }

  private void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, message);
    }
  }

  private String toSnakeCase(String value) {
    return value
        .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
        .replace('-', '_')
        .toLowerCase(Locale.ROOT);
  }
}

