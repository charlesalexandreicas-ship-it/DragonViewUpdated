package ph.dragonview.mobile.ui.inventory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ph.dragonview.mobile.data.model.InventoryLot;

/** Distinct available fruit sizes and grades within one inventory batch. */
final class BatchComposition {
    private final Set<String> sizes = new HashSet<>();
    private final Set<String> grades = new HashSet<>();

    int sizeCount() { return sizes.size(); }
    int gradeCount() { return grades.size(); }

    /** Groups stock rows by batch so the card never treats combinations as distinct sizes or grades. */
    static Map<String, BatchComposition> fromLots(List<InventoryLot> lots) {
        Map<String, BatchComposition> byBatch = new HashMap<>();
        if (lots == null) return byBatch;
        for (InventoryLot lot : lots) {
            if (lot == null || lot.getAvailablePieces() <= 0 || lot.getBatchNumber() == null) {
                continue;
            }
            BatchComposition composition = byBatch.computeIfAbsent(
                    lot.getBatchNumber(), ignored -> new BatchComposition());
            addNormalized(composition.sizes, lot.getSize());
            addNormalized(composition.grades, lot.getGrade());
        }
        return byBatch;
    }

    private static void addNormalized(Set<String> values, String value) {
        if (value != null && !value.trim().isEmpty()) {
            values.add(value.trim().toUpperCase(Locale.US));
        }
    }
}
