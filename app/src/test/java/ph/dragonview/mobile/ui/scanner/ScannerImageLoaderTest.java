package ph.dragonview.mobile.ui.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScannerImageLoaderTest {
    @Test
    public void imagesAtOrBelowMaximumEdgeNeedNoDownsampling() {
        assertEquals(1, ScannerImageLoader.calculateSampleSize(640, 480, 2048));
        assertEquals(1, ScannerImageLoader.calculateSampleSize(2048, 2048, 2048));
    }

    @Test
    public void imageJustBelowTwiceTheCapIsStillDownsampled() {
        assertEquals(2, ScannerImageLoader.calculateSampleSize(4095, 4095, 2048));
    }

    @Test
    public void sampleSizeChangesAtPowerOfTwoBoundaries() {
        assertEquals(2, ScannerImageLoader.calculateSampleSize(4096, 2048, 2048));
        assertEquals(4, ScannerImageLoader.calculateSampleSize(4097, 2048, 2048));
    }

    @Test
    public void portraitAndLandscapeUseTheSameLargestEdgeRule() {
        assertEquals(4, ScannerImageLoader.calculateSampleSize(4000, 3000, 1600));
        assertEquals(4, ScannerImageLoader.calculateSampleSize(3000, 4000, 1600));
    }

    @Test
    public void sampleSizeIsPowerOfTwoAndCapsRoundedDecodedDimensions() {
        int[][] cases = {
                {1, 1, 1},
                {2049, 1024, 2048},
                {3024, 4032, 2048},
                {8193, 4097, 2048},
                {10000, 1, 100},
                {Integer.MAX_VALUE, 1, 2}
        };

        for (int[] dimensions : cases) {
            int sampleSize = ScannerImageLoader.calculateSampleSize(
                    dimensions[0], dimensions[1], dimensions[2]);
            long largestDecodedEdge = (Math.max(dimensions[0], dimensions[1])
                    + (long) sampleSize - 1L) / sampleSize;

            assertTrue("Sample size must be a positive power of two",
                    sampleSize > 0 && (sampleSize & (sampleSize - 1)) == 0);
            assertTrue("Rounded decoded size must not exceed the requested cap",
                    largestDecodedEdge <= dimensions[2]);
        }
    }

    @Test
    public void nonPositiveDimensionsAndCapAreRejected() {
        int[][] invalidCases = {
                {0, 100, 100},
                {-1, 100, 100},
                {100, 0, 100},
                {100, -1, 100},
                {100, 100, 0},
                {100, 100, -1}
        };

        for (int[] dimensions : invalidCases) {
            assertThrows(IllegalArgumentException.class,
                    () -> ScannerImageLoader.calculateSampleSize(
                            dimensions[0], dimensions[1], dimensions[2]));
        }
    }

    @Test
    public void extremeDimensionsDoNotOverflowTheSampleSize() {
        assertEquals(1 << 30,
                ScannerImageLoader.calculateSampleSize(Integer.MAX_VALUE, 1, 2));
        assertEquals(1, ScannerImageLoader.calculateSampleSize(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> ScannerImageLoader.calculateSampleSize(Integer.MAX_VALUE, 1, 1));
    }
}
