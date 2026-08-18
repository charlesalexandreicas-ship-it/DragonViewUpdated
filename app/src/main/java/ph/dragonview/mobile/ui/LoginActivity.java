package ph.dragonview.mobile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.SessionUser;
import ph.dragonview.mobile.databinding.ActivityLoginBinding;

public final class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;
    private SessionManager sessionManager;
    private boolean registrationMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = new SessionManager(this);
        if (sessionManager.isAuthenticated()) {
            openApplication();
            return;
        }
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.signInButton.setOnClickListener(view -> submit());
        binding.modeSwitch.setOnClickListener(
                view -> setRegistrationMode(!registrationMode));
    }

    private void submit() {
        String displayName = text(binding.displayNameInput.getText());
        String email = text(binding.emailInput.getText());
        String password = text(binding.passwordInput.getText());
        String confirmation = text(binding.confirmPasswordInput.getText());
        clearErrors();
        if (registrationMode && displayName.length() < 2) {
            binding.displayNameLayout.setError("Enter your full name.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.setError("Enter a valid email address.");
            return;
        }
        if (password.length() < 8) {
            binding.passwordLayout.setError(
                    "Password must contain at least 8 characters.");
            return;
        }
        if (registrationMode && !password.equals(confirmation)) {
            binding.confirmPasswordLayout.setError("Passwords do not match.");
            return;
        }

        setLoading(true);
        LocalRepository.Callback<SessionUser> callback =
                new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(SessionUser user) {
                        setLoading(false);
                        sessionManager.save(user);
                        openApplication();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showError(message);
                    }
                };
        if (registrationMode) {
            LocalRepository.get(this).register(
                    displayName, email, password, callback);
        } else {
            LocalRepository.get(this).login(email, password, callback);
        }
    }

    private void setRegistrationMode(boolean enabled) {
        registrationMode = enabled;
        binding.displayNameLayout.setVisibility(
                enabled ? View.VISIBLE : View.GONE);
        binding.confirmPasswordLayout.setVisibility(
                enabled ? View.VISIBLE : View.GONE);
        binding.signInButton.setText(
                enabled ? R.string.create_account : R.string.sign_in);
        binding.modeSwitch.setText(enabled
                ? R.string.existing_account_prompt
                : R.string.new_account_prompt);
        clearErrors();
    }

    private void clearErrors() {
        binding.displayNameLayout.setError(null);
        binding.emailLayout.setError(null);
        binding.passwordLayout.setError(null);
        binding.confirmPasswordLayout.setError(null);
        binding.errorText.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        binding.signInButton.setEnabled(!loading);
        binding.modeSwitch.setEnabled(!loading);
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            binding.signInButton.setText(
                    registrationMode ? "Creating local account…" : "Signing in…");
        } else {
            binding.signInButton.setText(
                    registrationMode ? R.string.create_account : R.string.sign_in);
        }
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private void openApplication() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
