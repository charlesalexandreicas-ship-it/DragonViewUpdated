package ph.dragonview.mobile.data.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cache_entries")
public final class CacheEntry {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String userId = "";
    @NonNull public String workspaceId = "";
    @NonNull public String resource = "";
    @NonNull public String json = "";
    public long updatedAt;
}
