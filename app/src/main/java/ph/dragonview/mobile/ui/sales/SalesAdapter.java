package ph.dragonview.mobile.ui.sales;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.model.SaleSummary;
import ph.dragonview.mobile.databinding.ItemSaleBinding;

final class SalesAdapter extends RecyclerView.Adapter<SalesAdapter.Holder> {
    interface Listener { void archive(SaleSummary sale); }
    private final List<SaleSummary> rows = new ArrayList<>();
    private final Listener listener;
    SalesAdapter(Listener listener) { this.listener = listener; }
    void submit(List<SaleSummary> values) { rows.clear(); rows.addAll(values); notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemSaleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(rows.get(position), listener);
    }
    @Override public int getItemCount() { return rows.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemSaleBinding binding;
        Holder(ItemSaleBinding binding) { super(binding.getRoot()); this.binding = binding; }
        void bind(SaleSummary sale, Listener listener) {
            binding.titleText.setText("Sale #" + sale.getId() + " • " + sale.getCustomerName());
            String date = sale.getTransactionDate() == null ? "" : sale.getTransactionDate().replace('T', ' ');
            binding.detailsText.setText(sale.getStatus() + " • " + sale.getTotalPieces() + " pieces\n" + date);
            binding.totalText.setText(NumberFormat.getCurrencyInstance(new Locale("en", "PH")).format(sale.getTotalAmount()));
            binding.archiveButton.setOnClickListener(v -> listener.archive(sale));
        }
    }
}

