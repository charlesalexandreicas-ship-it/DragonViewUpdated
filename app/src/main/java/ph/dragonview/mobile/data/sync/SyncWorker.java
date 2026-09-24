package ph.dragonview.mobile.data.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonParser;

import java.util.List;
import java.util.concurrent.TimeUnit;

import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.cache.CacheDao;
import ph.dragonview.mobile.data.cache.DragonViewCacheDatabase;
import ph.dragonview.mobile.data.cache.OutboxOperation;
import ph.dragonview.mobile.data.model.SessionUser;
import ph.dragonview.mobile.data.remote.SupabaseClient;
import ph.dragonview.mobile.data.remote.SupabaseException;

public final class SyncWorker extends Worker {
    private static final String UNIQUE_WORK = "dragonview-cloud-sync";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        SessionManager session = new SessionManager(getApplicationContext());
        SessionUser user = session.getUser();
        String workspaceId = session.getActiveWorkspaceId();
        if (user == null || session.getRefreshToken() == null || workspaceId.isEmpty()) {
            return Result.failure();
        }
        CacheDao dao = DragonViewCacheDatabase.get(getApplicationContext()).cacheDao();
        SupabaseClient cloud = new SupabaseClient(getApplicationContext());
        dao.assignLegacyOutbox(user.getId(), workspaceId);
        List<OutboxOperation> operations = dao.pendingWorkspace(user.getId(), workspaceId, 25);
        for (OutboxOperation operation : operations) {
            try {
                cloud.mutate(operation.functionName, JsonParser.parseString(operation.payloadJson), operation.operationId);
                dao.complete(operation.operationId);
            } catch (SupabaseException error) {
                dao.failedAttempt(operation.operationId, error.getMessage());
                return error.isRetryable() ? Result.retry() : Result.failure();
            }
        }
        if (!operations.isEmpty()) dao.clearWorkspaceCache(user.getId(), workspaceId);
        return Result.success();
    }
}
