package com.tradex.unionnorth.setup;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

class PayrollCalculatorTest {
    private Map<String, Object> salary() {
        return new HashMap<>(
                Map.of("payBasis", "MONTHLY", "basicSalary", "10000", "bra1", "100", "bra2", "50"));
    }

    private Map<String, Object> policy() {
        return new HashMap<>(
                Map.of(
                        "monthlyProration",
                        "FULL",
                        "dayDivisor",
                        "CALENDAR_DAYS",
                        "braTreatment",
                        "FIXED",
                        "rounding",
                        "HALF_UP",
                        "basicTaxable",
                        true,
                        "basicStatutoryEligible",
                        true));
    }

    private SetupStore.Item item(String category, String method, String value, Object... extra) {
        var data =
                new HashMap<String, Object>(
                        Map.of("category", category, "method", method, "value", value));
        for (int i = 0; i < extra.length; i += 2) data.put(extra[i].toString(), extra[i + 1]);
        return new SetupStore.Item(
                UUID.randomUUID(),
                "COMPONENT",
                "TEST",
                "Synthetic test component",
                true,
                0,
                LocalDate.of(2025, 1, 1),
                data);
    }

    private PayrollCalculator.Result calculate(
            Map<String, Object> salary,
            Map<String, Object> policy,
            List<SetupStore.Item> components,
            Map<String, BigDecimal> inputs,
            Map<String, BigDecimal> amounts) {
        return PayrollCalculator.calculate(
                salary, policy, components, List.of(), Map.of(), inputs, amounts, 30);
    }

    @Test
    void addsEarningsBeforeDeductionsAndKeepsEmployerCostSeparate() {
        var fixed = item("EARNING", "FIXED", "500", "taxable", true);
        var percent =
                item("EARNING", "PERCENTAGE", "10", "basis", "BASIC", "statutoryEligible", true);
        var variable = item("DEDUCTION", "INPUT", "0");
        var deduction = item("DEDUCTION", "PERCENTAGE", "5", "basis", "GROSS");
        var employer = item("EMPLOYER_CONTRIBUTION", "PERCENTAGE", "3", "basis", "STATUTORY");
        var result =
                calculate(
                        salary(),
                        policy(),
                        List.of(deduction, employer, fixed, percent, variable),
                        Map.of(),
                        Map.of(variable.id().toString(), new BigDecimal("25")));
        assertThat(result.basic()).isEqualTo("10150.00");
        assertThat(result.gross()).isEqualTo("11665.00");
        assertThat(result.taxable()).isEqualTo("10650.00");
        assertThat(result.statutory()).isEqualTo("11165.00");
        assertThat(result.deductions()).isEqualTo("608.25");
        assertThat(result.net()).isEqualTo("11056.75");
        assertThat(result.employerContributions()).isEqualTo("334.95");
        assertThat(result.employerCost()).isEqualTo("11999.95");
    }

    @Test
    void matchesTheDocumentedFullEngineSyntheticScenario() {
        var fixedEarning =
                item("EARNING", "FIXED", "500", "taxable", true, "statutoryEligible", false);
        var percentageEarning =
                item(
                        "EARNING",
                        "PERCENTAGE",
                        "10",
                        "basis",
                        "BASIC",
                        "taxable",
                        false,
                        "statutoryEligible",
                        true);
        var overtime =
                item(
                        "EARNING",
                        "FORMULA",
                        "0",
                        "formula",
                        "basicRate / 25 / 8 * 1.5 * overtimeHours",
                        "eligibility",
                        "OVERTIME",
                        "taxable",
                        true,
                        "statutoryEligible",
                        true);
        var enteredEarning = item("EARNING", "INPUT", "0");
        var fixedDeduction = item("DEDUCTION", "FIXED", "100");
        var percentageDeduction = item("DEDUCTION", "PERCENTAGE", "5", "basis", "GROSS");
        var enteredDeduction = item("DEDUCTION", "INPUT", "0");
        var formulaDeduction =
                item("DEDUCTION", "FORMULA", "0", "formula", "max(0, gross - 12000) * 0.1");
        var employer = item("EMPLOYER_CONTRIBUTION", "PERCENTAGE", "3", "basis", "STATUTORY");
        var percentageRule =
                item(
                        "DEDUCTION",
                        "PERCENTAGE",
                        "0",
                        "basis",
                        "STATUTORY",
                        "employeeRate",
                        8,
                        "employerRate",
                        12,
                        "taxRule",
                        false);
        var tieredRule =
                item(
                        "DEDUCTION",
                        "TIERED",
                        "0",
                        "basis",
                        "TAXABLE",
                        "employeeRate",
                        0,
                        "employerRate",
                        0,
                        "taxRule",
                        true,
                        "bands",
                        List.of(
                                Map.of("upperBound", 5000, "rate", 0),
                                Map.of("upperBound", 10000, "rate", 10),
                                Map.of("rate", 20)));
        var fixedRule =
                item(
                        "DEDUCTION",
                        "FIXED",
                        "0",
                        "basis",
                        "BASIC",
                        "employeeRate",
                        7,
                        "employerRate",
                        11,
                        "taxRule",
                        false);
        var financial = salary();
        financial.put("overtimePaid", true);

        var result =
                PayrollCalculator.calculate(
                        financial,
                        policy(),
                        List.of(
                                fixedEarning,
                                percentageEarning,
                                overtime,
                                enteredEarning,
                                fixedDeduction,
                                percentageDeduction,
                                enteredDeduction,
                                formulaDeduction,
                                employer),
                        List.of(percentageRule, tieredRule, fixedRule),
                        Map.of(),
                        Map.of("overtimeHours", new BigDecimal("10")),
                        Map.of(
                                enteredEarning.id().toString(), new BigDecimal("200"),
                                enteredDeduction.id().toString(), new BigDecimal("25")),
                        30);

        assertThat(result.basic()).isEqualTo("10150.00");
        assertThat(result.gross()).isEqualTo("12615.00");
        assertThat(result.taxable()).isEqualTo("11400.00");
        assertThat(result.statutory()).isEqualTo("11915.00");
        assertThat(result.deductions()).isEqualTo("2557.45");
        assertThat(result.net()).isEqualTo("10057.55");
        assertThat(result.employerContributions()).isEqualTo("1798.25");
        assertThat(result.employerCost()).isEqualTo("14413.25");
    }

    @Test
    void monthlyProrationRequiresAnExplicitInputAndAppliesConfiguredBraTreatment() {
        var p = policy();
        p.put("monthlyProration", "PAID_DAYS");
        p.put("braTreatment", "PRORATE_WITH_BASIC");
        assertThatThrownBy(() -> calculate(salary(), p, List.of(), Map.of(), Map.of()))
                .hasMessageContaining("paidDays");
        assertThat(
                        calculate(
                                        salary(),
                                        p,
                                        List.of(),
                                        Map.of("paidDays", new BigDecimal("15")),
                                        Map.of())
                                .basic())
                .isEqualTo("5075.00");
        p.put("braTreatment", "FIXED");
        assertThat(
                        calculate(
                                        salary(),
                                        p,
                                        List.of(),
                                        Map.of("paidDays", new BigDecimal("15")),
                                        Map.of())
                                .basic())
                .isEqualTo("5150.00");
        p.put("dayDivisor", "FIXED");
        p.put("fixedDays", "25");
        assertThat(
                        calculate(
                                        salary(),
                                        p,
                                        List.of(),
                                        Map.of("paidDays", new BigDecimal("20")),
                                        Map.of())
                                .basic())
                .isEqualTo("8150.00");
        assertThatThrownBy(
                        () ->
                                calculate(
                                        salary(),
                                        p,
                                        List.of(),
                                        Map.of("paidDays", new BigDecimal("26")),
                                        Map.of()))
                .hasMessageContaining("divisor");
    }

    @Test
    void dailyHourlyAndZeroPayAreExplicit() {
        var s = salary();
        s.put("payBasis", "DAILY");
        s.put("basicSalary", "125.50");
        assertThat(
                        calculate(
                                        s,
                                        policy(),
                                        List.of(),
                                        Map.of("paidDays", new BigDecimal("2.5")),
                                        Map.of())
                                .basic())
                .isEqualTo("463.75");
        s.put("payBasis", "HOURLY");
        assertThat(
                        calculate(
                                        s,
                                        policy(),
                                        List.of(),
                                        Map.of("paidHours", new BigDecimal("2.5")),
                                        Map.of())
                                .basic())
                .isEqualTo("463.75");
        s.put("bra1", 0);
        s.put("bra2", 0);
        assertThat(
                        calculate(
                                        s,
                                        policy(),
                                        List.of(),
                                        Map.of("paidHours", BigDecimal.ZERO),
                                        Map.of())
                                .net())
                .isEqualTo("0.00");
        assertThatThrownBy(
                        () ->
                                calculate(
                                        s,
                                        policy(),
                                        List.of(),
                                        Map.of("paidHours", new BigDecimal("721")),
                                        Map.of()))
                .hasMessageContaining("period length");
    }

    @Test
    void decimalRoundingHappensPerLineAndHonorsPolicy() {
        var s = salary();
        s.put("basicSalary", "1.005");
        s.put("bra1", "0.005");
        s.put("bra2", "0.005");
        var p = policy();
        assertThat(calculate(s, p, List.of(), Map.of(), Map.of()).gross()).isEqualTo("1.03");
        p.put("rounding", "HALF_EVEN");
        assertThat(calculate(s, p, List.of(), Map.of(), Map.of()).gross()).isEqualTo("1.00");
        p.put("rounding", "DOWN");
        assertThat(calculate(s, p, List.of(), Map.of(), Map.of()).gross()).isEqualTo("1.00");
    }

    @Test
    void eligibilityControlsInputsAndFormulasWithoutInventingAttendance() {
        var overtime =
                item(
                        "EARNING",
                        "FORMULA",
                        "0",
                        "formula",
                        "basicRate / 25 / 8 * 1.5 * overtimeHours",
                        "eligibility",
                        "OVERTIME");
        var s = salary();
        assertThat(PayrollCalculator.requiredInputs(s, policy(), List.of(overtime))).isEmpty();
        assertThat(calculate(s, policy(), List.of(overtime), Map.of(), Map.of()).gross())
                .isEqualTo("10150.00");
        s.put("overtimePaid", true);
        assertThat(PayrollCalculator.requiredInputs(s, policy(), List.of(overtime)))
                .containsExactly("overtimeHours");
        assertThatThrownBy(() -> calculate(s, policy(), List.of(overtime), Map.of(), Map.of()))
                .hasMessageContaining("overtimeHours");
        assertThat(
                        calculate(
                                        s,
                                        policy(),
                                        List.of(overtime),
                                        Map.of("overtimeHours", new BigDecimal("10")),
                                        Map.of())
                                .gross())
                .isEqualTo("10900.00");
    }

    @Test
    void progressiveBandsTaxExemptionAndFixedEmployerAmountsUseOnlyConfiguredValues() {
        // Deliberately synthetic rates, not legal/tax recommendations.
        var tiered =
                item(
                        "DEDUCTION",
                        "TIERED",
                        "0",
                        "basis",
                        "TAXABLE",
                        "employeeRate",
                        0,
                        "employerRate",
                        2,
                        "taxRule",
                        true,
                        "bands",
                        List.of(
                                Map.of("upperBound", 5000, "rate", 0),
                                Map.of("upperBound", 10000, "rate", 10),
                                Map.of("rate", 20)));
        var fixed =
                item(
                        "DEDUCTION",
                        "FIXED",
                        "0",
                        "basis",
                        "BASIC",
                        "employeeRate",
                        7,
                        "employerRate",
                        11);
        var result =
                PayrollCalculator.calculate(
                        salary(),
                        policy(),
                        List.of(),
                        List.of(tiered, fixed),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        30);
        assertThat(result.deductions()).isEqualTo("537.00");
        assertThat(result.employerContributions()).isEqualTo("214.00");
        var s = salary();
        s.put("taxExempted", true);
        var exempt =
                PayrollCalculator.calculate(
                        s,
                        policy(),
                        List.of(),
                        List.of(tiered, fixed),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        30);
        assertThat(exempt.deductions()).isEqualTo("7.00");
        assertThat(exempt.employerContributions()).isEqualTo("11.00");
    }

    @Test
    void recurringOverridesDoNotAffectDefaultsAndInputAmountsMustMatchExactly() {
        var fixed = item("EARNING", "FIXED", "500");
        var result =
                PayrollCalculator.calculate(
                        salary(),
                        policy(),
                        List.of(fixed),
                        List.of(),
                        Map.of(fixed.id().toString(), "50"),
                        Map.of(),
                        Map.of(),
                        30);
        assertThat(result.gross()).isEqualTo("10200.00");
        assertThat(fixed.data().get("value")).isEqualTo("500");
        var input = item("DEDUCTION", "INPUT", "0");
        assertThatThrownBy(() -> calculate(salary(), policy(), List.of(input), Map.of(), Map.of()))
                .hasMessageContaining("explicit zero");
        assertThatThrownBy(
                        () ->
                                calculate(
                                        salary(),
                                        policy(),
                                        List.of(),
                                        Map.of(),
                                        Map.of("unknown", BigDecimal.ZERO)))
                .hasMessageContaining("applicable INPUT");
        assertThatThrownBy(
                        () ->
                                calculate(
                                        salary(),
                                        policy(),
                                        List.of(input),
                                        Map.of(),
                                        Map.of(input.id().toString(), new BigDecimal("20000"))))
                .hasMessageContaining("exceed gross");
    }

    @Test
    void rejectsUnsupportedInputsPrecisionAmountsAndCircularEarnings() {
        assertThatThrownBy(
                        () ->
                                calculate(
                                        salary(),
                                        policy(),
                                        List.of(),
                                        Map.of("secret", BigDecimal.ONE),
                                        Map.of()))
                .hasMessageContaining("Unsupported period");
        for (var value :
                List.of(
                        new BigDecimal("-1"),
                        new BigDecimal("0.00001"),
                        new BigDecimal("10000000000")))
            assertThatThrownBy(
                            () ->
                                    calculate(
                                            salary(),
                                            policy(),
                                            List.of(),
                                            Map.of("paidDays", value),
                                            Map.of()))
                    .isInstanceOf(SetupException.class);
        assertThatThrownBy(
                        () ->
                                calculate(
                                        salary(),
                                        policy(),
                                        List.of(
                                                item(
                                                        "EARNING",
                                                        "PERCENTAGE",
                                                        "10",
                                                        "basis",
                                                        "GROSS")),
                                        Map.of(),
                                        Map.of()))
                .hasMessageContaining("cannot depend");
        assertThatThrownBy(
                        () ->
                                calculate(
                                        salary(),
                                        policy(),
                                        List.of(item("EARNING", "FORMULA", "0", "formula", "-1")),
                                        Map.of(),
                                        Map.of()))
                .hasMessageContaining("negative");
    }

    @Test
    void expressionLanguageIsDecimalBoundedAndCannotExecuteCode() {
        assertThat(
                        PayrollFormula.parse(
                                        "max(0, (basic - 5000) * 0.10) + min(bra1, bra2)", false)
                                .evaluate(
                                        Map.of(
                                                "basic",
                                                new BigDecimal("10000"),
                                                "bra1",
                                                new BigDecimal("20"),
                                                "bra2",
                                                new BigDecimal("10"))))
                .isEqualByComparingTo("510");
        assertThat(PayrollFormula.parse("0.1 + 0.2", true).evaluate(Map.of()))
                .isEqualByComparingTo("0.3");
        for (String expression :
                List.of(
                        "System.exit(0)",
                        "window.alert(1)",
                        "1; 2",
                        "unknown + 1",
                        "min(1)",
                        "1e10",
                        "1..2",
                        "",
                        "1".repeat(501)))
            assertThatThrownBy(() -> PayrollFormula.parse(expression, false))
                    .isInstanceOf(SetupException.class);
        assertThatThrownBy(() -> PayrollFormula.parse("gross * .1", true))
                .hasMessageContaining("circular");
        assertThatThrownBy(() -> PayrollFormula.parse("1 / 0", false).evaluate(Map.of()))
                .hasMessageContaining("zero");
        assertThatThrownBy(() -> PayrollFormula.parse("paidHours * 2", false).evaluate(Map.of()))
                .hasMessageContaining("paidHours");
        assertThatThrownBy(() -> PayrollFormula.parse("-".repeat(40) + "1", false))
                .hasMessageContaining("nesting");
        assertThatThrownBy(() -> PayrollFormula.parse("999999999999 * 2", false).evaluate(Map.of()))
                .hasMessageContaining("supported amount");
    }
}
