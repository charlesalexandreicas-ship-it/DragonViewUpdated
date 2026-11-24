package ph.dragonview.mobile.ui.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MobileNetV2PackagedModelTest {
    @Test
    public void packagedModelLoadsAndRunsOnDevice() {
        MobileNetV2Classifier.LoadResult result = MobileNetV2Classifier.load(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        assertEquals(result.technicalMessage, MobileNetV2Classifier.Availability.READY,
                result.availability);
        assertNotNull(result.classifier);
        Bitmap image = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.RED);
        try (MobileNetV2Classifier classifier = result.classifier) {
            MobileNetV2Classifier.Classification grade = classifier.classify(image);
            assertTrue(grade.label.equals("Class A") || grade.label.equals("Class B")
                    || grade.label.equals("Class C"));
            assertTrue(Float.isFinite(grade.confidence));
        } finally {
            image.recycle();
        }
    }
}
