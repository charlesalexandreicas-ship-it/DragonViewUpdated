package ph.dragonview.mobile.ui.sales;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ph.dragonview.mobile.data.model.CompleteSaleRequest;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.FruitPrice;
import ph.dragonview.mobile.data.model.InventoryLot;
import ph.dragonview.mobile.data.model.SaleSummary;
import ph.dragonview.mobile.databinding.DialogSaleItemBinding;
import ph.dragonview.mobile.databinding.DialogSaleBinding;
import ph.dragonview.mobile.databinding.DialogPriceBinding;
import ph.dragonview.mobile.databinding.FragmentSalesBinding;
import ph.dragonview.mobile.databinding.ItemWholeBatchSaleLineBinding;
import ph.dragonview.mobile.ui.archive.ArchiveDialog;

public final class SalesFragment extends Fragment {
    private FragmentSalesBinding binding;
    private SalesAdapter adapter;
    private List<FruitPrice> prices = Collections.emptyList();
    private List<InventoryLot> inventory = Collections.emptyList();

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentSalesBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter = new SalesAdapter(this::confirmArchive);
        binding.salesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.salesList.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.newSaleButton.setOnClickListener(v -> openSale());
        binding.priceButton.setVisibility(View.VISIBLE);
        binding.priceButton.setOnClickListener(v -> openPrices());
        load();
    }

    private void load() {
        binding.swipeRefresh.setRefreshing(true);
        LocalRepository repository = LocalRepository.get(requireContext());
        repository.sales(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<SaleSummary> rows) {
                if (binding == null) return;
                binding.swipeRefresh.setRefreshing(false);
                adapter.submit(rows);
                binding.messageText.setText(rows.isEmpty() ? "No sales recorded." : "");
            }
            @Override public void onError(String message) {
                if (binding == null) return;
                binding.swipeRefresh.setRefreshing(false);
                binding.messageText.setText(message);
            }
        });
        repository.prices(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<FruitPrice> values) { prices = values; }
            @Override public void onError(String message) {
                if (binding != null) binding.messageText.setText(message);
            }
        });
        repository.inventory(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<InventoryLot> values) { inventory = values; }
            @Override public void onError(String message) {
                if (binding != null) binding.messageText.setText(message);
            }
        });
    }

    private void openSale() {
        if (prices.isEmpty() || inventory.isEmpty()) {
            binding.messageText.setText("Prices and available inventory must load before checkout.");
            load();
            return;
        }
        DialogSaleBinding form = DialogSaleBinding.inflate(getLayoutInflater());
        String[] methods = {"CASH", "GCASH", "MAYA", "OTHER_E_WALLET", "BANK_TRANSFER"};
        form.methodInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, methods));
        form.methodInput.setText("CASH", false);
        List<SaleLine> lines = new ArrayList<>();
        Map<String, List<InventoryLot>> batches = availableBatches();
        List<String> batchLabels = new ArrayList<>();
        for (Map.Entry<String, List<InventoryLot>> entry : batches.entrySet()) {
            int pieces = 0;
            for (InventoryLot lot : entry.getValue()) pieces += lot.getAvailablePieces();
            batchLabels.add(entry.getKey() + " • " + pieces + " pieces");
        }
        form.batchInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, batchLabels));
        form.batchInput.setThreshold(0);
        form.batchInput.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Select batch")
                .setItems(batchLabels.toArray(new String[0]), (choice, position) -> {
                    String label = batchLabels.get(position);
                    form.batchInput.setText(label, false);
                    loadWholeBatch(form, lines, batches.get(batchKey(label)));
                }).show());
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("New Sale")
                .setView(form.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Complete sale", null)
                .create();
        form.addItemButton.setOnClickListener(v -> openItem(form, lines));
        form.saleModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            lines.clear();
            boolean wholeBatch = checkedId == form.entireBatchButton.getId();
            form.batchInput.setVisibility(wholeBatch ? View.VISIBLE : View.GONE);
            form.addItemButton.setVisibility(wholeBatch ? View.GONE : View.VISIBLE);
            form.overrideReasonInput.setVisibility(View.GONE);
            form.batchInput.setText("", false);
            form.modeHelpText.setText(wholeBatch
                    ? "All remaining pieces in the chosen batch are included. Enter the measured weight for each size and grade."
                    : "Choose one or more size and grade combinations. Oldest matching stock is deducted first.");
            renderLines(form, lines);
            if (wholeBatch && batchLabels.size() == 1) {
                String label = batchLabels.get(0);
                form.batchInput.setText(label, false);
                loadWholeBatch(form, lines, batches.get(batchKey(label)));
            }
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> complete(dialog, form, lines)));
        dialog.show();
    }

    private void confirmArchive(SaleSummary sale) {
        ArchiveDialog.show(this, "Remove Sale #" + sale.getId() + "?",
                "This hides the sale from the active sales list but preserves its "
                        + "accounting, payment, inventory, and FIFO history. It can be restored.",
                reason -> LocalRepository.get(requireContext()).archiveSale(
                        sale.getId(), reason, new LocalRepository.Callback<>() {
                            @Override public void onSuccess(Void ignored) { load(); }
                            @Override public void onError(String message) {
                                if (binding != null) binding.messageText.setText(message);
                            }
                        }));
    }

    private void openPrices() {
        DialogPriceBinding form = DialogPriceBinding.inflate(getLayoutInflater());
        String[] grades = {"A", "B", "C"};
        String[] sizes = {"EXTRA_SMALL", "SMALL", "MEDIUM", "LARGE", "JUMBO"};
        form.gradeInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, grades));
        form.sizeInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, sizes));
        form.gradeInput.setSelection(0);
        form.sizeInput.setSelection(2);
        AdapterView.OnItemSelectedListener selectionListener =
                new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id
                    ) {
                        updatePriceSelection(form);
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                };
        form.gradeInput.setOnItemSelectedListener(selectionListener);
        form.sizeInput.setOnItemSelectedListener(selectionListener);
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        StringBuilder current = new StringBuilder("Active prices\n");
        for (FruitPrice price : prices) current.append("Grade ").append(price.getGrade())
                .append(price.getSize() == null ? "" : " • " + price.getSize().replace('_', '-'))
                .append(": ").append(php.format(price.getPricePerKilogram())).append("/kg\n");
        form.currentPricesText.setText(current.toString().trim());
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Price Management")
                .setView(form.getRoot()).setNegativeButton("Cancel", null)
                .setPositiveButton("Update", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    double value; try { value = Double.parseDouble(text(form.priceInput.getText())); }
                    catch (NumberFormatException e) { value = 0; }
                    String grade = String.valueOf(form.gradeInput.getSelectedItem());
                    String size = String.valueOf(form.sizeInput.getSelectedItem());
                    if (value <= 0 || !List.of(grades).contains(grade)
                            || (!"C".equals(grade) && !List.of(sizes).contains(size))) {
                        form.errorText.setText("Choose a valid category and enter a positive price.");
                        return;
                    }
                    LocalRepository.get(requireContext()).configurePrice(
                            grade,
                            "C".equals(grade) ? null : size,
                            value,
                            new LocalRepository.Callback<>() {
                                @Override public void onSuccess(Void ignored) {
                                    dialog.dismiss();
                                    binding.messageText.setText("Price updated.");
                                    load();
                                }
                                @Override public void onError(String message) {
                                    form.errorText.setText(message);
                                }
                            });
                }));
        dialog.show();
    }

    private void updatePriceSelection(DialogPriceBinding form) {
        String grade = String.valueOf(form.gradeInput.getSelectedItem());
        String size = String.valueOf(form.sizeInput.getSelectedItem());
        boolean usesSize = !"C".equals(grade);
        form.sizeLabel.setVisibility(usesSize ? View.VISIBLE : View.GONE);
        form.sizeInput.setVisibility(usesSize ? View.VISIBLE : View.GONE);
        for (FruitPrice price : prices) {
            if (grade.equals(price.getGrade())
                    && (!usesSize || size.equals(price.getSize()))) {
                form.priceInput.setText(String.format(
                        Locale.US, "%.2f", price.getPricePerKilogram()));
                return;
            }
        }
        form.priceInput.setText("");
    }

    private void openItem(DialogSaleBinding saleForm, List<SaleLine> lines) {
        DialogSaleItemBinding item = DialogSaleItemBinding.inflate(getLayoutInflater());
        String[] sizes = {"EXTRA_SMALL", "SMALL", "MEDIUM", "LARGE", "JUMBO"};
        String[] grades = {"A", "B", "C"};
        item.sizeInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, sizes));
        item.gradeInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, grades));
        item.sizeInput.setText("MEDIUM", false);
        item.gradeInput.setText("A", false);
        Runnable updateAvailability = () -> {
            String size = text(item.sizeInput.getText());
            String grade = text(item.gradeInput.getText());
            int count = available(size, grade) - reserved(lines, size, grade);
            item.availableText.setText("Available: " + Math.max(0, count) + " pieces");
        };
        item.sizeInput.setOnItemClickListener((p, v, pos, id) -> updateAvailability.run());
        item.gradeInput.setOnItemClickListener((p, v, pos, id) -> updateAvailability.run());
        updateAvailability.run();
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Add Fruit Item").setView(item.getRoot())
                .setNegativeButton("Cancel", null).setPositiveButton("Add", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String size = item.sizeInput.getText().toString();
                    String grade = item.gradeInput.getText().toString();
                    int pieces; double weight;
                    try { pieces = Integer.parseInt(text(item.piecesInput.getText())); }
                    catch (NumberFormatException e) { pieces = 0; }
                    try { weight = Double.parseDouble(text(item.weightInput.getText())); }
                    catch (NumberFormatException e) { weight = 0; }
                    int available = available(size, grade) - reserved(lines, size, grade);
                    double price = price(size, grade);
                    if (pieces < 1 || weight <= 0 || pieces > available || price <= 0) {
                        item.errorText.setText(price <= 0 ? "No active price exists for this category."
                                : "Enter valid pieces/weight. Available pieces: " + available);
                        return;
                    }
                    lines.add(new SaleLine(size, grade, pieces, weight, price));
                    renderLines(saleForm, lines);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void renderLines(DialogSaleBinding form, List<SaleLine> lines) {
        form.itemsContainer.removeAllViews();
        double total = 0;
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        for (SaleLine line : lines) {
            TextView row = new TextView(requireContext());
            row.setPadding(8, 12, 8, 12);
            row.setText(line.size.replace('_', '-') + " • Grade " + line.grade + "\n"
                    + line.pieces + " pieces • " + line.weight + " kg • "
                    + php.format(line.subtotal()) + "\nTap to remove");
            row.setOnClickListener(v -> {
                lines.remove(line);
                renderLines(form, lines);
            });
            form.itemsContainer.addView(row);
            total += line.subtotal();
        }
        form.totalText.setText("Total: " + php.format(total));
    }

    private void complete(AlertDialog dialog, DialogSaleBinding form, List<SaleLine> lines) {
        String name = text(form.nameInput.getText()), address = text(form.addressInput.getText());
        String contact = text(form.contactInput.getText()), email = text(form.emailInput.getText());
        String method = text(form.methodInput.getText()), paidText = text(form.amountInput.getText());
        double paid; try { paid = Double.parseDouble(paidText); } catch (NumberFormatException e) { paid = -1; }
        double total = 0; boolean invalidLine = false;
        for (SaleLine line : lines) {
            total += line.subtotal();
            if (line.weight <= 0 || line.price <= 0) invalidLine = true;
        }
        boolean exactElectronic = !"CASH".equals(method) && Math.abs(paid - total) > 0.009;
        if (name.isEmpty() || address.isEmpty() || contact.length() < 7 || !email.contains("@")
                || lines.isEmpty() || invalidLine || paid < total || exactElectronic) {
            form.errorText.setText(invalidLine ? "Enter measured weight for every item and ensure all prices exist."
                    : exactElectronic ? "Electronic payment must equal the sale total."
                    : "Complete customer, items, and sufficient payment.");
            return;
        }
        boolean wholeBatch = form.entireBatchButton.isChecked();
        String batchNumber = wholeBatch ? batchKey(text(form.batchInput.getText())) : null;
        boolean overrideRequired = wholeBatch && requiresOverride(lines);
        if (wholeBatch && batchNumber.isEmpty()) {
            form.errorText.setText("Select a batch to sell.");
            return;
        }
        if (overrideRequired && text(form.overrideReasonInput.getText()).isEmpty()) {
            form.errorText.setText("Enter a reason for skipping older matching stock.");
            return;
        }
        List<CompleteSaleRequest.Item> items = new ArrayList<>();
        for (SaleLine line : lines) items.add(new CompleteSaleRequest.Item(
                line.size, line.grade, line.pieces, line.weight, line.inventoryId));
        CompleteSaleRequest request = new CompleteSaleRequest(name, address, contact, email,
                items, method, String.format(Locale.US, "%.2f", paid),
                text(form.referenceInput.getText()), text(form.providerInput.getText()),
                wholeBatch ? CompleteSaleRequest.MODE_ENTIRE_BATCH
                        : CompleteSaleRequest.MODE_SELECTED_ITEMS,
                batchNumber, text(form.overrideReasonInput.getText()));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        LocalRepository.get(requireContext()).completeSale(
                request, new LocalRepository.Callback<>() {
                    @Override public void onSuccess(Void ignored) {
                        dialog.dismiss();
                        binding.messageText.setText("Sale completed successfully.");
                        load();
                    }
                    @Override public void onError(String message) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        form.errorText.setText(message);
                    }
                });
    }

    private int available(String size, String grade) {
        int count = 0;
        for (InventoryLot lot : inventory)
            if (size.equals(lot.getSize()) && grade.equals(lot.getGrade())) count += lot.getAvailablePieces();
        return count;
    }
    private int reserved(List<SaleLine> lines, String size, String grade) {
        int count = 0;
        for (SaleLine line : lines) if (size.equals(line.size) && grade.equals(line.grade)) count += line.pieces;
        return count;
    }
    private double price(String size, String grade) {
        for (FruitPrice price : prices)
            if (grade.equals(price.getGrade()) && ("C".equals(grade) || size.equals(price.getSize())))
                return price.getPricePerKilogram();
        return 0;
    }
    private Map<String, List<InventoryLot>> availableBatches() {
        Map<String, List<InventoryLot>> result = new LinkedHashMap<>();
        for (InventoryLot lot : inventory) result.computeIfAbsent(
                lot.getBatchNumber(), ignored -> new ArrayList<>()).add(lot);
        return result;
    }
    private void loadWholeBatch(DialogSaleBinding form, List<SaleLine> lines,
                                List<InventoryLot> lots) {
        lines.clear();
        if (lots != null) for (InventoryLot lot : lots) {
            double categoryPrice = price(lot.getSize(), lot.getGrade());
            lines.add(new SaleLine(lot.getSize(), lot.getGrade(),
                    lot.getAvailablePieces(), 0, categoryPrice, lot.getId(), lot.isNextOut()));
        }
        boolean missingPrice = false;
        for (SaleLine line : lines) if (line.price <= 0) missingPrice = true;
        form.overrideReasonInput.setVisibility(requiresOverride(lines) ? View.VISIBLE : View.GONE);
        form.errorText.setText(missingPrice
                ? "Price Management is missing a price for one or more batch items." : "");
        renderWholeBatchLines(form, lines);
    }
    private void renderWholeBatchLines(DialogSaleBinding form, List<SaleLine> lines) {
        form.itemsContainer.removeAllViews();
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        for (SaleLine line : lines) {
            ItemWholeBatchSaleLineBinding row = ItemWholeBatchSaleLineBinding.inflate(
                    getLayoutInflater(), form.itemsContainer, false);
            row.categoryText.setText(line.size.replace('_', '-') + " • Grade " + line.grade);
            row.detailsText.setText(line.pieces + " pieces • "
                    + php.format(line.price) + "/kg");
            row.weightInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    try { line.weight = Double.parseDouble(text(s)); }
                    catch (NumberFormatException ignored) { line.weight = 0; }
                    row.subtotalText.setText("Subtotal: " + php.format(line.subtotal()));
                    updateTotal(form, lines);
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            form.itemsContainer.addView(row.getRoot());
        }
        updateTotal(form, lines);
    }
    private void updateTotal(DialogSaleBinding form, List<SaleLine> lines) {
        double total = 0;
        for (SaleLine line : lines) total += line.subtotal();
        form.totalText.setText("Total: " + NumberFormat.getCurrencyInstance(
                new Locale("en", "PH")).format(total));
    }
    private static boolean requiresOverride(List<SaleLine> lines) {
        for (SaleLine line : lines) if (!line.nextOut) return true;
        return false;
    }
    private static String batchKey(String label) {
        int separator = label.indexOf(" • ");
        return separator < 0 ? label.trim() : label.substring(0, separator).trim();
    }
    private static String text(CharSequence value) { return value == null ? "" : value.toString().trim(); }
    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }

    private static final class SaleLine {
        final String size, grade; final int pieces; double weight; final double price;
        final Long inventoryId; final boolean nextOut;
        SaleLine(String size, String grade, int pieces, double weight, double price) {
            this(size, grade, pieces, weight, price, null, true);
        }
        SaleLine(String size, String grade, int pieces, double weight, double price,
                 Long inventoryId, boolean nextOut) {
            this.size = size; this.grade = grade; this.pieces = pieces; this.weight = weight; this.price = price;
            this.inventoryId = inventoryId; this.nextOut = nextOut;
        }
        double subtotal() { return weight * price; }
    }
}
