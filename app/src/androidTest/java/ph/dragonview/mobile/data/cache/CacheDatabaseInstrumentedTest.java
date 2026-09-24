package ph.dragonview.mobile.data.cache;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CacheDatabaseInstrumentedTest {
    private DragonViewCacheDatabase database;
    private CacheDao dao;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, DragonViewCacheDatabase.class)
                .allowMainThreadQueries().build();
        dao = database.cacheDao();
    }

    @After public void tearDown() { database.close(); }

    @Test public void snapshotAndOutboxAreIsolatedByUserAndWorkspace() {
        CacheEntry entry = new CacheEntry();
        entry.id = "user-a:workspace-a:inventory"; entry.userId = "user-a";
        entry.workspaceId = "workspace-a";
        entry.resource = "inventory"; entry.json = "[]"; dao.put(entry);
        OutboxOperation operation = new OutboxOperation();
        operation.operationId = "operation-1"; operation.userId = "user-a";
        operation.workspaceId = "workspace-a";
        operation.functionName = "register_harvest"; dao.enqueue(operation);
        assertEquals(1, dao.pendingCount("user-a"));
        assertEquals(0, dao.pendingCount("user-b"));
        assertEquals(1, dao.pendingWorkspace("user-a", "workspace-a", 10).size());
        assertEquals(0, dao.pendingWorkspace("user-a", "workspace-b", 10).size());
        assertEquals("[]", dao.get("user-a:workspace-a:inventory").json);
        dao.clearWorkspaceCache("user-a", "workspace-b");
        assertEquals("[]", dao.get("user-a:workspace-a:inventory").json);
        dao.clearWorkspaceCache("user-a", "workspace-a");
        org.junit.Assert.assertNull(dao.get("user-a:workspace-a:inventory"));
    }

    @Test public void legacyOutboxMovesToFirstSelectedWorkspace() {
        OutboxOperation operation = new OutboxOperation();
        operation.operationId = "legacy-operation";
        operation.userId = "user-a";
        operation.workspaceId = "";
        operation.functionName = "create_planting";
        dao.enqueue(operation);

        dao.assignLegacyOutbox("user-a", "workspace-a");

        assertEquals(1, dao.pendingWorkspace("user-a", "workspace-a", 10).size());
        assertEquals(0, dao.pendingWorkspace("user-a", "", 10).size());
    }
}
