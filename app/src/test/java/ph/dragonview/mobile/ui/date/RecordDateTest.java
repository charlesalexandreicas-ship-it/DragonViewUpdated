package ph.dragonview.mobile.ui.date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class RecordDateTest {
    @Test public void separatorsAppearAsMonthAndDayAreTyped() {
        assertEquals("01/", RecordDate.formatDigits("01"));
        assertEquals("01/2", RecordDate.formatDigits("012"));
        assertEquals("01/29/", RecordDate.formatDigits("0129"));
        assertEquals("01/29/2026", RecordDate.formatDigits("01292026"));
    }

    @Test public void validTypedDateConvertsToExistingStorageFormat() {
        assertEquals("2024-02-29", RecordDate.toIso("02/29/2024"));
        assertEquals("09/29/2026", RecordDate.toDisplay("2026-09-29"));
    }

    @Test public void invalidOrIncompleteCalendarDateIsRejected() {
        assertNull(RecordDate.toIso("02/29/2025"));
        assertNull(RecordDate.toIso("13/01/2026"));
        assertNull(RecordDate.toIso("01/00/2026"));
        assertNull(RecordDate.toIso("01/29/20"));
    }
}
