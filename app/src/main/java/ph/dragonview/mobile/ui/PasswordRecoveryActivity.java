package ph.dragonview.mobile.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.databinding.ActivityPasswordRecoveryBinding;

/** Handles a Supabase recovery link without saving its short-lived token as a login session. */
public final class PasswordRecoveryActivity extends AppCompatActivity {
    private ActivityPasswordRecoveryBinding binding;
    @Nullable private String recoveryToken;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        binding = ActivityPasswordRecoveryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        recoveryToken = recoveryToken(getIntent().getData());
        if (recoveryToken == null) {
            binding.savePasswordButton.setEnabled(false);
            showStatus(getString(R.string.recovery_invalid_link), true);
        }
        binding.savePasswordButton.setOnClickListener(view -> updatePassword());
        binding.backToSignInButton.setOnClickListener(view -> openLogin());
    }

    private void updatePassword() {
        if (recoveryToken == null) return;
        String password = String.valueOf(binding.newPasswordInput.getText());
        String confirmation = String.valueOf(binding.confirmPasswordInput.getText());
        binding.newPasswordLayout.setError(null);
        binding.confirmPasswordLayout.setError(null);
        binding.statusText.setVisibility(View.GONE);
        if (password.length() < 8) {
            binding.newPasswordLayout.setError(getString(R.string.recovery_password_short));
            return;
        }
        if (!password.equals(confirmation)) {
            binding.confirmPasswordLayout.setError(getString(R.string.recovery_password_mismatch));
            return;
        }

        setLoading(true);
        LocalRepository.get(this).updateRecoveredPassword(recoveryToken, password,
                new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        if (isFinishing() || isDestroyed()) return;
                        recoveryToken = null;
                        getIntent().setData(null);
                        binding.newPasswordInput.setText(null);
                        binding.confirmPasswordInput.setText(null);
                        setLoading(false);
                        binding.savePasswordButton.setEnabled(false);
                        showStatus(getString(R.string.recovery_success), false);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        setLoading(false);
                        showStatus(message, true);
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.savePasswordButton.setEnabled(!loading);
        binding.backToSignInButton.setEnabled(!loading);
        binding.savePasswordButton.setText(loading
                ? R.string.recovery_saving : R.string.recovery_save);
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String message, boolean error) {
        binding.statusText.setTextColor(getColor(error
                ? R.color.dragon_error : R.color.dragon_text_soft));
        binding.statusText.setText(message);
        binding.statusText.setVisibility(View.VISIBLE);
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @Nullable
    private static String recoveryToken(@Nullable Uri link) {
        if (link == null || !"ph.dragonview.mobile".equals(link.getScheme())
                || !"auth".equals(link.getHost()) || !"/recover".equals(link.getPath())) {
            return null;
        }
        String fragment = link.getEncodedFragment();
        if (fragment == null) return null;
        Uri parameters = new Uri.Builder().scheme("https").authority("recovery.invalid")
                .encodedQuery(fragment).build();
        String token = parameters.getQueryParameter("access_token");
        return "recovery".equals(parameters.getQueryParameter("type"))
                && token != null && !token.isEmpty() ? token : null;
    }
}
