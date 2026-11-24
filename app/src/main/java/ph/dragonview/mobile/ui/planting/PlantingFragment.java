package ph.dragonview.mobile.ui.planting;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.os.Bundle;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
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
import ph.dragonview.mobile.ui.date.RecordDate;
import ph.dragonview.mobile.ui.date.RecordDateInput;
import ph.dragonview.mobile.ui.scanner.ScannerImageLoader;

/** Coordinates planting groups, planting/grafting forms, and stage-guidance sheets. */
public final class PlantingFragment extends Fragment {
    private FragmentPlantingBinding binding;
    private PlantingAdapter adapter;
    private List<PlantingGroup> currentRows = new ArrayList<>();
    private PlantingGroup pendingPhotoGroup;
    private PlantingStage pendingPhotoStage;
    private final ActivityResultLauncher<String[]> photoPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::saveSelectedPhoto);
    private long sectionPhotoGroupId = -1;
    private String sectionPhotoScope;
    private Dialog sectionPhotoViewer;
    private final ActivityResultLauncher<String[]> sectionPhotoPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::saveSectionPhoto);

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) {
            sectionPhotoGroupId = state.getLong("sectionPhotoGroup", -1);
            sectionPhotoScope = state.getString("sectionPhotoScope");
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putLong("sectionPhotoGroup", sectionPhotoGroupId);
        state.putString("sectionPhotoScope", sectionPhotoScope);
    }

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

            @Override public boolean hasSectionPhoto(PlantingGroup group) {
                return sectionPhotoUri(group.getId()) != null;
            }

            @Override public void openSectionPhoto(PlantingGroup group) {
                showSectionPhoto(group.getId());
            }
        }, new SessionManager(requireContext()).isWorkspaceOwner());
        binding.plantingList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.plantingList.setAdapter(adapter);
        binding.plantingList.setNestedScrollingEnabled(false);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.addButton.setOnClickListener(v -> showCreateDialog());
        binding.graftButton.setOnClickListener(v -> showGraftingDialog());
        binding.farmLayoutButton.setOnClickListener(v -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_planting_to_farm_layout));
        binding.emptyActionButton.setOnClickListener(v -> showCreateDialog());
        load();
    }

    private SharedPreferences sectionPhotoPreferences() {
        return requireContext().getSharedPreferences("planting_section_photos", Context.MODE_PRIVATE);
    }

    /** Photos stay on this device and are isolated by account, workspace, and planting group. */
    @Nullable private String currentPhotoScope() {
        SessionManager session = new SessionManager(requireContext());
        var user = session.getUser();
        String workspace = session.getActiveWorkspaceId();
        return user == null || workspace.isEmpty() ? null : user.getId() + "/" + workspace;
    }

    @Nullable private String sectionPhotoUri(long groupId) {
        String scope = currentPhotoScope();
        return scope == null ? null : sectionPhotoPreferences().getString(scope + "/" + groupId, null);
    }

    private void chooseSectionPhoto(long groupId) {
        String scope = currentPhotoScope();
        if (scope == null) return;
        sectionPhotoGroupId = groupId;
        sectionPhotoScope = scope;
        sectionPhotoPicker.launch(new String[]{"image/*"});
    }

    /** Validate before replacing a saved URI, and ignore results after a workspace change. */
    private void saveSectionPhoto(@Nullable Uri uri) {
        long groupId = sectionPhotoGroupId;
        String scope = sectionPhotoScope;
        sectionPhotoGroupId = -1;
        sectionPhotoScope = null;
        if (uri == null || groupId < 0 || binding == null
                || scope == null || !scope.equals(currentPhotoScope())) return;
        loadSectionPhoto(uri, bitmap -> {
            bitmap.recycle();
            if (!scope.equals(currentPhotoScope())) return;
            try {
                requireContext().getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sectionPhotoPreferences().edit().putString(scope + "/" + groupId, uri.toString()).apply();
                adapter.submit(currentRows);
            } catch (SecurityException error) {
                Toast.makeText(requireContext(), R.string.farm_section_photo_save_error,
                        Toast.LENGTH_LONG).show();
            }
        }, R.string.farm_section_photo_save_error, null);
    }

    /** Decode away from the UI thread and discard images if the originating view has closed. */
    private void loadSectionPhoto(Uri uri, Consumer<Bitmap> ready, int errorText,
                                  @Nullable Runnable recover) {
        Context context = requireContext().getApplicationContext();
        FragmentPlantingBinding origin = binding;
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                Bitmap bitmap = ScannerImageLoader.load(context, uri, 1600);
                main.post(() -> {
                    if (binding == origin && isAdded()) ready.accept(bitmap);
                    else bitmap.recycle();
                });
            } catch (IOException | RuntimeException error) {
                main.post(() -> {
                    if (binding != origin || !isAdded()) return;
                    Toast.makeText(requireContext(), errorText, Toast.LENGTH_LONG).show();
                    if (recover != null) recover.run();
                });
            }
        }, "farm-section-photo").start();
    }

    private void showSectionPhoto(long groupId) {
        String saved = sectionPhotoUri(groupId);
        if (saved == null) {
            chooseSectionPhoto(groupId);
            return;
        }
        loadSectionPhoto(Uri.parse(saved), bitmap -> {
            if (!saved.equals(sectionPhotoUri(groupId))) {
                bitmap.recycle();
                return;
            }
            if (sectionPhotoViewer != null) sectionPhotoViewer.dismiss();
            Dialog viewer = new Dialog(requireContext(), android.R.style.Theme_Material_Light_NoActionBar);
            sectionPhotoViewer = viewer;
            LinearLayout panel = new LinearLayout(requireContext());
            panel.setOrientation(LinearLayout.VERTICAL);
            int spacing = Math.round(16 * getResources().getDisplayMetrics().density);
            panel.setPadding(spacing, spacing, spacing, spacing);
            ImageView image = new ImageView(requireContext());
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription(getString(R.string.farm_section_photo_description));
            image.setImageBitmap(bitmap);
            panel.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
            int height = Math.round(56 * getResources().getDisplayMetrics().density);
            MaterialButton change = new MaterialButton(requireContext());
            change.setText(R.string.farm_section_change_photo);
            change.setMinHeight(height);
            change.setOnClickListener(v -> {
                viewer.dismiss();
                chooseSectionPhoto(groupId);
            });
            LinearLayout.LayoutParams changeParams = new LinearLayout.LayoutParams(-1, -2);
            changeParams.topMargin = spacing;
            panel.addView(change, changeParams);
            MaterialButton close = new MaterialButton(requireContext());
            close.setText(android.R.string.cancel);
            close.setMinHeight(height);
            close.setOnClickListener(v -> viewer.dismiss());
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, -2);
            closeParams.topMargin = spacing / 2;
            panel.addView(close, closeParams);
            viewer.setContentView(panel);
            viewer.setOnDismissListener(dialog -> {
                image.setImageDrawable(null);
                if (sectionPhotoViewer == viewer) sectionPhotoViewer = null;
            });
            viewer.show();
        }, R.string.farm_section_photo_error, () -> {
            if (saved.equals(sectionPhotoUri(groupId))) chooseSectionPhoto(groupId);
        });
    }

    /** Reloads planting groups and updates their summary and empty-state actions. */
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
                        binding.summaryText.setText(rows.size() + " groups");
                        binding.readyText.setText(ready + " ready");
                        binding.messageText.setText("");
                        binding.emptyState.setVisibility(
                                rows.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        binding.emptyState.setVisibility(View.GONE);
                        binding.messageText.setText(message);
                    }
                });
    }

    /** Builds the record-planting form and adapts fields to the propagation method. */
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
        RecordDateInput.attach(dialogBinding.dateInput);
        dialog.setOnShowListener(ignored -> {
            // Keep these dialog actions flat, including the pressed elevation state.
            for (int which : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE}) {
                View button = dialog.getButton(which);
                button.setStateListAnimator(null);
                button.setElevation(0f);
                button.setTranslationZ(0f);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> create(dialog, dialogBinding));
        });
        dialog.show();
    }

    /** Validates form values and submits a new planting group. */
    private void create(AlertDialog dialog, DialogPlantingBinding form) {
        String number = text(form.recordInput.getText());
        String date = RecordDate.toIso(text(form.dateInput.getText()));
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
        if (date == null) {
            form.errorText.setText("Enter a real planting date as MM/DD/YYYY.");
            return;
        }
        if (number.isEmpty() || location.isEmpty() || plants < 1) {
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

    /** Collects and submits a grafting activity for an existing plant group. */
    private void showGraftingDialog() {
        List<PlantingGroup> seedRows = new ArrayList<>();
        for (PlantingGroup row : currentRows) {
            if (row.getPropagationMethod() == PropagationMethod.SEED) {
                seedRows.add(row);
            }
        }
        if (seedRows.isEmpty()) {
            AlertDialog notice = new AlertDialog.Builder(requireContext())
                    .setTitle("No eligible seed record")
                    .setMessage("Create a seed-grown farm record first. Record grafting only "
                            + "after its suitability has been professionally confirmed.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Record planting", (dialog, which) -> showCreateDialog())
                    .create();
            notice.setOnShowListener(ignored -> {
                View button = notice.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setStateListAnimator(null);
                button.setElevation(0f);
                button.setTranslationZ(0f);
            });
            notice.show();
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
        RecordDateInput.attach(form.dateInput);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Record Grafting")
                .setView(form.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String date = RecordDate.toIso(text(form.dateInput.getText()));
                    String variety = text(form.scionVarietyInput.getText());
                    if (date == null || variety.isEmpty()) {
                        form.errorText.setText("Enter a real grafting date (MM/DD/YYYY) and scion variety.");
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

    /** Renders stage-specific guidance and prepares optional photo attachment. */
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
        PlantGuidanceContent.bind(sheet, stage);
        sheet.sourcesToggle.setOnClickListener(v -> sheet.sourcesContainer.setVisibility(
                sheet.sourcesContainer.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE));
        sheet.closeButton.setOnClickListener(v -> dialog.dismiss());
        if (stage == PlantingStage.READY_FOR_HARVEST) {
            com.google.android.material.button.MaterialButton review =
                    new com.google.android.material.button.MaterialButton(requireContext());
            review.setText("Post-harvest guide / Gabay pagkatapos ng ani");
            ((ViewGroup) sheet.getRoot()).addView(review);
            review.setOnClickListener(v -> {
                dialog.dismiss();
                showGuidanceSheet(group, PlantingStage.HARVESTED);
            });
        }

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

    /** Persists photo access and records its URI against the selected planting stage. */
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

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override public void onDestroyView() {
        if (sectionPhotoViewer != null) sectionPhotoViewer.dismiss();
        binding = null;
        currentRows = new ArrayList<>();
        super.onDestroyView();
    }
}
