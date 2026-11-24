package ph.dragonview.mobile.ui.planting;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FarmLayoutZoomTest {
    @Test public void zoomStaysWithinReadableRange() {
        assertEquals(1f, FarmLayoutFragment.boundedZoom(0.2f), 0f);
        assertEquals(2.5f, FarmLayoutFragment.boundedZoom(2.5f), 0f);
        assertEquals(4f, FarmLayoutFragment.boundedZoom(9f), 0f);
    }
}
