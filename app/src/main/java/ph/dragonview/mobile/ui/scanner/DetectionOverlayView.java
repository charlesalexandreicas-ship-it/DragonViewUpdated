package ph.dragonview.mobile.ui.scanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** Draws detector boxes over the fully contained selected image. */
public final class DetectionOverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<DragonFruitDetector.Detection> detections = Collections.emptyList();
    private int sourceWidth;
    private int sourceHeight;

    public DetectionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(2.5f * density);
        boxPaint.setColor(Color.rgb(239, 54, 116));
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
        labelPaint.setFakeBoldText(true);
        labelBackgroundPaint.setColor(Color.argb(220, 92, 22, 52));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setDetections(@Nullable List<DragonFruitDetector.Detection> values,
                       int imageWidth, int imageHeight) {
        detections = values == null ? Collections.emptyList() : values;
        sourceWidth = imageWidth;
        sourceHeight = imageHeight;
        invalidate();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (sourceWidth <= 0 || sourceHeight <= 0 || detections.isEmpty()) return;
        // Match ImageView FIT_CENTER so boxes include the same letterbox offsets as the photo.
        float scale = Math.min(getWidth() / (float) sourceWidth, getHeight() / (float) sourceHeight);
        float offsetX = (getWidth() - sourceWidth * scale) / 2f;
        float offsetY = (getHeight() - sourceHeight * scale) / 2f;
        int number = 1;
        for (DragonFruitDetector.Detection detection : detections) {
            RectF source = detection.bounds;
            RectF shown = new RectF(source.left * scale + offsetX, source.top * scale + offsetY,
                    source.right * scale + offsetX, source.bottom * scale + offsetY);
            canvas.drawRect(shown, boxPaint);
            String label = detection.grade == null
                    ? "Fruit " + number
                    : number + " · " + detection.grade.label;
            float labelWidth = labelPaint.measureText(label);
            float labelHeight = labelPaint.getTextSize() * 1.45f;
            float labelTop = Math.max(0f, shown.top - labelHeight);
            canvas.drawRoundRect(new RectF(shown.left, labelTop,
                    shown.left + labelWidth + 16f, labelTop + labelHeight), 7f, 7f,
                    labelBackgroundPaint);
            canvas.drawText(label, shown.left + 8f,
                    labelTop + labelHeight * 0.72f, labelPaint);
            number++;
        }
    }
}
