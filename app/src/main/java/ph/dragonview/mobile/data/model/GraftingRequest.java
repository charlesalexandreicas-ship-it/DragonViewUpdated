package ph.dragonview.mobile.data.model;

public final class GraftingRequest {
    private final long plantingId;
    private final String graftingDate;
    private final String scionVariety;
    private final String note;

    public GraftingRequest(long plantingId, String graftingDate,
                           String scionVariety, String note) {
        this.plantingId = plantingId;
        this.graftingDate = graftingDate;
        this.scionVariety = scionVariety;
        this.note = note;
    }

    public long getPlantingId() { return plantingId; }
    public String getGraftingDate() { return graftingDate; }
    public String getScionVariety() { return scionVariety; }
    public String getNote() { return note; }
}
