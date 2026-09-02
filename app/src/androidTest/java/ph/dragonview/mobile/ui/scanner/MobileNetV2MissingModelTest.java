package ph.dragonview.mobile.ui.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public final class MobileNetV2MissingModelTest {
    @Test
    public void absentModelReturnsUnavailableWithoutCreatingAClassifier() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeFalse("A real model is bundled; the missing-model scenario no longer applies",
                modelAssetExists(context));

        MobileNetV2Classifier.LoadResult result = MobileNetV2Classifier.load(context);

        assertEquals(MobileNetV2Classifier.Availability.MISSING_MODEL, result.availability);
        assertNull(result.classifier);
        assertNotNull(result.technicalMessage);
        assertTrue(result.technicalMessage.contains(MobileNetV2Classifier.MODEL_ASSET));
    }

    private static boolean modelAssetExists(Context context) {
        try (InputStream ignored = context.getAssets().open(MobileNetV2Classifier.MODEL_ASSET)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
