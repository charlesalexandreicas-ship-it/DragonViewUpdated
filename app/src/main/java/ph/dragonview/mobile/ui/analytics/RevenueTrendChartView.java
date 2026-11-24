package ph.dragonview.mobile.ui.analytics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.model.SalesAnalytics;

/** Draws current and comparison revenue on one scale. */
public final class RevenueTrendChartView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint comparisonLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint comparisonPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<SalesAnalytics.Trend> current = Collections.emptyList();
    private List<SalesAnalytics.Trend> comparison = Collections.emptyList();
    private String period = "daily";
    private String statusMessage;

    public RevenueTrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        grid.setColor(context.getColor(R.color.dragon_outline));
        grid.setStrokeWidth(density);
        axisText.setColor(context.getColor(R.color.muted_ink));
        axisText.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(context, R.font.manrope_400));
        axisText.setTextSize(10 * density);
        configureLine(currentLine, context.getColor(R.color.dragon_green), density);
        configureLine(comparisonLine, context.getColor(R.color.botanical_header), density);
        comparisonLine.setPathEffect(new DashPathEffect(new float[]{6 * density, 5 * density}, 0));
        currentPoint.setColor(context.getColor(R.color.dragon_green));
        comparisonPoint.setColor(context.getColor(R.color.botanical_header));
    }

    private static void configureLine(Paint paint, int color, float density) {
        paint.setColor(color);
        paint.setStrokeWidth(2.5f * density);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setData(List<SalesAnalytics.Trend> values, String selectedPeriod) {
        setData(values, Collections.emptyList(), selectedPeriod);
    }

    public void setData(List<SalesAnalytics.Trend> currentValues,
                        List<SalesAnalytics.Trend> comparisonValues, String selectedPeriod) {
        current = currentValues == null ? Collections.emptyList() : currentValues;
        comparison = comparisonValues == null ? Collections.emptyList() : comparisonValues;
        period = selectedPeriod == null ? "daily" : selectedPeriod;
        statusMessage = null;
        setContentDescription(current.isEmpty() && comparison.isEmpty()
                ? "Revenue trend chart. No sales in either period."
                : comparison.isEmpty() ? "Revenue trend chart. Solid green."
                : "Revenue trend chart comparing the selected " + period
                + " period in solid green with the comparison period in dashed berry.");
        invalidate();
    }

    public void showMessage(String message) {
        current = Collections.emptyList();
        comparison = Collections.emptyList();
        statusMessage = message;
        setContentDescription(message);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float left = 40 * density, right = getWidth() - 24 * density;
        float top = 30 * density, bottom = getHeight() - 36 * density;
        if (right <= left || bottom <= top) return;

        double maxValue = 0;
        for (SalesAnalytics.Trend value : current) maxValue = Math.max(maxValue, value.getRevenue());
        for (SalesAnalytics.Trend value : comparison) maxValue = Math.max(maxValue, value.getRevenue());
        double max = niceMaximum(maxValue);
        float widest = 0;
        for (int index = 0; index <= 4; index++) {
            widest = Math.max(widest, axisText.measureText(formatAxis(max * index / 4)));
        }
        left = Math.max(left, widest + 8 * density);
        if (right <= left) return;
        axisText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Revenue (PHP)", left, 13 * density, axisText);
        axisText.setTextAlign(Paint.Align.RIGHT);
        for (int index = 0; index <= 4; index++) {
            float y = bottom - (bottom - top) * index / 4f;
            canvas.drawLine(left, y, right, y, grid);
            canvas.drawText(formatAxis(max * index / 4), left - 6 * density,
                    y + 4 * density, axisText);
        }
        if (statusMessage != null || current.isEmpty() && comparison.isEmpty()) {
            axisText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(statusMessage == null ? "No sales in either period" : statusMessage,
                    (left + right) / 2, (top + bottom) / 2, axisText);
            return;
        }
        int chartBuckets = Math.max(current.size(), comparison.size());
        drawSeries(canvas, comparison, chartBuckets, left, right, top, bottom, max,
                comparisonLine, comparisonPoint, false);
        drawSeries(canvas, current, chartBuckets, left, right, top, bottom, max,
                currentLine, currentPoint, true);
    }

    private void drawSeries(Canvas canvas, List<SalesAnalytics.Trend> values,
                            int chartBuckets,
                            float left, float right, float top, float bottom, double max,
                            Paint line, Paint point, boolean drawLabels) {
        if (values.isEmpty()) return;
        float density = getResources().getDisplayMetrics().density;
        Path path = new Path();
        int count = values.size();
        int labelStep = labelStep(count);
        float lastLabelRight = Float.NEGATIVE_INFINITY;
        String finalLabel = values.get(count - 1).getLabel();
        float finalHalfWidth = axisText.measureText(finalLabel) / 2;
        float finalX = Math.max(left + finalHalfWidth,
                Math.min(right, right - finalHalfWidth));
        for (int index = 0; index < count; index++) {
            float x = chartBuckets == 1 ? (left + right) / 2
                    : left + (right - left) * index / (chartBuckets - 1f);
            float y = bottom - (float) (values.get(index).getRevenue() / max)
                    * (bottom - top);
            if (index == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
            canvas.drawCircle(x, y, 2.5f * density, point);
            if (drawLabels && (index % labelStep == 0 || index == count - 1)) {
                axisText.setTextAlign(Paint.Align.CENTER);
                String label = values.get(index).getLabel();
                float halfWidth = axisText.measureText(label) / 2;
                float labelX = Math.max(left + halfWidth,
                        Math.min(x, right - halfWidth));
                // Keep compact-axis labels apart and reserve room for the final date.
                float start = labelX - halfWidth;
                float end = labelX + halfWidth;
                boolean clearOfLast = index == count - 1
                        || end + 8 * density <= finalX - finalHalfWidth;
                if (start >= lastLabelRight + 8 * density && clearOfLast) {
                    canvas.drawText(label, labelX, bottom + 20 * density, axisText);
                    lastLabelRight = end;
                }
            }
        }
        // One bucket is a point, never an artificial horizontal line.
        if (count > 1) canvas.drawPath(path, line);
    }

    private int labelStep(int count) {
        if ("daily".equals(period)) return 4;
        if ("monthly".equals(period)) return Math.max(1, count / 6);
        if ("yearly".equals(period)) return 2;
        return 1;
    }

    private static double niceMaximum(double value) {
        if (value <= 0) return 100;
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        return Math.ceil(value / magnitude) * magnitude;
    }

    private static String formatAxis(double value) {
        if (value >= 1_000_000) return String.format(Locale.US, "₱%.1fM", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.US, "₱%.1fk", value / 1_000);
        return String.format(Locale.US, "₱%.0f", value);
    }
}
