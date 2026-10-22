package ph.dragonview.mobile.ui.inventory;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import ph.dragonview.mobile.data.model.InventoryLot;

public final class BatchCompositionTest {
    @Test
    public void countsDistinctAvailableSizesAndGradesForOneBatch() {
        BatchComposition composition = BatchComposition.fromLots(Arrays.asList(
                lot("1042", "SMALL", "A", 2),
                lot("1042", "SMALL", "B", 1),
                lot("1042", "MEDIUM", "A", 2),
                lot("1042", "LARGE", "A", 0),
                lot("1043", "JUMBO", "C", 8)
        )).get("1042");

        assertEquals(2, composition.sizeCount());
        assertEquals(2, composition.gradeCount());
    }

    @Test
    public void ignoresBlankValuesAndNormalizesDuplicateLabels() {
        BatchComposition composition = BatchComposition.fromLots(Arrays.asList(
                lot("1042", " Medium ", "a", 1),
                lot("1042", "MEDIUM", " A ", 1),
                lot("1042", "", "", 1)
        )).get("1042");

        assertEquals(1, composition.sizeCount());
        assertEquals(1, composition.gradeCount());
    }

    @Test
    public void emptyInventoryHasNoComposition() {
        assertEquals(Collections.emptyMap(), BatchComposition.fromLots(Collections.emptyList()));
    }

    private static InventoryLot lot(String batch, String size, String grade, int pieces) {
        return new InventoryLot(1, batch, "2026-09-25", size, grade, pieces, false);
    }
}
