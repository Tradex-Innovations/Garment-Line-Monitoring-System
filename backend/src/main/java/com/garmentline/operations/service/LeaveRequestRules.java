package com.garmentline.operations.service;

import com.garmentline.operations.support.ApiException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Shared request validation for HR entry and the employee portal. */
public final class LeaveRequestRules {
  private static final Set<String> TYPES = Set.of("full_day", "half_day", "short_leave");
  private static final Set<String> CATEGORIES = Set.of("annual", "casual", "sick", "no_pay",
      "emergency", "personal", "medical", "other");
  private static final Set<String> SESSIONS = Set.of("first_half", "second_half");

  private LeaveRequestRules() {}

  public static void validate(String type, String category, String start, String end,
                              String startTime, String endTime, String halfDaySession,
                              String reason) {
    if (!TYPES.contains(type)) bad("Select a valid leave type.");
    if (!CATEGORIES.contains(category)) bad("Select a valid leave category.");
    LocalDate from;
    LocalDate to;
    try {
      from = LocalDate.parse(start);
      to = LocalDate.parse(end);
    } catch (RuntimeException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Leave dates must use yyyy-MM-dd format.");
    }
    if (to.isBefore(from)) bad("End date must be same as or after start date.");
    if (reason != null && reason.length() > 1000) bad("Leave reason must be 1000 characters or fewer.");
    if ("full_day".equals(type)) {
      if (hasText(startTime) || hasText(endTime) || hasText(halfDaySession))
        bad("Full-day leave cannot contain half-day or short-leave fields.");
    } else if ("half_day".equals(type)) {
      if (!from.equals(to) || !SESSIONS.contains(halfDaySession))
        bad("Half-day leave needs one date and a first or second half.");
      if (hasText(startTime) || hasText(endTime)) bad("Half-day leave cannot contain times.");
    } else {
      if (!from.equals(to) || hasText(halfDaySession))
        bad("Short leave needs one date and no half-day session.");
      try {
        if (!LocalTime.parse(startTime).isBefore(LocalTime.parse(endTime)))
          bad("Short-leave end time must be after start time.");
      } catch (ApiException exception) {
        throw exception;
      } catch (RuntimeException exception) {
        bad("Enter valid start and end times for short leave.");
      }
    }
  }

  private static boolean hasText(String value) { return value != null && !value.isBlank(); }
  private static void bad(String message) { throw new ApiException(HttpStatus.BAD_REQUEST, message); }
}
