package ph.dragonview.mobile.ui.pricing;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.FruitPrice;
import ph.dragonview.mobile.databinding.DialogPriceBinding;

public final class PriceManagementDialog {
    public interface Listener {
        void onUpdated();
        void onError(String message);
    }

    private PriceManagementDialog() { }

    public static void show(Fragment fragment, Listener listener) {
        LocalRepository.get(fragment.requireContext()).prices(
                new LocalRepository.Callback<>() {
                    @Override public void onSuccess(List<FruitPrice> prices) {
                        if (fragment.isAdded()) showLoaded(fragment, prices, listener);
                    }

                    @Override public void onError(String message) {
                        if (fragment.isAdded()) listener.onError(message);
                    }
                });
    }

    private static void showLoaded(
            Fragment fragment, List<FruitPrice> prices, Listener listener
    ) {
        DialogPriceBinding form = DialogPriceBinding.inflate(fragment.getLayoutInflater());
        String[] grades = {"A", "B", "C"};
        String[] sizes = {"EXTRA_SMALL", "SMALL", "MEDIUM", "LARGE", "JUMBO"};
        form.gradeInput.setAdapter(new ArrayAdapter<>(fragment.requireContext(),
                android.R.layout.simple_dropdown_item_1line, grades));
        form.sizeInput.setAdapter(new ArrayAdapter<>(fragment.requireContext(),
                android.R.layout.simple_dropdown_item_1line, sizes));
        form.gradeInput.setSelection(0);
        form.sizeInput.setSelection(2);
        AdapterView.OnItemSelectedListener selectionListener =
                new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id
                    ) {
                        updateSelection(form, prices);
                    }

                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                };
        form.gradeInput.setOnItemSelectedListener(selectionListener);
        form.sizeInput.setOnItemSelectedListener(selectionListener);
        renderCurrentPrices(form, prices);

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle("Price Management")
                .setView(form.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> update(
                        fragment, dialog, form, grades, sizes, listener)));
        dialog.show();
    }

    private static void update(
            Fragment fragment,
            AlertDialog dialog,
            DialogPriceBinding form,
            String[] grades,
            String[] sizes,
            Listener listener
    ) {
        double value;
        try {
            value = Double.parseDouble(text(form.priceInput.getText()));
        } catch (NumberFormatException error) {
            value = 0;
        }
        String grade = String.valueOf(form.gradeInput.getSelectedItem());
        String size = String.valueOf(form.sizeInput.getSelectedItem());
        if (value <= 0 || !List.of(grades).contains(grade)
                || (!"C".equals(grade) && !List.of(sizes).contains(size))) {
            form.errorText.setText("Choose a valid category and enter a positive price.");
            return;
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        LocalRepository.get(fragment.requireContext()).configurePrice(
                grade,
                "C".equals(grade) ? null : size,
                value,
                new LocalRepository.Callback<>() {
                    @Override public void onSuccess(Void ignored) {
                        dialog.dismiss();
                        listener.onUpdated();
                    }

                    @Override public void onError(String message) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        form.errorText.setText(message);
                    }
                });
    }

    private static void updateSelection(
            DialogPriceBinding form, List<FruitPrice> prices
    ) {
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

    private static void renderCurrentPrices(
            DialogPriceBinding form, List<FruitPrice> prices
    ) {
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        StringBuilder current = new StringBuilder("Active prices\n");
        for (FruitPrice price : prices) {
            current.append("Grade ").append(price.getGrade())
                    .append(price.getSize() == null
                            ? "" : " • " + price.getSize().replace('_', '-'))
                    .append(": ").append(php.format(price.getPricePerKilogram()))
                    .append("/kg\n");
        }
        form.currentPricesText.setText(current.toString().trim());
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
