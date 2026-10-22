package ph.dragonview.mobile.ui.analytics;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.SalesAnalytics;
import ph.dragonview.mobile.databinding.FragmentAnalyticsBinding;
import ph.dragonview.mobile.R;

/** Displays period-based sales analytics with optional date-to-date comparison. */
public final class AnalyticsFragment extends Fragment {
    private FragmentAnalyticsBinding binding;
    private AnalyticsAdapter adapter;
    private String comparisonDate;
    private boolean summaryExpanded;
    private int requestVersion;
    private SalesAnalytics displayedReport;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentAnalyticsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (state != null) {
            comparisonDate = state.getString("comparisonDate");
            summaryExpanded = state.getBoolean("summaryExpanded");
        }
        adapter = new AnalyticsAdapter();
        binding.summaryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.summaryList.setAdapter(adapter);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Calendar.getInstance().getTime());
        String selectedDate = state == null ? today : state.getString("selectedDate", today);
        String selectedPeriod = state == null ? "daily" : state.getString("selectedPeriod", "daily");
        binding.dateInput.setText(selectedDate);
        binding.periodInput.check(periodChipId(selectedPeriod));
        binding.periodInput.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) load();
        });
        binding.dateInput.setOnClickListener(v -> chooseDate());
        binding.comparisonCard.setOnClickListener(v -> chooseComparison());
        binding.summaryToggle.setOnClickListener(v -> {
            summaryExpanded = !summaryExpanded;
            updateSummaryVisibility();
        });
        binding.historicalRecordsButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_analytics_to_historical));
        binding.historicalMonthlyButton.setOnClickListener(v ->
                binding.periodInput.check(R.id.periodMonthlyButton));
        binding.expandTrendButton.setOnClickListener(v -> openExpandedChart());
        // This view can stay alive on the back stack, so observe the handoff instead of
        // only reading it during view creation.
        NavBackStackEntry analyticsEntry = NavHostFragment.findNavController(this)
                .getBackStackEntry(R.id.analyticsFragment);
        SavedStateHandle handle = analyticsEntry.getSavedStateHandle();
        handle.<String>getLiveData("historicalTrendDate").observe(getViewLifecycleOwner(), date -> {
            if (binding == null || date == null || date.isEmpty()) return;
            handle.remove("historicalTrendDate");
            comparisonDate = null;
            binding.dateInput.setText(date);
            boolean wasMonthly = R.id.periodMonthlyButton == binding.periodInput.getCheckedChipId();
            binding.periodInput.check(R.id.periodMonthlyButton);
            if (wasMonthly) load();
        });
        load();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("comparisonDate", comparisonDate);
        outState.putBoolean("summaryExpanded", summaryExpanded);
        if (binding != null) {
            outState.putString("selectedDate", binding.dateInput.getText().toString());
            outState.putString("selectedPeriod", selectedPeriod());
        }
        super.onSaveInstanceState(outState);
    }

    private static int periodChipId(String period) {
        if ("weekly".equals(period)) return R.id.periodWeeklyButton;
        if ("monthly".equals(period)) return R.id.periodMonthlyButton;
        if ("yearly".equals(period)) return R.id.periodAnnualButton;
        return R.id.periodDailyButton;
    }

    /** Selects the report date used as the endpoint of the current period. */
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

    /** Lets the user add, change, or clear the comparison baseline. */
    private void chooseComparison() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Compare revenue with")
                .setItems(new String[]{"Previous matching period", "Choose another date"},
                        (dialog, choice) -> {
                            if (choice == 0) {
                                comparisonDate = null;
                                load();
                            } else {
                                String seed = comparisonDate == null
                                        ? binding.dateInput.getText().toString() : comparisonDate;
                                String[] parts = seed.split("-");
                                new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
                                    comparisonDate = String.format(Locale.US,
                                            "%04d-%02d-%02d", year, month + 1, day);
                                    load();
                                }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                                        Integer.parseInt(parts[2])).show();
                            }
                        }).show();
    }

    /** Starts a versioned request so a slower old response cannot replace newer data. */
    private void load() {
        int thisRequest = ++requestVersion;
        displayedReport = null;
        binding.expandTrendButton.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        binding.messageText.setText("");
        binding.trendChart.showMessage("Loading analytics…");
        LocalRepository.get(requireContext()).analytics(
                selectedPeriod(),
                binding.dateInput.getText().toString(),
                comparisonDate,
                new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(SalesAnalytics report) {
                        if (binding == null || thisRequest != requestVersion) return;
                        binding.progress.setVisibility(View.GONE);
                        bind(report);
                    }
                    @Override
                    public void onError(String message) {
                        if (binding == null || thisRequest != requestVersion) return;
                        binding.progress.setVisibility(View.GONE);
                        error(message);
                    }
                });
    }

    /** Formats summary metrics, trend points, comparison values, and category rows. */
    private void bind(SalesAnalytics report) {
        displayedReport = report;
        binding.expandTrendButton.setEnabled(true);
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        AnalyticsDateRange currentRange = AnalyticsDateRange.forDate(
                report.getPeriod(), binding.dateInput.getText().toString());
        AnalyticsDateRange compareRange = comparisonDate == null
                ? currentRange.previous()
                : AnalyticsDateRange.forDate(report.getPeriod(), comparisonDate);
        binding.reportingRangeText.setText("Reporting period: " + currentRange.label());
        binding.comparisonCardLabel.setText("Revenue vs " + compareRange.label());
        binding.comparisonCard.setContentDescription("Revenue compared with "
                + compareRange.label() + ". Tap to change comparison date.");
        binding.revenueText.setText(php.format(report.getTotals().getRevenue()));
        binding.revenueDetailText.setText(report.getHistoricalRevenue() > 0
                ? "Includes reviewed paper sales" : "In selected period");
        binding.salesText.setText(String.valueOf(report.getTotals().getCompletedSales()));
        binding.weightText.setText(String.format(Locale.US, "%.3f kg",
                report.getTotals().getWeightKilograms()));
        Double change = report.getComparisonPercent();
        binding.comparisonValueText.setText(formatComparisonValue(report));
        binding.comparisonText.setText(formatComparisonMessage(report,
                comparisonDate == null ? "previous period" : compareRange.label()));
        int comparisonColor = change == null || change >= 0
                ? ph.dragonview.mobile.R.color.dragon_green
                : ph.dragonview.mobile.R.color.dragon_error;
        binding.comparisonValueText.setTextColor(requireContext().getColor(comparisonColor));
        binding.comparisonText.setTextColor(requireContext().getColor(
                comparisonColor));
        binding.trendChart.setData(
                report.getTrend() == null ? Collections.emptyList() : report.getTrend(),
                report.getComparisonTrend() == null
                        ? Collections.emptyList() : report.getComparisonTrend(),
                report.getPeriod());
        String legend = "● " + currentRange.label() + "     ● " + compareRange.label();
        SpannableString markedLegend = new SpannableString(legend);
        markedLegend.setSpan(new ForegroundColorSpan(requireContext().getColor(R.color.dragon_magenta)),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int secondDot = legend.indexOf('●', 1);
        markedLegend.setSpan(new ForegroundColorSpan(requireContext().getColor(R.color.dragon_green)),
                secondDot, secondDot + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.trendLegendText.setText(markedLegend);
        boolean datedPaperSalesOnHourlyView = "daily".equals(report.getPeriod())
                && (report.getHistoricalRevenue() > 0
                || report.getComparisonHistoricalRevenue() > 0);
        binding.historicalHourNote.setVisibility(datedPaperSalesOnHourlyView
                ? View.VISIBLE : View.GONE);
        binding.historicalMonthlyButton.setVisibility(datedPaperSalesOnHourlyView
                ? View.VISIBLE : View.GONE);
        List<SalesAnalytics.Summary> summaries = report.getSummary() == null
                ? Collections.emptyList() : report.getSummary();
        adapter.submit(summaries);
        updateSummaryVisibility();
    }

    /** Opens a wide chart using the report already visible, so its dates stay in sync. */
    private void openExpandedChart() {
        if (displayedReport == null) return;
        AnalyticsDateRange currentRange = AnalyticsDateRange.forDate(
                displayedReport.getPeriod(), binding.dateInput.getText().toString());
        AnalyticsDateRange compareRange = comparisonDate == null
                ? currentRange.previous()
                : AnalyticsDateRange.forDate(displayedReport.getPeriod(), comparisonDate);
        Intent intent = new Intent(requireContext(), ExpandedRevenueTrendActivity.class);
        intent.putExtra("period", displayedReport.getPeriod());
        intent.putExtra("range", "Reporting period: " + currentRange.label());
        intent.putExtra("legend", "Pink: " + currentRange.label()
                + "     Green: " + compareRange.label());
        ExpandedRevenueTrendActivity.putSeries(intent, "current",
                displayedReport.getTrend() == null
                        ? Collections.emptyList() : displayedReport.getTrend());
        ExpandedRevenueTrendActivity.putSeries(intent, "comparison",
                displayedReport.getComparisonTrend() == null
                        ? Collections.emptyList() : displayedReport.getComparisonTrend());
        startActivity(intent);
    }

    private void updateSummaryVisibility() {
        if (binding == null || adapter == null) return;
        binding.summaryToggle.setText(summaryExpanded
                ? "Sales by size and grade · Hide" : "Sales by size and grade · Show");
        binding.summaryToggle.setContentDescription(summaryExpanded
                ? "Hide sales by size and grade" : "Show sales by size and grade");
        binding.summaryDescriptionText.setVisibility(summaryExpanded ? View.VISIBLE : View.GONE);
        binding.summaryEmptyText.setVisibility(summaryExpanded && adapter.getItemCount() == 0
                ? View.VISIBLE : View.GONE);
        binding.summaryList.setVisibility(summaryExpanded && adapter.getItemCount() > 0
                ? View.VISIBLE : View.GONE);
    }

    static String formatComparisonMessage(SalesAnalytics report) {
        return formatComparisonMessage(report, "previous period");
    }

    static String formatComparisonMessage(SalesAnalytics report, String comparedPeriod) {
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
                : String.format(Locale.US, "%+.1f%% vs %s", change, comparedPeriod);
    }

    static String formatComparisonValue(SalesAnalytics report) {
        double previousRevenue = report.getPreviousRevenue();
        double currentRevenue = report.getTotals().getRevenue();
        if (previousRevenue == 0.0) return currentRevenue > 0.0 ? "New" : "—";
        Double change = report.getComparisonPercent();
        return change == null ? "—" : String.format(Locale.US, "%+.1f%%", change);
    }

    private String selectedPeriod() {
        int checkedId = binding.periodInput.getCheckedChipId();
        if (checkedId == ph.dragonview.mobile.R.id.periodWeeklyButton) return "weekly";
        if (checkedId == ph.dragonview.mobile.R.id.periodMonthlyButton) return "monthly";
        if (checkedId == ph.dragonview.mobile.R.id.periodAnnualButton) return "yearly";
        return "daily";
    }

    private void error(String message) {
        displayedReport = null;
        binding.expandTrendButton.setEnabled(false);
        String detail = message == null || message.trim().isEmpty()
                ? "Check your connection and try again." : message.trim();
        binding.messageText.setText("Analytics could not be loaded. " + detail);
        binding.trendChart.showMessage("Revenue trend is unavailable");
    }
    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }
}
