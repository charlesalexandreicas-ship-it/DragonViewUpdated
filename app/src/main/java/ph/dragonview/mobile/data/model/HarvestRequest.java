package ph.dragonview.mobile.data.model;

import java.util.ArrayList;
import java.util.List;

public final class HarvestRequest {
    private final String batchNumber;
    private final String harvestDate;
    private final List<Item> items;

    public HarvestRequest(String batchNumber, String harvestDate, List<Item> items) {
        this.batchNumber = batchNumber;
        this.harvestDate = harvestDate;
        this.items = new ArrayList<>(items);
    }
    public String getBatchNumber() { return batchNumber; }
    public String getHarvestDate() { return harvestDate; }
    public List<Item> getItems() { return new ArrayList<>(items); }

    public static final class Item {
        private final String size;
        private final String grade;
        private final int pieces;
        public Item(String size, String grade, int pieces) {
            this.size = size;
            this.grade = grade;
            this.pieces = pieces;
        }

        public String getSize() { return size; }
        public String getGrade() { return grade; }
        public int getPieces() { return pieces; }
    }
}
