package com.tradex.unionnorth.setup;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

/** A bounded decimal expression language. No scripts, reflection, I/O or executable code. */
public final class PayrollFormula {
    public static final Set<String> VARIABLES =
            Set.of(
                    "basic",
                    "basicRate",
                    "bra1",
                    "bra2",
                    "gross",
                    "taxable",
                    "statutory",
                    "periodDays",
                    "paidDays",
                    "paidHours",
                    "overtimeHours",
                    "unpaidDays",
                    "lateMinutes");
    public static final Set<String> AGGREGATES = Set.of("gross", "taxable", "statutory");
    private static final MathContext MC = MathContext.DECIMAL128;

    private interface Node {
        BigDecimal evaluate(Map<String, BigDecimal> values);
    }

    public record Expression(
            Set<String> variables,
            java.util.function.Function<Map<String, BigDecimal>, BigDecimal> calculation) {
        public BigDecimal evaluate(Map<String, BigDecimal> values) {
            if (!values.keySet().containsAll(variables)) {
                var missing = new TreeSet<>(variables);
                missing.removeAll(values.keySet());
                throw new SetupException("Enter period inputs: " + String.join(", ", missing));
            }
            try {
                var result = calculation.apply(values);
                if (result.abs().compareTo(new BigDecimal("999999999999")) > 0)
                    throw new SetupException("Formula result exceeds the supported amount.");
                return result;
            } catch (ArithmeticException e) {
                throw new SetupException("Formula cannot divide by zero.");
            }
        }
    }

    public static Expression parse(String source, boolean earning) {
        if (source == null || source.isBlank() || source.length() > 500)
            throw new SetupException("Enter a formula of at most 500 characters.");
        var parser = new Parser(source);
        Node node = parser.expression(0);
        parser.spaces();
        if (parser.position != source.length()) throw parser.invalid();
        if (earning && !Collections.disjoint(parser.variables, AGGREGATES))
            throw new SetupException(
                    "Earnings cannot use gross, taxable or statutory totals because that creates a"
                        + " circular calculation.");
        return new Expression(Set.copyOf(parser.variables), node::evaluate);
    }

    private static class Parser {
        final String source;
        final Set<String> variables = new HashSet<>();
        int position;

        Parser(String source) {
            this.source = source;
        }

        void spaces() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position)))
                position++;
        }

        boolean take(char c) {
            spaces();
            if (position < source.length() && source.charAt(position) == c) {
                position++;
                return true;
            }
            return false;
        }

        SetupException invalid() {
            return new SetupException(
                    "Invalid payroll formula near character "
                            + (position + 1)
                            + ". Use decimal numbers, supported variables, + - * /, parentheses,"
                            + " min(a,b) or max(a,b).");
        }

        Node expression(int depth) {
            if (depth > 32) throw new SetupException("Formula nesting is too deep.");
            Node left = term(depth + 1);
            while (true) {
                if (take('+')) {
                    Node a = left, b = term(depth + 1);
                    left = v -> a.evaluate(v).add(b.evaluate(v), MC);
                } else if (take('-')) {
                    Node a = left, b = term(depth + 1);
                    left = v -> a.evaluate(v).subtract(b.evaluate(v), MC);
                } else return left;
            }
        }

        Node term(int depth) {
            Node left = atom(depth + 1);
            while (true) {
                if (take('*')) {
                    Node a = left, b = atom(depth + 1);
                    left = v -> a.evaluate(v).multiply(b.evaluate(v), MC);
                } else if (take('/')) {
                    Node a = left, b = atom(depth + 1);
                    left = v -> a.evaluate(v).divide(b.evaluate(v), MC);
                } else return left;
            }
        }

        Node atom(int depth) {
            if (depth > 32) throw new SetupException("Formula nesting is too deep.");
            if (take('+')) return atom(depth + 1);
            if (take('-')) {
                Node n = atom(depth + 1);
                return v -> n.evaluate(v).negate();
            }
            if (take('(')) {
                Node n = expression(depth + 1);
                if (!take(')')) throw invalid();
                return n;
            }
            spaces();
            int start = position;
            while (position < source.length()
                    && (Character.isDigit(source.charAt(position))
                            || source.charAt(position) == '.')) position++;
            if (position > start) {
                try {
                    var number = new BigDecimal(source.substring(start, position));
                    return v -> number;
                } catch (NumberFormatException e) {
                    throw invalid();
                }
            }
            while (position < source.length() && Character.isLetter(source.charAt(position)))
                position++;
            // BRA variables are the only supported names with numeric suffixes.
            if (position < source.length()
                    && source.substring(start, position).equals("bra")
                    && (source.charAt(position) == '1' || source.charAt(position) == '2'))
                position++;
            String name = source.substring(start, position);
            if (Set.of("min", "max").contains(name)) {
                if (!take('(')) throw invalid();
                Node a = expression(depth + 1);
                if (!take(',')) throw invalid();
                Node b = expression(depth + 1);
                if (!take(')')) throw invalid();
                return name.equals("min")
                        ? v -> a.evaluate(v).min(b.evaluate(v))
                        : v -> a.evaluate(v).max(b.evaluate(v));
            }
            if (!VARIABLES.contains(name)) throw invalid();
            variables.add(name);
            return v -> v.get(name);
        }
    }
}
