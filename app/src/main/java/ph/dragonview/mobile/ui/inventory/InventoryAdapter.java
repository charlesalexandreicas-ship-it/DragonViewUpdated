package ph.dragonview.mobile.ui.inventory;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ph.dragonview.mobile.data.model.InventoryBatch;
import ph.dragonview.mobile.databinding.ItemInventoryLotBinding;
import ph.dragonview.mobile.R;

final class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.Holder> {
    private final List<InventoryBatch> batches = new ArrayList<>();
    interface Listener {
        void open(InventoryBatch batch);
        void archive(InventoryBatch batch);
    }
    private final Listener listener;
    InventoryAdapter(Listener listener) { this.listener = listener; }

    void submit(List<InventoryBatch> newBatches) {
        batches.clear();
        batches.addAll(newBatches);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemInventoryLotBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(batches.get(position), listener);
    }

    @Override public int getItemCount() { return batches.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemInventoryLotBinding binding;
        Holder(ItemInventoryLotBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        void bind(InventoryBatch batch, Listener listener) {
            binding.batchText.setText("#" + batch.getBatchNumber());
            binding.detailsText.setText(batch.getAvailablePieces() + " pieces available\n"
                    + batch.getItemCount() + " size/grade entries  •  Harvested "
                    + batch.getHarvestDate().substring(0, 10));
            binding.gradeText.setText(batch.getItemCount() == 1
                    ? "1 ENTRY" : batch.getItemCount() + " ENTRIES");
            int age = batch.getAgeInDays();
            String ageLabel = age == 1 ? "1 day old" : age + " days old";
            int accent;
            int background;
            String queueLabel = batch.getNextOutItems() > 0
                    ? batch.getNextOutItems() + " FIFO priorit" +
                    (batch.getNextOutItems() == 1 ? "y" : "ies") : "Queued";
            if (age >= 4) {
                binding.fifoStatusText.setText("FIFO · "
                        + queueLabel + " · " + ageLabel);
                accent = R.color.dragon_error;
                background = R.color.fifo_urgent_soft;
            } else if (age >= 3) {
                binding.fifoStatusText.setText("FIFO · USE SOON · "
                        + queueLabel + " · " + ageLabel);
                accent = R.color.fifo_warning;
                background = R.color.fifo_warning_soft;
            } else {
                binding.fifoStatusText.setText("FIFO · FRESH · "
                        + queueLabel + " · " + ageLabel);
                accent = R.color.dragon_green;
                background = R.color.dragon_green_soft;
            }
            int accentColor = binding.getRoot().getContext().getColor(accent);
            int backgroundColor = binding.getRoot().getContext().getColor(background);
            binding.fifoStatusText.setTextColor(accentColor);
            binding.fifoStatusText.setBackgroundTintList(
                    ColorStateList.valueOf(backgroundColor));
            binding.getRoot().setStrokeColor(accentColor);
            binding.getRoot().setStrokeWidth(batch.getNextOutItems() > 0 ? 3 : 1);
            binding.getRoot().setOnClickListener(v -> listener.open(batch));
            binding.archiveButton.setOnClickListener(v -> listener.archive(batch));
        }
    }
}
