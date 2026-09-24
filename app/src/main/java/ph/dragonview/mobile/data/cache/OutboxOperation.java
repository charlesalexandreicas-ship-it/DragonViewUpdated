package ph.dragonview.mobile.data.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_outbox")
public final class OutboxOperation {
    @PrimaryKey @NonNull public String operationId = "";
    @NonNull public String userId = "";
    @NonNull public String workspaceId = "";
    @NonNull public String functionName = "";
    @NonNull public String payloadJson = "{}";
    public long createdAt;
    public int attempts;
    public String lastError;
}
