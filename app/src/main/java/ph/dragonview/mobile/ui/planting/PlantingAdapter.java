package ph.dragonview.mobile.ui.planting;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.model.PlantingGroup;
import ph.dragonview.mobile.data.model.PlantingStage;
import ph.dragonview.mobile.databinding.ItemGuidanceStageBinding;
import ph.dragonview.mobile.databinding.ItemPlantingBinding;

final class PlantingAdapter extends RecyclerView.Adapter<PlantingAdapter.Holder> {
    interface Listener {
        void showGuidance(PlantingGroup group, PlantingStage stage);
        void manage(PlantingGroup group);
        void archive(PlantingGroup group);
    }

    private final List<PlantingGroup> rows = new ArrayList<>();
    private final Listener listener;
    private long expandedId = -1L;

    PlantingAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<PlantingGroup> values) {
        rows.clear();
        rows.addAll(values);
        if (positionOf(expandedId) < 0) expandedId = -1L;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemPlantingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PlantingGroup row = rows.get(position);
        holder.bind(row, row.getId() == expandedId, listener,
                () -> toggle(row.getId()));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private void toggle(long id) {
        long previous = expandedId;
        expandedId = previous == id ? -1L : id;
        int previousPosition = positionOf(previous);
        int currentPosition = positionOf(expandedId);
        if (previousPosition >= 0) notifyItemChanged(previousPosition);
        if (currentPosition >= 0 && currentPosition != previousPosition) {
            notifyItemChanged(currentPosition);
        }
    }

    private int positionOf(long id) {
        if (id < 0) return -1;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getId() == id) return index;
        }
        return -1;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemPlantingBinding binding;

        Holder(ItemPlantingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PlantingGroup row, boolean expanded, Listener listener,
                  Runnable toggle) {
            String record = row.getRecordNumber();
            if (!record.toLowerCase(Locale.US).startsWith("farm")) {
                record = "Farm Section " + record;
            }
            binding.recordText.setText(record);
            binding.detailsText.setText(row.getPropagationMethod().getDisplayName()
                    + " • " + row.getVariety() + " • "
                    + row.getNumberOfPlants() + " "
                    + row.getPropagationMethod().getQuantityLabel());
            binding.stageText.setText("Current: "
                    + row.getCurrentStage().getDisplayName() + " • Day "
                    + row.getElapsedDays());
            binding.expandedContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.expandIcon.setRotation(expanded ? 180f : 0f);
            binding.accordionHeader.setContentDescription(
                    (expanded ? "Collapse " : "Expand ") + record + " guidance");
            binding.accordionHeader.setOnClickListener(v -> toggle.run());
            binding.manageRecordButton.setOnClickListener(v -> listener.manage(row));
            binding.archiveRecordButton.setOnClickListener(v -> listener.archive(row));

            binding.stageContainer.removeAllViews();
            if (!expanded) return;

            List<PlantingStage> stages = PlantingStage.forMethod(
                    row.getPropagationMethod());
            int currentIndex = stages.indexOf(row.getCurrentStage());
            if (currentIndex < 0) currentIndex = 0;
            LayoutInflater inflater = LayoutInflater.from(binding.getRoot().getContext());
            for (int index = 0; index < stages.size(); index++) {
                PlantingStage stage = stages.get(index);
                ItemGuidanceStageBinding stageBinding = ItemGuidanceStageBinding.inflate(
                        inflater, binding.stageContainer, false);
                stageBinding.stageNameText.setText((index + 1) + ". "
                        + stage.getDisplayName());
                stageBinding.timingText.setText(stage.getEstimatedTiming());
                bindStatus(stageBinding, index, currentIndex);
                stageBinding.stageRow.setOnClickListener(
                        v -> listener.showGuidance(row, stage));
                binding.stageContainer.addView(stageBinding.getRoot());
            }
        }

        private void bindStatus(ItemGuidanceStageBinding stageBinding,
                                int index, int currentIndex) {
            int color;
            String status;
            if (index < currentIndex) {
                status = "Completed";
                color = R.color.dragon_green;
            } else if (index == currentIndex) {
                status = "Current stage";
                color = R.color.dragon_magenta;
            } else {
                status = "Upcoming";
                color = R.color.muted_ink;
            }
            int resolvedColor = ContextCompat.getColor(
                    stageBinding.getRoot().getContext(), color);
            stageBinding.statusText.setText(status);
            stageBinding.statusText.setTextColor(resolvedColor);
            stageBinding.stageImage.setColorFilter(resolvedColor);
        }
    }
}
