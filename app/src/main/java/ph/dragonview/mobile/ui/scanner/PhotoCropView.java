package ph.dragonview.mobile.ui.scanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Displays the whole uploaded photo with a movable, resizable crop selection. */
public final class PhotoCropView extends View {
    private static final float MIN_FRACTION = 0.08f;
    private static final int MOVE = 5;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadePaint = new Paint();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final RectF selection = new RectF(0f, 0f, 1f, 1f);
    @Nullable private Bitmap bitmap;
    private int dragMode;
    private float lastX;
    private float lastY;

    public PhotoCropView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        shadePaint.setColor(0x99000000);
        outlinePaint.setColor(Color.WHITE);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(2));
        handlePaint.setColor(0xFFCF3472);
        setContentDescription("Move the crop area or drag a pink corner to resize it");
    }

    void setPhoto(@NonNull Bitmap photo) {
        bitmap = photo;
        selection.set(0f, 0f, 1f, 1f);
        invalidate();
    }

    @NonNull
    Rect cropBounds() {
        if (bitmap == null) throw new IllegalStateException("No photo loaded");
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = Math.max(0, Math.min(width - 1, (int) Math.floor(selection.left * width)));
        int top = Math.max(0, Math.min(height - 1, (int) Math.floor(selection.top * height)));
        int right = Math.max(left + 1, Math.min(width, (int) Math.ceil(selection.right * width)));
        int bottom = Math.max(top + 1, Math.min(height, (int) Math.ceil(selection.bottom * height)));
        return new Rect(left, top, right, bottom);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
        float scale = Math.min(getWidth() / (float) bitmap.getWidth(),
                getHeight() / (float) bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        imageRect.set((getWidth() - width) / 2f, (getHeight() - height) / 2f,
                (getWidth() + width) / 2f, (getHeight() + height) / 2f);
        canvas.drawBitmap(bitmap, null, imageRect, imagePaint);
        RectF selected = displayedSelection();
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, selected.top, shadePaint);
        canvas.drawRect(imageRect.left, selected.bottom, imageRect.right, imageRect.bottom, shadePaint);
        canvas.drawRect(imageRect.left, selected.top, selected.left, selected.bottom, shadePaint);
        canvas.drawRect(selected.right, selected.top, imageRect.right, selected.bottom, shadePaint);
        canvas.drawRect(selected, outlinePaint);
        float handle = dp(6);
        canvas.drawCircle(selected.left, selected.top, handle, handlePaint);
        canvas.drawCircle(selected.right, selected.top, handle, handlePaint);
        canvas.drawCircle(selected.left, selected.bottom, handle, handlePaint);
        canvas.drawCircle(selected.right, selected.bottom, handle, handlePaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (bitmap == null || imageRect.isEmpty()) return false;
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                RectF selected = displayedSelection();
                float radius = dp(32);
                dragMode = corner(x, y, selected.left, selected.top, radius) ? 1
                        : corner(x, y, selected.right, selected.top, radius) ? 2
                        : corner(x, y, selected.left, selected.bottom, radius) ? 3
                        : corner(x, y, selected.right, selected.bottom, radius) ? 4
                        : selected.contains(x, y) ? MOVE : 0;
                if (dragMode == 0) return false;
                lastX = x;
                lastY = y;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragMode == 0) return false;
                float dx = (x - lastX) / imageRect.width();
                float dy = (y - lastY) / imageRect.height();
                if (dragMode == MOVE) {
                    dx = clamp(dx, -selection.left, 1f - selection.right);
                    dy = clamp(dy, -selection.top, 1f - selection.bottom);
                    selection.offset(dx, dy);
                } else {
                    if (dragMode == 1 || dragMode == 3) {
                        selection.left = clamp(selection.left + dx, 0f,
                                selection.right - MIN_FRACTION);
                    } else {
                        selection.right = clamp(selection.right + dx,
                                selection.left + MIN_FRACTION, 1f);
                    }
                    if (dragMode == 1 || dragMode == 2) {
                        selection.top = clamp(selection.top + dy, 0f,
                                selection.bottom - MIN_FRACTION);
                    } else {
                        selection.bottom = clamp(selection.bottom + dy,
                                selection.top + MIN_FRACTION, 1f);
                    }
                }
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (dragMode == 0) return false;
                dragMode = 0;
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragMode = 0;
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @NonNull
    private RectF displayedSelection() {
        return new RectF(imageRect.left + selection.left * imageRect.width(),
                imageRect.top + selection.top * imageRect.height(),
                imageRect.left + selection.right * imageRect.width(),
                imageRect.top + selection.bottom * imageRect.height());
    }

    private boolean corner(float x, float y, float cx, float cy, float radius) {
        return Math.abs(x - cx) <= radius && Math.abs(y - cy) <= radius;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
