package com.garmentline.operations.calculations.incentives;

import com.garmentline.operations.calculations.loader.CalculationRuleLoaderService;
import com.garmentline.operations.calculations.model.CalculationWarning;
import com.garmentline.operations.calculations.model.EfficiencyCalculationResult;
import com.garmentline.operations.calculations.model.IncentiveBand;
import com.garmentline.operations.calculations.model.IncentiveCalculationResult;
import com.garmentline.operations.calculations.model.IncentivePolicyRuleSet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class IncentiveCalculationService {

  private final CalculationRuleLoaderService ruleLoaderService;

  public IncentiveCalculationService(CalculationRuleLoaderService ruleLoaderService) {
    this.ruleLoaderService = ruleLoaderService;
  }

  public IncentiveCalculationResult calculate(EfficiencyCalculationResult metrics) {
    IncentivePolicyRuleSet policy = ruleLoaderService.getIncentivePolicyRuleSet();
    RoundingMode roundingMode =
        RoundingMode.valueOf(policy.rounding().mode().toUpperCase(Locale.ROOT));
    BigDecimal basisValue =
        metrics.actualEfficiency() == null
            ? BigDecimal.ZERO
            : metrics.actualEfficiency().setScale(policy.rounding().scale(), roundingMode);

    LinkedHashSet<String> warnings = new LinkedHashSet<>(metrics.warnings());
    if (metrics.clockHours() == null || metrics.clockHours().compareTo(BigDecimal.ZERO) == 0) {
      if (policy.payoutRules().produceWarningWhenClockHoursZero()) {
        warnings.add(CalculationWarning.NO_WORKING_HOURS.name());
      }
    }

    boolean shouldZero =
        (policy.payoutRules().allowZeroWhenNoCadre()
                && metrics.actualCadreTotal().compareTo(BigDecimal.ZERO) == 0)
            || (policy.payoutRules().allowZeroWhenNoActualPcs()
                && metrics.actualSah().compareTo(BigDecimal.ZERO) == 0);

    IncentiveBand matchedBand = findBand(basisValue);
    BigDecimal incentiveAmount =
        shouldZero || matchedBand == null ? BigDecimal.ZERO : matchedBand.incentiveAmount();

    return new IncentiveCalculationResult(
        policy.basis(),
        basisValue,
        matchedBand == null ? null : matchedBand.label(),
        incentiveAmount,
        ruleLoaderService.getIncentiveLadderRuleSet().ruleSetId(),
        ruleLoaderService.getIncentiveLadderRuleSet().version(),
        List.copyOf(new ArrayList<>(warnings)));
  }

  private IncentiveBand findBand(BigDecimal basisValue) {
    for (IncentiveBand band : ruleLoaderService.getIncentiveLadderRuleSet().bands()) {
      if (basisValue.compareTo(band.minEfficiency()) >= 0
          && basisValue.compareTo(band.maxEfficiencyExclusive()) < 0) {
        return band;
      }
    }
    return null;
  }
}

