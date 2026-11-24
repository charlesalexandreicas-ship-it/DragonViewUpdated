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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.model.PlantingGroup;
import ph.dragonview.mobile.data.model.PlantingStage;
import ph.dragonview.mobile.data.model.PropagationMethod;
import ph.dragonview.mobile.databinding.ItemGuidanceStageBinding;
import ph.dragonview.mobile.databinding.ItemPlantingBinding;

final class PlantingAdapter extends RecyclerView.Adapter<PlantingAdapter.Holder> {
    interface Listener {
        void showGuidance(PlantingGroup group, PlantingStage stage);
        void manage(PlantingGroup group);
        void archive(PlantingGroup group);
        boolean hasSectionPhoto(PlantingGroup group);
        void openSectionPhoto(PlantingGroup group);
    }

    private final List<PlantingGroup> rows = new ArrayList<>();
    private final Listener listener;
    private final boolean canArchive;
    private long expandedId = -1L;

    PlantingAdapter(Listener listener, boolean canArchive) {
        this.listener = listener;
        this.canArchive = canArchive;
    }

    void submit(List<PlantingGroup> values) {
        rows.clear();
        rows.addAll(values);
        // Start with compact rows; an existing user-expanded row stays open after refresh.
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
        holder.bind(row, row.getId() == expandedId, listener, canArchive,
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
                  boolean canArchive, Runnable toggle) {
            String record = row.getRecordNumber();
            if (!record.toLowerCase(Locale.US).startsWith("farm")) {
                record = "Farm Section " + record;
            }
            binding.recordText.setText(record);
            boolean grafted = row.getPropagationMethod() == PropagationMethod.GRAFTED;
            binding.detailsText.setText(row.getNumberOfPlants() + " "
                    + row.getPropagationMethod().getQuantityLabel() + " · "
                    + (grafted ? "Grafted " : "Planted ") + shortDate(row.getPlantingDate()));
            binding.stageText.setText(row.getCurrentStage().getDisplayName());
            binding.expandedContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.compactGuidanceButton.setVisibility(expanded ? View.GONE : View.VISIBLE);
            binding.compactGuidanceButton.setOnClickListener(
                    v -> listener.showGuidance(row, row.getCurrentStage()));
            binding.expandIcon.setRotation(expanded ? 180f : 0f);
            binding.accordionHeader.setContentDescription(
                    (expanded ? "Collapse " : "Expand ") + record + " guidance");
            binding.accordionHeader.setOnClickListener(v -> toggle.run());
            binding.manageRecordButton.setOnClickListener(v -> listener.manage(row));
            binding.archiveRecordButton.setVisibility(canArchive ? View.VISIBLE : View.GONE);
            binding.archiveRecordButton.setOnClickListener(v -> listener.archive(row));

            binding.stageContainer.removeAllViews();
            if (!expanded) return;

            binding.sectionPhotoButton.setText(listener.hasSectionPhoto(row)
                    ? R.string.farm_section_view_photo : R.string.farm_section_add_photo);
            binding.sectionPhotoButton.setOnClickListener(v -> listener.openSectionPhoto(row));

            List<PlantingStage> stages = PlantingStage.forMethod(
                    row.getPropagationMethod());
            int currentIndex = stages.indexOf(row.getCurrentStage());
            if (currentIndex < 0) currentIndex = 0;
            // The real method-specific sequence remains available; its length varies by record.
            binding.stageProgressText.setText("Growth stages");
            binding.viewGuidanceButton.setOnClickListener(
                    v -> listener.showGuidance(row, row.getCurrentStage()));
            LayoutInflater inflater = LayoutInflater.from(binding.getRoot().getContext());
            for (int index = 0; index < stages.size(); index++) {
                PlantingStage stage = stages.get(index);
                ItemGuidanceStageBinding stageBinding = ItemGuidanceStageBinding.inflate(
                        inflater, binding.stageContainer, false);
                stageBinding.stageNameText.setText(stage.getDisplayName());
                stageBinding.timingText.setText(stage.getEstimatedTiming());
                bindStatus(stageBinding, index, currentIndex);
                // Hide only the outer ends so the rail connects adjacent stage numbers.
                stageBinding.stageLineAbove.setVisibility(index == 0 ? View.INVISIBLE : View.VISIBLE);
                stageBinding.stageLineBelow.setVisibility(index == stages.size() - 1 ? View.INVISIBLE : View.VISIBLE);
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
                color = R.color.muted_ink;
            } else if (index == currentIndex) {
                status = "Current";
                color = R.color.botanical_green;
            } else {
                status = "Upcoming";
                color = R.color.muted_ink;
            }
            int resolvedColor = ContextCompat.getColor(
                    stageBinding.getRoot().getContext(), color);
            stageBinding.statusText.setText(status);
            stageBinding.statusText.setTextColor(resolvedColor);
            android.content.Context context = stageBinding.getRoot().getContext();
            boolean current = index == currentIndex;
            android.graphics.drawable.GradientDrawable marker = new android.graphics.drawable.GradientDrawable();
            marker.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            marker.setColor(ContextCompat.getColor(context, current ? R.color.botanical_green
                    : index < currentIndex ? R.color.dragon_green_soft : R.color.white));
            if (index > currentIndex) marker.setStroke(
                    Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)),
                    ContextCompat.getColor(context, R.color.botanical_border));
            stageBinding.stageNumber.setBackground(marker);
            stageBinding.stageNumber.setText(String.valueOf(index + 1));
            stageBinding.stageNumber.setTextColor(current ? ContextCompat.getColor(context, R.color.white) : resolvedColor);
            if (current) {
                stageBinding.stageRow.setBackgroundTintList(null);
                stageBinding.stageRow.setBackground(new android.graphics.drawable.RippleDrawable(
                        ContextCompat.getColorStateList(context, R.color.focus_ripple),
                        new android.graphics.drawable.ColorDrawable(ContextCompat.getColor(context, R.color.dragon_green_soft)),
                        null));
            }
        }

        private static String shortDate(String value) {
            if (value == null || value.length() < 10) return "Date unavailable";
            try {
                Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value.substring(0, 10));
                return new SimpleDateFormat("MMM d", Locale.US).format(date);
            } catch (ParseException ignored) {
                return value.substring(0, 10);
            }
        }
    }
}
