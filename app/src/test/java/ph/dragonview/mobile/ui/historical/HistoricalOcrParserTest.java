package ph.dragonview.mobile.ui.historical;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class HistoricalOcrParserTest {
    @Test public void extractsDateRowsAndOptionalGrandTotalForReview() {
        String paper = "August 12, 2026\nSize Kilo Price/kg Subtotal\n" +
                "Large 140 100 14000\nMedium 200 80 16000\nSmall 300 40 12000\n" +
                "Grand total 42000";
        HistoricalOcrParser.Result result = HistoricalOcrParser.parse(paper);
        assertEquals("2026-08-12", result.date());
        assertEquals(3, result.items().size());
        assertEquals(140.0, result.items().get(0).getWeightKilograms(), 0.0);
        assertEquals(null, result.items().get(0).getActualSales());
        assertEquals(42000.0, result.grandTotal(), 0.0);
    }

    @Test public void pieceBasedPageIsNotMisreadAsKilograms() {
        HistoricalOcrParser.Result result = HistoricalOcrParser.parse(
                "August 12, 2026\nSize Pcs Price/kg Subtotal\nLarge 120 100 14000");
        assertEquals(0, result.items().size());
    }

    @Test public void fractionalWeightIsTranscribedForOwnerReview() {
        HistoricalOcrParser.Result result = HistoricalOcrParser.parse(
                "August 12, 2026\nSize Kilo Price/kg Total\nLarge 1.125 180 202.50");
        assertEquals(1.125, result.items().get(0).getWeightKilograms(), 0.0);
    }

    @Test public void blemishedRowIsTranscribedForOwnerReview() {
        HistoricalOcrParser.Result result = HistoricalOcrParser.parse(
                "August 12, 2026\nSize Kilo Price/kg Total\nBlemished 12.5 80 1000");
        assertEquals(1, result.items().size());
        assertEquals("BLEMISHED", result.items().get(0).getSize());
        assertEquals(12.5, result.items().get(0).getWeightKilograms(), 0.0);
        assertEquals(1000.0, result.items().get(0).getSubtotal(), 0.0);
    }

    @Test public void unreadableImageNeverInventsDateOrMoney() {
        HistoricalOcrParser.Result result = HistoricalOcrParser.parse("unreadable page");
        assertEquals("", result.date());
        assertEquals(0, result.items().size());
        assertEquals(null, result.grandTotal());
    }

    @Test public void multipleDatesAreNotSilentlyMergedIntoOneRecord() {
        HistoricalOcrParser.Result result = HistoricalOcrParser.parse(
                "August 12, 2026\nLarge 140 100 14000\nAugust 13, 2026\nSmall 80 50 4000");
        assertEquals("", result.date());
        assertEquals(true, result.multipleDates());
    }
}
