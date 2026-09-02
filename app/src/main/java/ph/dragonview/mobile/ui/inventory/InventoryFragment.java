package ph.dragonview.mobile.ui.inventory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.Collections;
import java.util.List;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.InventoryBatch;
import ph.dragonview.mobile.databinding.FragmentInventoryBinding;
import ph.dragonview.mobile.ui.archive.ArchiveDialog;
import ph.dragonview.mobile.ui.pricing.PriceManagementDialog;

public final class InventoryFragment extends Fragment {
    private FragmentInventoryBinding binding;
    private InventoryAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentInventoryBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
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
        });
        binding.inventoryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.inventoryList.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.recordHarvestButton.setOnClickListener(v ->
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
        load();
    }

    private void load() {
        binding.swipeRefresh.setRefreshing(true);
        binding.emptyText.setVisibility(View.GONE);
        LocalRepository.get(requireContext()).inventoryBatches(
                new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(List<InventoryBatch> batches) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        adapter.submit(batches);
                        int pieces = 0;
                        for (InventoryBatch batch : batches) {
                            pieces += batch.getAvailablePieces();
                        }
                        binding.summaryText.setText(batches.size() + " active batches • "
                                + pieces + " available pieces");
                        binding.emptyText.setVisibility(
                                batches.isEmpty() ? View.VISIBLE : View.GONE);
                        binding.emptyText.setText("No active inventory batches.");
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        showError();
                    }
                });
    }

    private void showError() {
        adapter.submit(Collections.emptyList());
        binding.emptyText.setText("Inventory could not be loaded from this device.");
        binding.emptyText.setVisibility(View.VISIBLE);
    }

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
                                if (binding != null) binding.emptyText.setText(message);
                            }
                        }));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
