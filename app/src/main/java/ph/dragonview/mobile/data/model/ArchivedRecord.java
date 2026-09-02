package ph.dragonview.mobile.data.model;

public final class ArchivedRecord {
    public enum Type {
        PLANTING("Planting record"),
        INVENTORY_BATCH("Inventory batch"),
        SALE("Sale record");

        private final String displayName;
        Type(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private final Type type;
    private final String key;
    private final String title;
    private final String details;
    private final String reason;
    private final long archivedAt;

    public ArchivedRecord(Type type, String key, String title, String details,
                          String reason, long archivedAt) {
        this.type = type;
        this.key = key;
        this.title = title;
        this.details = details;
        this.reason = reason;
        this.archivedAt = archivedAt;
    }

    public Type getType() { return type; }
    public String getKey() { return key; }
    public String getTitle() { return title; }
    public String getDetails() { return details; }
    public String getReason() { return reason; }
    public long getArchivedAt() { return archivedAt; }
}
