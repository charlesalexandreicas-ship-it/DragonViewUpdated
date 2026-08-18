package ph.dragonview.mobile.data.model;

import java.util.List;

public final class InventoryDetails {
    private long id;
    private String batchNumber, harvestDate, size, grade;
    private int availablePieces;
    private List<Transaction> transactions;
    public InventoryDetails(long id, String batchNumber, String harvestDate,
                            String size, String grade, int availablePieces,
                            List<Transaction> transactions) {
        this.id = id; this.batchNumber = batchNumber; this.harvestDate = harvestDate;
        this.size = size; this.grade = grade; this.availablePieces = availablePieces;
        this.transactions = transactions;
    }
    public long getId() { return id; }
    public String getBatchNumber() { return batchNumber; }
    public String getHarvestDate() { return harvestDate; }
    public String getSize() { return size; }
    public String getGrade() { return grade; }
    public int getAvailablePieces() { return availablePieces; }
    public List<Transaction> getTransactions() { return transactions; }
    public static final class Transaction {
        private long id; private String type, remarks, createdAt, createdBy; private int pieces;
        public Transaction(long id, String type, String remarks, String createdAt,
                           String createdBy, int pieces) {
            this.id = id; this.type = type; this.remarks = remarks;
            this.createdAt = createdAt; this.createdBy = createdBy; this.pieces = pieces;
        }
        public long getId() { return id; } public String getType() { return type; }
        public String getRemarks() { return remarks; } public String getCreatedAt() { return createdAt; }
        public String getCreatedBy() { return createdBy; } public int getPieces() { return pieces; }
    }
}
