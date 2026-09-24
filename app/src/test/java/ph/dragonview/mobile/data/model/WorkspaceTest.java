package ph.dragonview.mobile.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WorkspaceTest {
    @Test public void ownerWorkspaceExposesOwnerRole() {
        Workspace workspace = new Workspace(
                "workspace-1", "North Farm", "OWNER", true, 3, "A1B2C3D4");

        assertTrue(workspace.isOwner());
        assertTrue(workspace.isActive());
        assertEquals(3, workspace.getMemberCount());
        assertEquals("A1B2C3D4", workspace.getInviteCode());
    }

    @Test public void missingRoleDefaultsToFarmerPermissions() {
        Workspace workspace = new Workspace(
                "workspace-2", "South Farm", null, false, 1, null);

        assertFalse(workspace.isOwner());
        assertEquals("FARMER", workspace.getRole());
    }
}
