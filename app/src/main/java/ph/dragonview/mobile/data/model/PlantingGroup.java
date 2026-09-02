package ph.dragonview.mobile.data.model;

public final class PlantingGroup {
    private final long id;
    private final String recordNumber;
    private final String plantingDate;
    private final String variety;
    private final String location;
    private final int numberOfPlants;
    private final String cuttingType;
    private final PropagationMethod propagationMethod;
    private final int elapsedDays;
    private final PlantingStage currentStage;
    private final PlantingStage suggestedStage;
    private final Integer fruitAgeDays;
    private final String estimatedHarvestWindow;

    public PlantingGroup(long id, String recordNumber, String plantingDate,
                         String variety, String location, int numberOfPlants,
                         String cuttingType, int elapsedDays,
                         PropagationMethod propagationMethod,
                         PlantingStage currentStage,
                         PlantingStage suggestedStage,
                         Integer fruitAgeDays,
                         String estimatedHarvestWindow) {
        this.id = id;
        this.recordNumber = recordNumber;
        this.plantingDate = plantingDate;
        this.variety = variety;
        this.location = location;
        this.numberOfPlants = numberOfPlants;
        this.cuttingType = cuttingType;
        this.propagationMethod = propagationMethod;
        this.elapsedDays = elapsedDays;
        this.currentStage = currentStage;
        this.suggestedStage = suggestedStage;
        this.fruitAgeDays = fruitAgeDays;
        this.estimatedHarvestWindow = estimatedHarvestWindow;
    }

    public long getId() { return id; }
    public String getRecordNumber() { return recordNumber; }
    public String getPlantingDate() { return plantingDate; }
    public String getGraftingDate() { return plantingDate; }
    public String getVariety() { return variety; }
    public String getLocation() { return location; }
    public int getNumberOfPlants() { return numberOfPlants; }
    public String getCuttingType() { return cuttingType; }
    public PropagationMethod getPropagationMethod() { return propagationMethod; }
    public int getElapsedDays() { return elapsedDays; }
    public PlantingStage getCurrentStage() { return currentStage; }
    public PlantingStage getSuggestedStage() { return suggestedStage; }
    public String getStage() { return currentStage.name(); }
    public Integer getFruitAgeDays() { return fruitAgeDays; }
    public String getEstimatedHarvestWindow() { return estimatedHarvestWindow; }
    public double getProgressPercent() { return currentStage.getProgressPercent(); }
    public boolean isReadyForHarvest() {
        return currentStage == PlantingStage.READY_FOR_HARVEST
                || currentStage == PlantingStage.FRUIT_MATURATION;
    }
}
