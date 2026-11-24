package ph.dragonview.mobile.ui.settings;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.work.WorkInfo;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.SessionUser;
import ph.dragonview.mobile.data.sync.SyncWorker;
import ph.dragonview.mobile.databinding.FragmentSettingsBinding;
import ph.dragonview.mobile.ui.MainActivity;

/** Shows account, workspace, synchronization, version, and sign-out information. */
public final class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private boolean syncInProgress;
    private int statusGeneration;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup parent,
            Bundle state
    ) {
        binding = FragmentSettingsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        SessionManager session = new SessionManager(requireContext());
        SessionUser user = session.getUser();
        if (user != null) {
            binding.accountNameText.setText(user.getDisplayName());
            binding.accountEmailText.setText(user.getEmail());
        }
        binding.versionText.setText(versionLabel());
        binding.workspaceNameText.setText(session.getActiveWorkspaceName().isEmpty()
                ? "Workspace & Farmers" : session.getActiveWorkspaceName());
        binding.workspaceRoleText.setText(session.isWorkspaceOwner()
                ? "Owner · manage farmers and invite code"
                : session.isWorkspaceFarmer()
                ? "Farmer · view or switch workspace"
                : "Loading workspace access…");
        binding.workspaceCard.setOnClickListener(clicked ->
                NavHostFragment.findNavController(this).navigate(R.id.workspaceFragment));
        refreshSyncStatus();
        binding.syncNowButton.setOnClickListener(clicked -> {
            statusGeneration++;
            syncInProgress = true;
            binding.syncStatusText.setText("Requesting sync…");
            SyncWorker.enqueue(requireContext());
        });
        SyncWorker.observe(requireContext()).observe(getViewLifecycleOwner(), jobs -> {
            if (binding == null) return;
            boolean running = jobs.stream().anyMatch(job -> job.getState() == WorkInfo.State.RUNNING);
            boolean pending = jobs.stream().anyMatch(job -> !job.getState().isFinished());
            if (pending) {
                statusGeneration++;
                syncInProgress = true;
                binding.syncNowButton.setEnabled(!running);
                binding.syncStatusText.setText(running ? "Uploading queued changes…"
                        : "Sync queued. Waiting for a connection or retry.");
            } else if (syncInProgress) {
                syncInProgress = false;
                binding.syncNowButton.setEnabled(true);
                refreshSyncStatus();
            }
        });
        binding.logoutButton.setOnClickListener(clicked -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).signOut();
            }
        });
    }

    /** Read the current queue and server status after WorkManager reports completion. */
    private void refreshSyncStatus() {
        int generation = ++statusGeneration;
        LocalRepository.get(requireContext()).syncStatus(new LocalRepository.Callback<>() {
            @Override public void onSuccess(String value) {
                if (binding != null && generation == statusGeneration) binding.syncStatusText.setText(value);
            }
            @Override public void onError(String message) {
                if (binding != null && generation == statusGeneration) binding.syncStatusText.setText(message);
            }
        });
    }

    /** Reads the installed version name while keeping a safe fallback for test builds. */
    private String versionLabel() {
        try {
            String version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0)
                    .versionName;
            return "Version " + version;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "Dragon View Mobile";
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
