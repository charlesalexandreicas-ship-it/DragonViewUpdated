package ph.dragonview.mobile.ui.sales;

import android.os.Bundle;
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
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.model.CompleteSaleRequest;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.FruitPrice;
import ph.dragonview.mobile.data.model.InventoryLot;
import ph.dragonview.mobile.data.model.SaleSummary;
import ph.dragonview.mobile.databinding.DialogSaleItemBinding;
import ph.dragonview.mobile.databinding.DialogSaleBinding;
import ph.dragonview.mobile.databinding.DialogPriceBinding;
import ph.dragonview.mobile.databinding.FragmentSalesBinding;

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
        adapter = new SalesAdapter();
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
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("New Sale")
                .setView(form.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Complete sale", null)
                .create();
        form.addItemButton.setOnClickListener(v -> openItem(form, lines));
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> complete(dialog, form, lines)));
        dialog.show();
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
                    + php.format(line.subtotal()));
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
        double total = 0; for (SaleLine line : lines) total += line.subtotal();
        boolean exactElectronic = !"CASH".equals(method) && Math.abs(paid - total) > 0.009;
        if (name.isEmpty() || address.isEmpty() || contact.length() < 7 || !email.contains("@")
                || lines.isEmpty() || paid < total || exactElectronic) {
            form.errorText.setText(exactElectronic ? "Electronic payment must equal the sale total."
                    : "Complete customer, items, and sufficient payment.");
            return;
        }
        List<CompleteSaleRequest.Item> items = new ArrayList<>();
        for (SaleLine line : lines) items.add(new CompleteSaleRequest.Item(
                line.size, line.grade, line.pieces, line.weight));
        CompleteSaleRequest request = new CompleteSaleRequest(name, address, contact, email,
                items, method, String.format(Locale.US, "%.2f", paid),
                text(form.referenceInput.getText()), text(form.providerInput.getText()));
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
    private static String text(CharSequence value) { return value == null ? "" : value.toString().trim(); }
    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }

    private static final class SaleLine {
        final String size, grade; final int pieces; final double weight, price;
        SaleLine(String size, String grade, int pieces, double weight, double price) {
            this.size = size; this.grade = grade; this.pieces = pieces; this.weight = weight; this.price = price;
        }
        double subtotal() { return weight * price; }
    }
}
