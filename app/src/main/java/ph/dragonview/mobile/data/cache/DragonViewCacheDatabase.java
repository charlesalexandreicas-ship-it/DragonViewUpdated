package ph.dragonview.mobile.data.cache;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {CacheEntry.class, OutboxOperation.class}, version = 2, exportSchema = false)
public abstract class DragonViewCacheDatabase extends RoomDatabase {
    private static volatile DragonViewCacheDatabase instance;
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE cache_entries ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE sync_outbox ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''");
        }
    };

    public abstract CacheDao cacheDao();

    // The mapping build added tables; retaining them preserves data while Room validates the shared cache tables.
    private static final Migration MIGRATION_3_2 = new Migration(3, 2) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            // No shared cache columns were removed by the Before Mapping rollback.
        }
    };

    public static DragonViewCacheDatabase get(Context context) {
        if (instance == null) {
            synchronized (DragonViewCacheDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            DragonViewCacheDatabase.class, "dragon-view-cache.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_3_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
