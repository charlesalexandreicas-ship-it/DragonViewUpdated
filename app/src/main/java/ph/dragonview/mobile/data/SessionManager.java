package ph.dragonview.mobile.data;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.security.GeneralSecurityException;

import ph.dragonview.mobile.data.model.SessionUser;

public final class SessionManager {
    public static final String ACTION_SESSION_EXPIRED =
            "ph.dragonview.mobile.action.SESSION_EXPIRED";
    private static final String FILE_NAME = "dragon_view_offline_session";
    private static final String USER_ID_KEY = "user_id";
    private static final String USER_EMAIL_KEY = "user_email";
    private static final String USER_NAME_KEY = "user_name";
    private static final String ACCESS_TOKEN_KEY = "access_token";
    private static final String REFRESH_TOKEN_KEY = "refresh_token";
    private static final String EXPIRES_AT_KEY = "expires_at";
    private static final String WORKSPACE_ID_KEY = "workspace_id";
    private static final String WORKSPACE_NAME_KEY = "workspace_name";
    private static final String WORKSPACE_ROLE_KEY = "workspace_role";

    private final SharedPreferences preferences;
    private final Context applicationContext;

    public SessionManager(Context context) {
        applicationContext = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            preferences = EncryptedSharedPreferences.create(
                    applicationContext,
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

    public void saveAuthenticated(SessionUser user, String accessToken,
                                  String refreshToken, long expiresAt) {
        preferences.edit()
                .putString(USER_ID_KEY, user.getId())
                .putString(USER_EMAIL_KEY, user.getEmail())
                .putString(USER_NAME_KEY, user.getDisplayName())
                .putString(ACCESS_TOKEN_KEY, accessToken)
                .putString(REFRESH_TOKEN_KEY, refreshToken)
                .putLong(EXPIRES_AT_KEY, expiresAt)
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
        return getUser() != null && getRefreshToken() != null;
    }

    public String getAccessToken() { return preferences.getString(ACCESS_TOKEN_KEY, null); }
    public String getRefreshToken() { return preferences.getString(REFRESH_TOKEN_KEY, null); }
    public long getExpiresAt() { return preferences.getLong(EXPIRES_AT_KEY, 0L); }

    public void setActiveWorkspace(String id, String name, String role) {
        preferences.edit()
                .putString(WORKSPACE_ID_KEY, id)
                .putString(WORKSPACE_NAME_KEY, name)
                .putString(WORKSPACE_ROLE_KEY, role)
                .apply();
    }

    public void clearActiveWorkspace() {
        preferences.edit()
                .remove(WORKSPACE_ID_KEY)
                .remove(WORKSPACE_NAME_KEY)
                .remove(WORKSPACE_ROLE_KEY)
                .apply();
    }

    @NonNull public String getActiveWorkspaceId() {
        String value = preferences.getString(WORKSPACE_ID_KEY, null);
        return value == null ? "" : value;
    }
    @NonNull public String getActiveWorkspaceName() {
        String value = preferences.getString(WORKSPACE_NAME_KEY, null);
        return value == null ? "" : value;
    }
    @NonNull public String getActiveWorkspaceRole() {
        String value = preferences.getString(WORKSPACE_ROLE_KEY, null);
        return value == null ? "" : value;
    }
    public boolean isWorkspaceOwner() { return "OWNER".equals(getActiveWorkspaceRole()); }
    public boolean isWorkspaceFarmer() { return "FARMER".equals(getActiveWorkspaceRole()); }

    public void clear() {
        preferences.edit().clear().apply();
    }

    /** Clears a rejected cloud session before notifying the visible activity. */
    public void expire() {
        preferences.edit().clear().commit();
        applicationContext.sendBroadcast(new Intent(ACTION_SESSION_EXPIRED)
                .setPackage(applicationContext.getPackageName()));
    }
}
