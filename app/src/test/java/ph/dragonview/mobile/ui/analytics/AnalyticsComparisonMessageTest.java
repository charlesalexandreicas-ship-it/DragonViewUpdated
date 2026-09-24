package ph.dragonview.mobile.ui.analytics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;

import ph.dragonview.mobile.data.model.SalesAnalytics;

public final class AnalyticsComparisonMessageTest {
    private static final String[] PERIODS = {"daily", "weekly", "monthly", "yearly"};

    @Test
    public void previousRevenueGetterExposesServerValue() {
        SalesAnalytics report = report("daily", 2500.0, 1250.0, 100.0);

        assertEquals(1250.0, report.getPreviousRevenue(), 0.0);
    }

    @Test
    public void positiveRevenueWithoutBaselineUsesNewRevenueMessageForEveryPeriod() {
        for (String period : PERIODS) {
            SalesAnalytics report = report(period, 1250.0, 0.0, null);

            assertEquals("New revenue: ₱1,250.00 (no percentage baseline).",
                    AnalyticsFragment.formatComparisonMessage(report));
        }
    }

    @Test
    public void zeroRevenueInBothPeriodsUsesNoSalesMessageForEveryPeriod() {
        for (String period : PERIODS) {
            SalesAnalytics report = report(period, 0.0, 0.0, null);

            assertEquals("No sales in either period.",
                    AnalyticsFragment.formatComparisonMessage(report));
        }
    }

    @Test
    public void nonzeroBaselineKeepsExistingPercentageMessage() {
        SalesAnalytics report = report("monthly", 875.0, 1000.0, -12.5);

        assertEquals("-12.5% vs previous period",
                AnalyticsFragment.formatComparisonMessage(report));
    }

    private static SalesAnalytics report(String period, double currentRevenue,
                                         double previousRevenue, Double comparisonPercent) {
        SalesAnalytics.Totals totals = new SalesAnalytics.Totals(currentRevenue, 0, 0, 0.0);
        return new SalesAnalytics(period, "2026-09-23", previousRevenue,
                comparisonPercent, totals, Collections.emptyList(), Collections.emptyList());
    }
}
