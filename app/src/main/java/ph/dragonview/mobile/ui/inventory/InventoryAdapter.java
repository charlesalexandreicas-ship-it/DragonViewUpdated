package ph.dragonview.mobile.ui.inventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.model.InventoryBatch;
import ph.dragonview.mobile.data.model.InventoryLot;
import ph.dragonview.mobile.databinding.ItemInventoryLotBinding;

final class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.Holder> {
    private final List<InventoryBatch> batches = new ArrayList<>();
    private Map<String, BatchComposition> compositionByBatch = Collections.emptyMap();
    private Map<String, String> breakdownByBatch = Collections.emptyMap();
    private String expandedBatchNumber;
    interface Listener {
        void open(InventoryBatch batch);
        void archive(InventoryBatch batch);
    }
    private final Listener listener;
    private final boolean canArchive;
    InventoryAdapter(Listener listener, boolean canArchive) {
        this.listener = listener;
        this.canArchive = canArchive;
    }

    void submit(List<InventoryBatch> newBatches) {
        batches.clear();
        batches.addAll(newBatches);
        compositionByBatch = Collections.emptyMap();
        breakdownByBatch = Collections.emptyMap();
        if (!batches.isEmpty() && positionOf(expandedBatchNumber) < 0) {
            expandedBatchNumber = batches.get(0).getBatchNumber();
        }
        notifyDataSetChanged();
    }

    /** Adds the finer stock breakdown after the batch list has loaded. */
    void submitComposition(List<InventoryLot> lots) {
        compositionByBatch = BatchComposition.fromLots(lots);
        Map<String, Map<String, Integer>> rows = new HashMap<>();
        if (lots == null) lots = Collections.emptyList();
        for (InventoryLot lot : lots) {
            if (lot == null || lot.getAvailablePieces() <= 0 || lot.getBatchNumber() == null) continue;
            String size = lot.getSize() == null || lot.getSize().trim().isEmpty()
                    ? "Unknown" : lot.displaySize();
            String grade = lot.getGrade() == null || lot.getGrade().trim().isEmpty()
                    ? "?" : lot.getGrade().trim().toUpperCase(Locale.US);
            rows.computeIfAbsent(lot.getBatchNumber(), ignored -> new TreeMap<>())
                    .merge(size + " · " + grade, lot.getAvailablePieces(), Integer::sum);
        }
        Map<String, String> breakdowns = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> batch : rows.entrySet()) {
            StringBuilder text = new StringBuilder("AVAILABLE BY SIZE & GRADE");
            for (Map.Entry<String, Integer> row : batch.getValue().entrySet()) {
                text.append('\n').append(row.getKey()).append("  ·  ")
                        .append(row.getValue()).append(" pcs");
            }
            breakdowns.put(batch.getKey(), text.toString());
        }
        breakdownByBatch = breakdowns;
        if (!batches.isEmpty()) notifyItemRangeChanged(0, batches.size());
    }

    /** Keeps a single batch open while leaving the detail navigation and archive actions intact. */
    private void toggle(String batchNumber) {
        String previous = expandedBatchNumber;
        expandedBatchNumber = batchNumber.equals(previous) ? null : batchNumber;
        int oldPosition = positionOf(previous);
        int newPosition = positionOf(expandedBatchNumber);
        if (oldPosition >= 0) notifyItemChanged(oldPosition);
        if (newPosition >= 0 && newPosition != oldPosition) notifyItemChanged(newPosition);
    }

    private int positionOf(String batchNumber) {
        if (batchNumber == null) return -1;
        for (int index = 0; index < batches.size(); index++) {
            if (batchNumber.equals(batches.get(index).getBatchNumber())) return index;
        }
        return -1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemInventoryLotBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        InventoryBatch batch = batches.get(position);
        String number = batch.getBatchNumber();
        holder.bind(batch, compositionByBatch.get(number), breakdownByBatch.get(number),
                number.equals(expandedBatchNumber), listener, canArchive,
                () -> toggle(number));
    }

    @Override public int getItemCount() { return batches.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemInventoryLotBinding binding;
        Holder(ItemInventoryLotBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        void bind(InventoryBatch batch, BatchComposition composition, String breakdown,
                  boolean expanded, Listener listener, boolean canArchive, Runnable toggle) {
            // The batch identifier and stock facts stay readable in one short row.
            String number = batch.getBatchNumber();
            Context context = binding.getRoot().getContext();
            binding.batchText.setText(number.startsWith("#") ? number
                    : context.getString(R.string.inventory_batch_number, number));
            binding.piecesText.setText(context.getString(R.string.inventory_piece_count,
                    batch.getAvailablePieces()));
            binding.compositionText.setText(composition == null
                    ? context.getString(R.string.inventory_combination_count, batch.getItemCount())
                    : composition.sizeCount() + " sizes · " + composition.gradeCount() + " grades");
            binding.dateText.setText(shortDate(batch.getHarvestDate()));
            int age = batch.getAgeInDays();
            String ageLabel = age == 1 ? context.getString(R.string.inventory_one_day_old)
                    : context.getString(R.string.inventory_days_old, age);
            binding.ageText.setText(ageLabel);
            // FIFO age keeps its warning color even though the new card border stays neutral.
            int ageColor = age >= 4 ? R.color.dragon_error
                    : age >= 3 ? R.color.fifo_warning : R.color.muted_ink;
            binding.ageText.setTextColor(context.getColor(ageColor));
            binding.dateText.setTextColor(context.getColor(
                    batch.getNextOutItems() > 0 ? ageColor : R.color.muted_ink));
            String queueLabel = batch.getNextOutItems() > 0
                    ? batch.getNextOutItems() + " FIFO priorit" +
                    (batch.getNextOutItems() == 1 ? "y" : "ies") : "Queued";
            binding.expandedContent.setVisibility(expanded ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.expandIcon.setRotation(expanded ? 180f : 0f);
            binding.expandIcon.setContentDescription(expanded ? "Collapse batch" : "Expand batch");
            binding.breakdownText.setText(breakdown == null
                    ? "Size and grade breakdown unavailable" : breakdown);
            binding.batchHeader.setOnClickListener(v -> toggle.run());
            binding.batchHeader.setContentDescription((expanded ? "Collapse " : "Expand ")
                    + "batch " + number + ", " + binding.piecesText.getText());
            binding.getRoot().setContentDescription("Batch " + number + ", "
                    + binding.piecesText.getText() + ", "
                    + binding.compositionText.getText() + ", harvested "
                    + binding.dateText.getText() + ", " + queueLabel + ", " + ageLabel);
            binding.detailsButton.setOnClickListener(v -> listener.open(batch));
            binding.archiveButton.setVisibility(canArchive
                    ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.archiveButton.setOnClickListener(v -> listener.archive(batch));
        }

        private static String shortDate(String value) {
            if (value == null || value.length() < 10) return value == null ? "" : value;
            try {
                Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value.substring(0, 10));
                return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(date);
            } catch (ParseException ignored) {
                return value.substring(0, 10);
            }
        }
    }
}
