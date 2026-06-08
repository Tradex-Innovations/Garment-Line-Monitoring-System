package com.garmentline.operations.calculations.loader;

import com.garmentline.operations.calculations.model.AggregationRuleSet;
import com.garmentline.operations.calculations.model.CalculationRuleCatalogEntry;
import com.garmentline.operations.calculations.model.EfficiencyConstantsRuleSet;
import com.garmentline.operations.calculations.model.FormulaRuleSet;
import com.garmentline.operations.calculations.model.IncentiveLadderRuleSet;
import com.garmentline.operations.calculations.model.IncentivePolicyRuleSet;
import java.util.List;

public record LoadedCalculationRules(
    FormulaRuleSet formulaRuleSet,
    EfficiencyConstantsRuleSet constantsRuleSet,
    IncentiveLadderRuleSet incentiveLadderRuleSet,
    IncentivePolicyRuleSet incentivePolicyRuleSet,
    AggregationRuleSet aggregationRuleSet,
    List<CalculationRuleCatalogEntry> catalogEntries) {
}

