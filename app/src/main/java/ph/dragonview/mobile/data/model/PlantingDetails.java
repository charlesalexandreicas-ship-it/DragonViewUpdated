package ph.dragonview.mobile.data.model;

import java.util.ArrayList;
import java.util.List;

public final class PlantingDetails {
    private final PlantingGroup group;
    private final PlantingStage guidance;
    private final List<Update> updates;

    public PlantingDetails(PlantingGroup group, PlantingStage guidance,
                           List<Update> updates) {
        this.group = group;
        this.guidance = guidance;
        this.updates = new ArrayList<>(updates);
    }

    public PlantingGroup getGroup() { return group; }
    public PlantingStage getGuidance() { return guidance; }
    public List<Update> getUpdates() { return new ArrayList<>(updates); }

    public static final class Update {
        private final long id;
        private final String type;
        private final String stage;
        private final String note;
        private final Double measurementCentimeters;
        private final String photoUri;
        private final String recordedDate;

        public Update(long id, String type, String stage, String note,
                      Double measurementCentimeters, String photoUri,
                      String recordedDate) {
            this.id = id;
            this.type = type;
            this.stage = stage;
            this.note = note;
            this.measurementCentimeters = measurementCentimeters;
            this.photoUri = photoUri;
            this.recordedDate = recordedDate;
        }

        public long getId() { return id; }
        public String getType() { return type; }
        public String getStage() { return stage; }
        public String getNote() { return note; }
        public Double getMeasurementCentimeters() { return measurementCentimeters; }
        public String getPhotoUri() { return photoUri; }
        public String getRecordedDate() { return recordedDate; }
    }
}
