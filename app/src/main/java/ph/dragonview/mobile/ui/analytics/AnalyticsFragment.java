package ph.dragonview.mobile.ui.analytics;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.SalesAnalytics;
import ph.dragonview.mobile.databinding.FragmentAnalyticsBinding;

public final class AnalyticsFragment extends Fragment {
    private FragmentAnalyticsBinding binding;
    private AnalyticsAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentAnalyticsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter = new AnalyticsAdapter();
        binding.summaryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.summaryList.setAdapter(adapter);
        binding.dateInput.setText(
                new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(Calendar.getInstance().getTime()));
        binding.periodInput.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) load();
        });
        binding.dateInput.setOnClickListener(v -> chooseDate());
        load();
    }

    private void chooseDate() {
        String[] dateParts = binding.dateInput.getText().toString().split("-");
        int currentYear = Integer.parseInt(dateParts[0]);
        int currentMonth = Integer.parseInt(dateParts[1]) - 1;
        int currentDay = Integer.parseInt(dateParts[2]);
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            binding.dateInput.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day));
            load();
        }, currentYear, currentMonth, currentDay).show();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.messageText.setText("");
        binding.trendChart.showMessage("Loading analytics…");
        LocalRepository.get(requireContext()).analytics(
                selectedPeriod(),
                binding.dateInput.getText().toString(),
                new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(SalesAnalytics report) {
                        if (binding == null) return;
                        binding.progress.setVisibility(View.GONE);
                        bind(report);
                    }
                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        binding.progress.setVisibility(View.GONE);
                        error(message);
                    }
                });
    }

    private void bind(SalesAnalytics report) {
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        binding.revenueText.setText(php.format(report.getTotals().getRevenue()));
        binding.salesText.setText(String.valueOf(report.getTotals().getCompletedSales()));
        binding.piecesText.setText(String.valueOf(report.getTotals().getPieces()));
        binding.weightText.setText(String.format(Locale.US, "%.3f kg",
                report.getTotals().getWeightKilograms()));
        Double change = report.getComparisonPercent();
        binding.comparisonText.setText(formatComparisonMessage(report));
        binding.comparisonText.setTextColor(requireContext().getColor(
                change == null || change >= 0
                        ? ph.dragonview.mobile.R.color.dragon_green
                        : ph.dragonview.mobile.R.color.dragon_error));
        binding.trendChart.setData(
                report.getTrend() == null ? Collections.emptyList() : report.getTrend(),
                report.getPeriod());
        List<SalesAnalytics.Summary> summaries = report.getSummary() == null
                ? Collections.emptyList() : report.getSummary();
        adapter.submit(summaries);
        binding.summaryEmptyText.setVisibility(
                summaries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    static String formatComparisonMessage(SalesAnalytics report) {
        double previousRevenue = report.getPreviousRevenue();
        double currentRevenue = report.getTotals().getRevenue();
        if (previousRevenue == 0.0 && currentRevenue > 0.0) {
            NumberFormat amount = NumberFormat.getNumberInstance(Locale.US);
            amount.setMinimumFractionDigits(2);
            amount.setMaximumFractionDigits(2);
            return "New revenue: ₱" + amount.format(currentRevenue)
                    + " (no percentage baseline).";
        }
        if (previousRevenue == 0.0 && currentRevenue == 0.0) {
            return "No sales in either period.";
        }
        Double change = report.getComparisonPercent();
        return change == null ? "No previous-period baseline"
                : String.format(Locale.US, "%+.1f%% vs previous period", change);
    }

    private String selectedPeriod() {
        int checkedId = binding.periodInput.getCheckedChipId();
        if (checkedId == ph.dragonview.mobile.R.id.periodWeeklyButton) return "weekly";
        if (checkedId == ph.dragonview.mobile.R.id.periodMonthlyButton) return "monthly";
        if (checkedId == ph.dragonview.mobile.R.id.periodAnnualButton) return "yearly";
        return "daily";
    }

    private void error(String message) {
        String detail = message == null || message.trim().isEmpty()
                ? "Check your connection and try again." : message.trim();
        binding.messageText.setText("Analytics could not be loaded. " + detail);
        binding.trendChart.showMessage("Revenue trend is unavailable");
    }
    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }
}
