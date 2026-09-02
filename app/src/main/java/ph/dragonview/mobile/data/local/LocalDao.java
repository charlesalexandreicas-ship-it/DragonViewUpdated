package ph.dragonview.mobile.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import ph.dragonview.mobile.data.local.LocalEntities.Inventory;
import ph.dragonview.mobile.data.local.LocalEntities.InventoryTransaction;
import ph.dragonview.mobile.data.local.LocalEntities.GraftingEvent;
import ph.dragonview.mobile.data.local.LocalEntities.Planting;
import ph.dragonview.mobile.data.local.LocalEntities.PlantingUpdate;
import ph.dragonview.mobile.data.local.LocalEntities.Price;
import ph.dragonview.mobile.data.local.LocalEntities.RecordArchiveEvent;
import ph.dragonview.mobile.data.local.LocalEntities.Sale;
import ph.dragonview.mobile.data.local.LocalEntities.SaleItem;
import ph.dragonview.mobile.data.local.LocalEntities.User;

@Dao
public interface LocalDao {
    @Insert long insertUser(User user);
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User findUserByEmail(String email);
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User findUserById(long id);

    @Insert long insertInventory(Inventory inventory);
    @Update void updateInventory(Inventory inventory);
    @Query("SELECT * FROM inventory WHERE userId = :userId AND archivedAt IS NULL " +
            "AND availablePieces > 0 " +
            "ORDER BY harvestDate, id")
    List<Inventory> activeInventory(long userId);
    @Query("SELECT * FROM inventory WHERE userId = :userId AND archivedAt IS NULL " +
            "AND batchNumber = :batch " +
            "ORDER BY size, grade, id")
    List<Inventory> inventoryByBatch(long userId, String batch);
    @Query("SELECT * FROM inventory WHERE id = :id AND userId = :userId " +
            "AND archivedAt IS NULL LIMIT 1")
    Inventory inventoryById(long id, long userId);
    @Query("SELECT * FROM inventory WHERE userId = :userId AND archivedAt IS NULL " +
            "AND size = :size " +
            "AND grade = :grade AND availablePieces > 0 ORDER BY harvestDate, id")
    List<Inventory> fifoInventory(long userId, String size, String grade);
    @Query("SELECT * FROM inventory WHERE userId = :userId AND archivedAt IS NULL " +
            "AND batchNumber = :batch " +
            "AND size = :size AND grade = :grade LIMIT 1")
    Inventory inventoryCombination(
            long userId, String batch, String size, String grade);
    @Query("SELECT COUNT(*) FROM inventory WHERE userId = :userId AND batchNumber = :batch")
    int batchItemCount(long userId, String batch);
    @Query("SELECT COALESCE(SUM(availablePieces), 0) FROM inventory " +
            "WHERE userId = :userId AND archivedAt IS NULL")
    int inventoryPieces(long userId);
    @Query("SELECT COUNT(DISTINCT batchNumber) FROM inventory " +
            "WHERE userId = :userId AND archivedAt IS NULL AND availablePieces > 0")
    int activeBatchCount(long userId);
    @Query("UPDATE inventory SET archivedAt = :archivedAt, archiveReason = :reason " +
            "WHERE userId = :userId AND batchNumber = :batch AND archivedAt IS NULL")
    int archiveInventoryBatch(long userId, String batch, long archivedAt, String reason);
    @Query("UPDATE inventory SET archivedAt = NULL, archiveReason = NULL " +
            "WHERE userId = :userId AND batchNumber = :batch AND archivedAt IS NOT NULL")
    int restoreInventoryBatch(long userId, String batch);
    @Query("SELECT * FROM inventory WHERE userId = :userId AND archivedAt IS NOT NULL " +
            "ORDER BY archivedAt DESC, id")
    List<Inventory> archivedInventory(long userId);

    @Insert long insertInventoryTransaction(InventoryTransaction transaction);
    @Query("SELECT * FROM inventory_transactions WHERE userId = :userId " +
            "AND inventoryId = :inventoryId ORDER BY createdAt, id")
    List<InventoryTransaction> inventoryTransactions(long userId, long inventoryId);

    @Insert long insertPrice(Price price);
    @Query("UPDATE prices SET active = 0 WHERE userId = :userId AND grade = :grade " +
            "AND ((size IS NULL AND :size IS NULL) OR size = :size) AND active = 1")
    void deactivatePrice(long userId, String grade, String size);
    @Query("SELECT * FROM prices WHERE userId = :userId AND active = 1 " +
            "ORDER BY grade, size")
    List<Price> activePrices(long userId);
    @Query("SELECT * FROM prices WHERE userId = :userId AND grade = :grade " +
            "AND ((grade = 'C' AND size IS NULL) OR size = :size) AND active = 1 " +
            "ORDER BY effectiveAt DESC LIMIT 1")
    Price activePrice(long userId, String grade, String size);

    @Insert long insertSale(Sale sale);
    @Insert long insertSaleItem(SaleItem item);
    @Query("SELECT * FROM sales WHERE userId = :userId AND archivedAt IS NULL " +
            "ORDER BY completedAt DESC, id DESC")
    List<Sale> sales(long userId);
    @Query("SELECT * FROM sales WHERE userId = :userId AND id = :saleId " +
            "AND archivedAt IS NULL LIMIT 1")
    Sale saleById(long userId, long saleId);
    @Query("UPDATE sales SET archivedAt = :archivedAt, archiveReason = :reason " +
            "WHERE userId = :userId AND id = :saleId AND archivedAt IS NULL")
    int archiveSale(long userId, long saleId, long archivedAt, String reason);
    @Query("UPDATE sales SET archivedAt = NULL, archiveReason = NULL " +
            "WHERE userId = :userId AND id = :saleId AND archivedAt IS NOT NULL")
    int restoreSale(long userId, long saleId);
    @Query("SELECT * FROM sales WHERE userId = :userId AND archivedAt IS NOT NULL " +
            "ORDER BY archivedAt DESC, id DESC")
    List<Sale> archivedSales(long userId);
    @Query("SELECT * FROM sales WHERE userId = :userId " +
            "AND completedAt >= :start AND completedAt < :end ORDER BY completedAt")
    List<Sale> salesInPeriod(long userId, long start, long end);
    @Query("SELECT * FROM sale_items WHERE userId = :userId AND saleId IN (:saleIds)")
    List<SaleItem> saleItems(long userId, List<Long> saleIds);
    @Query("SELECT COALESCE(SUM(pieces), 0) FROM sale_items WHERE saleId = :saleId")
    int totalPiecesForSale(long saleId);
    @Query("SELECT COUNT(*) FROM sales WHERE userId = :userId " +
            "AND completedAt >= :start AND completedAt < :end")
    int saleCount(long userId, long start, long end);
    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM sales WHERE userId = :userId " +
            "AND completedAt >= :start AND completedAt < :end")
    double saleRevenue(long userId, long start, long end);

    @Insert long insertPlanting(Planting planting);
    @Update void updatePlanting(Planting planting);
    @Query("SELECT * FROM planting WHERE userId = :userId AND archivedAt IS NULL " +
            "ORDER BY graftingDate, id")
    List<Planting> planting(long userId);
    @Query("SELECT * FROM planting WHERE id = :id AND userId = :userId " +
            "AND archivedAt IS NULL LIMIT 1")
    Planting plantingById(long id, long userId);
    @Query("SELECT COUNT(*) FROM planting WHERE userId = :userId AND archivedAt IS NULL")
    int plantingCount(long userId);
    @Query("UPDATE planting SET archivedAt = :archivedAt, archiveReason = :reason, " +
            "updatedAt = :archivedAt WHERE userId = :userId AND id = :plantingId " +
            "AND archivedAt IS NULL")
    int archivePlanting(long userId, long plantingId, long archivedAt, String reason);
    @Query("UPDATE planting SET archivedAt = NULL, archiveReason = NULL, " +
            "updatedAt = :restoredAt WHERE userId = :userId AND id = :plantingId " +
            "AND archivedAt IS NOT NULL")
    int restorePlanting(long userId, long plantingId, long restoredAt);
    @Query("SELECT * FROM planting WHERE userId = :userId AND archivedAt IS NOT NULL " +
            "ORDER BY archivedAt DESC, id DESC")
    List<Planting> archivedPlantings(long userId);

    @Insert long insertGraftingEvent(GraftingEvent event);
    @Query("SELECT * FROM grafting_events WHERE userId = :userId " +
            "AND plantingId = :plantingId ORDER BY graftingDate DESC, id DESC")
    List<GraftingEvent> graftingEvents(long userId, long plantingId);

    @Insert long insertPlantingUpdate(PlantingUpdate update);
    @Query("SELECT * FROM planting_updates WHERE userId = :userId " +
            "AND plantingId = :plantingId ORDER BY recordedDate DESC, createdAt DESC, id DESC")
    List<PlantingUpdate> plantingUpdates(long userId, long plantingId);

    @Insert long insertArchiveEvent(RecordArchiveEvent event);
    @Query("SELECT * FROM record_archive_events WHERE userId = :userId " +
            "ORDER BY eventAt DESC, id DESC")
    List<RecordArchiveEvent> archiveHistory(long userId);
}
