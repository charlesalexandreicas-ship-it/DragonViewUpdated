package ph.dragonview.mobile.data.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Owner-reviewed paper values; new rows record kilograms, never inferred pieces. */
public final class HistoricalRecordDraft {
    private final String date;
    private final List<Item> items;
    private final Double paperGrandTotal;

    public HistoricalRecordDraft(String date, List<Item> items, Double paperGrandTotal) {
        this.date = date;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.paperGrandTotal = paperGrandTotal;
    }

    public String getDate() { return date; }
    public List<Item> getItems() { return items; }
    public Double getPaperGrandTotal() { return paperGrandTotal; }

    public double subtotalSum() {
        BigDecimal total = BigDecimal.ZERO;
        for (Item item : items) total = total.add(BigDecimal.valueOf(item.subtotal));
        return total.doubleValue();
    }

    public double salesSubtotalSum() {
        BigDecimal total = BigDecimal.ZERO;
        for (Item item : items) {
            if (Boolean.TRUE.equals(item.actualSales)) {
                total = total.add(BigDecimal.valueOf(item.subtotal));
            }
        }
        return total.doubleValue();
    }

    /** Empty string means the draft is safe to submit. */
    public String validationError() {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "Enter the record date, including the year.";
        }
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            formatter.setLenient(false);
            formatter.parse(date);
        } catch (ParseException error) {
            return "Enter a valid record date.";
        }
        if (items.isEmpty()) return "Add at least one size row.";
        if (items.size() > 100) return "Use at most 100 size rows per record.";
        for (Item item : items) {
            if (item.size == null || item.size.trim().isEmpty()) return "Enter a size or condition for every row.";
            // Match the historical-only categories accepted by the Supabase save function.
            String category = item.size.trim().toUpperCase(Locale.US).replace(' ', '_');
            if (!category.equals("EXTRA_SMALL") && !category.equals("SMALL")
                    && !category.equals("MEDIUM") && !category.equals("LARGE")
                    && !category.equals("JUMBO") && !category.equals("BLEMISHED")) {
                return "Choose a listed size or condition for every row.";
            }
            if (item.weightKilograms == null || !Double.isFinite(item.weightKilograms)
                    || item.weightKilograms <= 0
                    || BigDecimal.valueOf(item.weightKilograms).scale() > 3) {
                return "Enter a positive weight in kg (up to 3 decimals) for every row.";
            }
            if (!Double.isFinite(item.pricePerKilogram) || item.pricePerKilogram < 0) {
                return "Enter a valid price per kilogram for every row.";
            }
            if (BigDecimal.valueOf(item.pricePerKilogram).scale() > 2) {
                return "Use at most 2 decimal places for the price per kg.";
            }
            if (!Double.isFinite(item.subtotal) || item.subtotal < 0) {
                return "Enter a valid subtotal for every row.";
            }
            if (BigDecimal.valueOf(item.subtotal).scale() > 2) {
                return "Use at most 2 decimal places for each subtotal.";
            }
            // Compare the owner's paper total with the price calculation, to the cent.
            BigDecimal expected = BigDecimal.valueOf(item.weightKilograms)
                    .multiply(BigDecimal.valueOf(item.pricePerKilogram))
                    .setScale(2, RoundingMode.HALF_UP);
            if (expected.subtract(BigDecimal.valueOf(item.subtotal)).abs()
                    .compareTo(new BigDecimal("0.01")) > 0) {
                return "Each subtotal must match kilograms × price per kg (within ₱0.01).";
            }
            if (item.actualSales == null) {
                return "Choose actual sales or estimated value for every row.";
            }
        }
        if (paperGrandTotal != null) {
            if (!Double.isFinite(paperGrandTotal) || paperGrandTotal < 0) {
                return "Enter a valid grand total from the paper.";
            }
            if (Math.abs(subtotalSum() - paperGrandTotal) > 0.01) {
                return "Grand total on paper does not match the subtotals.";
            }
        }
        return "";
    }

    public static final class Item {
        private final String size;
        private final Double weightKilograms;
        // Present only on rows saved before the kilogram correction; never reinterpreted.
        private final Integer pieces;
        private final double pricePerKilogram;
        private final double subtotal;
        private final Boolean actualSales;

        public Item(String size, double weightKilograms, double pricePerKilogram, double subtotal) {
            this(size, weightKilograms, pricePerKilogram, subtotal, null);
        }

        public Item(String size, double weightKilograms, double pricePerKilogram, double subtotal,
                    Boolean actualSales) {
            this.size = size;
            this.weightKilograms = weightKilograms;
            this.pieces = null;
            this.pricePerKilogram = pricePerKilogram;
            this.subtotal = subtotal;
            this.actualSales = actualSales;
        }

        public String getSize() { return size; }
        public Double getWeightKilograms() { return weightKilograms; }
        public Integer getLegacyPieces() { return pieces; }
        public double getPricePerKilogram() { return pricePerKilogram; }
        public double getSubtotal() { return subtotal; }
        public Boolean getActualSales() { return actualSales; }
    }
}
