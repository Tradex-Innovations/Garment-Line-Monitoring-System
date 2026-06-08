package com.garmentline.operations.calculations.engine;

import com.garmentline.operations.calculations.evaluator.SafeExpressionEvaluator;
import com.garmentline.operations.calculations.loader.CalculationRuleLoaderService;
import com.garmentline.operations.calculations.model.CalculationWarning;
import com.garmentline.operations.calculations.model.EfficiencyCalculationInput;
import com.garmentline.operations.calculations.model.EfficiencyCalculationResult;
import com.garmentline.operations.calculations.model.FormulaDefinition;
import com.garmentline.operations.calculations.model.FormulaRuleSet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EfficiencyCalculationService {

  private final CalculationRuleLoaderService ruleLoaderService;
  private final SafeExpressionEvaluator evaluator = new SafeExpressionEvaluator();

  public EfficiencyCalculationService(CalculationRuleLoaderService ruleLoaderService) {
    this.ruleLoaderService = ruleLoaderService;
  }

  public EfficiencyCalculationResult calculate(EfficiencyCalculationInput input) {
    FormulaRuleSet formulaRuleSet = ruleLoaderService.getFormulaRuleSet();
    int scale = ruleLoaderService.getConstantsRuleSet().calculationScale();
    RoundingMode roundingMode =
        RoundingMode.valueOf(
            ruleLoaderService
                .getConstantsRuleSet()
                .calculationRoundingMode()
                .toUpperCase(Locale.ROOT));

    Map<String, BigDecimal> context = new LinkedHashMap<>();
    putInput(context, "planned_mo", input.plannedMo());
    putInput(context, "planned_hel", input.plannedHel());
    putInput(context, "actual_mo", input.actualMo());
    putInput(context, "actual_hel", input.actualHel());
    putInput(context, "team_members", input.teamMembers());
    putInput(context, "working_hours", input.workingHours());
    putInput(context, "smv", input.smv());
    putInput(context, "planned_pcs", input.plannedPcs());
    putInput(context, "forecast_pcs", input.forecastPcs());
    putInput(context, "actual_pcs", input.actualPcs());
    putInput(context, "lost_time_minutes", input.lostTimeMinutes());

    if (ruleLoaderService.getConstantsRuleSet().numericConstants() != null) {
      context.putAll(ruleLoaderService.getConstantsRuleSet().numericConstants());
    }

    LinkedHashSet<String> warningSet = new LinkedHashSet<>();
    if (input.isEffectivelyEmpty()) {
      warningSet.add(CalculationWarning.EMPTY_LINE_INPUT.name());
    }
    if (valueOrZero(input.workingHours()).compareTo(BigDecimal.ZERO) == 0) {
      warningSet.add(CalculationWarning.NO_WORKING_HOURS.name());
    }
    if (valueOrZero(input.actualPcs()).compareTo(BigDecimal.ZERO) == 0) {
      warningSet.add(CalculationWarning.NO_OUTPUT.name());
    }

    Map<String, Object> formulaOutputs = new LinkedHashMap<>();
    for (Map.Entry<String, FormulaDefinition> entry : formulaRuleSet.formulas().entrySet()) {
      List<String> expressionWarnings = new ArrayList<>();
      BigDecimal value =
          evaluator.evaluate(
              entry.getValue().expression(),
              context,
              expressionWarnings,
              formulaRuleSet.safeDivideDefault() == null
                  ? BigDecimal.ZERO
                  : formulaRuleSet.safeDivideDefault(),
              scale,
              roundingMode);
      warningSet.addAll(expressionWarnings);
      context.put(entry.getKey(), value);
      context.put(toSnakeCase(entry.getKey()), value);
      formulaOutputs.put(entry.getKey(), value);
    }

    BigDecimal plannedCadreTotal = valueOrZero(context.get("planned_cadre_total"));
    BigDecimal actualCadreTotal = valueOrZero(context.get("actual_cadre_total"));
    BigDecimal clockHours = valueOrZero(context.get("clock_hours"));
    if (plannedCadreTotal.compareTo(BigDecimal.ZERO) == 0) {
      warningSet.add(CalculationWarning.NO_PLANNED_CADRE.name());
    }
    if (actualCadreTotal.compareTo(BigDecimal.ZERO) == 0) {
      warningSet.add(CalculationWarning.NO_ACTUAL_CADRE.name());
    }
    if (clockHours.compareTo(BigDecimal.ZERO) == 0) {
      warningSet.add(CalculationWarning.NO_WORKING_HOURS.name());
    }

    Map<String, Object> inputSnapshot = new LinkedHashMap<>();
    inputSnapshot.put("productionLineId", input.productionLineId());
    inputSnapshot.put("productionDate", input.productionDate());
    inputSnapshot.put("shiftCode", input.shiftCode());
    inputSnapshot.put("plannedMo", valueOrZero(input.plannedMo()));
    inputSnapshot.put("plannedHel", valueOrZero(input.plannedHel()));
    inputSnapshot.put("actualMo", valueOrZero(input.actualMo()));
    inputSnapshot.put("actualHel", valueOrZero(input.actualHel()));
    inputSnapshot.put("teamMembers", valueOrZero(input.teamMembers()));
    inputSnapshot.put("workingHours", valueOrZero(input.workingHours()));
    inputSnapshot.put("smv", valueOrZero(input.smv()));
    inputSnapshot.put("plannedPcs", valueOrZero(input.plannedPcs()));
    inputSnapshot.put("forecastPcs", valueOrZero(input.forecastPcs()));
    inputSnapshot.put("actualPcs", valueOrZero(input.actualPcs()));
    inputSnapshot.put("lostTimeMinutes", valueOrZero(input.lostTimeMinutes()));
    inputSnapshot.put("remarks", input.remarks());
    inputSnapshot.put("sourceMetadata", input.sourceMetadata());

    Map<String, Object> debugSnapshot = new LinkedHashMap<>();
    debugSnapshot.put("inputs", inputSnapshot);
    debugSnapshot.put("outputs", formulaOutputs);
    debugSnapshot.put("warnings", new ArrayList<>(warningSet));
    debugSnapshot.put("formulaRuleSetId", formulaRuleSet.ruleSetId());
    debugSnapshot.put("formulaRuleVersion", formulaRuleSet.version());

    return new EfficiencyCalculationResult(
        input.productionLineId(),
        input.productionDate(),
        input.shiftCode(),
        plannedCadreTotal,
        actualCadreTotal,
        clockHours,
        valueOrZero(context.get("planned_sah")),
        valueOrZero(context.get("planned_efficiency")),
        valueOrZero(context.get("forecast_sah")),
        valueOrZero(context.get("forecast_efficiency")),
        valueOrZero(context.get("actual_sah")),
        valueOrZero(context.get("actual_efficiency")),
        valueOrZero(context.get("piece_variance")),
        valueOrZero(context.get("sah_variance")),
        BigDecimal.ZERO,
        null,
        formulaRuleSet.ruleSetId(),
        formulaRuleSet.version(),
        null,
        0,
        List.copyOf(warningSet),
        debugSnapshot);
  }

  private void putInput(Map<String, BigDecimal> context, String key, BigDecimal value) {
    context.put(key, valueOrZero(value));
  }

  private BigDecimal valueOrZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String toSnakeCase(String value) {
    return value
        .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
        .replace('-', '_')
        .toLowerCase(Locale.ROOT);
  }
}

