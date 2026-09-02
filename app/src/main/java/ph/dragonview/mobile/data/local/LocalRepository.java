package ph.dragonview.mobile.data.local;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ph.dragonview.mobile.data.SessionManager;
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
import ph.dragonview.mobile.data.model.CompleteSaleRequest;
import ph.dragonview.mobile.data.model.ArchivedRecord;
import ph.dragonview.mobile.data.model.DashboardData;
import ph.dragonview.mobile.data.model.FruitPrice;
import ph.dragonview.mobile.data.model.GraftingRequest;
import ph.dragonview.mobile.data.model.HarvestRequest;
import ph.dragonview.mobile.data.model.InventoryDetails;
import ph.dragonview.mobile.data.model.InventoryBatch;
import ph.dragonview.mobile.data.model.InventoryBatchDetails;
import ph.dragonview.mobile.data.model.InventoryLot;
import ph.dragonview.mobile.data.model.PlantingDetails;
import ph.dragonview.mobile.data.model.PlantingGroup;
import ph.dragonview.mobile.data.model.PlantingRequest;
import ph.dragonview.mobile.data.model.PlantingStage;
import ph.dragonview.mobile.data.model.PlantingUpdateRequest;
import ph.dragonview.mobile.data.model.PropagationMethod;
import ph.dragonview.mobile.data.model.SaleSummary;
import ph.dragonview.mobile.data.model.SalesAnalytics;
import ph.dragonview.mobile.data.model.SessionUser;

public final class LocalRepository {
    public interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    private interface Task<T> { T run() throws Exception; }

    private static volatile LocalRepository instance;
    private final DragonViewDatabase database;
    private final LocalDao dao;
    private final SessionManager session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LocalRepository(Context context) {
        database = DragonViewDatabase.get(context);
        dao = database.localDao();
        session = new SessionManager(context.getApplicationContext());
    }

    public static LocalRepository get(Context context) {
        if (instance == null) {
            synchronized (LocalRepository.class) {
                if (instance == null) {
                    instance = new LocalRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void register(
            String displayName, String email, String password,
            Callback<SessionUser> callback
    ) {
        execute(() -> {
            String normalized = email.trim().toLowerCase(Locale.US);
            if (dao.findUserByEmail(normalized) != null) {
                throw new IllegalStateException(
                        "An account with this email already exists on this device.");
            }
            PasswordHasher.Credentials credentials = PasswordHasher.hash(password);
            User user = new User();
            user.email = normalized;
            user.displayName = displayName.trim();
            user.passwordHash = credentials.hash;
            user.passwordSalt = credentials.salt;
            user.createdAt = System.currentTimeMillis();
            user.id = dao.insertUser(user);
            seedPrices(user.id);
            return sessionUser(user);
        }, callback);
    }

    public void login(String email, String password, Callback<SessionUser> callback) {
        execute(() -> {
            User user = dao.findUserByEmail(email.trim().toLowerCase(Locale.US));
            if (user == null || !PasswordHasher.verify(
                    password, user.passwordHash, user.passwordSalt)) {
                throw new IllegalArgumentException(
                        "The email or password is incorrect.");
            }
            return sessionUser(user);
        }, callback);
    }

    public void inventory(Callback<List<InventoryLot>> callback) {
        execute(() -> {
            long userId = userId();
            List<InventoryLot> result = new ArrayList<>();
            Set<String> nextOutCategories = new HashSet<>();
            for (Inventory item : dao.activeInventory(userId)) {
                String category = item.size + "|" + item.grade;
                boolean nextOut = nextOutCategories.add(category);
                result.add(inventoryLot(item, nextOut));
            }
            return result;
        }, callback);
    }

    public void inventoryBatches(Callback<List<InventoryBatch>> callback) {
        execute(() -> {
            long userId = userId();
            Map<String, int[]> totals = new LinkedHashMap<>();
            Map<String, String> dates = new LinkedHashMap<>();
            Set<String> nextOutCategories = new HashSet<>();
            for (Inventory item : dao.activeInventory(userId)) {
                String category = item.size + "|" + item.grade;
                boolean nextOut = nextOutCategories.add(category);
                int[] values = totals.computeIfAbsent(
                        item.batchNumber, ignored -> new int[3]);
                values[0]++;
                values[1] += item.availablePieces;
                if (nextOut) values[2]++;
                dates.putIfAbsent(item.batchNumber, item.harvestDate);
            }
            List<InventoryBatch> result = new ArrayList<>();
            for (Map.Entry<String, int[]> entry : totals.entrySet()) {
                int[] values = entry.getValue();
                result.add(new InventoryBatch(
                        entry.getKey(), dates.get(entry.getKey()),
                        values[0], values[1], values[2]));
            }
            return result;
        }, callback);
    }

    public void inventoryDetails(long id, Callback<InventoryDetails> callback) {
        execute(() -> {
            long userId = userId();
            Inventory item = dao.inventoryById(id, userId);
            if (item == null) throw new IllegalArgumentException("Inventory not found.");
            List<InventoryDetails.Transaction> transactions = new ArrayList<>();
            for (InventoryTransaction transaction
                    : dao.inventoryTransactions(userId, id)) {
                transactions.add(new InventoryDetails.Transaction(
                        transaction.id,
                        transaction.type,
                        transaction.remarks,
                        timestamp(transaction.createdAt),
                        transaction.createdBy,
                        transaction.pieces));
            }
            return new InventoryDetails(
                    item.id, item.batchNumber, item.harvestDate,
                    item.size, item.grade, item.availablePieces, transactions);
        }, callback);
    }

    public void inventoryBatchDetails(
            String batchNumber, Callback<InventoryBatchDetails> callback
    ) {
        execute(() -> {
            long userId = userId();
            List<Inventory> rows = dao.inventoryByBatch(userId, batchNumber);
            if (rows.isEmpty()) throw new IllegalArgumentException("Inventory batch not found.");
            Set<Long> nextOutIds = new HashSet<>();
            Set<String> categories = new HashSet<>();
            for (Inventory item : dao.activeInventory(userId)) {
                String category = item.size + "|" + item.grade;
                if (categories.add(category)) nextOutIds.add(item.id);
            }
            List<InventoryBatchDetails.Item> items = new ArrayList<>();
            int totalAvailable = 0;
            for (Inventory item : rows) {
                totalAvailable += item.availablePieces;
                List<InventoryDetails.Transaction> transactions = new ArrayList<>();
                for (InventoryTransaction transaction
                        : dao.inventoryTransactions(userId, item.id)) {
                    transactions.add(new InventoryDetails.Transaction(
                            transaction.id, transaction.type, transaction.remarks,
                            timestamp(transaction.createdAt), transaction.createdBy,
                            transaction.pieces));
                }
                items.add(new InventoryBatchDetails.Item(
                        item.id, item.size, item.grade, item.originalPieces,
                        item.availablePieces, nextOutIds.contains(item.id), transactions));
            }
            Inventory first = rows.get(0);
            return new InventoryBatchDetails(
                    first.batchNumber, first.harvestDate, totalAvailable, items);
        }, callback);
    }

    public void registerHarvest(
            HarvestRequest request, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            User user = requireUser(userId);
            database.runInTransaction(() -> {
                if (dao.batchItemCount(userId, request.getBatchNumber()) > 0) {
                    throw new IllegalStateException(
                            "This batch number already exists on this device.");
                }
                long now = System.currentTimeMillis();
                for (HarvestRequest.Item requested : request.getItems()) {
                    Inventory item = new Inventory();
                    item.userId = userId;
                    item.batchNumber = request.getBatchNumber();
                    item.harvestDate = request.getHarvestDate();
                    item.size = requested.getSize();
                    item.grade = requested.getGrade();
                    item.originalPieces = requested.getPieces();
                    item.availablePieces = requested.getPieces();
                    item.createdAt = now;
                    item.id = dao.insertInventory(item);
                    addTransaction(
                            userId, item.id, "HARVEST_IN", requested.getPieces(),
                            "Initial harvest registration", user.displayName);
                }
            });
            return null;
        }, callback);
    }

    public void adjustInventory(
            long inventoryId, int pieces, String reason, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            Inventory inventory = dao.inventoryById(inventoryId, userId);
            if (inventory == null) throw new IllegalArgumentException("Inventory not found.");
            if (inventory.availablePieces + pieces < 0) {
                throw new IllegalArgumentException(
                        "Adjustment exceeds available inventory.");
            }
            inventory.availablePieces += pieces;
            dao.updateInventory(inventory);
            addTransaction(
                    userId, inventoryId, "MANUAL_ADJUSTMENT", pieces,
                    reason, requireUser(userId).displayName);
            return null;
        }, callback);
    }

    public void regradeInventory(
            long inventoryId, String targetGrade, int pieces,
            String reason, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            User user = requireUser(userId);
            database.runInTransaction(() -> {
                Inventory source = dao.inventoryById(inventoryId, userId);
                if (source == null) {
                    throw new IllegalArgumentException("Inventory not found.");
                }
                validateRegrade(source.grade, targetGrade);
                if (pieces < 1 || pieces > source.availablePieces) {
                    throw new IllegalArgumentException(
                            "Regrading exceeds available inventory.");
                }
                Inventory target = dao.inventoryCombination(
                        userId, source.batchNumber, source.size, targetGrade);
                if (target == null) {
                    target = new Inventory();
                    target.userId = userId;
                    target.batchNumber = source.batchNumber;
                    target.harvestDate = source.harvestDate;
                    target.size = source.size;
                    target.grade = targetGrade;
                    target.originalPieces = pieces;
                    target.availablePieces = pieces;
                    target.createdAt = System.currentTimeMillis();
                    target.id = dao.insertInventory(target);
                } else {
                    target.originalPieces += pieces;
                    target.availablePieces += pieces;
                    dao.updateInventory(target);
                }
                source.availablePieces -= pieces;
                dao.updateInventory(source);
                addTransaction(userId, source.id, "REGRADING_OUT", -pieces,
                        reason, user.displayName);
                addTransaction(userId, target.id, "REGRADING_IN", pieces,
                        reason, user.displayName);
            });
            return null;
        }, callback);
    }

    public void prices(Callback<List<FruitPrice>> callback) {
        execute(() -> {
            List<FruitPrice> result = new ArrayList<>();
            for (Price price : dao.activePrices(userId())) {
                result.add(new FruitPrice(
                        price.id, price.grade, price.size, price.pricePerKilogram));
            }
            return result;
        }, callback);
    }

    public void configurePrice(
            String grade, String size, double value, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            database.runInTransaction(() -> {
                dao.deactivatePrice(userId, grade, size);
                Price price = new Price();
                price.userId = userId;
                price.grade = grade;
                price.size = "C".equals(grade) ? null : size;
                price.pricePerKilogram = value;
                price.active = true;
                price.effectiveAt = System.currentTimeMillis();
                dao.insertPrice(price);
            });
            return null;
        }, callback);
    }

    public void sales(Callback<List<SaleSummary>> callback) {
        execute(() -> {
            long userId = userId();
            List<SaleSummary> result = new ArrayList<>();
            for (Sale sale : dao.sales(userId)) {
                result.add(new SaleSummary(
                        sale.id, sale.customerName, sale.status,
                        sale.paymentStatus, sale.paymentMethod,
                        sale.totalAmount, dao.totalPiecesForSale(sale.id),
                        timestamp(sale.completedAt)));
            }
            return result;
        }, callback);
    }

    public void completeSale(CompleteSaleRequest request, Callback<Void> callback) {
        execute(() -> {
            long userId = userId();
            database.runInTransaction(() -> completeSaleTransaction(userId, request));
            return null;
        }, callback);
    }

    public void planting(Callback<List<PlantingGroup>> callback) {
        execute(() -> {
            List<PlantingGroup> result = new ArrayList<>();
            for (Planting planting : dao.planting(userId())) {
                result.add(plantingGroup(planting));
            }
            return result;
        }, callback);
    }

    public void plantingDetails(long plantingId, Callback<PlantingDetails> callback) {
        execute(() -> {
            long userId = userId();
            Planting planting = dao.plantingById(plantingId, userId);
            if (planting == null) throw new IllegalArgumentException("Planting record not found.");
            PlantingGroup group = plantingGroup(planting);
            List<PlantingDetails.Update> updates = new ArrayList<>();
            for (PlantingUpdate update : dao.plantingUpdates(userId, plantingId)) {
                updates.add(new PlantingDetails.Update(
                        update.id, update.type, update.stage, update.note,
                        update.measurementCentimeters, update.photoUri,
                        update.recordedDate));
            }
            return new PlantingDetails(group, group.getCurrentStage(), updates);
        }, callback);
    }

    public void createPlanting(
            PlantingRequest request, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            database.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                Planting planting = new Planting();
                planting.userId = userId;
                planting.recordNumber = request.getRecordNumber();
                planting.graftingDate = request.getGraftingDate();
                planting.variety = request.getVariety();
                planting.location = request.getLocation();
                planting.numberOfPlants = request.getNumberOfPlants();
                planting.propagationMethod = request.getPropagationMethod().name();
                planting.cuttingType = request.getCuttingType();
                PlantingStage initialStage = PlantingStage.firstFor(
                        request.getPropagationMethod());
                planting.currentStage = initialStage.name();
                planting.createdAt = now;
                planting.updatedAt = now;
                planting.id = dao.insertPlanting(planting);

                PlantingUpdate update = new PlantingUpdate();
                update.userId = userId;
                update.plantingId = planting.id;
                update.type = "MILESTONE";
                update.stage = initialStage.name();
                update.note = request.getPropagationMethod().getDisplayName()
                        + " record created.";
                update.recordedDate = request.getGraftingDate();
                update.createdAt = now;
                dao.insertPlantingUpdate(update);
            });
            return null;
        }, callback);
    }

    public void updatePlantingStage(
            long plantingId, PlantingStage stage, String recordedDate,
            String note, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            database.runInTransaction(() -> {
                Planting planting = dao.plantingById(plantingId, userId);
                if (planting == null) {
                    throw new IllegalArgumentException("Planting record not found.");
                }
                planting.currentStage = stage.name();
                if (stage == PlantingStage.FLOWERING) {
                    planting.floweringDate = recordedDate;
                }
                planting.updatedAt = System.currentTimeMillis();
                dao.updatePlanting(planting);

                PlantingUpdate update = new PlantingUpdate();
                update.userId = userId;
                update.plantingId = plantingId;
                update.type = "MILESTONE";
                update.stage = stage.name();
                update.note = note == null || note.trim().isEmpty()
                        ? "Stage confirmed: " + stage.getDisplayName() : note.trim();
                update.recordedDate = recordedDate;
                update.createdAt = System.currentTimeMillis();
                dao.insertPlantingUpdate(update);
            });
            return null;
        }, callback);
    }

    public void archiveInventoryBatch(
            String batchNumber, String reason, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            database.runInTransaction(() -> {
                List<Inventory> rows = dao.inventoryByBatch(userId, batchNumber);
                if (rows.isEmpty()) {
                    throw new IllegalArgumentException("Inventory batch not found.");
                }
                long now = System.currentTimeMillis();
                if (dao.archiveInventoryBatch(userId, batchNumber, now,
                        normalizedReason(reason)) < 1) {
                    throw new IllegalStateException("Inventory batch could not be archived.");
                }
                recordArchiveEvent(userId, ArchivedRecord.Type.INVENTORY_BATCH,
                        batchNumber, "Batch #" + batchNumber, "ARCHIVED", reason, now);
            });
            return null;
        }, callback);
    }

    public void archiveSale(long saleId, String reason, Callback<Void> callback) {
        execute(() -> {
            long userId = userId();
            database.runInTransaction(() -> {
                Sale sale = dao.saleById(userId, saleId);
                if (sale == null) throw new IllegalArgumentException("Sale not found.");
                long now = System.currentTimeMillis();
                if (dao.archiveSale(userId, saleId, now, normalizedReason(reason)) < 1) {
                    throw new IllegalStateException("Sale could not be archived.");
                }
                recordArchiveEvent(userId, ArchivedRecord.Type.SALE,
                        String.valueOf(saleId), "Sale #" + saleId, "ARCHIVED", reason, now);
            });
            return null;
        }, callback);
    }

    public void archivePlanting(
            long plantingId, String reason, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            database.runInTransaction(() -> {
                Planting planting = dao.plantingById(plantingId, userId);
                if (planting == null) {
                    throw new IllegalArgumentException("Planting record not found.");
                }
                long now = System.currentTimeMillis();
                if (dao.archivePlanting(userId, plantingId, now,
                        normalizedReason(reason)) < 1) {
                    throw new IllegalStateException("Planting record could not be archived.");
                }
                recordArchiveEvent(userId, ArchivedRecord.Type.PLANTING,
                        String.valueOf(plantingId),
                        "Farm Section " + planting.recordNumber,
                        "ARCHIVED", reason, now);
            });
            return null;
        }, callback);
    }

    public void archivedRecords(Callback<List<ArchivedRecord>> callback) {
        execute(() -> {
            long userId = userId();
            List<ArchivedRecord> records = new ArrayList<>();
            Map<String, List<Inventory>> batches = new LinkedHashMap<>();
            for (Inventory item : dao.archivedInventory(userId)) {
                batches.computeIfAbsent(item.batchNumber,
                        ignored -> new ArrayList<>()).add(item);
            }
            for (Map.Entry<String, List<Inventory>> entry : batches.entrySet()) {
                int pieces = 0;
                long archivedAt = 0;
                String reason = null;
                for (Inventory item : entry.getValue()) {
                    pieces += item.availablePieces;
                    if (item.archivedAt != null && item.archivedAt > archivedAt) {
                        archivedAt = item.archivedAt;
                        reason = item.archiveReason;
                    }
                }
                records.add(new ArchivedRecord(ArchivedRecord.Type.INVENTORY_BATCH,
                        entry.getKey(), "Batch #" + entry.getKey(),
                        pieces + " pieces unavailable while archived", reason, archivedAt));
            }
            for (Planting planting : dao.archivedPlantings(userId)) {
                records.add(new ArchivedRecord(ArchivedRecord.Type.PLANTING,
                        String.valueOf(planting.id),
                        "Farm Section " + planting.recordNumber,
                        planting.location + " • " + planting.variety,
                        planting.archiveReason, valueOrZero(planting.archivedAt)));
            }
            for (Sale sale : dao.archivedSales(userId)) {
                records.add(new ArchivedRecord(ArchivedRecord.Type.SALE,
                        String.valueOf(sale.id), "Sale #" + sale.id,
                        sale.customerName + " • accounting history retained",
                        sale.archiveReason, valueOrZero(sale.archivedAt)));
            }
            records.sort(Comparator.comparingLong(
                    ArchivedRecord::getArchivedAt).reversed());
            return records;
        }, callback);
    }

    public void restoreArchivedRecord(
            ArchivedRecord.Type type, String key, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            database.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                int restored;
                String title;
                if (type == ArchivedRecord.Type.INVENTORY_BATCH) {
                    restored = dao.restoreInventoryBatch(userId, key);
                    title = "Batch #" + key;
                } else if (type == ArchivedRecord.Type.SALE) {
                    long id = Long.parseLong(key);
                    restored = dao.restoreSale(userId, id);
                    title = "Sale #" + key;
                } else {
                    long id = Long.parseLong(key);
                    restored = dao.restorePlanting(userId, id, now);
                    title = "Planting record #" + key;
                }
                if (restored < 1) {
                    throw new IllegalStateException("This record is no longer archived.");
                }
                recordArchiveEvent(userId, type, key, title,
                        "RESTORED", null, now);
            });
            return null;
        }, callback);
    }

    public void recordGrafting(GraftingRequest request, Callback<Void> callback) {
        execute(() -> {
            long userId = userId();
            database.runInTransaction(() -> {
                Planting planting = dao.plantingById(request.getPlantingId(), userId);
                if (planting == null) {
                    throw new IllegalArgumentException("Planting record not found.");
                }
                PropagationMethod current = PropagationMethod.fromCode(
                        planting.propagationMethod);
                if (current != PropagationMethod.SEED) {
                    throw new IllegalArgumentException(
                            "Only a seed-grown record can be transferred into this grafting pathway.");
                }
                long now = System.currentTimeMillis();
                GraftingEvent event = new GraftingEvent();
                event.userId = userId;
                event.plantingId = planting.id;
                event.graftingDate = request.getGraftingDate();
                event.scionVariety = request.getScionVariety();
                event.note = request.getNote() == null
                        || request.getNote().trim().isEmpty()
                        ? null : request.getNote().trim();
                event.createdAt = now;
                dao.insertGraftingEvent(event);

                planting.propagationMethod = PropagationMethod.GRAFTED.name();
                planting.currentStage = PlantingStage.GRAFT_UNION.name();
                planting.variety = request.getScionVariety();
                planting.updatedAt = now;
                dao.updatePlanting(planting);

                PlantingUpdate update = new PlantingUpdate();
                update.userId = userId;
                update.plantingId = planting.id;
                update.type = "GRAFTING";
                update.stage = PlantingStage.GRAFT_UNION.name();
                update.note = event.note == null
                        ? "Grafting recorded with scion variety " + event.scionVariety + "."
                        : event.note;
                update.recordedDate = event.graftingDate;
                update.createdAt = now;
                dao.insertPlantingUpdate(update);
            });
            return null;
        }, callback);
    }

    public void addPlantingUpdate(
            PlantingUpdateRequest request, Callback<Void> callback
    ) {
        execute(() -> {
            long userId = userId();
            Planting planting = dao.plantingById(request.getPlantingId(), userId);
            if (planting == null) throw new IllegalArgumentException("Planting record not found.");
            boolean hasNote = request.getNote() != null
                    && !request.getNote().trim().isEmpty();
            if (!hasNote && request.getMeasurementCentimeters() == null
                    && request.getPhotoUri() == null) {
                throw new IllegalArgumentException(
                        "Add a note, measurement, or progress photo.");
            }
            PlantingUpdate update = new PlantingUpdate();
            update.userId = userId;
            update.plantingId = request.getPlantingId();
            update.type = request.getPhotoUri() == null ? "OBSERVATION" : "PHOTO";
            update.stage = planting.currentStage;
            update.note = hasNote ? request.getNote().trim() : null;
            update.measurementCentimeters = request.getMeasurementCentimeters();
            update.photoUri = request.getPhotoUri();
            update.recordedDate = request.getRecordedDate();
            update.createdAt = System.currentTimeMillis();
            dao.insertPlantingUpdate(update);
            planting.updatedAt = update.createdAt;
            dao.updatePlanting(planting);
            return null;
        }, callback);
    }

    public void dashboard(Callback<DashboardData> callback) {
        execute(() -> {
            long userId = userId();
            long[] today = dayBounds(Calendar.getInstance());
            Calendar month = Calendar.getInstance();
            month.set(Calendar.DAY_OF_MONTH, 1);
            zeroTime(month);
            Calendar nextMonth = (Calendar) month.clone();
            nextMonth.add(Calendar.MONTH, 1);
            DashboardData.Summary summary = new DashboardData.Summary(
                    dao.inventoryPieces(userId),
                    dao.activeBatchCount(userId),
                    dao.saleCount(userId, today[0], today[1]),
                    dao.saleRevenue(userId, month.getTimeInMillis(),
                            nextMonth.getTimeInMillis()),
                    dao.plantingCount(userId),
                    0);
            String selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(Calendar.getInstance().getTime());
            SalesAnalytics todayAnalytics =
                    buildAnalytics(userId, "daily", selectedDate);
            DashboardData.AnalyticsOverview analyticsOverview =
                    new DashboardData.AnalyticsOverview(
                            todayAnalytics.getTotals().getRevenue(),
                            todayAnalytics.getTotals().getPieces(),
                            todayAnalytics.getTotals().getWeightKilograms(),
                            todayAnalytics.getComparisonPercent());
            return new DashboardData(summary, analyticsOverview);
        }, callback);
    }

    public void analytics(
            String period, String selectedDate, Callback<SalesAnalytics> callback
    ) {
        execute(() -> buildAnalytics(userId(), period, selectedDate), callback);
    }

    private void completeSaleTransaction(long userId, CompleteSaleRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Add at least one sale item.");
        }
        Map<Long, Inventory> exactBatchLots = new HashMap<>();
        boolean fifoOverride = false;
        if (request.isEntireBatch()) {
            if (request.getBatchNumber() == null || request.getBatchNumber().trim().isEmpty()) {
                throw new IllegalArgumentException("Select a batch to sell.");
            }
            Set<Long> requestedIds = new HashSet<>();
            for (CompleteSaleRequest.Item item : request.getItems()) {
                if (item.getInventoryId() == null || !requestedIds.add(item.getInventoryId())) {
                    throw new IllegalArgumentException("Whole-batch items are invalid.");
                }
            }
            Set<Long> activeBatchIds = new HashSet<>();
            for (Inventory lot : dao.inventoryByBatch(userId, request.getBatchNumber())) {
                if (lot.availablePieces <= 0) continue;
                activeBatchIds.add(lot.id);
                exactBatchLots.put(lot.id, lot);
            }
            if (activeBatchIds.isEmpty() || !activeBatchIds.equals(requestedIds)) {
                throw new IllegalStateException(
                        "The batch changed. Reload it before completing the sale.");
            }
            for (CompleteSaleRequest.Item item : request.getItems()) {
                Inventory lot = exactBatchLots.get(item.getInventoryId());
                if (lot == null || !lot.size.equals(item.getSize())
                        || !lot.grade.equals(item.getGrade())
                        || lot.availablePieces != item.getPieces()) {
                    throw new IllegalStateException(
                            "Whole-batch piece counts must match current inventory.");
                }
                List<Inventory> fifo = dao.fifoInventory(userId, lot.size, lot.grade);
                if (!fifo.isEmpty() && fifo.get(0).id != lot.id) fifoOverride = true;
            }
            if (fifoOverride && (request.getFifoOverrideReason() == null
                    || request.getFifoOverrideReason().trim().isEmpty())) {
                throw new IllegalArgumentException(
                        "Explain why older matching stock is being skipped.");
            }
        }
        double total = 0;
        List<Price> matchedPrices = new ArrayList<>();
        Map<String, Integer> requestedByCategory = new HashMap<>();
        for (CompleteSaleRequest.Item requested : request.getItems()) {
            if (requested.getPieces() < 1 || requested.getWeightKilograms() <= 0) {
                throw new IllegalArgumentException(
                        "Every sale item needs pieces and measured weight.");
            }
            Price price = dao.activePrice(
                    userId, requested.getGrade(), requested.getSize());
            if (price == null) {
                throw new IllegalStateException(
                        "No active price exists for a sale item.");
            }
            int available = request.isEntireBatch()
                    ? exactBatchLots.get(requested.getInventoryId()).availablePieces : 0;
            if (!request.isEntireBatch()) for (Inventory lot : dao.fifoInventory(
                    userId, requested.getSize(), requested.getGrade())) {
                available += lot.availablePieces;
            }
            String category = requested.getSize() + "|" + requested.getGrade();
            int cumulativeRequested = request.isEntireBatch() ? requested.getPieces()
                    : requestedByCategory.getOrDefault(category, 0) + requested.getPieces();
            requestedByCategory.put(category, cumulativeRequested);
            if (available < cumulativeRequested) {
                throw new IllegalStateException(
                        "Insufficient matching inventory.");
            }
            matchedPrices.add(price);
            total += requested.getWeightKilograms() * price.pricePerKilogram;
        }
        double paid = request.getAmountPaid();
        boolean cash = "CASH".equals(request.getPaymentMethod());
        if ((cash && paid + 0.005 < total)
                || (!cash && Math.abs(paid - total) > 0.009)) {
            throw new IllegalArgumentException("Payment amount is invalid.");
        }

        Sale sale = new Sale();
        sale.userId = userId;
        sale.customerName = request.getCustomerName();
        sale.customerAddress = request.getCustomerAddress();
        sale.customerContact = request.getCustomerContactNumber();
        sale.customerEmail = request.getCustomerEmailAddress();
        sale.paymentMethod = request.getPaymentMethod();
        sale.amountPaid = paid;
        sale.totalAmount = total;
        sale.changeDue = cash ? paid - total : 0;
        sale.paymentReference = request.getPaymentReference();
        sale.provider = request.getProvider();
        sale.completedAt = System.currentTimeMillis();
        sale.id = dao.insertSale(sale);

        User user = requireUser(userId);
        for (int index = 0; index < request.getItems().size(); index++) {
            CompleteSaleRequest.Item requested = request.getItems().get(index);
            Price price = matchedPrices.get(index);
            SaleItem item = new SaleItem();
            item.userId = userId;
            item.saleId = sale.id;
            item.size = requested.getSize();
            item.grade = requested.getGrade();
            item.pieces = requested.getPieces();
            item.weightKilograms = requested.getWeightKilograms();
            item.pricePerKilogram = price.pricePerKilogram;
            item.subtotal = item.weightKilograms * item.pricePerKilogram;
            dao.insertSaleItem(item);

            int remaining = requested.getPieces();
            List<Inventory> sources = request.isEntireBatch()
                    ? List.of(exactBatchLots.get(requested.getInventoryId()))
                    : dao.fifoInventory(userId, item.size, item.grade);
            for (Inventory lot : sources) {
                if (remaining == 0) break;
                int allocated = Math.min(remaining, lot.availablePieces);
                lot.availablePieces -= allocated;
                dao.updateInventory(lot);
                String remarks = "Sale #" + sale.id;
                if (request.isEntireBatch()) {
                    remarks += " • Entire batch " + request.getBatchNumber();
                    if (fifoOverride) remarks += " • FIFO override: "
                            + request.getFifoOverrideReason().trim();
                }
                addTransaction(userId, lot.id, "SALE_OUT", -allocated,
                        remarks, user.displayName);
                remaining -= allocated;
            }
        }
    }

    private SalesAnalytics buildAnalytics(
            long userId, String period, String selectedDate
    ) throws Exception {
        Calendar selected = Calendar.getInstance();
        selected.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .parse(selectedDate));
        zeroTime(selected);
        Calendar start = (Calendar) selected.clone();
        Calendar end = (Calendar) selected.clone();
        Calendar previous = (Calendar) selected.clone();
        if ("weekly".equals(period)) {
            int offset = (start.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7;
            start.add(Calendar.DAY_OF_YEAR, -offset);
            end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_YEAR, 7);
            previous = (Calendar) start.clone();
            previous.add(Calendar.DAY_OF_YEAR, -7);
        } else if ("monthly".equals(period)) {
            start.set(Calendar.DAY_OF_MONTH, 1);
            end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);
            previous = (Calendar) start.clone();
            previous.add(Calendar.MONTH, -1);
        } else if ("annual".equals(period)) {
            start.set(Calendar.DAY_OF_YEAR, 1);
            end = (Calendar) start.clone();
            end.add(Calendar.YEAR, 1);
            previous = (Calendar) start.clone();
            previous.add(Calendar.YEAR, -1);
        } else {
            end.add(Calendar.DAY_OF_YEAR, 1);
            previous.add(Calendar.DAY_OF_YEAR, -1);
        }
        List<Sale> sales = dao.salesInPeriod(
                userId, start.getTimeInMillis(), end.getTimeInMillis());
        List<Long> ids = new ArrayList<>();
        double revenue = 0;
        for (Sale sale : sales) {
            ids.add(sale.id);
            revenue += sale.totalAmount;
        }
        List<SaleItem> items = ids.isEmpty()
                ? new ArrayList<>() : dao.saleItems(userId, ids);
        int pieces = 0;
        double weight = 0;
        Map<String, double[]> values = new HashMap<>();
        Map<Long, double[]> trendValues = new HashMap<>();
        for (SaleItem item : items) {
            pieces += item.pieces;
            weight += item.weightKilograms;
            String key = item.size + "|" + item.grade;
            double[] totals = values.computeIfAbsent(key, ignored -> new double[3]);
            totals[0] += item.pieces;
            totals[1] += item.weightKilograms;
            totals[2] += item.subtotal;
        }
        for (Sale sale : sales) {
            Calendar bucket = Calendar.getInstance();
            bucket.setTimeInMillis(sale.completedAt);
            if ("annual".equals(period)) {
                bucket.set(Calendar.DAY_OF_MONTH, 1);
                bucket.set(Calendar.HOUR_OF_DAY, 0);
            } else if ("monthly".equals(period) || "weekly".equals(period)) {
                bucket.set(Calendar.HOUR_OF_DAY, 0);
            } else {
                bucket.set(Calendar.MINUTE, 0);
            }
            bucket.set(Calendar.SECOND, 0);
            bucket.set(Calendar.MILLISECOND, 0);
            double[] totals = trendValues.computeIfAbsent(
                    bucket.getTimeInMillis(), ignored -> new double[2]);
            totals[0] += sale.totalAmount;
        }
        for (SaleItem item : items) {
            Sale itemSale = null;
            for (Sale sale : sales) {
                if (sale.id == item.saleId) {
                    itemSale = sale;
                    break;
                }
            }
            if (itemSale == null) continue;
            Calendar bucket = Calendar.getInstance();
            bucket.setTimeInMillis(itemSale.completedAt);
            if ("annual".equals(period)) {
                bucket.set(Calendar.DAY_OF_MONTH, 1);
                bucket.set(Calendar.HOUR_OF_DAY, 0);
            } else if ("monthly".equals(period) || "weekly".equals(period)) {
                bucket.set(Calendar.HOUR_OF_DAY, 0);
            } else {
                bucket.set(Calendar.MINUTE, 0);
            }
            bucket.set(Calendar.SECOND, 0);
            bucket.set(Calendar.MILLISECOND, 0);
            trendValues.get(bucket.getTimeInMillis())[1] += item.pieces;
        }
        Calendar cursor = (Calendar) start.clone();
        while (cursor.before(end)) {
            trendValues.computeIfAbsent(cursor.getTimeInMillis(), ignored -> new double[2]);
            if ("annual".equals(period)) cursor.add(Calendar.MONTH, 1);
            else if ("monthly".equals(period) || "weekly".equals(period))
                cursor.add(Calendar.DAY_OF_YEAR, 1);
            else cursor.add(Calendar.HOUR_OF_DAY, 1);
        }
        List<Long> trendKeys = new ArrayList<>(trendValues.keySet());
        trendKeys.sort(Long::compareTo);
        List<SalesAnalytics.Trend> trend = new ArrayList<>();
        SimpleDateFormat trendFormat = new SimpleDateFormat(
                "annual".equals(period) ? "MMM"
                        : "weekly".equals(period) ? "EEE"
                        : "monthly".equals(period) ? "dd" : "HH:00",
                Locale.US);
        for (Long key : trendKeys) {
            double[] totals = trendValues.get(key);
            trend.add(new SalesAnalytics.Trend(
                    trendFormat.format(key), totals[0], (int) totals[1]));
        }
        List<SalesAnalytics.Summary> summary = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : values.entrySet()) {
            String[] key = entry.getKey().split("\\|");
            double[] totals = entry.getValue();
            summary.add(new SalesAnalytics.Summary(
                    key[0], key[1], (int) totals[0], totals[1], totals[2]));
        }
        double previousRevenue = dao.saleRevenue(
                userId, previous.getTimeInMillis(), start.getTimeInMillis());
        Double comparison = previousRevenue == 0 ? null
                : ((revenue - previousRevenue) / previousRevenue) * 100;
        return new SalesAnalytics(
                period, selectedDate, previousRevenue, comparison,
                new SalesAnalytics.Totals(
                        revenue, sales.size(), pieces, weight), trend, summary);
    }

    private void seedPrices(long userId) {
        String[] sizes = {"EXTRA_SMALL", "SMALL", "MEDIUM", "LARGE", "JUMBO"};
        double[] gradeA = {70, 80, 100, 120, 140};
        double[] gradeB = {60, 70, 85, 100, 115};
        for (int index = 0; index < sizes.length; index++) {
            insertSeedPrice(userId, "A", sizes[index], gradeA[index]);
            insertSeedPrice(userId, "B", sizes[index], gradeB[index]);
        }
        insertSeedPrice(userId, "C", null, 50);
    }

    private void insertSeedPrice(
            long userId, String grade, String size, double value
    ) {
        Price price = new Price();
        price.userId = userId;
        price.grade = grade;
        price.size = size;
        price.pricePerKilogram = value;
        price.active = true;
        price.effectiveAt = System.currentTimeMillis();
        dao.insertPrice(price);
    }

    private void addTransaction(
            long userId, long inventoryId, String type, int pieces,
            String remarks, String createdBy
    ) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.userId = userId;
        transaction.inventoryId = inventoryId;
        transaction.type = type;
        transaction.pieces = pieces;
        transaction.remarks = remarks;
        transaction.createdBy = createdBy;
        transaction.createdAt = System.currentTimeMillis();
        dao.insertInventoryTransaction(transaction);
    }

    private void recordArchiveEvent(
            long userId, ArchivedRecord.Type type, String key, String title,
            String action, String reason, long eventAt
    ) {
        RecordArchiveEvent event = new RecordArchiveEvent();
        event.userId = userId;
        event.recordType = type.name();
        event.recordKey = key;
        event.recordTitle = title;
        event.action = action;
        event.reason = normalizedReason(reason);
        event.eventAt = eventAt;
        dao.insertArchiveEvent(event);
    }

    private static String normalizedReason(String reason) {
        return reason == null || reason.trim().isEmpty()
                ? "No reason provided" : reason.trim();
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private static void validateRegrade(String source, String target) {
        boolean valid = ("A".equals(source)
                && ("B".equals(target) || "C".equals(target)))
                || ("B".equals(source) && "C".equals(target));
        if (!valid) throw new IllegalArgumentException(
                "This grade change is not allowed.");
    }

    private PlantingGroup plantingGroup(Planting item) throws Exception {
        PropagationMethod method = PropagationMethod.fromCode(item.propagationMethod);
        String trackingDate = item.graftingDate;
        if (method == PropagationMethod.GRAFTED) {
            List<GraftingEvent> events = dao.graftingEvents(item.userId, item.id);
            if (!events.isEmpty()) trackingDate = events.get(0).graftingDate;
        }
        Calendar started = Calendar.getInstance();
        started.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .parse(trackingDate));
        zeroTime(started);
        Calendar today = Calendar.getInstance();
        zeroTime(today);
        int elapsed = (int) Math.max(0,
                (today.getTimeInMillis() - started.getTimeInMillis()) / 86_400_000L);
        PlantingStage currentStage = PlantingStage.fromCode(item.currentStage, method);
        PlantingStage ageSuggestion = PlantingStage.suggestedForAge(method, elapsed);
        List<PlantingStage> pathway = PlantingStage.forMethod(method);
        int currentIndex = pathway.indexOf(currentStage);
        int ageIndex = pathway.indexOf(ageSuggestion);
        PlantingStage suggestedStage = currentIndex < ageIndex
                ? ageSuggestion : currentStage.next(method);
        Integer fruitAge = item.floweringDate == null
                ? null : daysSince(item.floweringDate);
        String harvestWindow = item.floweringDate == null
                ? "Starts after flowering is recorded"
                : addDays(item.floweringDate, 30) + " to "
                + addDays(item.floweringDate, 50);
        return new PlantingGroup(
                item.id, item.recordNumber, item.graftingDate,
                item.variety, item.location, item.numberOfPlants,
                item.cuttingType, elapsed, method, currentStage, suggestedStage,
                fruitAge, harvestWindow);
    }

    private static int daysSince(String date) throws Exception {
        Calendar start = Calendar.getInstance();
        start.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date));
        zeroTime(start);
        Calendar today = Calendar.getInstance();
        zeroTime(today);
        return (int) Math.max(0,
                (today.getTimeInMillis() - start.getTimeInMillis()) / 86_400_000L);
    }

    private static String addDays(String date, int days) throws Exception {
        Calendar value = Calendar.getInstance();
        value.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date));
        value.add(Calendar.DAY_OF_YEAR, days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(value.getTime());
    }

    private InventoryLot inventoryLot(Inventory item, boolean nextOut) {
        return new InventoryLot(
                item.id, item.batchNumber, item.harvestDate,
                item.size, item.grade, item.availablePieces, nextOut);
    }

    private SessionUser sessionUser(User user) {
        return new SessionUser(
                String.valueOf(user.id), user.email, user.displayName);
    }

    private User requireUser(long userId) {
        User user = dao.findUserById(userId);
        if (user == null) throw new IllegalStateException("Local account not found.");
        return user;
    }

    private long userId() {
        SessionUser user = session.getUser();
        if (user == null) throw new IllegalStateException("Sign in is required.");
        return Long.parseLong(user.getId());
    }

    private <T> void execute(Task<T> task, Callback<T> callback) {
        executor.execute(() -> {
            try {
                T value = task.run();
                main.post(() -> callback.onSuccess(value));
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? "The local operation failed." : error.getMessage();
                main.post(() -> callback.onError(message));
            }
        });
    }

    private static String timestamp(long value) {
        return new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(value);
    }

    private static long[] dayBounds(Calendar day) {
        zeroTime(day);
        long start = day.getTimeInMillis();
        day.add(Calendar.DAY_OF_YEAR, 1);
        return new long[]{start, day.getTimeInMillis()};
    }

    private static void zeroTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
