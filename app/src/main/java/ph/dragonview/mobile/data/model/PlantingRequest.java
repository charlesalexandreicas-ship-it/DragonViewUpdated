package ph.dragonview.mobile.data.model;

public final class PlantingRequest {
    private final String recordNumber;
    private final String graftingDate;
    private final String variety;
    private final String location;
    private final int numberOfPlants;
    private final String cuttingType;

    public PlantingRequest(String recordNumber, String graftingDate, String variety,
                           String location, int numberOfPlants, String cuttingType) {
        this.recordNumber = recordNumber;
        this.graftingDate = graftingDate;
        this.variety = variety;
        this.location = location;
        this.numberOfPlants = numberOfPlants;
        this.cuttingType = cuttingType;
    }
    public String getRecordNumber() { return recordNumber; }
    public String getGraftingDate() { return graftingDate; }
    public String getVariety() { return variety; }
    public String getLocation() { return location; }
    public int getNumberOfPlants() { return numberOfPlants; }
    public String getCuttingType() { return cuttingType; }
}
