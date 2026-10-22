package ph.dragonview.mobile.ui.analytics;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.model.SalesAnalytics;
import ph.dragonview.mobile.databinding.ItemAnalyticsSummaryBinding;

final class AnalyticsAdapter extends RecyclerView.Adapter<AnalyticsAdapter.Holder> {
    private final List<SalesAnalytics.Summary> rows = new ArrayList<>();
    void submit(List<SalesAnalytics.Summary> values) { rows.clear(); rows.addAll(values); notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemAnalyticsSummaryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) { holder.bind(rows.get(position)); }
    @Override public int getItemCount() { return rows.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemAnalyticsSummaryBinding binding;
        Holder(ItemAnalyticsSummaryBinding binding) { super(binding.getRoot()); this.binding = binding; }
        void bind(SalesAnalytics.Summary row) {
            String size = row.getSize() == null ? "Size not recorded"
                    : row.getSize().replace('_', ' ');
            boolean gradeMissing = row.getGrade() == null || row.getGrade().trim().isEmpty();
            binding.categoryText.setText(size + " • " + (gradeMissing
                    ? "Grade not recorded" : "Grade " + row.getGrade()));
            if (gradeMissing) {
                String weight = row.getWeightKilograms() > 0
                        ? String.format(Locale.US, "%.3f kg", row.getWeightKilograms())
                        : "Weight not recorded";
                String legacy = row.getPieces() > 0
                        ? " • " + row.getPieces() + " legacy pieces" : "";
                binding.quantityText.setText(weight + legacy + " • Historical sale");
            } else {
                binding.quantityText.setText(row.getPieces() + " pieces • "
                        + String.format(Locale.US, "%.3f kg", row.getWeightKilograms()));
            }
            binding.revenueText.setText(NumberFormat.getCurrencyInstance(new Locale("en", "PH")).format(row.getRevenue()));
        }
    }
}
