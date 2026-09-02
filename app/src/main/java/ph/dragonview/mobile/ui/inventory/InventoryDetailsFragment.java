package ph.dragonview.mobile.ui.inventory;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.text.NumberFormat;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.InventoryBatchDetails;
import ph.dragonview.mobile.data.model.InventoryDetails;
import ph.dragonview.mobile.data.model.FruitPrice;
import ph.dragonview.mobile.databinding.DialogBatchValueEstimateBinding;
import ph.dragonview.mobile.databinding.FragmentInventoryDetailsBinding;
import ph.dragonview.mobile.databinding.ItemWholeBatchSaleLineBinding;

public final class InventoryDetailsFragment extends Fragment {
    private FragmentInventoryDetailsBinding binding;
    private String batchNumber;
    private InventoryBatchDetails currentDetails;
    private List<FruitPrice> prices = Collections.emptyList();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup parent, Bundle state) {
        binding = FragmentInventoryDetailsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        batchNumber = requireArguments().getString("batchNumber", "");
        load();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.messageText.setText("");
        LocalRepository.get(requireContext()).inventoryBatchDetails(
                batchNumber, new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(InventoryBatchDetails details) {
                        if (binding == null) return;
                        binding.progress.setVisibility(View.GONE);
                        bind(details);
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        binding.progress.setVisibility(View.GONE);
                        binding.messageText.setText(message);
                    }
                });
        LocalRepository.get(requireContext()).prices(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<FruitPrice> values) { prices = values; }
            @Override public void onError(String message) {
                if (binding != null) binding.messageText.setText(message);
            }
        });
    }

    private void bind(InventoryBatchDetails details) {
        currentDetails = details;
        binding.batchText.setText("#" + details.getBatchNumber());
        binding.gradeText.setText(details.getItems().size() == 1
                ? "1 ENTRY" : details.getItems().size() + " ENTRIES");
        String harvestDate = details.getHarvestDate();
        String displayedDate = harvestDate.length() > 10
                ? harvestDate.substring(0, 10) : harvestDate;
        binding.factsText.setText(String.format(Locale.US,
                "%,d available pieces • Harvested %s",
                details.getAvailablePieces(), displayedDate));
        binding.itemsContainer.removeAllViews();
        binding.estimateButton.setOnClickListener(v -> openEstimate());
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (InventoryBatchDetails.Item item : details.getItems()) {
            View row = inflater.inflate(
                    R.layout.item_inventory_batch_line,
                    binding.itemsContainer, false);
            TextView lineItemText = row.findViewById(R.id.lineItemText);
            TextView availableText = row.findViewById(R.id.availableText);
            TextView fifoText = row.findViewById(R.id.fifoText);
            TextView historyText = row.findViewById(R.id.historyText);
            MaterialButton adjustButton = row.findViewById(R.id.adjustButton);
            MaterialButton regradeButton = row.findViewById(R.id.regradeButton);

            lineItemText.setText(formatSize(item.getSize())
                    + " • Grade " + item.getGrade());
            availableText.setText(String.format(Locale.US,
                    "%,d available of %,d recorded pieces",
                    item.getAvailablePieces(), item.getOriginalPieces()));
            fifoText.setText(item.isNextOut()
                    ? "FIFO PRIORITY · This classification sells first"
                    : item.getAvailablePieces() == 0
                    ? "DEPLETED" : "FIFO QUEUED");
            historyText.setText(history(item));
            adjustButton.setOnClickListener(v -> actionDialog(item, false));
            regradeButton.setVisibility("C".equals(item.getGrade())
                    || item.getAvailablePieces() == 0 ? View.GONE : View.VISIBLE);
            regradeButton.setOnClickListener(v -> actionDialog(item, true));
            binding.itemsContainer.addView(row);
        }
    }

    private void openEstimate() {
        if (currentDetails == null) return;
        if (prices.isEmpty()) {
            binding.messageText.setText("Price Management must contain active prices first.");
            return;
        }
        DialogBatchValueEstimateBinding form = DialogBatchValueEstimateBinding.inflate(
                getLayoutInflater());
        List<EstimateLine> lines = new ArrayList<>();
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        boolean missingPrice = false;
        for (InventoryBatchDetails.Item item : currentDetails.getItems()) {
            if (item.getAvailablePieces() <= 0) continue;
            double rate = price(item.getSize(), item.getGrade());
            if (rate <= 0) missingPrice = true;
            EstimateLine line = new EstimateLine(rate);
            lines.add(line);
            ItemWholeBatchSaleLineBinding row = ItemWholeBatchSaleLineBinding.inflate(
                    getLayoutInflater(), form.itemsContainer, false);
            row.categoryText.setText(formatSize(item.getSize()) + " • Grade " + item.getGrade());
            row.detailsText.setText(String.format(Locale.US, "%,d pieces • %s/kg",
                    item.getAvailablePieces(), rate > 0 ? php.format(rate) : "No active price"));
            row.subtotalText.setText("Estimated subtotal: " + php.format(0));
            row.weightInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    try { line.weight = Double.parseDouble(s.toString().trim()); }
                    catch (NumberFormatException ignored) { line.weight = 0; }
                    row.subtotalText.setText("Estimated subtotal: "
                            + php.format(line.weight * line.rate));
                    updateEstimateTotal(form, lines, php);
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            form.itemsContainer.addView(row.getRoot());
        }
        form.errorText.setText(missingPrice
                ? "Some categories have no active price. Update Price Management to include them." : "");
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Batch #" + currentDetails.getBatchNumber() + " estimate")
                .setView(form.getRoot()).setNegativeButton("Close", null).create();
        dialog.show();
    }

    private void updateEstimateTotal(DialogBatchValueEstimateBinding form,
                                     List<EstimateLine> lines, NumberFormat php) {
        double total = 0;
        for (EstimateLine line : lines) total += line.weight * line.rate;
        form.totalText.setText("Estimated total: " + php.format(total));
        binding.estimateSummaryText.setText("Latest estimate: " + php.format(total)
                + " • not yet recorded as a sale");
    }

    private double price(String size, String grade) {
        for (FruitPrice price : prices) {
            if (grade.equals(price.getGrade())
                    && ("C".equals(grade) || size.equals(price.getSize()))) {
                return price.getPricePerKilogram();
            }
        }
        return 0;
    }

    private String history(InventoryBatchDetails.Item item) {
        StringBuilder history = new StringBuilder();
        for (InventoryDetails.Transaction transaction : item.getTransactions()) {
            history.append(transaction.getType().replace('_', ' '))
                    .append(" • ")
                    .append(String.format(Locale.US, "%+d pieces", transaction.getPieces()))
                    .append("\n")
                    .append(transaction.getCreatedBy()).append(" • ")
                    .append(transaction.getCreatedAt().replace('T', ' '));
            if (transaction.getRemarks() != null
                    && !transaction.getRemarks().trim().isEmpty()) {
                history.append("\n").append(transaction.getRemarks());
            }
            history.append("\n\n");
        }
        return history.length() == 0
                ? "No transactions recorded."
                : history.toString().trim();
    }

    private void actionDialog(InventoryBatchDetails.Item item, boolean regrade) {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(40, 12, 40, 0);
        EditText pieces = new EditText(requireContext());
        pieces.setHint(regrade
                ? "Pieces to regrade"
                : "Quantity change, e.g. -2 or 5");
        pieces.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText reason = new EditText(requireContext());
        reason.setHint("Reason");
        form.addView(pieces);
        EditText target = null;
        if (regrade) {
            target = new EditText(requireContext());
            target.setHint("Target grade: B or C");
            target.setText("C");
            form.addView(target);
        }
        form.addView(reason);
        EditText finalTarget = target;
        new AlertDialog.Builder(requireContext())
                .setTitle(regrade
                        ? "Regrade " + formatSize(item.getSize()) + " Grade " + item.getGrade()
                        : "Adjust " + formatSize(item.getSize()) + " Grade " + item.getGrade())
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    int count;
                    try {
                        count = Integer.parseInt(pieces.getText().toString());
                    } catch (NumberFormatException ignored) {
                        count = 0;
                    }
                    String actionReason = reason.getText().toString().trim();
                    LocalRepository.Callback<Void> callback = new LocalRepository.Callback<>() {
                        @Override
                        public void onSuccess(Void ignored) {
                            if (binding == null) return;
                            binding.messageText.setText("Batch inventory updated.");
                            load();
                        }

                        @Override
                        public void onError(String message) {
                            if (binding != null) binding.messageText.setText(message);
                        }
                    };
                    if (regrade) {
                        LocalRepository.get(requireContext()).regradeInventory(
                                item.getId(), finalTarget.getText().toString().trim()
                                        .toUpperCase(Locale.US),
                                count, actionReason, callback);
                    } else {
                        LocalRepository.get(requireContext()).adjustInventory(
                                item.getId(), count, actionReason, callback);
                    }
                })
                .show();
    }

    private static String formatSize(String size) {
        String value = size.toLowerCase(Locale.US).replace('_', ' ');
        StringBuilder label = new StringBuilder(value.length());
        boolean capitalize = true;
        for (char character : value.toCharArray()) {
            label.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return label.toString();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private static final class EstimateLine {
        final double rate;
        double weight;
        EstimateLine(double rate) { this.rate = rate; }
    }
}
