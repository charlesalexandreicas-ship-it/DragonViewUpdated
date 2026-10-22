package ph.dragonview.mobile.ui.historical;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import ph.dragonview.mobile.ui.date.RecordDate;

/** Pure form rules shared by live totals, paper checks, and the final saved draft. */
final class HistoricalEntryMath {
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private HistoricalEntryMath() { }

    /** A row is complete only after valid kg and price input; unfinished rows add no total. */
    @Nullable static BigDecimal subtotal(String weight, String price) {
        BigDecimal kilograms = decimal(weight);
        BigDecimal perKilogram = decimal(price);
        if (kilograms == null || perKilogram == null || kilograms.signum() <= 0
                || perKilogram.signum() < 0 || kilograms.scale() > 3
                || perKilogram.scale() > 2) return null;
        return kilograms.multiply(perKilogram).setScale(2, RoundingMode.HALF_UP);
    }

    /** Missing paper amounts are optional; supplied ones must agree to the cent. */
    static boolean paperMatches(String paper, @Nullable BigDecimal calculated) {
        if (paper == null || paper.trim().isEmpty()) return true;
        BigDecimal written = decimal(paper);
        return calculated != null && written != null && written.signum() >= 0
                && written.scale() <= 2
                && written.subtract(calculated).abs().compareTo(CENT) <= 0;
    }

    /** Preserve a reviewed paper subtotal when supplied, otherwise save the calculation. */
    static BigDecimal amountForSave(String paper, BigDecimal calculated) {
        if (!paperMatches(paper, calculated)) {
            throw new IllegalArgumentException("Paper subtotal does not match the calculation.");
        }
        BigDecimal written = paper == null || paper.trim().isEmpty() ? null : decimal(paper);
        return written == null ? calculated : written.setScale(2, RoundingMode.HALF_UP);
    }

    /** The review total ignores a newly opened row until its inputs are complete. */
    static BigDecimal sumCompletedRows(List<BigDecimal> amounts) {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        if (amounts == null) return total;
        for (BigDecimal amount : amounts) {
            if (amount != null) total = total.add(amount);
        }
        return total;
    }

    /** An explicit row choice overrides the record choice without defaulting to actual sale. */
    @Nullable static Boolean classification(@Nullable Boolean recordChoice,
                                            @Nullable Boolean rowOverride) {
        return rowOverride != null ? rowOverride : recordChoice;
    }

    /** Manual dates use the existing typed-date format and retain the old no-future rule. */
    @Nullable static String isoDate(String displayed, String todayIso) {
        String iso = RecordDate.toIso(displayed);
        return iso == null || todayIso == null || iso.compareTo(todayIso) > 0 ? null : iso;
    }

    @Nullable private static BigDecimal decimal(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
