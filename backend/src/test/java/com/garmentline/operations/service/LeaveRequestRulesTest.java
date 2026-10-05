package com.garmentline.operations.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garmentline.operations.support.ApiException;
import org.junit.jupiter.api.Test;

class LeaveRequestRulesTest {
  @Test void acceptsWellFormedDayHalfDayAndShortLeave() {
    assertThatCode(() -> LeaveRequestRules.validate("full_day", "annual", "2026-11-01",
        "2026-11-03", null, null, null, "Personal leave")).doesNotThrowAnyException();
    assertThatCode(() -> LeaveRequestRules.validate("half_day", "casual", "2026-11-01",
        "2026-11-01", null, null, "first_half", null)).doesNotThrowAnyException();
    assertThatCode(() -> LeaveRequestRules.validate("short_leave", "medical", "2026-11-01",
        "2026-11-01", "10:00", "12:00", null, null)).doesNotThrowAnyException();
  }

  @Test void rejectsAmbiguousOrInvalidRequests() {
    assertThatThrownBy(() -> LeaveRequestRules.validate("half_day", "casual", "2026-11-01",
        "2026-11-02", null, null, "first_half", null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> LeaveRequestRules.validate("short_leave", "medical", "2026-11-01",
        "2026-11-01", "12:00", "10:00", null, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> LeaveRequestRules.validate("full_day", "unknown", "2026-11-01",
        "2026-11-01", null, null, null, null)).isInstanceOf(ApiException.class);
  }
}
