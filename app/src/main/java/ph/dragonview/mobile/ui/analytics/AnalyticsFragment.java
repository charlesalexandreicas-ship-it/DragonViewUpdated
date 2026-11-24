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
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.SalesAnalytics;
import ph.dragonview.mobile.databinding.FragmentAnalyticsBinding;
import ph.dragonview.mobile.databinding.SheetYearlyAnalyticsBinding;
import ph.dragonview.mobile.R;

/** Displays period-based sales analytics with optional date-to-date comparison. */
public final class AnalyticsFragment extends Fragment {
    private FragmentAnalyticsBinding binding;
    private AnalyticsAdapter adapter;
    private static final String[] PERIODS = {"daily", "weekly", "monthly", "yearly"};
    private static final String[] PERIOD_LABELS = {"Daily", "Weekly", "Monthly", "Yearly"};
    private String selectedDate;
    private String period;
    private String comparisonDate;
    private String yearlyComparisonDate;
    private boolean yearlyCutoff;
    private boolean yearlyComparisonOff;
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
            yearlyComparisonDate = state.getString("yearlyComparisonDate");
            yearlyCutoff = state.getBoolean("yearlyCutoff");
            yearlyComparisonOff = state.getBoolean("yearlyComparisonOff");
            summaryExpanded = state.getBoolean("summaryExpanded");
        }
        adapter = new AnalyticsAdapter();
        binding.summaryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.summaryList.setAdapter(adapter);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Calendar.getInstance().getTime());
        selectedDate = state == null ? today : state.getString("selectedDate", today);
        period = state == null ? "daily" : state.getString("selectedPeriod", "daily");
        binding.periodInput.setOnClickListener(v -> choosePeriod());
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
                setPeriod("monthly"));
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
            selectedDate = date;
            setPeriod("monthly");
        });
        load();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("comparisonDate", comparisonDate);
        outState.putString("yearlyComparisonDate", yearlyComparisonDate);
        outState.putBoolean("yearlyCutoff", yearlyCutoff);
        outState.putBoolean("yearlyComparisonOff", yearlyComparisonOff);
        outState.putBoolean("summaryExpanded", summaryExpanded);
        if (binding != null) {
            outState.putString("selectedDate", selectedDate);
            outState.putString("selectedPeriod", period);
        }
        super.onSaveInstanceState(outState);
    }

    private void choosePeriod() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reporting period")
                .setSingleChoiceItems(PERIOD_LABELS, Arrays.asList(PERIODS).indexOf(period),
                        (dialog, choice) -> {
                            dialog.dismiss();
                            setPeriod(PERIODS[choice]);
                        })
                .show();
    }

    private void setPeriod(String value) {
        period = value;
        load();
    }

    /** Selects the report date used as the endpoint of the current period. */
    private void chooseDate() {
        if ("yearly".equals(period)) {
            chooseYearlyDates();
            return;
        }
        String[] dateParts = selectedDate.split("-");
        int currentYear = Integer.parseInt(dateParts[0]);
        int currentMonth = Integer.parseInt(dateParts[1]) - 1;
        int currentDay = Integer.parseInt(dateParts[2]);
        if ("monthly".equals(period)) {
            chooseMonth(currentYear, currentMonth);
            return;
        }
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            load();
        }, currentYear, currentMonth, currentDay).show();
    }

    /** Monthly queries only need the first day of the chosen month. */
    private void chooseMonth(int currentYear, int currentMonth) {
        LinearLayout inputs = new LinearLayout(requireContext());
        inputs.setGravity(android.view.Gravity.CENTER);
        NumberPicker year = yearPicker(currentYear);

        NumberPicker month = new NumberPicker(requireContext());
        month.setMinValue(1);
        month.setMaxValue(12);
        month.setDisplayedValues(Arrays.copyOf(
                new java.text.DateFormatSymbols(Locale.US).getMonths(), 12));
        month.setValue(currentMonth + 1);
        month.setContentDescription("Month");
        inputs.addView(month);
        inputs.addView(year);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Choose month")
                .setView(inputs)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    inputs.clearFocus();
                    selectedDate = String.format(Locale.US, "%04d-%02d-01", year.getValue(),
                            month.getValue());
                    load();
                })
                .show();
    }

    private NumberPicker yearPicker(int year) {
        NumberPicker picker = new NumberPicker(requireContext());
        picker.setMinValue(Math.min(1900, year));
        picker.setMaxValue(Math.max(2100, year));
        picker.setValue(year);
        picker.setContentDescription("Year");
        return picker;
    }

    private void pickYear(int initial, java.util.function.IntConsumer picked) {
        NumberPicker picker = yearPicker(initial);
        LinearLayout content = new LinearLayout(requireContext());
        content.setGravity(android.view.Gravity.CENTER);
        content.addView(picker);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Choose year").setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    picker.clearFocus();
                    picked.accept(picker.getValue());
                }).show();
    }

    /** Keep edits in the sheet until Apply so dismissing it preserves the displayed report. */
    private void chooseYearlyDates() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        SheetYearlyAnalyticsBinding sheet = SheetYearlyAnalyticsBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheet.getRoot());
        final class Draft {
            String date = selectedDate;
            int compareYear = yearlyComparisonDate == null
                    ? Integer.parseInt(date.substring(0, 4)) - 1
                    : Integer.parseInt(yearlyComparisonDate.substring(0, 4));
        }
        Draft draft = new Draft();
        sheet.rangeMode.check(yearlyCutoff ? R.id.throughDate : R.id.fullYear);
        sheet.comparisonMode.check(yearlyComparisonOff ? R.id.comparisonOff
                : yearlyComparisonDate == null ? R.id.previousYear : R.id.anotherYear);
        Runnable refresh = () -> {
            boolean cutoff = sheet.rangeMode.getCheckedRadioButtonId() == R.id.throughDate;
            sheet.rangeDate.setText(AnalyticsDateRange.forDate(cutoff ? "daily" : "yearly", draft.date)
                    .compactLabel());
            sheet.comparisonYear.setText(String.valueOf(draft.compareYear));
            sheet.comparisonYear.setVisibility(sheet.comparisonMode.getCheckedRadioButtonId()
                    == R.id.anotherYear ? View.VISIBLE : View.GONE);
        };
        sheet.rangeMode.setOnCheckedChangeListener((group, id) -> refresh.run());
        sheet.comparisonMode.setOnCheckedChangeListener((group, id) -> refresh.run());
        sheet.rangeDate.setOnClickListener(view -> {
            String[] date = draft.date.split("-");
            int year = Integer.parseInt(date[0]);
            if (sheet.rangeMode.getCheckedRadioButtonId() == R.id.fullYear) {
                pickYear(year, chosen -> {
                    draft.date = String.format(Locale.US, "%04d-01-01", chosen);
                    refresh.run();
                });
            } else {
                new DatePickerDialog(requireContext(), (picker, chosen, month, day) -> {
                    draft.date = String.format(Locale.US, "%04d-%02d-%02d", chosen, month + 1, day);
                    refresh.run();
                }, year, Integer.parseInt(date[1]) - 1, Integer.parseInt(date[2])).show();
            }
        });
        sheet.comparisonYear.setOnClickListener(view -> pickYear(draft.compareYear, chosen -> {
            draft.compareYear = chosen;
            refresh.run();
        }));
        sheet.apply.setOnClickListener(view -> {
            selectedDate = draft.date;
            yearlyCutoff = sheet.rangeMode.getCheckedRadioButtonId() == R.id.throughDate;
            int choice = sheet.comparisonMode.getCheckedRadioButtonId();
            yearlyComparisonOff = choice == R.id.comparisonOff;
            yearlyComparisonDate = choice == R.id.anotherYear
                    ? String.format(Locale.US, "%04d-01-01", draft.compareYear) : null;
            dialog.dismiss();
            load();
        });
        refresh.run();
        dialog.show();
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
                                        ? selectedDate : comparisonDate;
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
        // Keep the exact query date separate from the shorter label on the filter button.
        binding.periodInput.setText(PERIOD_LABELS[Arrays.asList(PERIODS).indexOf(period)]);
        binding.dateInput.setText(hasYearlyCutoff()
                ? "Through " + AnalyticsDateRange.forDate("daily", selectedDate).compactLabel()
                : AnalyticsDateRange.forDate(period, selectedDate).compactLabel());
        binding.comparisonCard.setVisibility("yearly".equals(period) ? View.GONE : View.VISIBLE);
        binding.trendLegendText.setText(null);
        binding.trendLegendText.setContentDescription(null);
        binding.comparisonText.setText(null);
        binding.revenueText.setText("—");
        binding.salesText.setText("—");
        binding.piecesText.setText("—");
        binding.weightText.setText("—");
        int thisRequest = ++requestVersion;
        displayedReport = null;
        binding.expandTrendButton.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        binding.messageText.setText("");
        binding.trendChart.showMessage("Loading analytics…");
        LocalRepository repository = LocalRepository.get(requireContext());
        LocalRepository.Callback<SalesAnalytics> callback = new LocalRepository.Callback<>() {
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
                };
        if (hasYearlyCutoff()) {
            repository.yearlyAnalyticsThroughDate(selectedDate, yearlyComparisonDate, callback);
        } else {
            repository.analytics(period, selectedDate, activeComparisonDate(), callback);
        }
    }

    private boolean hasYearlyCutoff() {
        return "yearly".equals(period) && yearlyCutoff;
    }

    private String activeComparisonDate() {
        return "yearly".equals(period) ? yearlyComparisonDate : comparisonDate;
    }

    private boolean comparisonEnabled() {
        return !"yearly".equals(period) || !yearlyComparisonOff;
    }

    /** Both chart sizes use the same date bounds, including leap-year cutoffs. */
    private AnalyticsDateRange chartRange(boolean comparison) {
        AnalyticsDateRange selected = AnalyticsDateRange.forDate(period, selectedDate);
        if (!comparison) {
            return hasYearlyCutoff() ? AnalyticsDateRange.yearlyThrough(selectedDate, selectedDate)
                    : selected;
        }
        String date = activeComparisonDate();
        if (hasYearlyCutoff()) {
            // Shift the cutoff against the year anchor instead of shifting a rendered label.
            return AnalyticsDateRange.yearlyThrough(selectedDate,
                    date == null ? AnalyticsDateRange.previousYearAnchor(selectedDate) : date);
        }
        return date == null ? selected.previous() : AnalyticsDateRange.forDate(period, date);
    }

    private String legendLabel(AnalyticsDateRange range) {
        return hasYearlyCutoff() ? range.label() : range.compactLabel();
    }

    /** Formats summary metrics, trend points, comparison values, and category rows. */
    private void bind(SalesAnalytics report) {
        displayedReport = report;
        binding.expandTrendButton.setEnabled(true);
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        AnalyticsDateRange currentRange = chartRange(false);
        AnalyticsDateRange compareRange = chartRange(true);
        binding.revenueText.setText(php.format(report.getTotals().getRevenue()));
        binding.salesText.setText(NumberFormat.getIntegerInstance(Locale.US)
                .format(report.getTotals().getCompletedSales()));
        binding.piecesText.setText(NumberFormat.getIntegerInstance(Locale.US)
                .format(report.getTotals().getPieces()));
        NumberFormat kilograms = NumberFormat.getNumberInstance(Locale.US);
        kilograms.setMaximumFractionDigits(3);
        binding.weightText.setText(kilograms.format(report.getTotals().getWeightKilograms()) + " kg");
        binding.comparisonText.setVisibility(comparisonEnabled() ? View.VISIBLE : View.GONE);
        binding.comparisonText.setText(comparisonEnabled()
                ? formatComparisonMessage(report, legendLabel(compareRange)) : null);
        binding.trendChart.setData(
                report.getTrend() == null ? Collections.emptyList() : report.getTrend(),
                !comparisonEnabled() || report.getComparisonTrend() == null
                        ? Collections.emptyList() : report.getComparisonTrend(),
                report.getPeriod());
        setTrendLegend(binding.trendLegendText, legendLabel(currentRange),
                comparisonEnabled() ? legendLabel(compareRange) : null);
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
        AnalyticsDateRange currentRange = chartRange(false);
        AnalyticsDateRange compareRange = chartRange(true);
        Intent intent = new Intent(requireContext(), ExpandedRevenueTrendActivity.class);
        intent.putExtra("period", displayedReport.getPeriod());
        intent.putExtra("range", "Reporting period: " + currentRange.label());
        intent.putExtra("currentDateLabel", legendLabel(currentRange));
        intent.putExtra("comparisonDateLabel", comparisonEnabled() ? legendLabel(compareRange) : null);
        ExpandedRevenueTrendActivity.putSeries(intent, "current",
                displayedReport.getTrend() == null
                        ? Collections.emptyList() : displayedReport.getTrend());
        ExpandedRevenueTrendActivity.putSeries(intent, "comparison",
                !comparisonEnabled() || displayedReport.getComparisonTrend() == null
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

    /** Match each legend marker to its line style in both chart sizes. */
    static void setTrendLegend(android.widget.TextView view, String currentDate, String comparisonDate) {
        String solid = "━━";
        String dashed = "━ ━";
        String current = solid + " " + currentDate;
        if (comparisonDate == null) {
            SpannableString legend = new SpannableString(current);
            legend.setSpan(new ForegroundColorSpan(view.getContext().getColor(R.color.dragon_green)),
                    0, solid.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            view.setText(legend);
            view.setContentDescription(currentDate + ", solid green.");
            return;
        }
        current += "    ";
        SpannableString legend = new SpannableString(current + dashed + " " + comparisonDate);
        legend.setSpan(new ForegroundColorSpan(view.getContext().getColor(R.color.dragon_green)),
                0, solid.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        legend.setSpan(new ForegroundColorSpan(view.getContext().getColor(R.color.botanical_header)),
                current.length(), current.length() + dashed.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(legend);
        view.setContentDescription(currentDate + ", solid green. "
                + comparisonDate + ", dashed berry.");
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
