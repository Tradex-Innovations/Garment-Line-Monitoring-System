package com.garmentline.operations.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Calendar-day leave usage. Entitlements are supplied by approved company policy, never defaulted. */
public final class LeaveBalanceCalculator {
  public record Policy(String category, double entitlementDays) {}
  public record Leave(String category, String type, LocalDate start, LocalDate end, String status) {}

  private LeaveBalanceCalculator() {}

  public static Map<String, Object> summarize(int year, List<Policy> policies, List<Leave> leaves) {
    Map<String, Double> entitlements = new LinkedHashMap<>();
    for (Policy policy : policies) entitlements.put(policy.category(), policy.entitlementDays());
    Map<String, Double> usage = new LinkedHashMap<>();
    for (Leave leave : leaves) {
      if (!"approved".equals(leave.status())) continue;
      double days = daysInYear(leave, year);
      if (days > 0) usage.merge(leave.category(), days, Double::sum);
    }
    List<Map<String, Object>> categories = new ArrayList<>();
    for (String category : List.of("annual", "casual", "sick", "no_pay", "emergency",
        "personal", "medical", "other")) {
      if (!entitlements.containsKey(category) && !usage.containsKey(category)) continue;
      Double entitlement = entitlements.get(category);
      double used = usage.getOrDefault(category, 0.0);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("category", category);
      row.put("entitlementDays", entitlement);
      row.put("usedDays", used);
      row.put("remainingDays", entitlement == null ? null : Math.max(0, entitlement - used));
      categories.add(row);
    }
    double allowance = entitlements.values().stream().mapToDouble(Double::doubleValue).sum();
    double used = usage.entrySet().stream()
        .filter(row -> entitlements.containsKey(row.getKey()))
        .mapToDouble(Map.Entry::getValue).sum();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("year", year);
    result.put("configured", !entitlements.isEmpty());
    result.put("allowanceDays", entitlements.isEmpty() ? null : allowance);
    result.put("usedDays", used);
    result.put("remainingDays", entitlements.isEmpty() ? null : Math.max(0, allowance - used));
    result.put("categories", categories);
    return result;
  }

  public static double daysInYear(Leave leave, int year) {
    Objects.requireNonNull(leave);
    LocalDate first = leave.start().isAfter(LocalDate.of(year, 1, 1))
        ? leave.start() : LocalDate.of(year, 1, 1);
    LocalDate last = leave.end().isBefore(LocalDate.of(year, 12, 31))
        ? leave.end() : LocalDate.of(year, 12, 31);
    if (last.isBefore(first)) return 0;
    if ("short_leave".equals(leave.type())) return 0;
    if ("half_day".equals(leave.type())) return 0.5;
    return ChronoUnit.DAYS.between(first, last) + 1;
  }
}
