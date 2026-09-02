package ph.dragonview.mobile.ui.scanner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MobileNetV2ClassifierTest {
    private static final float FLOAT_TOLERANCE = 0.000001f;

    @Test
    public void validSoftmaxProbabilitiesArePreservedWithoutMutatingInput() {
        float[] input = {0.8f, 0.2f};

        float[] result = MobileNetV2Classifier.toProbabilities(input);

        assertArrayEquals(new float[]{0.8f, 0.2f}, result, FLOAT_TOLERANCE);
        assertNotSame(input, result);
        result[0] = 0.0f;
        assertArrayEquals(new float[]{0.8f, 0.2f}, input, 0.0f);
    }

    @Test
    public void smallSoftmaxRoundingDriftIsRenormalized() {
        float[] result = MobileNetV2Classifier.toProbabilities(new float[]{0.6f, 0.39f});

        assertArrayEquals(new float[]{0.6f / 0.99f, 0.39f / 0.99f},
                result, FLOAT_TOLERANCE);
        assertEquals(1.0f, result[0] + result[1], FLOAT_TOLERANCE);
    }

    @Test
    public void smallSoftmaxExcessIsRenormalized() {
        float[] result = MobileNetV2Classifier.toProbabilities(new float[]{0.6f, 0.41f});

        assertArrayEquals(new float[]{0.6f / 1.01f, 0.41f / 1.01f},
                result, FLOAT_TOLERANCE);
        assertEquals(1.0f, result[0] + result[1], FLOAT_TOLERANCE);
    }

    @Test
    public void oneHotProbabilitiesAcceptZeroAndOneEndpoints() {
        assertArrayEquals(new float[]{0.0f, 1.0f, 0.0f},
                MobileNetV2Classifier.toProbabilities(new float[]{0.0f, 1.0f, 0.0f}),
                0.0f);
    }

    @Test
    public void emptyProbabilitiesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MobileNetV2Classifier.toProbabilities(new float[0]));
    }

    @Test
    public void nonFiniteProbabilitiesAreRejected() {
        float[] invalidValues = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (float invalidValue : invalidValues) {
            assertThrows("Reject non-finite output: " + invalidValue,
                    IllegalArgumentException.class,
                    () -> MobileNetV2Classifier.toProbabilities(
                            new float[]{0.5f, invalidValue}));
        }
    }

    @Test
    public void probabilitiesOutsideZeroToOneAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MobileNetV2Classifier.toProbabilities(new float[]{-0.1f, 1.0f}));
        assertThrows(IllegalArgumentException.class,
                () -> MobileNetV2Classifier.toProbabilities(new float[]{0.0f, 1.1f}));
    }

    @Test
    public void unnormalizedScoresAreRejectedInsteadOfConvertedWithSoftmax() {
        assertThrows(IllegalArgumentException.class,
                () -> MobileNetV2Classifier.toProbabilities(new float[]{0.2f, 0.3f}));
        assertThrows(IllegalArgumentException.class,
                () -> MobileNetV2Classifier.toProbabilities(new float[]{0.7f, 0.4f}));
        assertThrows(IllegalArgumentException.class,
                () -> MobileNetV2Classifier.toProbabilities(new float[]{0.0f, 0.0f}));
    }

    @Test
    public void topIndexFindsLargestValueAtAnyPosition() {
        assertEquals(0, MobileNetV2Classifier.topIndex(new float[]{0.7f, 0.2f, 0.1f}));
        assertEquals(1, MobileNetV2Classifier.topIndex(new float[]{0.2f, 0.7f, 0.1f}));
        assertEquals(2, MobileNetV2Classifier.topIndex(new float[]{0.2f, 0.1f, 0.7f}));
    }

    @Test
    public void topIndexUsesFirstIndexWhenLargestValuesTie() {
        assertEquals(1, MobileNetV2Classifier.topIndex(new float[]{0.1f, 0.45f, 0.45f}));
    }

    @Test
    public void topIndexHandlesSingleValueAndRejectsEmptyInput() {
        assertEquals(0, MobileNetV2Classifier.topIndex(new float[]{1.0f}));
        assertThrows(IllegalArgumentException.class,
                () -> MobileNetV2Classifier.topIndex(new float[0]));
    }

    @Test
    public void channelEndpointsUseMobileNetV2NegativeOneToOneRange() {
        assertEquals(-1.0f, MobileNetV2Classifier.normalizeChannel(0), 0.0f);
        assertEquals(1.0f, MobileNetV2Classifier.normalizeChannel(255), 0.0f);
    }

    @Test
    public void middleChannelsRemainOnOppositeSidesOfZero() {
        assertEquals(-1.0f / 255.0f, MobileNetV2Classifier.normalizeChannel(127),
                FLOAT_TOLERANCE);
        assertEquals(1.0f / 255.0f, MobileNetV2Classifier.normalizeChannel(128),
                FLOAT_TOLERANCE);
    }

    @Test
    public void allByteChannelsNormalizeMonotonicallyInsideExpectedRange() {
        float previous = Float.NEGATIVE_INFINITY;
        for (int channel = 0; channel <= 255; channel++) {
            float normalized = MobileNetV2Classifier.normalizeChannel(channel);
            assertTrue("Channel " + channel + " must stay in [-1, 1]",
                    normalized >= -1.0f && normalized <= 1.0f);
            assertTrue("Channel " + channel + " must increase", normalized > previous);
            previous = normalized;
        }
    }
}
