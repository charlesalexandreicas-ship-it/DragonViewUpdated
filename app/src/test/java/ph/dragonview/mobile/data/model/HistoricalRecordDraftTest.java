package ph.dragonview.mobile.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;

public final class HistoricalRecordDraftTest {
    @Test public void oneDateMayContainMultipleSizesAndGrandTotalIsNotAddedAgain() {
        HistoricalRecordDraft draft = new HistoricalRecordDraft("2026-08-12", Arrays.asList(
                new HistoricalRecordDraft.Item("Large", 140, 100, 14000, true),
                new HistoricalRecordDraft.Item("Medium", 200, 80, 16000, false),
                new HistoricalRecordDraft.Item("Small", 300, 40, 12000, true)), 42000.0);
        assertEquals(42000.0, draft.subtotalSum(), 0.0);
        assertEquals(26000.0, draft.salesSubtotalSum(), 0.0);
        assertEquals("", draft.validationError());
    }

    @Test public void mismatchIsFlaggedBeforeSave() {
        HistoricalRecordDraft draft = new HistoricalRecordDraft("2026-08-12", Arrays.asList(
                new HistoricalRecordDraft.Item("Large", 140, 100, 14000, true)), 15000.0);
        assertEquals("Grand total on paper does not match the subtotals.",
                draft.validationError());
    }

    @Test public void kilosTimesPriceEqualsPaperTotal() {
        HistoricalRecordDraft draft = new HistoricalRecordDraft("2026-08-12", Arrays.asList(
                new HistoricalRecordDraft.Item("Large", 133, 180, 23940, false)), null);
        assertEquals("", draft.validationError());
        assertEquals(23940.0, draft.subtotalSum(), 0.0);
    }

    @Test public void blemishedUsesTheSameWeightAndPriceCalculation() {
        HistoricalRecordDraft draft = new HistoricalRecordDraft("2026-08-12", Arrays.asList(
                new HistoricalRecordDraft.Item("Blemished", 12.5, 80, 1000, true)), null);
        assertEquals("", draft.validationError());
        assertEquals(1000.0, draft.salesSubtotalSum(), 0.0);
        assertTrue(new Gson().toJson(draft).contains("\"size\":\"Blemished\""));
    }

    @Test public void invalidWeightIsRejected() {
        HistoricalRecordDraft draft = new HistoricalRecordDraft("2026-08-12", Arrays.asList(
                new HistoricalRecordDraft.Item("Large", 0, 100, 14000, true)), null);
        assertEquals("Enter a positive weight in kg (up to 3 decimals) for every row.",
                draft.validationError());
    }

    @Test public void subtotalMustMatchWeightTimesPrice() {
        HistoricalRecordDraft draft = new HistoricalRecordDraft("2026-08-12", Arrays.asList(
                new HistoricalRecordDraft.Item("Large", 125, 170, 21000, true)), null);
        assertEquals("Each subtotal must match kilograms × price per kg (within ₱0.01).",
                draft.validationError());
    }

    @Test public void fractionalKilogramsAreAccepted() {
        HistoricalRecordDraft draft = new HistoricalRecordDraft("2026-08-12", Arrays.asList(
                new HistoricalRecordDraft.Item("Large", 1.125, 180, 202.50, true)), null);
        assertEquals("", draft.validationError());
    }

    @Test public void newPayloadSendsWeightWithoutInventingPieces() {
        HistoricalRecordDraft draft = new HistoricalRecordDraft("2026-08-12", Arrays.asList(
                new HistoricalRecordDraft.Item("Large", 133, 180, 23940, true)), null);
        String json = new Gson().toJson(draft);
        assertTrue(json.contains("\"weightKilograms\":133.0"));
        assertTrue(!json.contains("\"pieces\""));
    }

    @Test public void legacyPiecesRemainSeparateFromUnknownWeight() {
        HistoricalRecordDraft.Item old = new Gson().fromJson(
                "{\"size\":\"LARGE\",\"pieces\":120,\"pricePerKilogram\":180,\"subtotal\":23940}",
                HistoricalRecordDraft.Item.class);
        assertEquals(Integer.valueOf(120), old.getLegacyPieces());
        assertNull(old.getWeightKilograms());
    }

    @Test public void everyRowRequiresAnExplicitRevenueClassification() {
        HistoricalRecordDraft draft = new HistoricalRecordDraft("2026-08-12", Arrays.asList(
                new HistoricalRecordDraft.Item("Large", 140, 100, 14000, null)), null);
        assertEquals("Choose actual sales or estimated value for every row.",
                draft.validationError());
    }
}
