package ph.dragonview.mobile.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

public final class LocalEntities {
    private LocalEntities() {}

    @Entity(
            tableName = "users",
            indices = {@Index(value = {"email"}, unique = true)}
    )
    public static final class User {
        @PrimaryKey(autoGenerate = true) public long id;
        @NonNull public String email = "";
        @NonNull public String displayName = "";
        @NonNull public String passwordHash = "";
        @NonNull public String passwordSalt = "";
        public long createdAt;
    }

    @Entity(
            tableName = "inventory",
            foreignKeys = @ForeignKey(
                    entity = User.class,
                    parentColumns = "id",
                    childColumns = "userId",
                    onDelete = ForeignKey.CASCADE),
            indices = {
                    @Index("userId"),
                    @Index(
                            value = {"userId", "batchNumber", "size", "grade"},
                            unique = true)
            }
    )
    public static final class Inventory {
        @PrimaryKey(autoGenerate = true) public long id;
        public long userId;
        @NonNull public String batchNumber = "";
        @NonNull public String harvestDate = "";
        @NonNull public String size = "";
        @NonNull public String grade = "";
        public int originalPieces;
        public int availablePieces;
        public long createdAt;
        public Long archivedAt;
        public String archiveReason;
    }

    @Entity(
            tableName = "inventory_transactions",
            foreignKeys = @ForeignKey(
                    entity = Inventory.class,
                    parentColumns = "id",
                    childColumns = "inventoryId",
                    onDelete = ForeignKey.CASCADE),
            indices = {@Index("inventoryId"), @Index("userId")}
    )
    public static final class InventoryTransaction {
        @PrimaryKey(autoGenerate = true) public long id;
        public long userId;
        public long inventoryId;
        @NonNull public String type = "";
        public int pieces;
        public String remarks;
        @NonNull public String createdBy = "";
        public long createdAt;
    }

    @Entity(
            tableName = "prices",
            foreignKeys = @ForeignKey(
                    entity = User.class,
                    parentColumns = "id",
                    childColumns = "userId",
                    onDelete = ForeignKey.CASCADE),
            indices = {@Index("userId")}
    )
    public static final class Price {
        @PrimaryKey(autoGenerate = true) public long id;
        public long userId;
        @NonNull public String grade = "";
        public String size;
        public double pricePerKilogram;
        public boolean active;
        public long effectiveAt;
    }

    @Entity(
            tableName = "sales",
            foreignKeys = @ForeignKey(
                    entity = User.class,
                    parentColumns = "id",
                    childColumns = "userId",
                    onDelete = ForeignKey.CASCADE),
            indices = {@Index("userId"), @Index("completedAt")}
    )
    public static final class Sale {
        @PrimaryKey(autoGenerate = true) public long id;
        public long userId;
        @NonNull public String customerName = "";
        @NonNull public String customerAddress = "";
        @NonNull public String customerContact = "";
        @NonNull public String customerEmail = "";
        @NonNull public String status = "COMPLETED";
        @NonNull public String paymentStatus = "PAID";
        @NonNull public String paymentMethod = "";
        public double amountPaid;
        public double totalAmount;
        public double changeDue;
        public String paymentReference;
        public String provider;
        public long completedAt;
        public Long archivedAt;
        public String archiveReason;
    }

    @Entity(
            tableName = "sale_items",
            foreignKeys = @ForeignKey(
                    entity = Sale.class,
                    parentColumns = "id",
                    childColumns = "saleId",
                    onDelete = ForeignKey.CASCADE),
            indices = {@Index("saleId"), @Index("userId")}
    )
    public static final class SaleItem {
        @PrimaryKey(autoGenerate = true) public long id;
        public long userId;
        public long saleId;
        @NonNull public String size = "";
        @NonNull public String grade = "";
        public int pieces;
        public double weightKilograms;
        public double pricePerKilogram;
        public double subtotal;
    }

    @Entity(
            tableName = "planting",
            foreignKeys = @ForeignKey(
                    entity = User.class,
                    parentColumns = "id",
                    childColumns = "userId",
                    onDelete = ForeignKey.CASCADE),
            indices = {
                    @Index("userId"),
                    @Index(value = {"userId", "recordNumber"}, unique = true)
            }
    )
    public static final class Planting {
        @PrimaryKey(autoGenerate = true) public long id;
        public long userId;
        @NonNull public String recordNumber = "";
        @NonNull public String graftingDate = "";
        @NonNull public String variety = "";
        @NonNull public String location = "";
        @NonNull public String propagationMethod = "STEM_CUTTING";
        @NonNull public String cuttingType = "UNROOTED";
        @NonNull public String currentStage = "PLANTED";
        public String floweringDate;
        public int numberOfPlants;
        public long createdAt;
        public long updatedAt;
        public Long archivedAt;
        public String archiveReason;
    }

    @Entity(
            tableName = "grafting_events",
            foreignKeys = {
                    @ForeignKey(
                            entity = User.class,
                            parentColumns = "id",
                            childColumns = "userId",
                            onDelete = ForeignKey.CASCADE),
                    @ForeignKey(
                            entity = Planting.class,
                            parentColumns = "id",
                            childColumns = "plantingId",
                            onDelete = ForeignKey.CASCADE)
            },
            indices = {@Index("userId"), @Index("plantingId")}
    )
    public static final class GraftingEvent {
        @PrimaryKey(autoGenerate = true) public long id;
        public long userId;
        public long plantingId;
        @NonNull public String graftingDate = "";
        @NonNull public String scionVariety = "";
        public String note;
        public long createdAt;
    }

    @Entity(
            tableName = "planting_updates",
            foreignKeys = {
                    @ForeignKey(
                            entity = User.class,
                            parentColumns = "id",
                            childColumns = "userId",
                            onDelete = ForeignKey.CASCADE),
                    @ForeignKey(
                            entity = Planting.class,
                            parentColumns = "id",
                            childColumns = "plantingId",
                            onDelete = ForeignKey.CASCADE)
            },
            indices = {@Index("userId"), @Index("plantingId")}
    )
    public static final class PlantingUpdate {
        @PrimaryKey(autoGenerate = true) public long id;
        public long userId;
        public long plantingId;
        @NonNull public String type = "OBSERVATION";
        public String stage;
        public String note;
        public Double measurementCentimeters;
        public String photoUri;
        @NonNull public String recordedDate = "";
        public long createdAt;
    }

    @Entity(
            tableName = "record_archive_events",
            foreignKeys = @ForeignKey(
                    entity = User.class,
                    parentColumns = "id",
                    childColumns = "userId",
                    onDelete = ForeignKey.CASCADE),
            indices = {@Index("userId"), @Index(value = {"recordType", "recordKey"})}
    )
    public static final class RecordArchiveEvent {
        @PrimaryKey(autoGenerate = true) public long id;
        public long userId;
        @NonNull public String recordType = "";
        @NonNull public String recordKey = "";
        @NonNull public String recordTitle = "";
        @NonNull public String action = "ARCHIVED";
        public String reason;
        public long eventAt;
    }
}
