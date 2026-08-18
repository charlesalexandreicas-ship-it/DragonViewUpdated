package ph.dragonview.mobile.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

import ph.dragonview.mobile.data.model.SessionUser;

public final class SessionManager {
    private static final String FILE_NAME = "dragon_view_offline_session";
    private static final String USER_ID_KEY = "user_id";
    private static final String USER_EMAIL_KEY = "user_email";
    private static final String USER_NAME_KEY = "user_name";

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            preferences = EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException error) {
            throw new IllegalStateException("Secure session storage is unavailable.", error);
        }
    }

    public void save(SessionUser user) {
        preferences.edit()
                .putString(USER_ID_KEY, user.getId())
                .putString(USER_EMAIL_KEY, user.getEmail())
                .putString(USER_NAME_KEY, user.getDisplayName())
                .apply();
    }

    public SessionUser getUser() {
        String id = preferences.getString(USER_ID_KEY, null);
        String email = preferences.getString(USER_EMAIL_KEY, null);
        String name = preferences.getString(USER_NAME_KEY, null);
        if (id == null || email == null || name == null) return null;
        return new SessionUser(id, email, name);
    }

    public boolean isAuthenticated() {
        return getUser() != null;
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
