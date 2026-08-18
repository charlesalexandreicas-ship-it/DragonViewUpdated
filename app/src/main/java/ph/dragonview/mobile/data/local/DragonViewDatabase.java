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
                LocalEntities.PlantingUpdate.class
        },
        version = 2,
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

    public abstract LocalDao localDao();

    public static DragonViewDatabase get(Context context) {
        if (instance == null) {
            synchronized (DragonViewDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    DragonViewDatabase.class,
                                    "dragon-view-offline.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
