package com.garmentline.operations.calculations.loader;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentline.operations.calculations.validation.CalculationRuleValidator;
import org.junit.jupiter.api.Test;

class CalculationRuleLoaderServiceTest {

  @Test
  void loadsYamlRuleSetsFromDedicatedFolder() {
    CalculationRuleLoaderService loader =
        new CalculationRuleLoaderService(new CalculationRuleValidator());

    loader.initialize();

    assertThat(loader.getFormulaRuleSet().ruleSetId()).isEqualTo("efficiency-v1");
    assertThat(loader.getIncentiveLadderRuleSet().bands()).hasSizeGreaterThan(30);
    assertThat(loader.listCatalogEntries()).extracting("ruleType").contains("efficiency", "incentive-ladder");
  }
}

