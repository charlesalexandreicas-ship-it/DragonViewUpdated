package ph.dragonview.mobile.ui.historical;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.HistoricalRecordDraft;
import ph.dragonview.mobile.databinding.FragmentHistoricalEditorBinding;
import ph.dragonview.mobile.databinding.ItemHistoricalRowBinding;
import ph.dragonview.mobile.ui.date.RecordDate;
import ph.dragonview.mobile.ui.date.RecordDateInput;
import ph.dragonview.mobile.ui.scanner.ScannerImageLoader;

/** Owner-reviewed entry flow for old paper records; inventory is never changed here. */
public final class HistoricalEditorFragment extends Fragment {
    // Blemished is a historical paper category, not a new live inventory size.
    private static final String[] SIZES = {"Extra small", "Small", "Medium", "Large", "Jumbo", "Blemished"};
    private final List<ItemHistoricalRowBinding> rows = new ArrayList<>();
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private FragmentHistoricalEditorBinding binding;
    private String source = "MANUAL";

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
                                        Bundle state) {
        binding = FragmentHistoricalEditorBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (!LocalRepository.get(requireContext()).isWorkspaceOwner()) {
            binding.messageText.setText(R.string.historical_owner_required);
            binding.saveButton.setEnabled(false);
            return;
        }
        Bundle args = getArguments();
        source = args == null ? "MANUAL" : args.getString("source", "MANUAL");
        boolean fromPhoto = "PHOTO".equals(source);
        binding.introText.setText(fromPhoto
                ? "Check the extracted paper values before saving. Missing values stay blank."
                : getString(R.string.historical_entry_intro));
        RecordDateInput.attach(binding.dateInput);
        binding.recordChoiceGroup.addOnButtonCheckedListener((group, checkedId, isChecked) ->
                updateTotal());
        binding.addRowButton.setOnClickListener(v -> {
            if (rows.size() >= 100) return;
            for (ItemHistoricalRowBinding row : rows) setExpanded(row, false);
            ItemHistoricalRowBinding added = addRow(null, true);
            binding.formScroll.post(() -> binding.formScroll.smoothScrollTo(0,
                    binding.rowsContainer.getTop() + added.getRoot().getTop()));
        });
        binding.saveButton.setOnClickListener(v -> reviewAndSave());
        binding.grandTotalToggle.setOnClickListener(v -> binding.grandTotalLayout.setVisibility(
                binding.grandTotalLayout.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        binding.grandTotalInput.addTextChangedListener(watcher(this::updateTotal));

        if (state != null && (state.containsKey("rowValues39") || state.containsKey("rowValues"))) {
            restore(state);
        } else if (fromPhoto) {
            populateFromPhoto(args);
        } else {
            addRow(null, true);
        }
        if (fromPhoto && args != null) showPhoto(args.getString("photoUri"));
        updateTotal();
    }

    /** OCR prefills only what it recognized; classification still needs the owner's decision. */
    private void populateFromPhoto(@Nullable Bundle args) {
        HistoricalOcrParser.Result extracted = HistoricalOcrParser.parse(
                args == null ? "" : args.getString("recognizedText", ""));
        binding.dateInput.setText(RecordDate.toDisplay(extracted.date()));
        for (HistoricalRecordDraft.Item item : extracted.items()) addRow(item, false);
        if (rows.isEmpty()) addRow(null, true);
        else setExpanded(rows.get(0), true);
        if (extracted.grandTotal() != null) {
            binding.grandTotalInput.setText(formatInput(extracted.grandTotal()));
            binding.grandTotalLayout.setVisibility(View.VISIBLE);
        }
        if (extracted.multipleDates()) {
            binding.messageText.setText(R.string.historical_multiple_dates);
        } else if (extracted.date().isEmpty() || extracted.items().isEmpty()) {
            binding.messageText.setText(R.string.historical_missing_ocr_details);
        }
    }

    /** New rows open for entry while existing ones can collapse to a readable one-line summary. */
    private ItemHistoricalRowBinding addRow(@Nullable HistoricalRecordDraft.Item item,
                                            boolean expanded) {
        ItemHistoricalRowBinding row = ItemHistoricalRowBinding.inflate(
                getLayoutInflater(), binding.rowsContainer, false);
        row.sizeInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, SIZES));
        row.sizeInput.setOnClickListener(v -> row.sizeInput.showDropDown());
        String useRecord = getString(R.string.historical_use_record_choice);
        String actual = getString(R.string.historical_actual_sale);
        String estimated = getString(R.string.historical_estimated_value);
        row.recordTypeInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new String[]{useRecord, actual, estimated}));
        row.recordTypeInput.setText(useRecord, false);
        row.recordTypeInput.setOnClickListener(v -> row.recordTypeInput.showDropDown());
        row.rowHeader.setOnClickListener(v ->
                setExpanded(row, row.rowFields.getVisibility() != View.VISIBLE));
        row.removeButton.setOnClickListener(v -> {
            binding.rowsContainer.removeView(row.getRoot());
            rows.remove(row);
            if (rows.isEmpty()) addRow(null, true);
            updateTotal();
        });
        row.paperSubtotalToggle.setOnClickListener(v -> row.paperSubtotalLayout.setVisibility(
                row.paperSubtotalLayout.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        row.rowTypeToggle.setOnClickListener(v -> row.recordTypeLayout.setVisibility(
                row.recordTypeLayout.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        rows.add(row);
        binding.rowsContainer.addView(row.getRoot());
        if (item != null) {
            row.sizeInput.setText(displaySize(item.getSize()), false);
            row.weightInput.setText(formatWeight(item.getWeightKilograms()));
            row.priceInput.setText(formatInput(item.getPricePerKilogram()));
            // A photographed subtotal is paper evidence, not a calculated value to overwrite.
            row.paperSubtotalInput.setText(formatInput(item.getSubtotal()));
            row.paperSubtotalLayout.setVisibility(View.VISIBLE);
            if (item.getActualSales() != null) {
                row.recordTypeInput.setText(item.getActualSales() ? actual : estimated, false);
                row.recordTypeLayout.setVisibility(View.VISIBLE);
            }
        }
        row.sizeInput.addTextChangedListener(watcher(this::updateTotal));
        row.weightInput.addTextChangedListener(watcher(this::updateTotal));
        row.priceInput.addTextChangedListener(watcher(this::updateTotal));
        row.paperSubtotalInput.addTextChangedListener(watcher(this::updateTotal));
        row.recordTypeInput.addTextChangedListener(watcher(this::updateTotal));
        setExpanded(row, expanded);
        updateTotal();
        return row;
    }

    private void setExpanded(ItemHistoricalRowBinding row, boolean expanded) {
        row.rowFields.setVisibility(expanded ? View.VISIBLE : View.GONE);
        row.rowSummary.setVisibility(expanded ? View.GONE : View.VISIBLE);
        row.expandText.setText(expanded
                ? R.string.historical_collapse : R.string.historical_expand);
    }

    /** Recalculates only completed rows and never claims a paper match before comparison. */
    private void updateTotal() {
        if (binding == null) return;
        List<BigDecimal> completedAmounts = new ArrayList<>();
        boolean allComplete = true;
        boolean hasCompletedRow = false;
        for (ItemHistoricalRowBinding row : rows) {
            String size = text(row.sizeInput.getText());
            String paper = text(row.paperSubtotalInput.getText());
            BigDecimal calculated = HistoricalEntryMath.subtotal(
                    text(row.weightInput.getText()), text(row.priceInput.getText()));
            boolean complete = !size.isEmpty() && calculated != null;
            // A newly opened, untouched size is not part of the review or paper comparison.
            if (!isBlank(row)) allComplete &= complete;
            row.rowTitle.setText(size.isEmpty() ? getString(R.string.historical_new_size) : size);
            row.calculatedSubtotalText.setText(calculated == null
                    ? getString(R.string.historical_subtotal_empty)
                    : getString(R.string.historical_subtotal_value, currency(calculated)));
            boolean paperMatches = HistoricalEntryMath.paperMatches(paper, calculated);
            row.rowMismatchText.setVisibility(paper.isEmpty() || paperMatches
                    ? View.GONE : View.VISIBLE);
            row.rowMismatchText.setText(R.string.historical_paper_row_mismatch);
            if (complete) {
                hasCompletedRow = true;
                BigDecimal recorded = paperMatches
                        ? HistoricalEntryMath.amountForSave(paper, calculated) : calculated;
                completedAmounts.add(recorded);
                row.rowSummary.setText(getString(R.string.historical_row_summary,
                        text(row.weightInput.getText()),
                        currency(new BigDecimal(text(row.priceInput.getText()).replace(",", ""))),
                        currency(recorded)));
            } else {
                completedAmounts.add(null);
                row.rowSummary.setText(R.string.historical_enter_weight_price);
            }
        }
        BigDecimal total = HistoricalEntryMath.sumCompletedRows(completedAmounts);
        binding.subtotalSumText.setText(getString(R.string.historical_total_value, currency(total)));
        String grand = text(binding.grandTotalInput.getText());
        if (grand.isEmpty()) {
            binding.matchStatusText.setText(R.string.historical_completed_rows);
            binding.matchStatusText.setTextColor(requireContext().getColor(R.color.dragon_text_soft));
        } else if (!allComplete || !hasCompletedRow) {
            binding.matchStatusText.setText(R.string.historical_complete_rows);
            binding.matchStatusText.setTextColor(requireContext().getColor(R.color.dragon_text_soft));
        } else if (HistoricalEntryMath.paperMatches(grand, total)) {
            binding.matchStatusText.setText(R.string.historical_totals_match);
            binding.matchStatusText.setTextColor(requireContext().getColor(R.color.dragon_green));
        } else {
            binding.matchStatusText.setText(R.string.historical_totals_mismatch);
            binding.matchStatusText.setTextColor(requireContext().getColor(R.color.dragon_error));
        }
    }

    /** Validates UI-only paper comparisons before using the existing save contract. */
    @Nullable private HistoricalRecordDraft collectDraft() {
        String iso = HistoricalEntryMath.isoDate(text(binding.dateInput.getText()), todayIso());
        if (iso == null) {
            showError(getString(R.string.historical_enter_date), null);
            binding.dateInput.setError(getString(R.string.historical_enter_date));
            binding.formScroll.smoothScrollTo(0, 0);
            return null;
        }
        List<HistoricalRecordDraft.Item> items = new ArrayList<>();
        Boolean recordChoice = recordClassification();
        for (ItemHistoricalRowBinding row : rows) {
            // Ignore an untouched Add size row; partial entries still require correction.
            if (isBlank(row)) continue;
            String size = text(row.sizeInput.getText());
            if (size.isEmpty()) {
                showError(getString(R.string.historical_enter_size), row);
                return null;
            }
            String weight = text(row.weightInput.getText());
            String price = text(row.priceInput.getText());
            BigDecimal calculated = HistoricalEntryMath.subtotal(weight, price);
            if (calculated == null) {
                showError(getString(R.string.historical_enter_weight_price), row);
                return null;
            }
            String paper = text(row.paperSubtotalInput.getText());
            if (!HistoricalEntryMath.paperMatches(paper, calculated)) {
                row.paperSubtotalLayout.setVisibility(View.VISIBLE);
                showError(getString(R.string.historical_paper_row_error, size), row);
                return null;
            }
            Boolean classification = HistoricalEntryMath.classification(
                    recordChoice, rowClassification(row));
            if (classification == null) {
                showError(getString(R.string.historical_choose_sale_type), row);
                return null;
            }
            items.add(new HistoricalRecordDraft.Item(
                    size.toUpperCase(Locale.US).replace(' ', '_'),
                    number(weight), number(price),
                    HistoricalEntryMath.amountForSave(paper, calculated).doubleValue(),
                    classification));
        }
        String grand = text(binding.grandTotalInput.getText());
        Double grandAmount = grand.isEmpty() ? null : numberOrInvalid(grand);
        HistoricalRecordDraft draft = new HistoricalRecordDraft(iso, items, grandAmount);
        String error = draft.validationError();
        if (!error.isEmpty()) {
            if (!grand.isEmpty()) binding.grandTotalLayout.setVisibility(View.VISIBLE);
            showError(error, null);
            return null;
        }
        binding.messageText.setText("");
        return draft;
    }

    /** Confirmation separates review from the external save and prevents accidental imports. */
    private void reviewAndSave() {
        HistoricalRecordDraft draft = collectDraft();
        if (draft == null) return;
        int actual = 0;
        for (HistoricalRecordDraft.Item item : draft.getItems()) {
            if (Boolean.TRUE.equals(item.getActualSales())) actual++;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.historical_review_confirm)
                .setMessage(getString(R.string.historical_review_message,
                        RecordDate.toDisplay(draft.getDate()), draft.getItems().size(),
                        currency(BigDecimal.valueOf(draft.subtotalSum())), actual,
                        draft.getItems().size() - actual))
                .setNegativeButton(R.string.historical_cancel, null)
                .setPositiveButton(R.string.historical_save, (dialog, which) -> saveDraft(draft))
                .show();
    }

    private void saveDraft(HistoricalRecordDraft draft) {
        binding.saveButton.setEnabled(false);
        binding.messageText.setText(R.string.historical_saving);
        LocalRepository.get(requireContext()).saveHistoricalRecord(draft, source,
                new LocalRepository.Callback<>() {
                    @Override public void onSuccess(Void ignored) {
                        if (binding == null) return;
                        NavHostFragment.findNavController(HistoricalEditorFragment.this)
                                .popBackStack(R.id.historicalRecordsFragment, false);
                    }
                    @Override public void onError(String message) {
                        if (binding == null) return;
                        binding.saveButton.setEnabled(true);
                        binding.messageText.setText(message);
                    }
                });
    }

    private void showError(String message, @Nullable ItemHistoricalRowBinding row) {
        binding.messageText.setText(message);
        if (row != null) {
            row.rowMismatchText.setText(message);
            row.rowMismatchText.setVisibility(View.VISIBLE);
            setExpanded(row, true);
            binding.formScroll.post(() -> binding.formScroll.smoothScrollTo(0,
                    binding.rowsContainer.getTop() + row.getRoot().getTop()));
        }
    }

    @Nullable private Boolean recordClassification() {
        int checked = binding.recordChoiceGroup.getCheckedButtonId();
        if (checked == R.id.actualSaleButton) return true;
        if (checked == R.id.estimatedValueButton) return false;
        return null;
    }

    @Nullable private Boolean rowClassification(ItemHistoricalRowBinding row) {
        String value = text(row.recordTypeInput.getText());
        if (value.equals(getString(R.string.historical_actual_sale))) return true;
        if (value.equals(getString(R.string.historical_estimated_value))) return false;
        return null;
    }

    /** An untouched optional row has no evidence to review or persist. */
    private boolean isBlank(ItemHistoricalRowBinding row) {
        String rowType = text(row.recordTypeInput.getText());
        return text(row.sizeInput.getText()).isEmpty()
                && text(row.weightInput.getText()).isEmpty()
                && text(row.priceInput.getText()).isEmpty()
                && text(row.paperSubtotalInput.getText()).isEmpty()
                && (rowType.isEmpty()
                || rowType.equals(getString(R.string.historical_use_record_choice)));
    }

    private void showPhoto(String rawUri) {
        if (rawUri == null) return;
        Uri uri = Uri.parse(rawUri);
        binding.photoPreview.setVisibility(View.VISIBLE);
        Context appContext = requireContext().getApplicationContext();
        imageExecutor.execute(() -> {
            try {
                Bitmap preview = ScannerImageLoader.load(appContext, uri, 1200);
                mainThread.post(() -> {
                    if (binding != null) binding.photoPreview.setImageBitmap(preview);
                    else preview.recycle();
                });
            } catch (Exception ignored) {
                mainThread.post(() -> {
                    if (binding != null) binding.photoPreview.setVisibility(View.GONE);
                });
            }
        });
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        if (binding != null) {
            outState.putString("date", text(binding.dateInput.getText()));
            outState.putString("grand", text(binding.grandTotalInput.getText()));
            outState.putBoolean("grandOpen", binding.grandTotalLayout.getVisibility() == View.VISIBLE);
            outState.putInt("choice", binding.recordChoiceGroup.getCheckedButtonId());
            ArrayList<String> values = new ArrayList<>();
            for (ItemHistoricalRowBinding row : rows) {
                values.add(text(row.sizeInput.getText()));
                values.add(text(row.weightInput.getText()));
                values.add(text(row.priceInput.getText()));
                values.add(text(row.paperSubtotalInput.getText()));
                values.add(text(row.recordTypeInput.getText()));
                values.add(row.rowFields.getVisibility() == View.VISIBLE ? "1" : "0");
                values.add(row.paperSubtotalLayout.getVisibility() == View.VISIBLE ? "1" : "0");
                values.add(row.recordTypeLayout.getVisibility() == View.VISIBLE ? "1" : "0");
            }
            outState.putStringArrayList("rowValues39", values);
        }
        super.onSaveInstanceState(outState);
    }

    /** Restores visible controls and values without changing any reviewed paper evidence. */
    private void restore(Bundle state) {
        String savedDate = state.getString("date", "");
        // Pre-#39 in-memory drafts stored ISO dates instead of the new display format.
        binding.dateInput.setText(savedDate.matches("\\d{4}-\\d{2}-\\d{2}")
                ? RecordDate.toDisplay(savedDate) : savedDate);
        binding.grandTotalInput.setText(state.getString("grand", ""));
        binding.grandTotalLayout.setVisibility(state.getBoolean("grandOpen")
                ? View.VISIBLE : View.GONE);
        int choice = state.getInt("choice", View.NO_ID);
        if (choice == R.id.actualSaleButton || choice == R.id.estimatedValueButton) {
            binding.recordChoiceGroup.check(choice);
        }
        ArrayList<String> values = state.getStringArrayList("rowValues39");
        if (values != null) {
            for (int offset = 0; offset + 7 < values.size(); offset += 8) {
                ItemHistoricalRowBinding row = addRow(null, "1".equals(values.get(offset + 5)));
                row.sizeInput.setText(values.get(offset), false);
                row.weightInput.setText(values.get(offset + 1));
                row.priceInput.setText(values.get(offset + 2));
                row.paperSubtotalInput.setText(values.get(offset + 3));
                row.recordTypeInput.setText(values.get(offset + 4), false);
                row.paperSubtotalLayout.setVisibility("1".equals(values.get(offset + 6))
                        ? View.VISIBLE : View.GONE);
                row.recordTypeLayout.setVisibility("1".equals(values.get(offset + 7))
                        ? View.VISIBLE : View.GONE);
            }
        } else {
            // Older in-memory state kept a manually entered subtotal in the fourth value.
            values = state.getStringArrayList("rowValues");
            if (values != null) for (int offset = 0; offset + 4 < values.size(); offset += 5) {
                ItemHistoricalRowBinding row = addRow(null, offset == 0);
                row.sizeInput.setText(values.get(offset), false);
                row.weightInput.setText(values.get(offset + 1));
                row.priceInput.setText(values.get(offset + 2));
                row.paperSubtotalInput.setText(values.get(offset + 3));
                row.paperSubtotalLayout.setVisibility(View.VISIBLE);
                String oldType = values.get(offset + 4);
                if (oldType.equals("Actual sales received")) {
                    oldType = getString(R.string.historical_actual_sale);
                } else if (oldType.equals("Estimated harvest value")) {
                    oldType = getString(R.string.historical_estimated_value);
                }
                row.recordTypeInput.setText(oldType, false);
                row.recordTypeLayout.setVisibility(View.VISIBLE);
            }
        }
        if (rows.isEmpty()) addRow(null, true);
    }

    private static TextWatcher watcher(Runnable onChange) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                onChange.run();
            }
            @Override public void afterTextChanged(Editable editable) { }
        };
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private static double number(String value) {
        return Double.parseDouble(value.replace(",", ""));
    }

    private static double numberOrInvalid(String value) {
        try { return number(value); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static String todayIso() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String currency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(new Locale("en", "PH")).format(amount);
    }

    private static String formatInput(double number) {
        return number == Math.rint(number)
                ? String.format(Locale.US, "%.0f", number)
                : String.format(Locale.US, "%.2f", number);
    }

    private static String formatWeight(@Nullable Double kilograms) {
        return kilograms == null ? "" : String.format(Locale.US, "%.3f", kilograms)
                .replaceFirst("\\.?0+$", "");
    }

    private static String displaySize(String size) {
        String value = size == null ? "" : size.replace('_', ' ').toLowerCase(Locale.US);
        return value.isEmpty() ? value
                : value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
    }

    @Override public void onDestroyView() {
        rows.clear();
        binding = null;
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        imageExecutor.shutdown();
        super.onDestroy();
    }
}
