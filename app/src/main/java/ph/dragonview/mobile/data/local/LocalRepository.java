package ph.dragonview.mobile.data.local;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.cache.CacheDao;
import ph.dragonview.mobile.data.cache.CacheEntry;
import ph.dragonview.mobile.data.cache.DragonViewCacheDatabase;
import ph.dragonview.mobile.data.cache.OutboxOperation;
import ph.dragonview.mobile.data.model.ArchivedRecord;
import ph.dragonview.mobile.data.model.CompleteSaleRequest;
import ph.dragonview.mobile.data.model.DashboardData;
import ph.dragonview.mobile.data.model.FruitPrice;
import ph.dragonview.mobile.data.model.GraftingRequest;
import ph.dragonview.mobile.data.model.HarvestRequest;
import ph.dragonview.mobile.data.model.InventoryBatch;
import ph.dragonview.mobile.data.model.InventoryBatchDetails;
import ph.dragonview.mobile.data.model.InventoryDetails;
import ph.dragonview.mobile.data.model.InventoryLot;
import ph.dragonview.mobile.data.model.PlantingDetails;
import ph.dragonview.mobile.data.model.PlantingGroup;
import ph.dragonview.mobile.data.model.PlantingRequest;
import ph.dragonview.mobile.data.model.PlantingStage;
import ph.dragonview.mobile.data.model.PlantingUpdateRequest;
import ph.dragonview.mobile.data.model.SaleSummary;
import ph.dragonview.mobile.data.model.SalesAnalytics;
import ph.dragonview.mobile.data.model.SessionUser;
import ph.dragonview.mobile.data.model.Workspace;
import ph.dragonview.mobile.data.model.WorkspaceMember;
import ph.dragonview.mobile.data.remote.SupabaseClient;
import ph.dragonview.mobile.data.remote.SupabaseException;
import ph.dragonview.mobile.data.sync.SyncWorker;

/** Cloud-first repository with Room snapshots and an idempotent offline outbox. */
public final class LocalRepository {
    public interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    private interface Work<T> { T run() throws Exception; }

    private static final Type INVENTORY_LIST = new TypeToken<List<InventoryLot>>() {}.getType();
    private static final Type BATCH_LIST = new TypeToken<List<InventoryBatch>>() {}.getType();
    private static final Type PRICE_LIST = new TypeToken<List<FruitPrice>>() {}.getType();
    private static final Type SALE_LIST = new TypeToken<List<SaleSummary>>() {}.getType();
    private static final Type PLANTING_LIST = new TypeToken<List<PlantingGroup>>() {}.getType();
    private static final Type ARCHIVE_LIST = new TypeToken<List<ArchivedRecord>>() {}.getType();
    private static final Type WORKSPACE_LIST = new TypeToken<List<Workspace>>() {}.getType();
    private static final Type MEMBER_LIST = new TypeToken<List<WorkspaceMember>>() {}.getType();

    private static volatile LocalRepository instance;
    private final Context context;
    private final SessionManager session;
    private final SupabaseClient cloud;
    private final CacheDao cache;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LocalRepository(Context context) {
        this.context = context.getApplicationContext();
        session = new SessionManager(this.context);
        cloud = new SupabaseClient(this.context);
        cache = DragonViewCacheDatabase.get(this.context).cacheDao();
    }

    public static LocalRepository get(Context context) {
        if (instance == null) {
            synchronized (LocalRepository.class) {
                if (instance == null) instance = new LocalRepository(context);
            }
        }
        return instance;
    }

    public void register(String displayName, String email, String password,
                         Callback<SessionUser> callback) {
        execute(() -> {
            SupabaseClient.AuthResult auth = cloud.signUp(displayName.trim(), normalizeEmail(email), password);
            establishCloudSession(auth);
            loadInitialWorkspace();
            return auth.user;
        }, callback);
    }

    public void login(String email, String password, Callback<SessionUser> callback) {
        execute(() -> {
            SupabaseClient.AuthResult auth = cloud.signIn(normalizeEmail(email), password);
            establishCloudSession(auth);
            loadInitialWorkspace();
            return auth.user;
        }, callback);
    }

    public void requestPasswordReset(String email, Callback<Void> callback) {
        execute(() -> {
            cloud.requestPasswordReset(normalizeEmail(email));
            return null;
        }, callback);
    }

    public void updateRecoveredPassword(String recoveryAccessToken, String password,
                                        Callback<Void> callback) {
        execute(() -> {
            cloud.updateRecoveredPassword(recoveryAccessToken, password);
            return null;
        }, callback);
    }

    public void signOut() {
        SessionUser current = session.getUser();
        String userId = current == null ? null : current.getId();
        String accessToken = session.getAccessToken();
        session.clear();
        executor.execute(() -> {
            cloud.logout(accessToken);
            if (userId != null) {
                cache.clearUserCache(userId);
                cache.clearUserOutbox(userId);
            }
        });
    }

    public void inventory(Callback<List<InventoryLot>> callback) {
        read("inventory", "get_inventory", Collections.emptyMap(), INVENTORY_LIST, callback);
    }

    public void inventoryBatches(Callback<List<InventoryBatch>> callback) {
        read("inventory-batches", "get_inventory_batches", Collections.emptyMap(), BATCH_LIST, callback);
    }

    public void inventoryDetails(long id, Callback<InventoryDetails> callback) {
        read("inventory-details-" + id, "get_inventory_details", map("p_inventory_id", id),
                InventoryDetails.class, callback);
    }

    public void inventoryBatchDetails(String batchNumber, Callback<InventoryBatchDetails> callback) {
        read("inventory-batch-" + batchNumber, "get_inventory_batch_details",
                map("p_batch_number", batchNumber), InventoryBatchDetails.class, callback);
    }

    public void registerHarvest(HarvestRequest request, Callback<Void> callback) {
        // Inventory is stock-sensitive business data. Only report success after
        // Supabase has committed the batch; an outbox acknowledgement would make
        // the form close while the new stock is still absent from inventory.
        mutate("register_harvest", gson.toJsonTree(request), false, callback);
    }

    public void adjustInventory(long inventoryId, int pieces, String reason, Callback<Void> callback) {
        mutate("adjust_inventory", object("inventoryId", inventoryId, "pieces", pieces, "reason", reason), false, callback);
    }

    public void regradeInventory(long inventoryId, String targetGrade, int pieces,
                                 String reason, Callback<Void> callback) {
        mutate("regrade_inventory", object("inventoryId", inventoryId, "targetGrade", targetGrade,
                "pieces", pieces, "reason", reason), false, callback);
    }

    public void prices(Callback<List<FruitPrice>> callback) {
        read("prices", "get_prices", Collections.emptyMap(), PRICE_LIST, callback);
    }

    public void configurePrice(String grade, String size, double value, Callback<Void> callback) {
        if (!requireOwner(callback)) return;
        mutate("configure_price", object("grade", grade, "size", size, "value", value), true, callback);
    }

    public void sales(Callback<List<SaleSummary>> callback) {
        read("sales", "get_sales", Collections.emptyMap(), SALE_LIST, callback);
    }

    public void completeSale(CompleteSaleRequest request, Callback<Void> callback) {
        mutate("complete_sale", gson.toJsonTree(request), false, callback);
    }

    public void planting(Callback<List<PlantingGroup>> callback) {
        read("planting", "get_planting", Collections.emptyMap(), PLANTING_LIST, callback);
    }

    public void plantingDetails(long plantingId, Callback<PlantingDetails> callback) {
        read("planting-details-" + plantingId, "get_planting_details",
                map("p_planting_id", plantingId), PlantingDetails.class, callback);
    }

    public void createPlanting(PlantingRequest request, Callback<Void> callback) {
        mutate("create_planting", gson.toJsonTree(request), true, callback);
    }

    public void updatePlantingStage(long plantingId, PlantingStage stage, String recordedDate,
                                    String note, Callback<Void> callback) {
        mutate("update_planting_stage", object("plantingId", plantingId, "stage", stage.name(),
                "recordedDate", recordedDate, "note", note), true, callback);
    }

    public void archiveInventoryBatch(String batchNumber, String reason, Callback<Void> callback) {
        if (!requireOwner(callback)) return;
        archive("INVENTORY_BATCH", batchNumber, reason, callback);
    }

    public void archiveSale(long saleId, String reason, Callback<Void> callback) {
        if (!requireOwner(callback)) return;
        archive("SALE", String.valueOf(saleId), reason, callback);
    }

    public void archivePlanting(long plantingId, String reason, Callback<Void> callback) {
        if (!requireOwner(callback)) return;
        archive("PLANTING", String.valueOf(plantingId), reason, callback);
    }

    public void archivedRecords(Callback<List<ArchivedRecord>> callback) {
        if (!requireOwner(callback)) return;
        read("archived-records", "get_archived_records", Collections.emptyMap(), ARCHIVE_LIST, callback);
    }

    public void restoreArchivedRecord(ArchivedRecord.Type type, String key, Callback<Void> callback) {
        if (!requireOwner(callback)) return;
        mutate("restore_record", object("type", type.name(), "key", key), true, callback);
    }

    public void recordGrafting(GraftingRequest request, Callback<Void> callback) {
        mutate("record_grafting", gson.toJsonTree(request), true, callback);
    }

    public void addPlantingUpdate(PlantingUpdateRequest request, Callback<Void> callback) {
        mutate("add_planting_update", gson.toJsonTree(request), true, callback);
    }

    public void dashboard(Callback<DashboardData> callback) {
        read("dashboard", "get_dashboard", Collections.emptyMap(), DashboardData.class, callback);
    }

    public void analytics(String period, String selectedDate, Callback<SalesAnalytics> callback) {
        if (!requireOwner(callback)) return;
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("p_period", period);
        arguments.put("p_selected_date", selectedDate);
        read("analytics-" + period + "-" + selectedDate, "get_sales_analytics",
                arguments, SalesAnalytics.class, callback);
    }

    public boolean isWorkspaceOwner() { return session.isWorkspaceOwner(); }

    public void ensureActiveWorkspace(Callback<Void> callback) {
        if (!session.getActiveWorkspaceId().isEmpty()) {
            postSuccess(callback, null);
            return;
        }
        execute(() -> {
            loadInitialWorkspace();
            return null;
        }, callback);
    }

    public void workspaces(Callback<List<Workspace>> callback) {
        execute(() -> gson.fromJson(cloud.read("get_workspaces", Collections.emptyMap()),
                WORKSPACE_LIST), callback);
    }

    public void workspaceMembers(Callback<List<WorkspaceMember>> callback) {
        execute(() -> {
            requireWorkspaceId();
            return gson.fromJson(cloud.read("get_workspace_members", Collections.emptyMap()), MEMBER_LIST);
        }, callback);
    }

    public void selectWorkspace(Workspace workspace, Callback<Void> callback) {
        execute(() -> {
            Map<String, Object> arguments = map("p_workspace_id", workspace.getId());
            JsonObject selected = cloud.read("select_workspace", arguments).getAsJsonObject();
            saveSelectedWorkspace(selected, workspace);
            cache.clearUserCache(requireUserId());
            SyncWorker.enqueue(context);
            return null;
        }, callback);
    }

    public void createWorkspace(String name, Callback<Void> callback) {
        execute(() -> {
            JsonObject created = cloud.read("create_workspace", map("p_name", name.trim())).getAsJsonObject();
            saveSelectedWorkspace(created, null);
            cache.clearUserCache(requireUserId());
            return null;
        }, callback);
    }

    public void joinWorkspace(String inviteCode, Callback<Void> callback) {
        execute(() -> {
            JsonObject joined = cloud.read("join_workspace", map("p_invite_code", inviteCode.trim())).getAsJsonObject();
            saveSelectedWorkspace(joined, null);
            cache.clearUserCache(requireUserId());
            return null;
        }, callback);
    }

    public void regenerateWorkspaceInvite(Callback<String> callback) {
        if (!requireOwner(callback)) return;
        execute(() -> cloud.read("regenerate_workspace_invite", Collections.emptyMap())
                .getAsJsonObject().get("inviteCode").getAsString(), callback);
    }

    public void removeWorkspaceMember(String userId, Callback<Void> callback) {
        if (!requireOwner(callback)) return;
        execute(() -> {
            cloud.read("remove_workspace_member", map("p_user_id", userId));
            return null;
        }, callback);
    }

    public void leaveWorkspace(Callback<Void> callback) {
        execute(() -> {
            cloud.read("leave_workspace", Collections.emptyMap());
            session.clearActiveWorkspace();
            loadInitialWorkspace();
            cache.clearUserCache(requireUserId());
            return null;
        }, callback);
    }

    public void deleteWorkspace(Callback<Void> callback) {
        if (!requireOwner(callback)) return;
        execute(() -> {
            String userId = requireUserId();
            JsonObject result = cloud.read("delete_workspace", Collections.emptyMap()).getAsJsonObject();
            session.clearActiveWorkspace();
            cache.clearUserCache(userId);
            if (result.has("nextWorkspace") && result.get("nextWorkspace").isJsonObject()) {
                saveSelectedWorkspace(result.getAsJsonObject("nextWorkspace"), null);
            }
            return null;
        }, callback);
    }

    public void syncStatus(Callback<String> callback) {
        execute(() -> {
            String userId = requireUserId();
            int pending = cache.pendingCount(userId);
            String error = cache.oldestError(userId);
            if (error != null && !error.trim().isEmpty()) return "Sync needs attention: " + error;
            if (pending > 0) return pending + (pending == 1 ? " change pending sync" : " changes pending sync");
            return "Cloud data is synchronized";
        }, callback);
    }

    private void archive(String type, String key, String reason, Callback<Void> callback) {
        mutate("archive_record", object("type", type, "key", key, "reason", reason), true, callback);
    }

    private <T> void read(String resource, String function, Map<String, Object> arguments,
                          Type type, Callback<T> callback) {
        executor.execute(() -> {
            String userId;
            try { userId = requireUserId(); }
            catch (Exception error) { postError(callback, message(error)); return; }
            String workspaceId;
            try { workspaceId = requireWorkspaceId(); }
            catch (Exception error) { postError(callback, message(error)); return; }
            CacheEntry saved = cache.get(cacheId(userId, workspaceId, resource));
            boolean deliveredCache = false;
            if (saved != null) {
                try {
                    postSuccess(callback, gson.fromJson(saved.json, type));
                    deliveredCache = true;
                } catch (Exception ignored) { }
            }
            try {
                JsonElement json = cloud.read(function, arguments);
                if (json == null || json.isJsonNull()) throw new IllegalArgumentException("Requested record was not found.");
                CacheEntry entry = new CacheEntry();
                entry.id = cacheId(userId, workspaceId, resource);
                entry.userId = userId;
                entry.workspaceId = workspaceId;
                entry.resource = resource;
                entry.json = json.toString();
                entry.updatedAt = System.currentTimeMillis();
                cache.put(entry);
                postSuccess(callback, gson.fromJson(json, type));
            } catch (Exception error) {
                if (!deliveredCache) postError(callback, message(error));
            }
        });
    }

    private void mutate(String function, JsonElement payload, boolean queueOffline,
                        Callback<Void> callback) {
        executor.execute(() -> {
            String operationId = UUID.randomUUID().toString();
            try {
                String userId = requireUserId();
                String workspaceId = requireWorkspaceId();
                cloud.mutate(function, payload, operationId);
                cache.clearWorkspaceCache(userId, workspaceId);
                postSuccess(callback, null);
            } catch (Exception error) {
                if (queueOffline && isRetryable(error) && session.getUser() != null) {
                    OutboxOperation operation = new OutboxOperation();
                    operation.operationId = operationId;
                    operation.userId = session.getUser().getId();
                    operation.workspaceId = requireWorkspaceId();
                    operation.functionName = function;
                    operation.payloadJson = payload.toString();
                    operation.createdAt = System.currentTimeMillis();
                    cache.enqueue(operation);
                    SyncWorker.enqueue(context);
                    main.post(() -> Toast.makeText(context,
                            "Saved offline — pending cloud sync", Toast.LENGTH_LONG).show());
                    postSuccess(callback, null);
                } else {
                    postError(callback, isRetryable(error) && !queueOffline
                            ? "Internet is required for this stock-sensitive operation."
                            : message(error));
                }
            }
        });
    }

    private void establishCloudSession(SupabaseClient.AuthResult auth) {
        session.saveAuthenticated(auth.user, auth.accessToken, auth.refreshToken, auth.expiresAt);
        session.clearActiveWorkspace();
        context.deleteDatabase("dragon-view-offline.db");
    }

    private void loadInitialWorkspace() throws Exception {
        List<Workspace> workspaces = gson.fromJson(
                cloud.read("get_workspaces", Collections.emptyMap()), WORKSPACE_LIST);
        if (workspaces == null || workspaces.isEmpty()) {
            throw new IllegalStateException("No workspace is available for this account.");
        }
        Workspace selected = workspaces.get(0);
        for (Workspace workspace : workspaces) if (workspace.isActive()) selected = workspace;
        session.setActiveWorkspace(selected.getId(), selected.getName(), selected.getRole());
    }

    private void saveSelectedWorkspace(JsonObject value, Workspace fallback) {
        String id = jsonString(value, "id", fallback == null ? null : fallback.getId());
        String name = jsonString(value, "name", fallback == null ? null : fallback.getName());
        String role = jsonString(value, "role", fallback == null ? null : fallback.getRole());
        if (id == null || name == null || role == null) {
            throw new IllegalStateException("The server returned an incomplete workspace.");
        }
        session.setActiveWorkspace(id, name, role);
    }

    private static String jsonString(JsonObject value, String key, String fallback) {
        if (value != null && value.has(key) && !value.get(key).isJsonNull()) {
            return value.get(key).getAsString();
        }
        return fallback;
    }

    private String requireUserId() {
        SessionUser user = session.getUser();
        if (user == null || session.getRefreshToken() == null) {
            throw new IllegalStateException("Your session expired. Sign in again.");
        }
        return user.getId();
    }

    private String requireWorkspaceId() {
        String workspaceId = session.getActiveWorkspaceId();
        if (workspaceId == null || workspaceId.isEmpty()) {
            throw new IllegalStateException("Select a workspace first.");
        }
        return workspaceId;
    }

    private boolean requireOwner(Callback<?> callback) {
        if (session.isWorkspaceOwner()) return true;
        postError(callback, "Workspace owner permission is required.");
        return false;
    }

    private <T> void execute(Work<T> work, Callback<T> callback) {
        executor.execute(() -> {
            try { postSuccess(callback, work.run()); }
            catch (Exception error) { postError(callback, message(error)); }
        });
    }

    private <T> void postSuccess(Callback<T> callback, T value) { main.post(() -> callback.onSuccess(value)); }
    private void postError(Callback<?> callback, String error) { main.post(() -> callback.onError(error)); }
    private static boolean isRetryable(Exception error) {
        return error instanceof SupabaseException && ((SupabaseException) error).isRetryable();
    }
    private static String message(Exception error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? "DragonView could not complete the request." : value;
    }
    private static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.US); }
    private static String cacheId(String userId, String workspaceId, String resource) {
        return userId + ":" + workspaceId + ":" + resource;
    }
    private static Map<String, Object> map(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put(key, value); return result;
    }
    private static JsonObject object(Object... values) {
        JsonObject result = new JsonObject();
        for (int index = 0; index < values.length; index += 2) {
            String key = String.valueOf(values[index]); Object value = values[index + 1];
            if (value == null) result.add(key, null);
            else if (value instanceof Number) result.addProperty(key, (Number) value);
            else if (value instanceof Boolean) result.addProperty(key, (Boolean) value);
            else result.addProperty(key, String.valueOf(value));
        }
        return result;
    }
}
