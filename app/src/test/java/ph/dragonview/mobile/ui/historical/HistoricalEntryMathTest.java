package ph.dragonview.mobile.ui.historical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;

public final class HistoricalEntryMathTest {
    @Test public void subtotalIsRoundedToCentsFromKilogramsAndPrice() {
        assertEquals(new BigDecimal("202.50"),
                HistoricalEntryMath.subtotal("1.125", "180"));
        assertEquals(new BigDecimal("1000.00"),
                HistoricalEntryMath.subtotal("12.5", "80"));
    }

    @Test public void incompleteOrInvalidRowsDoNotHaveACalculatedSubtotal() {
        assertNull(HistoricalEntryMath.subtotal("", "80"));
        assertNull(HistoricalEntryMath.subtotal("12.5", ""));
        assertNull(HistoricalEntryMath.subtotal("0", "80"));
        assertNull(HistoricalEntryMath.subtotal("12.1234", "80"));
        assertNull(HistoricalEntryMath.subtotal("12", "80.123"));
    }

    @Test public void optionalPaperSubtotalIsCheckedButNeverInvented() {
        BigDecimal calculated = new BigDecimal("1000.00");
        assertTrue(HistoricalEntryMath.paperMatches("", calculated));
        assertTrue(HistoricalEntryMath.paperMatches("1000.01", calculated));
        assertFalse(HistoricalEntryMath.paperMatches("1000.02", calculated));
        assertFalse(HistoricalEntryMath.paperMatches("not a price", calculated));
        assertEquals(calculated, HistoricalEntryMath.amountForSave("", calculated));
        assertEquals(new BigDecimal("1000.01"),
                HistoricalEntryMath.amountForSave("1000.01", calculated));
    }

    @Test public void rowOverrideWinsWithoutAssumingUnclassifiedSales() {
        assertNull(HistoricalEntryMath.classification(null, null));
        assertEquals(Boolean.FALSE, HistoricalEntryMath.classification(false, null));
        assertEquals(Boolean.TRUE, HistoricalEntryMath.classification(false, true));
    }

    @Test public void historicalDateRejectsIncompleteAndFutureInput() {
        assertEquals("2026-09-25", HistoricalEntryMath.isoDate("09/25/2026", "2026-09-29"));
        assertNull(HistoricalEntryMath.isoDate("09/30/2026", "2026-09-29"));
        assertNull(HistoricalEntryMath.isoDate("02/30/2026", "2026-09-29"));
    }

    @Test public void reviewTotalExcludesAStartedButUnfinishedSize() {
        assertEquals(new BigDecimal("1760.00"), HistoricalEntryMath.sumCompletedRows(
                Arrays.asList(new BigDecimal("1000.00"), new BigDecimal("760.00"), null)));
    }
}
