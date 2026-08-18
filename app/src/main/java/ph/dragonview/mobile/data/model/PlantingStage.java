package ph.dragonview.mobile.data.model;

import java.util.Locale;

public enum PlantingStage {
    PLANTED("Stem cutting planted", 5,
            "The cutting has been placed beside its support. A newly planted cutting may show little visible change while it settles.",
            "Confirm the cutting is stable. Keep the planting area well drained. Record the starting condition and a clear photo.",
            "Watch for a soft or darkened base, severe discoloration, or an unstable cutting."),
    ESTABLISHMENT("Establishment", 12,
            "The cutting should generally remain firm and green. Root development may be occurring even when no shoot is visible.",
            "Check stability, drainage, stem color, and the condition of the base. Compare with the first record.",
            "Watch for persistent saturation, spreading dark areas, softness, or collapse."),
    ROOTING_AND_BUD("Rooting and bud emergence", 22,
            "Roots and a new bud may begin developing. Timing varies between rooted and unrooted cuttings.",
            "Look for a new bud or shoot without forcing the plant. Record whether growth is visible and measure it when practical.",
            "No visible shoot is not automatically a failure. Check for deterioration before deciding that the cutting is unhealthy."),
    SHOOT_DEVELOPMENT("Primary shoot development", 34,
            "A healthy new shoot may elongate and become the main stem trained toward the support.",
            "Guide the strongest shoot toward the support and check that ties are loose enough to avoid damage.",
            "Watch for damaged ties, broken shoots, discoloration, or soft sections."),
    TRELLIS_TRAINING("Trellis training", 46,
            "The primary shoot continues growing upward. Growth speed depends on the cutting, weather, soil, and farm practices.",
            "Continue supporting the main shoot. Record height and inspect the post or trellis for stability.",
            "Watch for crowding, stem damage, weak supports, or shoots trailing on the ground."),
    TRELLIS_REACHED("Trellis reached", 55,
            "The main shoot has reached the top support and is ready to transition toward canopy development.",
            "Confirm the support is strong and record the date the trellis was reached. Follow locally validated pruning guidance.",
            "Do not prune or top solely because of the app; confirm the practice for the farm and variety."),
    CANOPY_DEVELOPMENT("Canopy development", 64,
            "Side shoots develop around the top support and begin forming the productive canopy.",
            "Keep the canopy manageable, record major new shoots, and inspect for damaged or crowded growth.",
            "Watch for dense tangles, damaged stems, or growth that interferes with farm work."),
    FLOWER_BUD("Flower-bud emergence", 72,
            "A reproductive bud is visible and begins enlarging before flowering.",
            "Record the first bud date and photograph the same bud consistently when possible.",
            "Avoid treating every new growth point as a flower bud until its development is clear."),
    FLOWERING("Flowering", 78,
            "The flower opens, usually for a short period. This milestone starts the app's separate fruit-age timeline.",
            "Record the flower-opening date and any farm pollination activity. Add a photo and note the plant location.",
            "Flowering and pollination behavior vary by variety; use farm-specific advice when available."),
    FRUIT_SET("Fruit set", 84,
            "After successful flowering, the ovary begins swelling and a young fruit becomes visible.",
            "Confirm fruit set, record the date, and continue photographing the same fruit for comparison.",
            "A flower does not always become a fruit. Record failed set without changing the original flowering history."),
    FRUIT_DEVELOPMENT("Fruit development", 90,
            "The fruit increases in size while its peel remains immature.",
            "Record size observations and inspect the fruit and supporting stem for damage.",
            "Watch for splitting, rot, physical damage, or abnormal discoloration and seek qualified advice when needed."),
    FRUIT_MATURATION("Fruit maturation", 96,
            "Peel color and other visible maturity characteristics begin changing as harvest approaches.",
            "Compare color and condition over time. Use the estimated window as guidance, not an automatic harvest instruction.",
            "Variety and local conditions affect maturity timing. Do not rely on color alone when farm standards require other checks."),
    READY_FOR_HARVEST("Ready for harvest", 100,
            "The farmer has confirmed that the fruit meets the farm's harvest criteria.",
            "Record the confirmation and create a harvest batch in Inventory when the fruit is collected.",
            "The app does not independently certify maturity or quality."),
    HARVESTED("Harvest recorded", 100,
            "The current fruiting cycle has been harvested. The perennial plant remains active for future cycles.",
            "Keep the planting record and begin another flowering-to-harvest cycle when new buds appear.",
            "Do not close or delete the plant record simply because one harvest cycle is complete.");

    private final String displayName;
    private final int progressPercent;
    private final String expectedAppearance;
    private final String tasks;
    private final String warnings;

    PlantingStage(String displayName, int progressPercent,
                  String expectedAppearance, String tasks, String warnings) {
        this.displayName = displayName;
        this.progressPercent = progressPercent;
        this.expectedAppearance = expectedAppearance;
        this.tasks = tasks;
        this.warnings = warnings;
    }

    public String getDisplayName() { return displayName; }
    public int getProgressPercent() { return progressPercent; }
    public String getExpectedAppearance() { return expectedAppearance; }
    public String getTasks() { return tasks; }
    public String getWarnings() { return warnings; }

    public PlantingStage next() {
        PlantingStage[] values = values();
        return ordinal() >= values.length - 1 ? this : values[ordinal() + 1];
    }

    public static PlantingStage fromCode(String value) {
        if (value == null || value.trim().isEmpty()) return PLANTED;
        try {
            return valueOf(value.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return PLANTED;
        }
    }

    public static PlantingStage suggestedForAge(int elapsedDays) {
        if (elapsedDays < 1) return PLANTED;
        if (elapsedDays < 15) return ESTABLISHMENT;
        if (elapsedDays < 31) return ROOTING_AND_BUD;
        if (elapsedDays < 61) return SHOOT_DEVELOPMENT;
        return TRELLIS_TRAINING;
    }
}
