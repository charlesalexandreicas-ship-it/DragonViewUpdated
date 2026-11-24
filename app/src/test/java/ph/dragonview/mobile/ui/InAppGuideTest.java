package ph.dragonview.mobile.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class InAppGuideTest {
    @Test public void everyReplayDestinationHasAnActionableGuide() {
        assertEquals(InAppGuide.NAMES.length, InAppGuide.DESTINATIONS.length);
        for (int destination : InAppGuide.DESTINATIONS) {
            InAppGuide.Step step = InAppGuide.stepFor(destination);
            assertNotNull(step);
            assertTrue(step.targetId != 0);
            assertFalse(step.body.isEmpty());
        }
        assertNull(InAppGuide.stepFor(-1));
    }
}
