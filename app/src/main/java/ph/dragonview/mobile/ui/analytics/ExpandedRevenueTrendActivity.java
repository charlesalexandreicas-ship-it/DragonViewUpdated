package ph.dragonview.mobile.ui.analytics;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import ph.dragonview.mobile.data.model.SalesAnalytics;
import ph.dragonview.mobile.databinding.ActivityExpandedRevenueTrendBinding;

/** Shows the same report points on a wider, landscape chart without another server request. */
public final class ExpandedRevenueTrendActivity extends AppCompatActivity {
    private ActivityExpandedRevenueTrendBinding binding;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityExpandedRevenueTrendBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.closeChart.setOnClickListener(view -> finish());

        Intent source = getIntent();
        binding.chartLegend.setText(source.getStringExtra("legend"));
        binding.chartRange.setText(source.getStringExtra("range"));
        List<SalesAnalytics.Trend> current = readSeries(source, "current");
        List<SalesAnalytics.Trend> comparison = readSeries(source, "comparison");
        if (current == null || comparison == null) {
            binding.expandedChart.showMessage("Revenue trend is unavailable");
            binding.chartLegend.setVisibility(View.GONE);
        } else {
            binding.expandedChart.setData(current, comparison,
                    source.getStringExtra("period"));
        }
    }

    /** Copies only the plotted labels and amounts across the Activity boundary. */
    public static void putSeries(@NonNull Intent intent, @NonNull String prefix,
                                 @NonNull List<SalesAnalytics.Trend> values) {
        String[] labels = new String[values.size()];
        double[] amounts = new double[values.size()];
        for (int index = 0; index < values.size(); index++) {
            labels[index] = values.get(index).getLabel();
            amounts[index] = values.get(index).getRevenue();
        }
        intent.putExtra(prefix + "Labels", labels);
        intent.putExtra(prefix + "Amounts", amounts);
    }

    private static List<SalesAnalytics.Trend> readSeries(Intent source, String prefix) {
        String[] labels = source.getStringArrayExtra(prefix + "Labels");
        double[] amounts = source.getDoubleArrayExtra(prefix + "Amounts");
        if (labels == null || amounts == null || labels.length != amounts.length) return null;
        List<SalesAnalytics.Trend> values = new ArrayList<>(labels.length);
        for (int index = 0; index < labels.length; index++) {
            values.add(new SalesAnalytics.Trend(labels[index], amounts[index], 0));
        }
        return values;
    }
}
