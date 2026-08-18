package ph.dragonview.mobile.data.model;

public final class PlantingUpdateRequest {
    private final long plantingId;
    private final String note;
    private final Double measurementCentimeters;
    private final String photoUri;
    private final String recordedDate;

    public PlantingUpdateRequest(long plantingId, String note,
                                 Double measurementCentimeters,
                                 String photoUri, String recordedDate) {
        this.plantingId = plantingId;
        this.note = note;
        this.measurementCentimeters = measurementCentimeters;
        this.photoUri = photoUri;
        this.recordedDate = recordedDate;
    }

    public long getPlantingId() { return plantingId; }
    public String getNote() { return note; }
    public Double getMeasurementCentimeters() { return measurementCentimeters; }
    public String getPhotoUri() { return photoUri; }
    public String getRecordedDate() { return recordedDate; }
}
