package com.tradex.unionnorth.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PayrollLeaveInputsServiceTest {
    @Test
    void clipsApprovedLeaveToPayrollPeriodWithoutAssumingPaidDays() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        assertEquals(new BigDecimal("3"), PayrollLeaveInputsService.overlapDays("full_day",
                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 3), start, end));
        assertEquals(new BigDecimal("0.5"), PayrollLeaveInputsService.overlapDays("half_day",
                LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), start, end));
        assertEquals(BigDecimal.ZERO, PayrollLeaveInputsService.overlapDays("short_leave",
                LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), start, end));
        assertEquals(BigDecimal.ZERO, PayrollLeaveInputsService.overlapDays("full_day",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), start, end));
    }
}
