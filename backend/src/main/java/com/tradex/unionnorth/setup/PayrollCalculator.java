package com.tradex.unionnorth.setup;

import static com.tradex.unionnorth.setup.MasterDataService.*;

import java.math.*;
import java.util.*;

/** Pure calculation; all money uses decimal arithmetic and explicit per-line rounding. */
public final class PayrollCalculator {
    public static final String VERSION = "1";
    public static final Set<String> INPUTS =
            Set.of("paidDays", "paidHours", "overtimeHours", "unpaidDays", "lateMinutes");
    private static final MathContext MC = MathContext.DECIMAL128;

    public record Line(String code, String name, String category, String equation, String amount) {}

    public record Result(
            String engineVersion,
            List<Line> lines,
            String basic,
            String gross,
            String taxable,
            String statutory,
            String deductions,
            String net,
            String employerContributions,
            String employerCost,
            Map<String, String> variables,
            List<String> warnings) {}

    private PayrollCalculator() {}

    public static Set<String> requiredInputs(
            Map<String, Object> financial,
            Map<String, Object> policy,
            List<SetupStore.Item> components) {
        Set<String> required = new TreeSet<>();
        String basis = text(financial, "payBasis");
        if ("DAILY".equals(basis)
                || ("MONTHLY".equals(basis) && "PAID_DAYS".equals(policy.get("monthlyProration"))))
            required.add("paidDays");
        if ("HOURLY".equals(basis)) required.add("paidHours");
        for (var component : components) {
            if (!eligible(component.data(), financial)) continue;
            if ("FORMULA".equals(component.data().get("method"))) {
                var names =
                        PayrollFormula.parse(
                                        text(component.data(), "formula"),
                                        "EARNING".equals(component.data().get("category")))
                                .variables();
                names.stream().filter(INPUTS::contains).forEach(required::add);
            }
        }
        return required;
    }

    public static Result calculate(
            Map<String, Object> financial,
            Map<String, Object> policy,
            List<SetupStore.Item> components,
            List<SetupStore.Item> rules,
            Map<String, Object> overrides,
            Map<String, BigDecimal> inputs,
            Map<String, BigDecimal> amounts,
            long days) {
        if (inputs == null || amounts == null || !INPUTS.containsAll(inputs.keySet()))
            throw new SetupException("Unsupported period inputs.");
        inputs.values().forEach(PayrollCalculator::validInput);
        amounts.values().forEach(PayrollCalculator::validInput);
        if (days < 1 || days > 366)
            throw new SetupException("Payroll periods must contain 1–366 days.");
        RoundingMode rounding;
        try {
            rounding = RoundingMode.valueOf(text(policy, "rounding"));
        } catch (IllegalArgumentException e) {
            throw new SetupException("Configure a calculation rounding policy.");
        }
        if (!Set.of(RoundingMode.HALF_UP, RoundingMode.HALF_EVEN, RoundingMode.DOWN)
                .contains(rounding)) throw new SetupException("Unsupported rounding policy.");
        var required = requiredInputs(financial, policy, components);
        if (!inputs.keySet().containsAll(required)) {
            required.removeAll(inputs.keySet());
            throw new SetupException("Enter period inputs: " + String.join(", ", required));
        }
        if (inputs.getOrDefault("paidDays", BigDecimal.ZERO).compareTo(BigDecimal.valueOf(days)) > 0
                || inputs.getOrDefault("unpaidDays", BigDecimal.ZERO)
                                .compareTo(BigDecimal.valueOf(days))
                        > 0
                || inputs.getOrDefault("paidHours", BigDecimal.ZERO)
                                .compareTo(BigDecimal.valueOf(days * 24))
                        > 0
                || inputs.getOrDefault("overtimeHours", BigDecimal.ZERO)
                                .compareTo(BigDecimal.valueOf(days * 24))
                        > 0)
            throw new SetupException("Reported days or hours exceed the period length.");

        BigDecimal rate = number(financial, "basicSalary"), factor = BigDecimal.ONE;
        String equation;
        switch (text(financial, "payBasis")) {
            case "MONTHLY" -> {
                if ("PAID_DAYS".equals(policy.get("monthlyProration"))) {
                    BigDecimal divisor =
                            "CALENDAR_DAYS".equals(policy.get("dayDivisor"))
                                    ? BigDecimal.valueOf(days)
                                    : number(policy, "fixedDays");
                    if (divisor.signum() <= 0 || inputs.get("paidDays").compareTo(divisor) > 0)
                        throw new SetupException(
                                "Paid days must not exceed the positive monthly divisor.");
                    factor = inputs.get("paidDays").divide(divisor, MC);
                    equation = rate + " × " + inputs.get("paidDays") + " / " + divisor;
                } else if ("FULL".equals(policy.get("monthlyProration")))
                    equation = rate.toPlainString();
                else throw new SetupException("Configure monthly proration.");
            }
            case "DAILY" -> {
                factor = inputs.get("paidDays");
                equation = rate + " × " + factor + " paid days";
            }
            case "HOURLY" -> {
                factor = inputs.get("paidHours");
                equation = rate + " × " + factor + " paid hours";
            }
            default -> throw new SetupException("Unsupported pay basis.");
        }
        var lines = new ArrayList<Line>();
        BigDecimal basePay = money(rate.multiply(factor, MC), rounding);
        lines.add(new Line("BASIC", "Basic pay", "EARNING", equation, basePay.toPlainString()));
        var vars = new LinkedHashMap<String, BigDecimal>(inputs);
        vars.put("periodDays", BigDecimal.valueOf(days));
        vars.put("basicRate", rate);
        BigDecimal basic = basePay;
        for (String key : List.of("bra1", "bra2")) {
            BigDecimal value = new BigDecimal(Objects.toString(financial.get(key), "0"));
            BigDecimal braFactor =
                    "PRORATE_WITH_BASIC".equals(policy.get("braTreatment"))
                            ? factor
                            : BigDecimal.ONE;
            BigDecimal amount = money(value.multiply(braFactor, MC), rounding);
            vars.put(key, amount);
            basic = basic.add(amount);
            if (value.signum() != 0)
                lines.add(
                        new Line(
                                key.toUpperCase(Locale.ROOT),
                                key.toUpperCase(Locale.ROOT),
                                "EARNING",
                                value + " × " + braFactor,
                                amount.toPlainString()));
        }
        vars.put("basic", basic);
        BigDecimal gross = basic;
        BigDecimal taxable =
                Boolean.TRUE.equals(policy.get("basicTaxable")) ? basic : BigDecimal.ZERO;
        BigDecimal statutory =
                Boolean.TRUE.equals(policy.get("basicStatutoryEligible")) ? basic : BigDecimal.ZERO;
        Set<String> amountIds = new HashSet<>();
        for (var c : components)
            if ("INPUT".equals(c.data().get("method")) && eligible(c.data(), financial))
                amountIds.add(c.id().toString());
        if (!amountIds.containsAll(amounts.keySet()))
            throw new SetupException(
                    "Input amounts must refer to applicable INPUT components in this salary"
                        + " structure.");
        if (!amounts.keySet().containsAll(amountIds))
            throw new SetupException(
                    "Enter every applicable component input amount, including an explicit zero when"
                        + " no amount applies.");

        for (var c : components) {
            if (!"EARNING".equals(c.data().get("category"))) continue;
            Line line = component(c, financial, overrides, vars, amounts, rounding);
            lines.add(line);
            BigDecimal amount = new BigDecimal(line.amount());
            gross = gross.add(amount);
            if (Boolean.TRUE.equals(c.data().get("taxable"))) taxable = taxable.add(amount);
            if (Boolean.TRUE.equals(c.data().get("statutoryEligible")))
                statutory = statutory.add(amount);
        }
        vars.put("gross", gross);
        vars.put("taxable", taxable);
        vars.put("statutory", statutory);
        BigDecimal deductions = BigDecimal.ZERO, employer = BigDecimal.ZERO;
        for (var c : components) {
            if ("EARNING".equals(c.data().get("category"))) continue;
            Line line = component(c, financial, overrides, vars, amounts, rounding);
            lines.add(line);
            if ("DEDUCTION".equals(line.category()))
                deductions = deductions.add(new BigDecimal(line.amount()));
            else if ("EMPLOYER_CONTRIBUTION".equals(line.category()))
                employer = employer.add(new BigDecimal(line.amount()));
            else throw new SetupException("Unsupported component category.");
        }
        for (var rule : rules) {
            var d = rule.data();
            if (Boolean.TRUE.equals(d.get("taxRule"))
                    && Boolean.TRUE.equals(financial.get("taxExempted"))) {
                lines.add(
                        new Line(
                                rule.code(),
                                rule.name(),
                                "DEDUCTION",
                                "Exempt under published employee setup",
                                "0.00"));
                continue;
            }
            BigDecimal basis = basis(vars, text(d, "basis"));
            BigDecimal employeeAmount;
            String ruleEquation;
            switch (text(d, "method")) {
                case "FIXED" -> {
                    employeeAmount = number(d, "employeeRate");
                    ruleEquation = employeeAmount.toPlainString();
                }
                case "PERCENTAGE" -> {
                    employeeAmount =
                            basis.multiply(number(d, "employeeRate"), MC)
                                    .divide(BigDecimal.valueOf(100), MC);
                    ruleEquation = basis + " × " + d.get("employeeRate") + "%";
                }
                case "TIERED" -> {
                    employeeAmount = BigDecimal.ZERO;
                    BigDecimal previous = BigDecimal.ZERO;
                    var terms = new ArrayList<String>();
                    for (var band : rows(d.get("bands"))) {
                        BigDecimal upper =
                                band.get("upperBound") == null || "".equals(band.get("upperBound"))
                                        ? basis
                                        : new BigDecimal(band.get("upperBound").toString());
                        BigDecimal slice = basis.min(upper).subtract(previous).max(BigDecimal.ZERO);
                        BigDecimal percent = number(band, "rate");
                        employeeAmount =
                                employeeAmount.add(
                                        slice.multiply(percent, MC)
                                                .divide(BigDecimal.valueOf(100), MC));
                        terms.add(slice + " × " + percent + "%");
                        previous = upper;
                    }
                    ruleEquation = String.join(" + ", terms);
                }
                default -> throw new SetupException("Unsupported statutory method.");
            }
            employeeAmount = money(employeeAmount, rounding);
            BigDecimal employerRate = number(d, "employerRate");
            BigDecimal employerAmount =
                    money(
                            "FIXED".equals(d.get("method"))
                                    ? employerRate
                                    : basis.multiply(employerRate, MC)
                                            .divide(BigDecimal.valueOf(100), MC),
                            rounding);
            lines.add(
                    new Line(
                            rule.code(),
                            rule.name() + " (employee)",
                            "DEDUCTION",
                            ruleEquation,
                            employeeAmount.toPlainString()));
            lines.add(
                    new Line(
                            rule.code() + "_EMPLOYER",
                            rule.name() + " (employer)",
                            "EMPLOYER_CONTRIBUTION",
                            "FIXED".equals(d.get("method"))
                                    ? employerRate.toPlainString()
                                    : basis + " × " + employerRate + "%",
                            employerAmount.toPlainString()));
            deductions = deductions.add(employeeAmount);
            employer = employer.add(employerAmount);
        }
        BigDecimal net = gross.subtract(deductions);
        if (net.signum() < 0)
            throw new SetupException(
                    "Deductions exceed gross pay. Resolve the amounts before saving payroll.");
        var variables = new LinkedHashMap<String, String>();
        vars.forEach((key, value) -> variables.put(key, value.toPlainString()));
        return new Result(
                VERSION,
                List.copyOf(lines),
                fmt(basic),
                fmt(gross),
                fmt(taxable),
                fmt(statutory),
                fmt(deductions),
                fmt(net),
                fmt(employer),
                fmt(gross.add(employer)),
                variables,
                rules.isEmpty()
                        ? List.of(
                                "No statutory rules apply in the published employee setup. Review"
                                    + " the recorded exemption before using this result.")
                        : List.of());
    }

    private static Line component(
            SetupStore.Item c,
            Map<String, Object> financial,
            Map<String, Object> overrides,
            Map<String, BigDecimal> vars,
            Map<String, BigDecimal> amounts,
            RoundingMode rounding) {
        var d = c.data();
        String category = text(d, "category");
        if (!eligible(d, financial))
            return new Line(
                    c.code(),
                    c.name(),
                    category,
                    "Not eligible under published employee setup",
                    "0.00");
        BigDecimal value =
                new BigDecimal(
                        Objects.toString(
                                overrides.getOrDefault(c.id().toString(), d.get("value"))));
        BigDecimal amount;
        String equation;
        switch (text(d, "method")) {
            case "FIXED" -> {
                amount = value;
                equation = value.toPlainString();
            }
            case "INPUT" -> {
                amount = amounts.get(c.id().toString());
                equation = "Entered period amount: " + amount;
            }
            case "PERCENTAGE" -> {
                if ("EARNING".equals(category) && !"BASIC".equals(d.get("basis")))
                    throw new SetupException(
                            "Percentage earnings cannot depend on gross, taxable or statutory"
                                + " totals.");
                if (value.compareTo(BigDecimal.valueOf(100)) > 0)
                    throw new SetupException("Percentage rate cannot exceed 100.");
                BigDecimal basis = basis(vars, text(d, "basis"));
                amount = basis.multiply(value, MC).divide(BigDecimal.valueOf(100), MC);
                equation = basis + " × " + value + "%";
            }
            case "FORMULA" -> {
                if (overrides.containsKey(c.id().toString()))
                    throw new SetupException(
                            "Formula components cannot have numeric value overrides. Revise the"
                                + " formula instead.");
                equation = text(d, "formula");
                amount = PayrollFormula.parse(equation, "EARNING".equals(category)).evaluate(vars);
            }
            default -> throw new SetupException("Unsupported component calculation method.");
        }
        return new Line(
                c.code(), c.name(), category, equation, money(amount, rounding).toPlainString());
    }

    public static boolean eligible(Map<String, Object> component, Map<String, Object> financial) {
        String key =
                switch (Objects.toString(component.get("eligibility"), "ALL")) {
                    case "OVERTIME" -> "overtimePaid";
                    case "HOLIDAY" -> "holidayPaymentEligible";
                    case "ATTENDANCE_BONUS" -> "attendanceBonusEligible";
                    case "OVERTIME_ALLOWANCE" -> "overtimeAllowanceEligible";
                    default -> null;
                };
        return key == null || Boolean.TRUE.equals(financial.get(key));
    }

    private static BigDecimal basis(Map<String, BigDecimal> vars, String basis) {
        var value = vars.get(basis.toLowerCase(Locale.ROOT));
        if (value == null)
            throw new SetupException("Unsupported or circular earnings basis: " + basis);
        return value;
    }

    static void validInput(BigDecimal value) {
        if (value == null
                || value.signum() < 0
                || value.scale() > 4
                || value.compareTo(new BigDecimal("9999999999")) > 0)
            throw new SetupException(
                    "Inputs must be non-negative decimal values with at most four decimal places.");
    }

    private static BigDecimal money(BigDecimal value, RoundingMode rounding) {
        if (value.signum() < 0 || value.compareTo(new BigDecimal("999999999999")) > 0)
            throw new SetupException(
                    "A calculation line produced a negative or unsupported amount.");
        return value.setScale(2, rounding);
    }

    private static String fmt(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
