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

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.SessionUser;
import ph.dragonview.mobile.databinding.FragmentSettingsBinding;
import ph.dragonview.mobile.ui.MainActivity;

/** Shows account, workspace, synchronization, version, and sign-out information. */
public final class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;

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
        LocalRepository.get(requireContext()).syncStatus(new LocalRepository.Callback<>() {
            @Override public void onSuccess(String value) {
                if (binding != null) binding.syncStatusText.setText(value);
            }
            @Override public void onError(String message) {
                if (binding != null) binding.syncStatusText.setText(message);
            }
        });
        binding.logoutButton.setOnClickListener(clicked -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).signOut();
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
