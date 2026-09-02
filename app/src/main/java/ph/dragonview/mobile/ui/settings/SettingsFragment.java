package ph.dragonview.mobile.ui.settings;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.model.SessionUser;
import ph.dragonview.mobile.databinding.FragmentSettingsBinding;
import ph.dragonview.mobile.ui.MainActivity;

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
        SessionUser user = new SessionManager(requireContext()).getUser();
        if (user != null) {
            binding.accountNameText.setText(user.getDisplayName());
            binding.accountEmailText.setText(user.getEmail());
        }
        binding.versionText.setText(versionLabel());
        binding.logoutButton.setOnClickListener(clicked -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).signOut();
            }
        });
    }

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
