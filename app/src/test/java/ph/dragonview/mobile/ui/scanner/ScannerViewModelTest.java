package ph.dragonview.mobile.ui.scanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScannerViewModelTest {
    @Test
    public void supportsEveryCountFromOneThroughEight() {
        for (int count = 1; count <= 8; count++) {
            assertTrue("Expected supported fruit count: " + count,
                    ScannerViewModel.isSupportedFruitCount(count));
        }
    }

    @Test
    public void rejectsZeroAndCountsAboveEight() {
        assertFalse(ScannerViewModel.isSupportedFruitCount(0));
        assertFalse(ScannerViewModel.isSupportedFruitCount(9));
        assertFalse(ScannerViewModel.isSupportedFruitCount(Integer.MAX_VALUE));
    }
}
