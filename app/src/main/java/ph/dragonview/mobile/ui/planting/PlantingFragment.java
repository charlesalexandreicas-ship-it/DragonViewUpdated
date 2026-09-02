package ph.dragonview.mobile.ui.planting;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.model.GraftingRequest;
import ph.dragonview.mobile.data.model.PlantingGroup;
import ph.dragonview.mobile.data.model.PlantingRequest;
import ph.dragonview.mobile.data.model.PlantingStage;
import ph.dragonview.mobile.data.model.PlantingUpdateRequest;
import ph.dragonview.mobile.data.model.PropagationMethod;
import ph.dragonview.mobile.databinding.BottomSheetStageGuidanceBinding;
import ph.dragonview.mobile.databinding.DialogGraftingBinding;
import ph.dragonview.mobile.databinding.DialogPlantingBinding;
import ph.dragonview.mobile.databinding.FragmentPlantingBinding;
import ph.dragonview.mobile.ui.archive.ArchiveDialog;

public final class PlantingFragment extends Fragment {
    private FragmentPlantingBinding binding;
    private PlantingAdapter adapter;
    private List<PlantingGroup> currentRows = new ArrayList<>();
    private PlantingGroup pendingPhotoGroup;
    private PlantingStage pendingPhotoStage;
    private final ActivityResultLauncher<String[]> photoPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::saveSelectedPhoto);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentPlantingBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter = new PlantingAdapter(new PlantingAdapter.Listener() {
            @Override
            public void showGuidance(PlantingGroup group, PlantingStage stage) {
                showGuidanceSheet(group, stage);
            }

            @Override
            public void manage(PlantingGroup group) {
                Bundle args = new Bundle();
                args.putLong("plantingId", group.getId());
                NavHostFragment.findNavController(PlantingFragment.this)
                        .navigate(R.id.action_planting_to_details, args);
            }

            @Override
            public void archive(PlantingGroup group) {
                confirmArchive(group);
            }
        });
        binding.plantingList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.plantingList.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.addButton.setOnClickListener(v -> showCreateDialog());
        binding.graftButton.setOnClickListener(v -> showGraftingDialog());
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
                        currentRows = new ArrayList<>(rows);
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
                .setTitle("Record Planting")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialogBinding.propagationInput.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{"From seed", "Stem cutting"}));
        dialogBinding.cuttingTypeInput.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Unrooted stem cutting", "Rooted stem cutting"}));
        dialogBinding.propagationInput.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                                               int position, long id) {
                        boolean stem = position == 1;
                        dialogBinding.cuttingTypeLabel.setVisibility(
                                stem ? View.VISIBLE : View.GONE);
                        dialogBinding.cuttingTypeInput.setVisibility(
                                stem ? View.VISIBLE : View.GONE);
                        dialogBinding.plantsInput.setHint(
                                stem ? "Number of cuttings" : "Number of seeds");
                    }

                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });
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
        PropagationMethod method = form.propagationInput.getSelectedItemPosition() == 1
                ? PropagationMethod.STEM_CUTTING : PropagationMethod.SEED;
        String cuttingType = method == PropagationMethod.SEED
                ? "NOT_APPLICABLE"
                : form.cuttingTypeInput.getSelectedItemPosition() == 1
                ? "ROOTED" : "UNROOTED";
        int plants;
        try { plants = Integer.parseInt(text(form.plantsInput.getText())); }
        catch (NumberFormatException ignored) { plants = 0; }
        if (number.isEmpty() || date.isEmpty() || location.isEmpty() || plants < 1) {
            form.errorText.setText(
                    "Enter a farm section, planting date, location, and at least one "
                            + method.getQuantityLabel().substring(
                            0, method.getQuantityLabel().length() - 1) + ".");
            return;
        }
        if (variety.isEmpty()) variety = "Not specified";
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        LocalRepository.get(requireContext()).createPlanting(
                new PlantingRequest(number, date, variety, location, plants,
                        cuttingType, method),
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

    private void showGraftingDialog() {
        List<PlantingGroup> seedRows = new ArrayList<>();
        for (PlantingGroup row : currentRows) {
            if (row.getPropagationMethod() == PropagationMethod.SEED) {
                seedRows.add(row);
            }
        }
        if (seedRows.isEmpty()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("No eligible seed record")
                    .setMessage("Create a seed-grown farm record first. Record grafting only "
                            + "after its suitability has been professionally confirmed.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        DialogGraftingBinding form = DialogGraftingBinding.inflate(getLayoutInflater());
        List<String> labels = new ArrayList<>();
        for (PlantingGroup row : seedRows) {
            labels.add(row.getRecordNumber() + " • " + row.getCurrentStage().getDisplayName()
                    + " • Day " + row.getElapsedDays());
        }
        form.sourceRecordInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        form.dateInput.setOnClickListener(v -> showDatePicker(form.dateInput));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Record Grafting")
                .setView(form.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String date = text(form.dateInput.getText());
                    String variety = text(form.scionVarietyInput.getText());
                    if (date.isEmpty() || variety.isEmpty()) {
                        form.errorText.setText("Enter the grafting date and scion variety.");
                        return;
                    }
                    int selected = form.sourceRecordInput.getSelectedItemPosition();
                    PlantingGroup source = seedRows.get(Math.max(0, selected));
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    LocalRepository.get(requireContext()).recordGrafting(
                            new GraftingRequest(source.getId(), date, variety,
                                    text(form.noteInput.getText())),
                            new LocalRepository.Callback<>() {
                                @Override public void onSuccess(Void ignoredValue) {
                                    dialog.dismiss();
                                    load();
                                }

                                @Override public void onError(String message) {
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                            .setEnabled(true);
                                    form.errorText.setText(message);
                                }
                            });
                }));
        dialog.show();
    }

    private void confirmArchive(PlantingGroup group) {
        ArchiveDialog.show(this,
                "Remove Farm Section " + group.getRecordNumber() + "?",
                "The farm record, guidance milestones, grafting events, observations, "
                        + "and photos will remain stored and can be restored from Recently Removed.",
                reason -> LocalRepository.get(requireContext()).archivePlanting(
                        group.getId(), reason, new LocalRepository.Callback<>() {
                            @Override public void onSuccess(Void ignored) { load(); }
                            @Override public void onError(String message) {
                                if (binding != null) binding.messageText.setText(message);
                            }
                        }));
    }

    private void showGuidanceSheet(PlantingGroup group, PlantingStage stage) {
        BottomSheetStageGuidanceBinding sheet =
                BottomSheetStageGuidanceBinding.inflate(getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(sheet.getRoot());
        sheet.stageTitleText.setText(stage.getDisplayName());
        sheet.stageTimingText.setText(stage.getEstimatedTiming());
        sheet.overviewText.setText(stage.getOverview());
        sheet.expectedText.setText(stage.getExpectedAppearance());
        sheet.tasksText.setText(stage.getTasks());
        sheet.recordText.setText(stage.getRecordGuidance());
        sheet.warningsText.setText(stage.getWarnings());
        sheet.completionText.setText(stage.getCompletionIndicator());
        sheet.validationText.setText(stage.getValidationStatus());
        sheet.sourceText.setText(stage.getReferenceTitle() + "\n"
                + stage.getReferenceUrl());
        Linkify.addLinks(sheet.sourceText, Linkify.WEB_URLS);
        sheet.sourcesToggle.setOnClickListener(v -> sheet.sourcesContainer.setVisibility(
                sheet.sourcesContainer.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE));
        sheet.closeButton.setOnClickListener(v -> dialog.dismiss());

        boolean current = stage == group.getCurrentStage();
        sheet.optionalPhotoButton.setVisibility(current ? View.VISIBLE : View.GONE);
        sheet.optionalPhotoButton.setOnClickListener(v -> {
            pendingPhotoGroup = group;
            pendingPhotoStage = stage;
            dialog.dismiss();
            photoPicker.launch(new String[]{"image/*"});
        });
        dialog.setOnShowListener(ignored -> {
            View bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) return;
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
            ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
            params.height = height;
            bottomSheet.setLayoutParams(params);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setPeekHeight(height);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        dialog.show();
    }

    private void saveSelectedPhoto(Uri uri) {
        if (uri == null || pendingPhotoGroup == null || pendingPhotoStage == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some document providers grant access without a persistable permission.
        }
        PlantingGroup group = pendingPhotoGroup;
        PlantingStage stage = pendingPhotoStage;
        pendingPhotoGroup = null;
        pendingPhotoStage = null;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Calendar.getInstance().getTime());
        LocalRepository.get(requireContext()).addPlantingUpdate(
                new PlantingUpdateRequest(group.getId(),
                        "Optional guidance photo for " + stage.getDisplayName() + ".",
                        null, uri.toString(), today),
                new LocalRepository.Callback<>() {
                    @Override public void onSuccess(Void ignored) {
                        Toast.makeText(requireContext(), "Photo added to farm record.",
                                Toast.LENGTH_SHORT).show();
                        load();
                    }

                    @Override public void onError(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showDatePicker(android.widget.TextView target) {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (picker, year, month, day) ->
                target.setText(String.format(Locale.US, "%04d-%02d-%02d",
                        year, month + 1, day)), now.get(Calendar.YEAR),
                now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override public void onDestroyView() {
        binding = null;
        currentRows = new ArrayList<>();
        super.onDestroyView();
    }
}
