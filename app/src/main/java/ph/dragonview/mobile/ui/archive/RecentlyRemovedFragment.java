package ph.dragonview.mobile.ui.archive;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.ArchivedRecord;
import ph.dragonview.mobile.databinding.FragmentRecentlyRemovedBinding;

public final class RecentlyRemovedFragment extends Fragment {
    private FragmentRecentlyRemovedBinding binding;
    private ArchivedRecordAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup parent, Bundle state) {
        binding = FragmentRecentlyRemovedBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter = new ArchivedRecordAdapter(this::confirmRestore);
        binding.archiveList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.archiveList.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        load();
    }

    private void load() {
        binding.swipeRefresh.setRefreshing(true);
        LocalRepository.get(requireContext()).archivedRecords(
                new LocalRepository.Callback<>() {
                    @Override public void onSuccess(List<ArchivedRecord> records) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        adapter.submit(records);
                        binding.messageText.setText(records.isEmpty()
                                ? "No removed records." : records.size()
                                + (records.size() == 1
                                ? " recoverable record" : " recoverable records"));
                    }

                    @Override public void onError(String message) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        binding.messageText.setText(message);
                    }
                });
    }

    private void confirmRestore(ArchivedRecord record) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Restore " + record.getTitle() + "?")
                .setMessage("The record and its existing history will return to its active list.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (dialog, which) ->
                        LocalRepository.get(requireContext()).restoreArchivedRecord(
                                record.getType(), record.getKey(),
                                new LocalRepository.Callback<>() {
                                    @Override public void onSuccess(Void ignored) { load(); }
                                    @Override public void onError(String message) {
                                        if (binding != null) binding.messageText.setText(message);
                                    }
                                }))
                .show();
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
