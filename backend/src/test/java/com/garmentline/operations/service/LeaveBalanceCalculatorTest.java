package com.garmentline.operations.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LeaveBalanceCalculatorTest {
  @Test void neverInventsAnEntitlementWhenPolicyIsMissing() {
    var balance = LeaveBalanceCalculator.summarize(2026, List.of(), List.of(
        new LeaveBalanceCalculator.Leave("casual", "full_day",
            LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-02"), "approved")));
    assertThat(balance.get("configured")).isEqualTo(false);
    assertThat(balance.get("allowanceDays")).isNull();
    assertThat(balance.get("remainingDays")).isNull();
    assertThat((List<?>) balance.get("categories")).hasSize(1);
  }

  @Test void countsOnlyApprovedDaysInTheRequestedYearAndKeepsNoPaySeparate() {
    var balance = LeaveBalanceCalculator.summarize(2026,
        List.of(new LeaveBalanceCalculator.Policy("annual", 12)),
        List.of(
            leave("annual", "full_day", "2025-12-31", "2026-01-02", "approved"),
            leave("annual", "half_day", "2026-02-01", "2026-02-01", "approved"),
            leave("annual", "full_day", "2026-03-01", "2026-03-03", "pending"),
            leave("no_pay", "full_day", "2026-04-01", "2026-04-02", "approved")));
    assertThat(balance.get("allowanceDays")).isEqualTo(12.0);
    assertThat(balance.get("usedDays")).isEqualTo(2.5);
    assertThat(balance.get("remainingDays")).isEqualTo(9.5);
    @SuppressWarnings("unchecked")
    var categories = (List<Map<String, Object>>) balance.get("categories");
    assertThat(categories).anySatisfy(row -> {
      assertThat(row.get("category")).isEqualTo("no_pay");
      assertThat(row.get("usedDays")).isEqualTo(2.0);
      assertThat(row.get("entitlementDays")).isNull();
    });
  }

  private static LeaveBalanceCalculator.Leave leave(String category, String type,
                                                     String start, String end, String status) {
    return new LeaveBalanceCalculator.Leave(category, type,
        LocalDate.parse(start), LocalDate.parse(end), status);
  }
}
