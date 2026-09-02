package ph.dragonview.mobile.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                LocalEntities.User.class,
                LocalEntities.Inventory.class,
                LocalEntities.InventoryTransaction.class,
                LocalEntities.Price.class,
                LocalEntities.Sale.class,
                LocalEntities.SaleItem.class,
                LocalEntities.Planting.class,
                LocalEntities.PlantingUpdate.class,
                LocalEntities.GraftingEvent.class,
                LocalEntities.RecordArchiveEvent.class
        },
        version = 4,
        exportSchema = false
)
public abstract class DragonViewDatabase extends RoomDatabase {
    private static volatile DragonViewDatabase instance;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE planting ADD COLUMN cuttingType " +
                    "TEXT NOT NULL DEFAULT 'UNROOTED'");
            database.execSQL("ALTER TABLE planting ADD COLUMN currentStage " +
                    "TEXT NOT NULL DEFAULT 'PLANTED'");
            database.execSQL("ALTER TABLE planting ADD COLUMN floweringDate TEXT");
            database.execSQL("ALTER TABLE planting ADD COLUMN updatedAt " +
                    "INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE TABLE IF NOT EXISTS planting_updates (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "userId INTEGER NOT NULL, plantingId INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, stage TEXT, note TEXT, " +
                    "measurementCentimeters REAL, photoUri TEXT, " +
                    "recordedDate TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(plantingId) REFERENCES planting(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_planting_updates_userId " +
                    "ON planting_updates(userId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_planting_updates_plantingId " +
                    "ON planting_updates(plantingId)");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE planting ADD COLUMN propagationMethod " +
                    "TEXT NOT NULL DEFAULT 'STEM_CUTTING'");
            database.execSQL("CREATE TABLE IF NOT EXISTS grafting_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "userId INTEGER NOT NULL, plantingId INTEGER NOT NULL, " +
                    "graftingDate TEXT NOT NULL, scionVariety TEXT NOT NULL, " +
                    "note TEXT, createdAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(plantingId) REFERENCES planting(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_grafting_events_userId " +
                    "ON grafting_events(userId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_grafting_events_plantingId " +
                    "ON grafting_events(plantingId)");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE inventory ADD COLUMN archivedAt INTEGER");
            database.execSQL("ALTER TABLE inventory ADD COLUMN archiveReason TEXT");
            database.execSQL("ALTER TABLE sales ADD COLUMN archivedAt INTEGER");
            database.execSQL("ALTER TABLE sales ADD COLUMN archiveReason TEXT");
            database.execSQL("ALTER TABLE planting ADD COLUMN archivedAt INTEGER");
            database.execSQL("ALTER TABLE planting ADD COLUMN archiveReason TEXT");
            database.execSQL("CREATE TABLE IF NOT EXISTS record_archive_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "userId INTEGER NOT NULL, recordType TEXT NOT NULL, " +
                    "recordKey TEXT NOT NULL, recordTitle TEXT NOT NULL, " +
                    "action TEXT NOT NULL, reason TEXT, eventAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(userId) REFERENCES users(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "index_record_archive_events_userId " +
                    "ON record_archive_events(userId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "index_record_archive_events_recordType_recordKey " +
                    "ON record_archive_events(recordType, recordKey)");
        }
    };

    public abstract LocalDao localDao();

    public static DragonViewDatabase get(Context context) {
        if (instance == null) {
            synchronized (DragonViewDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    DragonViewDatabase.class,
                                    "dragon-view-offline.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3,
                                    MIGRATION_3_4)
                            .build();
                }
            }
        }
        return instance;
    }
}
