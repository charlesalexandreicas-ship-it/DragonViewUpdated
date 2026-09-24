package ph.dragonview.mobile.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(CacheEntry entry);

    @Query("SELECT * FROM cache_entries WHERE id = :id LIMIT 1")
    CacheEntry get(String id);

    @Query("DELETE FROM cache_entries WHERE userId = :userId")
    void clearUserCache(String userId);

    @Query("DELETE FROM cache_entries WHERE userId = :userId AND workspaceId = :workspaceId")
    void clearWorkspaceCache(String userId, String workspaceId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long enqueue(OutboxOperation operation);

    @Query("SELECT * FROM sync_outbox WHERE userId = :userId ORDER BY createdAt LIMIT :limit")
    List<OutboxOperation> pending(String userId, int limit);

    @Query("SELECT * FROM sync_outbox WHERE userId = :userId AND workspaceId = :workspaceId ORDER BY createdAt LIMIT :limit")
    List<OutboxOperation> pendingWorkspace(String userId, String workspaceId, int limit);

    @Query("UPDATE sync_outbox SET workspaceId = :workspaceId WHERE userId = :userId AND workspaceId = ''")
    void assignLegacyOutbox(String userId, String workspaceId);

    @Query("UPDATE sync_outbox SET attempts = attempts + 1, lastError = :message WHERE operationId = :operationId")
    void failedAttempt(String operationId, String message);

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId")
    void complete(String operationId);

    @Query("DELETE FROM sync_outbox WHERE userId = :userId")
    void clearUserOutbox(String userId);

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE userId = :userId")
    int pendingCount(String userId);

    @Query("SELECT lastError FROM sync_outbox WHERE userId = :userId AND lastError IS NOT NULL ORDER BY createdAt LIMIT 1")
    String oldestError(String userId);
}
