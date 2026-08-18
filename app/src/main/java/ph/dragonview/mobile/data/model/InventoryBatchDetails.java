package ph.dragonview.mobile.data.model;

import java.util.ArrayList;
import java.util.List;

public final class InventoryBatchDetails {
    private final String batchNumber;
    private final String harvestDate;
    private final int availablePieces;
    private final List<Item> items;

    public InventoryBatchDetails(String batchNumber, String harvestDate,
                                 int availablePieces, List<Item> items) {
        this.batchNumber = batchNumber;
        this.harvestDate = harvestDate;
        this.availablePieces = availablePieces;
        this.items = new ArrayList<>(items);
    }

    public String getBatchNumber() { return batchNumber; }
    public String getHarvestDate() { return harvestDate; }
    public int getAvailablePieces() { return availablePieces; }
    public List<Item> getItems() { return new ArrayList<>(items); }

    public static final class Item {
        private final long id;
        private final String size;
        private final String grade;
        private final int originalPieces;
        private final int availablePieces;
        private final boolean nextOut;
        private final List<InventoryDetails.Transaction> transactions;

        public Item(long id, String size, String grade, int originalPieces,
                    int availablePieces, boolean nextOut,
                    List<InventoryDetails.Transaction> transactions) {
            this.id = id;
            this.size = size;
            this.grade = grade;
            this.originalPieces = originalPieces;
            this.availablePieces = availablePieces;
            this.nextOut = nextOut;
            this.transactions = new ArrayList<>(transactions);
        }

        public long getId() { return id; }
        public String getSize() { return size; }
        public String getGrade() { return grade; }
        public int getOriginalPieces() { return originalPieces; }
        public int getAvailablePieces() { return availablePieces; }
        public boolean isNextOut() { return nextOut; }
        public List<InventoryDetails.Transaction> getTransactions() {
            return new ArrayList<>(transactions);
        }
    }
}
