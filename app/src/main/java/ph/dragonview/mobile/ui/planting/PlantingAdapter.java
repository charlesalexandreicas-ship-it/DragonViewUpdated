package ph.dragonview.mobile.ui.planting;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ph.dragonview.mobile.data.model.PlantingGroup;
import ph.dragonview.mobile.databinding.ItemPlantingBinding;

final class PlantingAdapter extends RecyclerView.Adapter<PlantingAdapter.Holder> {
    private final List<PlantingGroup> rows = new ArrayList<>();
    interface Listener { void open(PlantingGroup row); }
    private final Listener listener;
    PlantingAdapter(Listener listener) { this.listener = listener; }
    void submit(List<PlantingGroup> values) {
        rows.clear(); rows.addAll(values); notifyDataSetChanged();
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemPlantingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(rows.get(position), listener);
    }
    @Override public int getItemCount() { return rows.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemPlantingBinding binding;
        Holder(ItemPlantingBinding binding) { super(binding.getRoot()); this.binding = binding; }
        void bind(PlantingGroup row, Listener listener) {
            binding.recordText.setText("#" + row.getRecordNumber());
            binding.detailsText.setText(row.getLocation() + " • " + row.getVariety()
                    + " • " + row.getNumberOfPlants() + " plants");
            binding.stageText.setText(row.getCurrentStage().getDisplayName());
            binding.progress.setProgress((int) row.getProgressPercent());
            String cutting = "ROOTED".equals(row.getCuttingType())
                    ? "Rooted cutting" : "Unrooted cutting";
            StringBuilder status = new StringBuilder("Day ")
                    .append(row.getElapsedDays()).append(" since planting • ")
                    .append(cutting);
            if (row.getFruitAgeDays() != null) {
                status.append("\nFruit day ").append(row.getFruitAgeDays())
                        .append(" • Estimated window: ")
                        .append(row.getEstimatedHarvestWindow());
            } else {
                status.append("\nSuggested next: ")
                        .append(row.getSuggestedStage().getDisplayName());
            }
            binding.daysText.setText(status.toString());
            binding.getRoot().setOnClickListener(v -> listener.open(row));
        }
    }
}
