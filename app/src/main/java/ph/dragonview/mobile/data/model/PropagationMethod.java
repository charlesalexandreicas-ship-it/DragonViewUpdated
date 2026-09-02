package ph.dragonview.mobile.data.model;

import java.util.Locale;

public enum PropagationMethod {
    SEED("From seed", "seeds"),
    STEM_CUTTING("Stem cutting", "cuttings"),
    GRAFTED("Grafted", "grafted plants");

    private final String displayName;
    private final String quantityLabel;

    PropagationMethod(String displayName, String quantityLabel) {
        this.displayName = displayName;
        this.quantityLabel = quantityLabel;
    }

    public String getDisplayName() { return displayName; }
    public String getQuantityLabel() { return quantityLabel; }

    public static PropagationMethod fromCode(String value) {
        if (value == null || value.trim().isEmpty()) return STEM_CUTTING;
        try {
            return valueOf(value.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return STEM_CUTTING;
        }
    }
}
