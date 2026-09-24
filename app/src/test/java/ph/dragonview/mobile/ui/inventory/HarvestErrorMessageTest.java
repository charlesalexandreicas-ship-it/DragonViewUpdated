package ph.dragonview.mobile.ui.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HarvestErrorMessageTest {
    @Test
    public void duplicateBatchCombinationExplainsBothChoices() {
        String displayed = HarvestErrorMessage.display(
                "duplicate key value violates unique constraint \"inventory_workspace_batch_size_grade_key\"");
        assertTrue(displayed.contains("batch number"));
        assertTrue(displayed.contains("same size and grade"));
        assertTrue(displayed.contains("update the existing inventory"));
    }

    @Test
    public void unrelatedCloudErrorIsPreserved() {
        assertEquals("Unable to reach the server",
                HarvestErrorMessage.display("Unable to reach the server"));
    }
}
