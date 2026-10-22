package ph.dragonview.mobile.data.model;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;

import org.junit.Test;

public final class SalesAnalyticsPayloadTest {
    @Test public void comparisonTrendAndHistoricalRevenueAreExposed() {
        SalesAnalytics report = new Gson().fromJson("{\"period\":\"monthly\"," +
                "\"comparisonTrend\":[{\"label\":\"Aug 1\",\"revenue\":120," +
                "\"pieces\":0}],\"historicalRevenue\":300}", SalesAnalytics.class);
        assertEquals(1, report.getComparisonTrend().size());
        assertEquals(120.0, report.getComparisonTrend().get(0).getRevenue(), 0.0);
        assertEquals(300.0, report.getHistoricalRevenue(), 0.0);
    }
}
