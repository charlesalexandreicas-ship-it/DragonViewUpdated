package ph.dragonview.mobile.ui.historical;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.HistoricalRecord;
import ph.dragonview.mobile.data.model.HistoricalRecordDraft;
import ph.dragonview.mobile.databinding.FragmentHistoricalRecordsBinding;

/** Owner-only list and entry point for reviewed paper records. */
public final class HistoricalRecordsFragment extends Fragment {
    private FragmentHistoricalRecordsBinding binding;
    private HistoricalRecordsAdapter adapter;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
                                        Bundle state) {
        binding = FragmentHistoricalRecordsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter = new HistoricalRecordsAdapter(this::showRecord);
        binding.recordsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recordsList.setAdapter(adapter);
        binding.manualButton.setOnClickListener(v -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_historical_to_editor));
        binding.captureButton.setOnClickListener(v -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_historical_to_capture));
    }

    @Override public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (binding == null) return;
        binding.progress.setVisibility(View.VISIBLE);
        binding.messageText.setText("");
        LocalRepository.get(requireContext()).historicalRecords(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<HistoricalRecord> records) {
                if (binding == null) return;
                binding.progress.setVisibility(View.GONE);
                adapter.submitList(records);
                binding.messageText.setText(records.isEmpty()
                        ? "No historical records yet. Enter one or capture a paper page." : "");
            }
            @Override public void onError(String message) {
                if (binding == null) return;
                binding.progress.setVisibility(View.GONE);
                binding.messageText.setText(message);
            }
        });
    }

    private void showRecord(HistoricalRecord record) {
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        StringBuilder details = new StringBuilder();
        if (record.getItems() != null) {
            for (HistoricalRecordDraft.Item item : record.getItems()) {
                details.append(item.getSize().replace('_', ' ')).append(" · ");
                if (item.getWeightKilograms() != null) {
                    details.append(String.format(Locale.US, "%.3f kg", item.getWeightKilograms()));
                } else {
                    details.append(item.getLegacyPieces()).append(" pcs (legacy; kg unknown)");
                }
                details.append(" · ")
                        .append(php.format(item.getPricePerKilogram())).append("/kg · ")
                        .append(php.format(item.getSubtotal())).append(" · ")
                        .append(Boolean.TRUE.equals(item.getActualSales())
                                ? "Actual sale" : "Estimate").append("\n");
            }
        }
        details.append("\nSubtotal sum: ").append(php.format(record.getTotalAmount()));
        if (record.getPaperGrandTotal() != null) {
            details.append("\nGrand total on paper: ")
                    .append(php.format(record.getPaperGrandTotal()));
        }
        details.append("\nActual sales included in revenue: ")
                .append(php.format(record.getSalesAmount()))
                .append("\nEstimated value excluded: ")
                .append(php.format(record.getTotalAmount() - record.getSalesAmount()))
                .append("\nInventory stays unchanged.");
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(record.getDate())
                .setMessage(details.toString())
                .setPositiveButton("Close", null);
        if (record.getSalesAmount() > 0) {
            dialog.setNeutralButton(R.string.historical_view_revenue_trend,
                    (ignored, which) -> viewRevenueTrend(record.getDate()));
        }
        dialog.show();
    }

    /** Focus Analytics on the paper date with day buckets, not today's hourly buckets. */
    private void viewRevenueTrend(String recordDate) {
        NavController controller = NavHostFragment.findNavController(this);
        controller.getBackStackEntry(R.id.analyticsFragment).getSavedStateHandle()
                .set("historicalTrendDate", recordDate);
        controller.popBackStack(R.id.analyticsFragment, false);
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
