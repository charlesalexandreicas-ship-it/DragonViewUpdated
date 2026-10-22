package ph.dragonview.mobile.ui.historical;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.Locale;

import ph.dragonview.mobile.data.model.HistoricalRecord;
import ph.dragonview.mobile.databinding.ItemHistoricalRecordBinding;

final class HistoricalRecordsAdapter extends ListAdapter<HistoricalRecord, HistoricalRecordsAdapter.Holder> {
    interface Listener { void open(HistoricalRecord record); }
    private final Listener listener;

    HistoricalRecordsAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override public boolean areItemsTheSame(@NonNull HistoricalRecord left,
                                                      @NonNull HistoricalRecord right) {
                return left.getId().equals(right.getId());
            }
            @Override public boolean areContentsTheSame(@NonNull HistoricalRecord left,
                                                         @NonNull HistoricalRecord right) {
                return left.getDate().equals(right.getDate())
                        && left.getTotalAmount() == right.getTotalAmount()
                        && left.getSalesAmount() == right.getSalesAmount();
            }
        });
        this.listener = listener;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemHistoricalRecordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemHistoricalRecordBinding binding;
        Holder(ItemHistoricalRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        void bind(HistoricalRecord record, Listener listener) {
            binding.dateText.setText(record.getDate());
            int count = record.getItems() == null ? 0 : record.getItems().size();
            binding.detailText.setText(count + (count == 1 ? " size" : " sizes") + " • "
                    + (record.getSalesAmount() == 0 ? "Estimated harvest value"
                    : record.getSalesAmount() == record.getTotalAmount()
                    ? "Actual sales" : "Actual sales + estimated value"));
            binding.amountText.setText(NumberFormat.getCurrencyInstance(new Locale("en", "PH"))
                    .format(record.getTotalAmount()));
            binding.getRoot().setContentDescription("Historical record " + record.getDate()
                    + ", " + count + " sizes. Tap to view details.");
            binding.getRoot().setOnClickListener(v -> listener.open(record));
        }
    }
}
