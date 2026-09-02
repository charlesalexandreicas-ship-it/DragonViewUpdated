package ph.dragonview.mobile.ui.archive;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.model.ArchivedRecord;
import ph.dragonview.mobile.databinding.ItemArchivedRecordBinding;

final class ArchivedRecordAdapter extends
        RecyclerView.Adapter<ArchivedRecordAdapter.Holder> {
    interface Listener { void restore(ArchivedRecord record); }

    private final List<ArchivedRecord> records = new ArrayList<>();
    private final Listener listener;

    ArchivedRecordAdapter(Listener listener) { this.listener = listener; }

    void submit(List<ArchivedRecord> values) {
        records.clear();
        records.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemArchivedRecordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(records.get(position), listener);
    }

    @Override public int getItemCount() { return records.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemArchivedRecordBinding binding;
        Holder(ItemArchivedRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ArchivedRecord record, Listener listener) {
            binding.typeText.setText(record.getType().getDisplayName());
            binding.titleText.setText(record.getTitle());
            String removed = new SimpleDateFormat(
                    "MMM d, yyyy h:mm a", Locale.US)
                    .format(new Date(record.getArchivedAt()));
            binding.detailsText.setText(record.getDetails()
                    + "\nRemoved: " + removed
                    + "\nReason: " + record.getReason());
            binding.restoreButton.setOnClickListener(v -> listener.restore(record));
        }
    }
}
