package ph.dragonview.mobile.ui.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DetectionOverlayViewTest {
    @Test public void scannerKeepsPhotoActionsAndResultsInOrder() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.content.Context target = androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getTargetContext();
            android.content.Context themed = new android.view.ContextThemeWrapper(
                    target, ph.dragonview.mobile.R.style.Theme_DragonView);
            ph.dragonview.mobile.databinding.FragmentScannerBinding ui =
                    ph.dragonview.mobile.databinding.FragmentScannerBinding.inflate(
                            android.view.LayoutInflater.from(themed));
            android.view.ViewGroup column = (android.view.ViewGroup) ui.scannerPreviewCard.getParent();
            assertEquals(0, column.indexOfChild(ui.scannerPreviewCard));
            assertEquals(1, column.indexOfChild(ui.scannerActions));
            assertEquals(android.widget.ImageView.ScaleType.FIT_CENTER,
                    ui.selectedImagePreview.getScaleType());
        });
    }

    @Test
    public void labelStaysAboveFruitWhenThereIsRoom() {
        RectF fruit = new RectF(20f, 40f, 100f, 120f);
        RectF label = DetectionOverlayView.labelBounds(fruit, 60f, 20f, 200f, 200f, 4f);
        assertTrue(label.bottom < fruit.top);
        assertEquals(20f, label.left, 0f);
    }

    @Test
    public void labelMovesBesideFruitAtTopEdge() {
        RectF fruit = new RectF(20f, 0f, 100f, 195f);
        RectF label = DetectionOverlayView.labelBounds(fruit, 60f, 20f, 200f, 200f, 4f);
        assertTrue(label.left > fruit.right);
        assertTrue(label.bottom <= 200f);
    }
}
