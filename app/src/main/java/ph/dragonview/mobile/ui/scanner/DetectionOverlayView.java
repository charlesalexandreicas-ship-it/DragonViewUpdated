package ph.dragonview.mobile.ui.scanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/** Draws detector boxes over the fully contained selected image. */
public final class DetectionOverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<DragonFruitDetector.Detection> detections = Collections.emptyList();
    private final GestureDetector taps;
    private final RectF shown = new RectF();
    private int selectedFruit;
    @Nullable private IntConsumer onFruitSelected;
    private int sourceWidth;
    private int sourceHeight;

    public DetectionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(2.5f * density);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
        labelPaint.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(
                context, ph.dragonview.mobile.R.font.manrope_700));
        labelBackgroundPaint.setColor(Color.argb(220, 92, 22, 52));
        taps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override public boolean onSingleTapUp(@NonNull MotionEvent event) {
                float scale = imageScale();
                for (int index = detections.size() - 1; index >= 0; index--) {
                    mapBounds(detections.get(index).bounds, scale);
                    if (shown.contains(event.getX(), event.getY())) {
                        if (onFruitSelected != null) onFruitSelected.accept(index);
                        return true;
                    }
                }
                return false;
            }
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setDetections(@Nullable List<DragonFruitDetector.Detection> values,
                       int imageWidth, int imageHeight) {
        detections = values == null ? Collections.emptyList() : values;
        sourceWidth = imageWidth;
        sourceHeight = imageHeight;
        invalidate();
    }

    void setOnFruitSelected(@NonNull IntConsumer listener) {
        onFruitSelected = listener;
    }

    void setSelectedFruit(int index) {
        selectedFruit = index;
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        return sourceWidth > 0 && sourceHeight > 0 && !detections.isEmpty()
                && taps.onTouchEvent(event);
    }

    private float imageScale() {
        return Math.min(getWidth() / (float) sourceWidth, getHeight() / (float) sourceHeight);
    }

    /** Drawing and hit testing both include the same letterbox offsets as the photo. */
    private void mapBounds(RectF source, float scale) {
        float offsetX = (getWidth() - sourceWidth * scale) / 2f;
        float offsetY = (getHeight() - sourceHeight * scale) / 2f;
        shown.set(source.left * scale + offsetX, source.top * scale + offsetY,
                source.right * scale + offsetX, source.bottom * scale + offsetY);
    }

    /** Prefer above or beside the box so the grade stays clear of the fruit. */
    static RectF labelBounds(RectF fruit, float width, float height,
                             float viewWidth, float viewHeight, float gap) {
        float left = Math.max(0f, Math.min(fruit.left, viewWidth - width));
        if (fruit.top - gap - height >= 0f) {
            return new RectF(left, fruit.top - gap - height, left + width, fruit.top - gap);
        }
        float top = Math.max(0f, Math.min(fruit.top, viewHeight - height));
        if (fruit.right + gap + width <= viewWidth) {
            return new RectF(fruit.right + gap, top, fruit.right + gap + width, top + height);
        }
        if (fruit.left - gap - width >= 0f) {
            return new RectF(fruit.left - gap - width, top, fruit.left - gap, top + height);
        }
        if (fruit.bottom + gap + height <= viewHeight) {
            return new RectF(left, fruit.bottom + gap, left + width, fruit.bottom + gap + height);
        }
        // shortcut: edge-to-edge boxes leave no outside space; keep the label visible at the lower edge.
        return new RectF(left, viewHeight - height, left + width, viewHeight);
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (sourceWidth <= 0 || sourceHeight <= 0 || detections.isEmpty()) return;
        float scale = imageScale();
        int number = 1;
        for (DragonFruitDetector.Detection detection : detections) {
            mapBounds(detection.bounds, scale);
            boolean selected = number - 1 == selectedFruit;
            int accent = Color.rgb(147, 12, 65);
            boxPaint.setColor(selected ? accent : Color.WHITE);
            labelBackgroundPaint.setColor(selected ? accent : Color.WHITE);
            labelPaint.setColor(selected ? Color.WHITE : Color.BLACK);
            canvas.drawRect(shown, boxPaint);
            String label = detection.grade == null
                    ? "Fruit " + number
                    : number + " · " + detection.grade.label;
            float labelWidth = Math.min(getWidth(), labelPaint.measureText(label) + 16f);
            float labelHeight = labelPaint.getTextSize() * 1.45f;
            RectF labelBox = labelBounds(shown, labelWidth, labelHeight,
                    getWidth(), getHeight(), 4f * getResources().getDisplayMetrics().density);
            canvas.drawRoundRect(labelBox, 7f, 7f, labelBackgroundPaint);
            canvas.drawText(label, labelBox.left + 8f,
                    labelBox.top + labelHeight * 0.72f, labelPaint);
            number++;
        }
    }
}
