package ph.dragonview.mobile.ui.planting;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.PlantingDetails;
import ph.dragonview.mobile.data.model.PlantingGroup;
import ph.dragonview.mobile.data.model.PlantingStage;
import ph.dragonview.mobile.data.model.PlantingUpdateRequest;
import ph.dragonview.mobile.databinding.DialogPlantingStageBinding;
import ph.dragonview.mobile.databinding.DialogPlantingUpdateBinding;
import ph.dragonview.mobile.databinding.FragmentPlantingDetailsBinding;

public final class PlantingDetailsFragment extends Fragment {
    private FragmentPlantingDetailsBinding binding;
    private long plantingId;
    private PlantingDetails details;

    private final ActivityResultLauncher<String[]> photoPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || !isAdded()) return;
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                    // Some document providers grant access without a persistable flag.
                }
                showObservationDialog(uri.toString());
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup parent, Bundle state) {
        binding = FragmentPlantingDetailsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        plantingId = requireArguments().getLong("plantingId");
        binding.updateStageButton.setOnClickListener(v -> showStageDialog());
        binding.addRecordButton.setOnClickListener(v -> showObservationDialog(null));
        binding.addPhotoButton.setOnClickListener(v ->
                photoPicker.launch(new String[]{"image/*"}));
        load();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.messageText.setText("");
        LocalRepository.get(requireContext()).plantingDetails(
                plantingId, new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(PlantingDetails value) {
                        if (binding == null) return;
                        binding.progress.setVisibility(View.GONE);
                        details = value;
                        bind(value);
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        binding.progress.setVisibility(View.GONE);
                        binding.messageText.setText(message);
                    }
                });
    }

    private void bind(PlantingDetails value) {
        PlantingGroup group = value.getGroup();
        PlantingStage guidance = value.getGuidance();
        binding.recordText.setText("#" + group.getRecordNumber());
        binding.stageText.setText(guidance.getDisplayName());
        binding.detailsText.setText(group.getLocation() + " • " + group.getVariety()
                + " • " + group.getNumberOfPlants() + " "
                + group.getPropagationMethod().getQuantityLabel());
        binding.progressIndicator.setProgress(guidance.getProgressPercent());
        String ageAnchor = group.getPropagationMethod()
                == ph.dragonview.mobile.data.model.PropagationMethod.GRAFTED
                ? " since grafting" : " since planting";
        binding.ageText.setText("Day " + group.getElapsedDays() + ageAnchor
                + " • " + group.getPropagationMethod().getDisplayName());
        if (group.getFruitAgeDays() == null) {
            binding.fruitAgeText.setText("Fruit-age tracking starts when flowering is confirmed.");
        } else {
            binding.fruitAgeText.setText("Fruit day " + group.getFruitAgeDays()
                    + " • Estimated harvest window: "
                    + group.getEstimatedHarvestWindow());
        }
        binding.suggestedText.setText("Suggested next milestone: "
                + group.getSuggestedStage().getDisplayName());
        binding.expectedText.setText(guidance.getExpectedAppearance());
        binding.tasksText.setText(guidance.getTasks());
        binding.warningsText.setText(guidance.getWarnings());
        renderHistory(value);
    }

    private void renderHistory(PlantingDetails value) {
        binding.historyContainer.removeAllViews();
        binding.emptyHistoryText.setVisibility(
                value.getUpdates().isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (PlantingDetails.Update update : value.getUpdates()) {
            View row = inflater.inflate(
                    R.layout.item_planting_update, binding.historyContainer, false);
            TextView typeText = row.findViewById(R.id.typeText);
            TextView dateStageText = row.findViewById(R.id.dateStageText);
            TextView noteText = row.findViewById(R.id.noteText);
            TextView measurementText = row.findViewById(R.id.measurementText);
            ImageView photoView = row.findViewById(R.id.photoView);

            PlantingStage stage = PlantingStage.fromCode(update.getStage());
            if ("MILESTONE".equals(update.getType())) {
                typeText.setText(stage.getDisplayName());
            } else if ("GRAFTING".equals(update.getType())) {
                typeText.setText("Grafting event");
            } else if ("PHOTO".equals(update.getType())) {
                typeText.setText("Progress photo");
            } else {
                typeText.setText("Progress record");
            }
            dateStageText.setText(update.getRecordedDate() + " • "
                    + stage.getDisplayName());
            String note = update.getNote();
            noteText.setVisibility(note == null || note.trim().isEmpty()
                    ? View.GONE : View.VISIBLE);
            noteText.setText(note);
            Double measurement = update.getMeasurementCentimeters();
            measurementText.setVisibility(measurement == null
                    ? View.GONE : View.VISIBLE);
            if (measurement != null) {
                measurementText.setText(String.format(
                        Locale.US, "Recorded length: %.1f cm", measurement));
            }
            String photoUri = update.getPhotoUri();
            photoView.setVisibility(photoUri == null ? View.GONE : View.VISIBLE);
            if (photoUri != null) {
                try {
                    photoView.setImageURI(Uri.parse(photoUri));
                } catch (Exception ignored) {
                    photoView.setVisibility(View.GONE);
                }
            }
            binding.historyContainer.addView(row);
        }
    }

    private void showStageDialog() {
        if (details == null) return;
        DialogPlantingStageBinding form = DialogPlantingStageBinding.inflate(
                getLayoutInflater());
        List<PlantingStage> stages = PlantingStage.forMethod(
                details.getGroup().getPropagationMethod());
        String[] labels = new String[stages.size()];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = stages.get(index).getDisplayName();
        }
        form.stageInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, labels));
        form.stageInput.setText(
                details.getGroup().getSuggestedStage().getDisplayName(), false);
        form.dateInput.setText(today());
        form.dateInput.setOnClickListener(v -> chooseDate(form.dateInput));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Confirm Growth Milestone")
                .setView(form.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save milestone", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    PlantingStage stage = stageForLabel(
                            text(form.stageInput.getText()), stages);
                    String date = text(form.dateInput.getText());
                    if (stage == null || date.isEmpty()) {
                        form.errorText.setText("Select a stage and event date.");
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    LocalRepository.get(requireContext()).updatePlantingStage(
                            plantingId, stage, date, text(form.noteInput.getText()),
                            callback(dialog));
                }));
        dialog.show();
    }

    private void showObservationDialog(String photoUri) {
        DialogPlantingUpdateBinding form = DialogPlantingUpdateBinding.inflate(
                getLayoutInflater());
        form.dateInput.setText(today());
        form.dateInput.setOnClickListener(v -> chooseDate(form.dateInput));
        form.photoLabel.setVisibility(photoUri == null ? View.GONE : View.VISIBLE);
        form.photoPreview.setVisibility(photoUri == null ? View.GONE : View.VISIBLE);
        if (photoUri != null) form.photoPreview.setImageURI(Uri.parse(photoUri));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(photoUri == null ? "Add Progress Record" : "Save Progress Photo")
                .setView(form.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save record", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String measurementValue = text(form.measurementInput.getText());
                    Double measurement = null;
                    if (!measurementValue.isEmpty()) {
                        try {
                            measurement = Double.parseDouble(measurementValue);
                        } catch (NumberFormatException error) {
                            form.errorText.setText("Enter a valid measurement in centimeters.");
                            return;
                        }
                    }
                    String note = text(form.noteInput.getText());
                    String date = text(form.dateInput.getText());
                    if (date.isEmpty() || (note.isEmpty()
                            && measurement == null && photoUri == null)) {
                        form.errorText.setText(
                                "Add a date and at least one note, measurement, or photo.");
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    LocalRepository.get(requireContext()).addPlantingUpdate(
                            new PlantingUpdateRequest(
                                    plantingId, note, measurement, photoUri, date),
                            callback(dialog));
                }));
        dialog.show();
    }

    private LocalRepository.Callback<Void> callback(AlertDialog dialog) {
        return new LocalRepository.Callback<>() {
            @Override
            public void onSuccess(Void ignored) {
                dialog.dismiss();
                load();
            }

            @Override
            public void onError(String message) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                if (binding != null) binding.messageText.setText(message);
            }
        };
    }

    private void chooseDate(TextView target) {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (picker, year, month, day) ->
                target.setText(String.format(Locale.US, "%04d-%02d-%02d",
                        year, month + 1, day)),
                now.get(Calendar.YEAR), now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static PlantingStage stageForLabel(
            String label, List<PlantingStage> stages) {
        for (PlantingStage stage : stages) {
            if (stage.getDisplayName().equals(label)) return stage;
        }
        return null;
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Calendar.getInstance().getTime());
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
