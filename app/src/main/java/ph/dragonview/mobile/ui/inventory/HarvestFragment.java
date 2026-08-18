package ph.dragonview.mobile.ui.inventory;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.HarvestRequest;
import ph.dragonview.mobile.databinding.FragmentHarvestBinding;

public final class HarvestFragment extends Fragment {
    private static final String[] SIZES = {
            "EXTRA_SMALL", "SMALL", "MEDIUM", "LARGE", "JUMBO"
    };
    private static final String[] GRADES = {"A", "B", "C"};

    private FragmentHarvestBinding binding;
    private final List<HarvestRequest.Item> items = new ArrayList<>();
    private int editingIndex = -1;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup parent,
            Bundle state
    ) {
        binding = FragmentHarvestBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        binding.sizeInput.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                SIZES));
        binding.gradeInput.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                GRADES));
        resetEditor();
        binding.dateInput.setOnClickListener(v -> chooseDate());
        binding.addItemButton.setOnClickListener(v -> addOrUpdateItem());
        binding.saveButton.setOnClickListener(v -> save());
        renderItems();
    }

    private void chooseDate() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(
                requireContext(),
                (picker, year, month, day) -> binding.dateInput.setText(
                        String.format(Locale.US, "%04d-%02d-%02d",
                                year, month + 1, day)),
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void addOrUpdateItem() {
        String size = text(binding.sizeInput.getText());
        String grade = text(binding.gradeInput.getText());
        int pieces = parsePieces();
        binding.sizeLayout.setError(size.isEmpty() ? "Select a fruit size." : null);
        binding.gradeLayout.setError(grade.isEmpty() ? "Select a grade." : null);
        binding.piecesLayout.setError(
                pieces < 1 ? "Enter at least one piece." : null);
        if (size.isEmpty() || grade.isEmpty() || pieces < 1) return;

        for (int index = 0; index < items.size(); index++) {
            HarvestRequest.Item item = items.get(index);
            if (index != editingIndex
                    && item.getSize().equals(size)
                    && item.getGrade().equals(grade)) {
                binding.piecesLayout.setError(
                        "This size and grade combination is already in the batch.");
                return;
            }
        }

        HarvestRequest.Item item = new HarvestRequest.Item(size, grade, pieces);
        if (editingIndex >= 0) {
            items.set(editingIndex, item);
        } else {
            items.add(item);
        }
        resetEditor();
        renderItems();
        binding.statusText.setText("");
    }

    private void editItem(int index) {
        HarvestRequest.Item item = items.get(index);
        editingIndex = index;
        binding.sizeInput.setText(item.getSize(), false);
        binding.gradeInput.setText(item.getGrade(), false);
        binding.piecesInput.setText(String.valueOf(item.getPieces()));
        binding.addItemButton.setText("Update combination");
        binding.statusText.setText("Editing " + combinationLabel(item) + ".");
    }

    private void removeItem(int index) {
        items.remove(index);
        if (editingIndex == index) {
            resetEditor();
        } else if (editingIndex > index) {
            editingIndex--;
        }
        renderItems();
    }

    private void resetEditor() {
        editingIndex = -1;
        binding.sizeInput.setText("MEDIUM", false);
        binding.gradeInput.setText("A", false);
        binding.piecesInput.setText("");
        binding.sizeLayout.setError(null);
        binding.gradeLayout.setError(null);
        binding.piecesLayout.setError(null);
        binding.addItemButton.setText("Add combination");
    }

    private void renderItems() {
        binding.itemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int totalPieces = 0;
        for (int index = 0; index < items.size(); index++) {
            HarvestRequest.Item item = items.get(index);
            totalPieces += item.getPieces();
            View row = inflater.inflate(
                    R.layout.item_harvest_entry,
                    binding.itemsContainer,
                    false);
            ((TextView) row.findViewById(R.id.combinationText))
                    .setText(combinationLabel(item));
            ((TextView) row.findViewById(R.id.piecesText))
                    .setText(String.format(
                            Locale.US,
                            "%,d piece%s",
                            item.getPieces(),
                            item.getPieces() == 1 ? "" : "s"));
            int itemIndex = index;
            ((MaterialButton) row.findViewById(R.id.editButton))
                    .setOnClickListener(v -> editItem(itemIndex));
            ((MaterialButton) row.findViewById(R.id.removeButton))
                    .setOnClickListener(v -> removeItem(itemIndex));
            binding.itemsContainer.addView(row);
        }
        binding.emptyItemsText.setVisibility(
                items.isEmpty() ? View.VISIBLE : View.GONE);
        binding.batchSummaryText.setText(String.format(
                Locale.US,
                "%d combination%s • %,d piece%s",
                items.size(),
                items.size() == 1 ? "" : "s",
                totalPieces,
                totalPieces == 1 ? "" : "s"));
    }

    private void save() {
        String batch = text(binding.batchInput.getText()).replaceFirst("^#", "");
        String date = text(binding.dateInput.getText());
        binding.batchLayout.setError(
                batch.isEmpty() ? "Batch number is required." : null);
        binding.dateLayout.setError(
                date.isEmpty() ? "Harvest date is required." : null);
        if (batch.isEmpty() || date.isEmpty()) return;
        if (editingIndex >= 0) {
            binding.statusText.setText(
                    "Finish updating the selected combination before saving.");
            return;
        }
        if (items.isEmpty()) {
            binding.statusText.setText(
                    "Add at least one size and grade combination.");
            return;
        }

        setSaving(true);
        HarvestRequest request = new HarvestRequest(batch, date, items);
        LocalRepository.get(requireContext()).registerHarvest(
                request, new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        if (binding == null) return;
                        setSaving(false);
                        NavHostFragment.findNavController(
                                HarvestFragment.this).navigateUp();
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        setSaving(false);
                        binding.statusText.setText(message);
                    }
                });
    }

    private void setSaving(boolean saving) {
        binding.saveButton.setEnabled(!saving);
        binding.addItemButton.setEnabled(!saving);
        binding.saveButton.setText(
                saving ? "Saving harvest batch…" : "Save entire harvest batch");
        if (saving) binding.statusText.setText("Saving all harvest items…");
    }

    private int parsePieces() {
        try {
            return Integer.parseInt(text(binding.piecesInput.getText()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String combinationLabel(HarvestRequest.Item item) {
        return formatSize(item.getSize()) + " • Grade " + item.getGrade();
    }

    private static String formatSize(String size) {
        String value = size.toLowerCase(Locale.US).replace('_', ' ');
        StringBuilder label = new StringBuilder(value.length());
        boolean capitalize = true;
        for (char character : value.toCharArray()) {
            label.append(capitalize
                    ? Character.toUpperCase(character)
                    : character);
            capitalize = character == ' ';
        }
        return label.toString();
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
