package ph.dragonview.mobile.ui.analytics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AnalyticsDateRangeTest {
    @Test public void dailyDefaultComparesWithPreviousDay() {
        AnalyticsDateRange current = AnalyticsDateRange.forDate("daily", "2026-08-12");
        assertEquals("Aug 12, 2026", current.label());
        assertEquals("Aug 11, 2026", current.previous().label());
    }

    @Test public void weekUsesMondayThroughSunday() {
        AnalyticsDateRange current = AnalyticsDateRange.forDate("weekly", "2026-08-12");
        assertEquals("Aug 10–16, 2026", current.label());
        assertEquals("Aug 3–9, 2026", current.previous().label());
    }

    @Test public void monthlyComparisonRespectsDifferentMonthLengths() {
        AnalyticsDateRange current = AnalyticsDateRange.forDate("monthly", "2026-03-31");
        assertEquals("Mar 1–31, 2026", current.label());
        assertEquals("Feb 1–28, 2026", current.previous().label());
        assertEquals("Aug 1–31, 2025",
                AnalyticsDateRange.forDate("monthly", "2025-08-19").label());
    }

    @Test public void yearlyComparisonRespectsLeapYears() {
        AnalyticsDateRange current = AnalyticsDateRange.forDate("yearly", "2025-09-25");
        assertEquals("Jan 1–Dec 31, 2025", current.label());
        assertEquals("Jan 1–Dec 31, 2024", current.previous().label());
    }
}
