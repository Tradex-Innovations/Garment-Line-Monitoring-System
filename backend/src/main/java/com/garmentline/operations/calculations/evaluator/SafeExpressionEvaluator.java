package com.garmentline.operations.calculations.evaluator;

import com.garmentline.operations.calculations.model.CalculationWarning;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SafeExpressionEvaluator {

  public BigDecimal evaluate(
      String expression,
      Map<String, BigDecimal> variables,
      List<String> warnings,
      BigDecimal safeDivideDefault,
      int scale,
      RoundingMode roundingMode) {
    Parser parser =
        new Parser(expression, variables, warnings, safeDivideDefault, scale, roundingMode);
    BigDecimal result = parser.parseExpression();
    parser.expect(TokenType.EOF);
    return result;
  }

  private enum TokenType {
    NUMBER,
    IDENTIFIER,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    LPAREN,
    RPAREN,
    COMMA,
    EOF
  }

  private record Token(TokenType type, String value) {
  }

  private static final class Parser {
    private final List<Token> tokens;
    private final Map<String, BigDecimal> variables;
    private final List<String> warnings;
    private final BigDecimal safeDivideDefault;
    private final int scale;
    private final RoundingMode roundingMode;
    private int index = 0;

    private Parser(
        String expression,
        Map<String, BigDecimal> variables,
        List<String> warnings,
        BigDecimal safeDivideDefault,
        int scale,
        RoundingMode roundingMode) {
      this.tokens = tokenize(expression);
      this.variables = variables;
      this.warnings = warnings;
      this.safeDivideDefault = safeDivideDefault;
      this.scale = scale;
      this.roundingMode = roundingMode;
    }

    private BigDecimal parseExpression() {
      BigDecimal value = parseTerm();
      while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
        Token operator = previous();
        BigDecimal right = parseTerm();
        value =
            operator.type() == TokenType.PLUS ? value.add(right) : value.subtract(right);
      }
      return value;
    }

    private BigDecimal parseTerm() {
      BigDecimal value = parseFactor();
      while (match(TokenType.STAR) || match(TokenType.SLASH)) {
        Token operator = previous();
        BigDecimal right = parseFactor();
        value =
            operator.type() == TokenType.STAR ? value.multiply(right) : safeDivide(value, right);
      }
      return value;
    }

    private BigDecimal parseFactor() {
      if (match(TokenType.MINUS)) {
        return parseFactor().negate();
      }
      if (match(TokenType.PLUS)) {
        return parseFactor();
      }
      return parsePrimary();
    }

    private BigDecimal parsePrimary() {
      if (match(TokenType.NUMBER)) {
        return new BigDecimal(previous().value());
      }

      if (match(TokenType.IDENTIFIER)) {
        String identifier = previous().value();
        if (match(TokenType.LPAREN)) {
          List<BigDecimal> arguments = new ArrayList<>();
          if (!check(TokenType.RPAREN)) {
            do {
              arguments.add(parseExpression());
            } while (match(TokenType.COMMA));
          }
          expect(TokenType.RPAREN);
          return applyFunction(identifier, arguments);
        }

        if (!variables.containsKey(identifier)) {
          throw new IllegalArgumentException("Unknown variable in rule expression: " + identifier);
        }
        return variables.get(identifier);
      }

      if (match(TokenType.LPAREN)) {
        BigDecimal value = parseExpression();
        expect(TokenType.RPAREN);
        return value;
      }

      throw new IllegalArgumentException("Unexpected token in rule expression: " + peek().value());
    }

    private BigDecimal applyFunction(String identifier, List<BigDecimal> arguments) {
      return switch (identifier) {
        case "safe_divide" -> {
          if (arguments.size() != 2) {
            throw new IllegalArgumentException("safe_divide requires exactly two arguments.");
          }
          yield safeDivide(arguments.get(0), arguments.get(1));
        }
        case "min" -> {
          if (arguments.size() != 2) {
            throw new IllegalArgumentException("min requires exactly two arguments.");
          }
          yield arguments.get(0).min(arguments.get(1));
        }
        case "max" -> {
          if (arguments.size() != 2) {
            throw new IllegalArgumentException("max requires exactly two arguments.");
          }
          yield arguments.get(0).max(arguments.get(1));
        }
        default ->
            throw new IllegalArgumentException(
                "Unsupported function in rule expression: " + identifier);
      };
    }

    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
      if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
        if (!warnings.contains(CalculationWarning.DIVIDE_BY_ZERO_PROTECTED.name())) {
          warnings.add(CalculationWarning.DIVIDE_BY_ZERO_PROTECTED.name());
        }
        return safeDivideDefault;
      }

      return numerator.divide(denominator, scale, roundingMode);
    }

    private boolean match(TokenType type) {
      if (check(type)) {
        index += 1;
        return true;
      }
      return false;
    }

    private boolean check(TokenType type) {
      return peek().type() == type;
    }

    private void expect(TokenType type) {
      if (!match(type)) {
        throw new IllegalArgumentException(
            "Expected token " + type + " but found " + peek().type() + ".");
      }
    }

    private Token previous() {
      return tokens.get(index - 1);
    }

    private Token peek() {
      return tokens.get(index);
    }

    private static List<Token> tokenize(String expression) {
      List<Token> tokens = new ArrayList<>();
      int cursor = 0;

      while (cursor < expression.length()) {
        char current = expression.charAt(cursor);
        if (Character.isWhitespace(current)) {
          cursor += 1;
          continue;
        }
        if (current == '+') {
          tokens.add(new Token(TokenType.PLUS, "+"));
          cursor += 1;
          continue;
        }
        if (current == '-') {
          tokens.add(new Token(TokenType.MINUS, "-"));
          cursor += 1;
          continue;
        }
        if (current == '*') {
          tokens.add(new Token(TokenType.STAR, "*"));
          cursor += 1;
          continue;
        }
        if (current == '/') {
          tokens.add(new Token(TokenType.SLASH, "/"));
          cursor += 1;
          continue;
        }
        if (current == '(') {
          tokens.add(new Token(TokenType.LPAREN, "("));
          cursor += 1;
          continue;
        }
        if (current == ')') {
          tokens.add(new Token(TokenType.RPAREN, ")"));
          cursor += 1;
          continue;
        }
        if (current == ',') {
          tokens.add(new Token(TokenType.COMMA, ","));
          cursor += 1;
          continue;
        }
        if (Character.isDigit(current) || current == '.') {
          int start = cursor;
          while (cursor < expression.length()) {
            char next = expression.charAt(cursor);
            if (!Character.isDigit(next) && next != '.') {
              break;
            }
            cursor += 1;
          }
          tokens.add(new Token(TokenType.NUMBER, expression.substring(start, cursor)));
          continue;
        }
        if (Character.isLetter(current) || current == '_') {
          int start = cursor;
          while (cursor < expression.length()) {
            char next = expression.charAt(cursor);
            if (!Character.isLetterOrDigit(next) && next != '_') {
              break;
            }
            cursor += 1;
          }
          tokens.add(new Token(TokenType.IDENTIFIER, expression.substring(start, cursor)));
          continue;
        }

        throw new IllegalArgumentException("Unsupported character in rule expression: " + current);
      }

      tokens.add(new Token(TokenType.EOF, ""));
      return tokens;
    }
  }
}

