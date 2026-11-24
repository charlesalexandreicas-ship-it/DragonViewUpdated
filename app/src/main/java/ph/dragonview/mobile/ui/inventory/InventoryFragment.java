package ph.dragonview.mobile.ui.inventory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.InventoryBatch;
import ph.dragonview.mobile.data.model.InventoryLot;
import ph.dragonview.mobile.databinding.FragmentInventoryBinding;
import ph.dragonview.mobile.ui.archive.ArchiveDialog;
import ph.dragonview.mobile.ui.pricing.PriceManagementDialog;

/** Lists active inventory batches and exposes harvest, pricing, and archive actions. */
public final class InventoryFragment extends Fragment {
    private FragmentInventoryBinding binding;
    private InventoryAdapter adapter;
    private int loadGeneration;
    private List<InventoryBatch> activeBatches = Collections.emptyList();
    @Nullable private List<InventoryLot> activeLots;
    private int classFilter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentInventoryBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        boolean owner = new SessionManager(requireContext()).isWorkspaceOwner();
        adapter = new InventoryAdapter(new InventoryAdapter.Listener() {
            @Override public void open(InventoryBatch batch) {
                Bundle args = new Bundle();
                args.putString("batchNumber", batch.getBatchNumber());
                NavHostFragment.findNavController(InventoryFragment.this)
                        .navigate(R.id.action_inventory_to_details, args);
            }

            @Override public void archive(InventoryBatch batch) {
                confirmArchive(batch);
            }
        }, owner);
        binding.inventoryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.inventoryList.setAdapter(adapter);
        binding.inventoryList.setNestedScrollingEnabled(false);
        String[] classes = {"All classes", "Class A", "Class B", "Class C"};
        binding.classFilter.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, classes));
        binding.classFilter.setKeyListener(null);
        binding.classFilter.setShowSoftInputOnFocus(false);
        binding.classFilter.setText(classes[classFilter], false);
        binding.classFilter.setOnItemClickListener((parentView, item, index, id) -> {
            classFilter = index;
            renderBatches();
        });
        binding.inventoryToggle.setOnClickListener(v -> {
            boolean opening = binding.summaryText.getVisibility() != View.VISIBLE;
            binding.summaryText.setVisibility(opening ? View.VISIBLE : View.GONE);
            binding.inventoryToggle.setText(opening
                    ? "Hide total inventory  ▴" : "View total inventory  ▾");
        });
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.recordHarvestButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_inventory_to_harvest));
        binding.emptyActionButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_inventory_to_harvest));
        binding.editPricesButton.setOnClickListener(v ->
                PriceManagementDialog.show(this, new PriceManagementDialog.Listener() {
                    @Override public void onUpdated() {
                        if (binding != null) {
                            com.google.android.material.snackbar.Snackbar.make(
                                    binding.getRoot(), "Prices updated.",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                    .show();
                        }
                    }

                    @Override public void onError(String message) {
                        if (binding != null) {
                            com.google.android.material.snackbar.Snackbar.make(
                                    binding.getRoot(), message,
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                    .show();
                        }
                    }
                }));
        binding.editPricesButton.setVisibility(owner ? View.VISIBLE : View.GONE);
        getParentFragmentManager().setFragmentResultListener(
                HarvestFragment.HARVEST_SAVED_RESULT,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String batchNumber = result.getString(
                            HarvestFragment.HARVEST_BATCH_NUMBER, "");
                    Snackbar.make(binding.getRoot(),
                            batchNumber.isEmpty()
                                    ? "Harvest saved to inventory."
                                    : "Batch #" + batchNumber + " saved to inventory.",
                            Snackbar.LENGTH_LONG).show();
                    load();
                });
        load();
    }

    /** Reloads batch data and switches between loading, empty, error, and list states. */
    private void load() {
        // Ignore older async responses after a pull-to-refresh starts a newer inventory load.
        final int generation = ++loadGeneration;
        binding.swipeRefresh.setRefreshing(true);
        binding.emptyState.setVisibility(View.GONE);
        LocalRepository.get(requireContext()).inventoryBatches(
                new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(List<InventoryBatch> batches) {
                        if (binding == null || generation != loadGeneration) return;
                        binding.swipeRefresh.setRefreshing(false);
                        activeBatches = batches;
                        renderBatches();
                        int pieces = 0;
                        for (InventoryBatch batch : batches) {
                            pieces += batch.getAvailablePieces();
                        }
                        binding.summaryText.setText(batches.size() + " active batches • "
                                + pieces + " available pieces");
                        loadSizeGradeSummary(batches.size(), pieces, generation);
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null || generation != loadGeneration) return;
                        binding.swipeRefresh.setRefreshing(false);
                        showError(message);
                    }
                });
    }

    /** Adds the actual available-piece breakdown beneath the collapsed inventory total. */
    private void loadSizeGradeSummary(int batches, int pieces, int generation) {
        LocalRepository.get(requireContext()).inventory(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<InventoryLot> lots) {
                if (binding == null || generation != loadGeneration) return;
                // The same available-stock rows power both the expanded total and each batch card.
                activeLots = lots;
                renderBatches();
                Map<String, Integer> bySizeGrade = new TreeMap<>();
                for (InventoryLot lot : lots) {
                    if (lot.getAvailablePieces() <= 0) continue;
                    String size = lot.getSize() == null || lot.getSize().isEmpty()
                            ? "Unknown" : lot.displaySize();
                    String grade = lot.getGrade() == null ? "?"
                            : lot.getGrade().trim().toUpperCase(Locale.US);
                    String key = size + " • Grade " + grade;
                    bySizeGrade.merge(key, lot.getAvailablePieces(), Integer::sum);
                }
                StringBuilder details = new StringBuilder(batches + " active batches • "
                        + pieces + " available pieces");
                for (Map.Entry<String, Integer> row : bySizeGrade.entrySet()) {
                    details.append("\n").append(row.getKey()).append(" — ")
                            .append(row.getValue()).append(" pcs");
                }
                binding.summaryText.setText(details.toString());
            }
            @Override public void onError(String message) {
                if (binding == null || generation != loadGeneration) return;
                notifyError("Class details could not be refreshed. Try again.", InventoryFragment.this::load);
            }
        });
    }

    /** Show only batches with available fruit in the selected class. Batch totals still cover the whole batch. */
    private void renderBatches() {
        List<InventoryBatch> shown = activeBatches;
        if (classFilter > 0) {
            shown = new ArrayList<>();
            String selected = String.valueOf((char) ('A' + classFilter - 1));
            Set<String> matches = new HashSet<>();
            if (activeLots != null) for (InventoryLot lot : activeLots) {
                if (lot.getAvailablePieces() > 0 && selected.equalsIgnoreCase(lot.getGrade())) {
                    matches.add(lot.getBatchNumber());
                }
            }
            for (InventoryBatch batch : activeBatches) {
                if (matches.contains(batch.getBatchNumber())) shown.add(batch);
            }
        }
        adapter.submit(shown);
        if (activeLots != null) adapter.submitComposition(activeLots);
        binding.availableCountText.setText(shown.size() + " available");
        binding.emptyState.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        binding.emptyText.setText(classFilter == 0 ? "No inventory yet."
                : activeLots == null ? "Class details are not available yet."
                : "No available batches match this class.");
        binding.emptyActionButton.setVisibility(classFilter == 0 ? View.VISIBLE : View.GONE);
    }

    private void notifyError(String message, @Nullable Runnable retry) {
        Snackbar bar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        if (retry != null) bar.setAction("Retry", view -> retry.run());
        bar.show();
    }

    private void showError(String message) {
        activeBatches = Collections.emptyList();
        activeLots = null;
        adapter.submit(Collections.emptyList());
        binding.availableCountText.setText("");
        binding.emptyText.setText(message == null || message.trim().isEmpty()
                ? "Inventory could not be loaded from this device."
                : message);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.emptyActionButton.setVisibility(View.GONE);
        notifyError("Inventory could not be loaded.", this::load);
    }

    /** Requests an archive reason before removing a batch from the active list. */
    private void confirmArchive(InventoryBatch batch) {
        ArchiveDialog.show(this, "Remove Batch #" + batch.getBatchNumber() + "?",
                "The batch and its transaction history will move to Recently Removed. "
                        + "Its remaining " + batch.getAvailablePieces()
                        + " pieces will be unavailable for sales until restored.",
                reason -> LocalRepository.get(requireContext()).archiveInventoryBatch(
                        batch.getBatchNumber(), reason,
                        new LocalRepository.Callback<>() {
                            @Override public void onSuccess(Void ignored) { load(); }
                            @Override public void onError(String message) {
                                if (binding != null) notifyError(
                                        "Could not remove the batch. " + message, null);
                            }
                        }));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
