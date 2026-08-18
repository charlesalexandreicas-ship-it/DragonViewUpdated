package ph.dragonview.mobile.ui.analytics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
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

public final class RevenueTrendChartView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<SalesAnalytics.Trend> points = Collections.emptyList();
    private String period = "daily";

    public RevenueTrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        grid.setColor(Color.parseColor("#E5E7EB"));
        grid.setStrokeWidth(density);
        axisText.setColor(Color.parseColor("#6B7280"));
        axisText.setTextSize(10 * density);
        line.setColor(context.getColor(R.color.dragon_green));
        line.setStrokeWidth(3 * density);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(Color.parseColor("#2215803D"));
        fill.setStyle(Paint.Style.FILL);
        point.setColor(context.getColor(R.color.dragon_green));
        point.setStyle(Paint.Style.FILL);
    }

    public void setData(List<SalesAnalytics.Trend> values, String selectedPeriod) {
        points = values == null ? Collections.emptyList() : values;
        period = selectedPeriod == null ? "daily" : selectedPeriod;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float left = 55 * density, right = getWidth() - 10 * density;
        float top = 15 * density, bottom = getHeight() - 30 * density;
        if (right <= left || bottom <= top) return;

        double max = 0;
        for (SalesAnalytics.Trend value : points) max = Math.max(max, value.getRevenue());
        max = niceMaximum(max);
        axisText.setTextAlign(Paint.Align.RIGHT);
        for (int index = 0; index <= 4; index++) {
            float y = bottom - (bottom - top) * index / 4f;
            canvas.drawLine(left, y, right, y, grid);
            canvas.drawText(formatAxis(max * index / 4), left - 7 * density,
                    y + 4 * density, axisText);
        }
        canvas.drawText("PHP", left - 7 * density, top - 4 * density, axisText);
        if (points.isEmpty()) {
            axisText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No completed sales in this period",
                    (left + right) / 2, (top + bottom) / 2, axisText);
            return;
        }

        int count = points.size();
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int index = 0; index < count; index++) {
            xs[index] = count == 1 ? (left + right) / 2
                    : left + (right - left) * index / (count - 1f);
            ys[index] = bottom - (float) (points.get(index).getRevenue() / max)
                    * (bottom - top);
        }
        Path curve = smoothPath(xs, ys);
        Path area = new Path(curve);
        area.lineTo(xs[count - 1], bottom);
        area.lineTo(xs[0], bottom);
        area.close();
        canvas.drawPath(area, fill);
        canvas.drawPath(curve, line);

        int labelStep = labelStep(count);
        axisText.setTextAlign(Paint.Align.CENTER);
        for (int index = 0; index < count; index++) {
            canvas.drawCircle(xs[index], ys[index], 3 * density, point);
            if (index % labelStep == 0 || index == count - 1) {
                canvas.drawText(points.get(index).getLabel(), xs[index],
                        bottom + 17 * density, axisText);
            }
        }
    }

    private Path smoothPath(float[] xs, float[] ys) {
        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int index = 1; index < xs.length; index++) {
            float midpoint = (xs[index - 1] + xs[index]) / 2;
            path.cubicTo(midpoint, ys[index - 1], midpoint, ys[index],
                    xs[index], ys[index]);
        }
        return path;
    }

    private int labelStep(int count) {
        if ("daily".equals(period)) return 4;
        if ("monthly".equals(period)) return Math.max(1, count / 6);
        return 1;
    }

    private double niceMaximum(double value) {
        if (value <= 0) return 100;
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        return Math.ceil(value / magnitude) * magnitude;
    }

    private String formatAxis(double value) {
        if (value >= 1_000_000) return String.format(Locale.US, "₱%.1fM", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.US, "₱%.1fk", value / 1_000);
        return String.format(Locale.US, "₱%.0f", value);
    }
}
