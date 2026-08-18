package ph.dragonview.mobile.ui.planting;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.model.PlantingGroup;
import ph.dragonview.mobile.data.model.PlantingRequest;
import ph.dragonview.mobile.databinding.DialogPlantingBinding;
import ph.dragonview.mobile.databinding.FragmentPlantingBinding;

public final class PlantingFragment extends Fragment {
    private FragmentPlantingBinding binding;
    private PlantingAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentPlantingBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter = new PlantingAdapter(row -> {
            Bundle args = new Bundle();
            args.putLong("plantingId", row.getId());
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_planting_to_details, args);
        });
        binding.plantingList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.plantingList.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.addButton.setOnClickListener(v -> showCreateDialog());
        load();
    }

    private void load() {
        binding.swipeRefresh.setRefreshing(true);
        LocalRepository.get(requireContext()).planting(
                new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(List<PlantingGroup> rows) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        adapter.submit(rows);
                        int ready = 0;
                        for (PlantingGroup row : rows) {
                            if (row.isReadyForHarvest()) ready++;
                        }
                        binding.summaryText.setText(rows.size() + " planting groups • "
                                + ready + " ready for harvest");
                        binding.messageText.setText(rows.isEmpty() ? "No planting groups found." : "");
                    }
                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        binding.messageText.setText(message);
                    }
                });
    }

    private void showCreateDialog() {
        DialogPlantingBinding dialogBinding = DialogPlantingBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Record Stem Planting")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialogBinding.cuttingTypeInput.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Unrooted stem cutting", "Rooted stem cutting"}));
        dialogBinding.dateInput.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (picker, year, month, day) ->
                    dialogBinding.dateInput.setText(String.format(Locale.US, "%04d-%02d-%02d",
                            year, month + 1, day)), now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> create(dialog, dialogBinding)));
        dialog.show();
    }

    private void create(AlertDialog dialog, DialogPlantingBinding form) {
        String number = text(form.recordInput.getText());
        String date = text(form.dateInput.getText());
        String variety = text(form.varietyInput.getText());
        String location = text(form.locationInput.getText());
        String cuttingType = form.cuttingTypeInput.getSelectedItemPosition() == 1
                ? "ROOTED" : "UNROOTED";
        int plants;
        try { plants = Integer.parseInt(text(form.plantsInput.getText())); }
        catch (NumberFormatException ignored) { plants = 0; }
        if (number.isEmpty() || date.isEmpty() || location.isEmpty() || plants < 1) {
            form.errorText.setText(
                    "Enter a record number, planting date, location, and at least one cutting.");
            return;
        }
        if (variety.isEmpty()) variety = "Not specified";
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        LocalRepository.get(requireContext()).createPlanting(
                new PlantingRequest(number, date, variety, location, plants, cuttingType),
                new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        dialog.dismiss();
                        load();
                    }
                    @Override
                    public void onError(String message) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        form.errorText.setText(message);
                    }
                });
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }
}
