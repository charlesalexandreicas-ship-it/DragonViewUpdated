package ph.dragonview.mobile.data.remote;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import ph.dragonview.mobile.BuildConfig;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.model.SessionUser;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Synchronous gateway around DragonView's Supabase authentication and RPC API.
 *
 * <p>Learning note: {@code LocalRepository} runs these blocking calls on its worker
 * executor, so Activities and Fragments never perform network I/O directly.</p>
 */
public final class SupabaseClient {
    public static final String PASSWORD_RECOVERY_REDIRECT = "ph.dragonview.mobile://auth/recover";
    public static final class AuthResult {
        public final SessionUser user;
        public final String accessToken;
        public final String refreshToken;
        public final long expiresAt;

        AuthResult(SessionUser user, String accessToken, String refreshToken, long expiresAt) {
            this.user = user;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }
    }

    private final String apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim();
    private final SessionManager session;
    private final SupabaseApi api;
    private final Gson gson = new Gson();

    /** Builds the HTTP client and reads the encrypted session used for authorization. */
    public SupabaseClient(Context context) {
        session = new SessionManager(context.getApplicationContext());
        String baseUrl = BuildConfig.SUPABASE_URL.trim();
        if (!baseUrl.isEmpty() && !baseUrl.endsWith("/")) baseUrl += "/";

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
        api = baseUrl.isEmpty() ? null : new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(SupabaseApi.class);
    }

    /** Checks that the build supplied both a Supabase URL and publishable key. */
    public boolean isConfigured() {
        return api != null && !apiKey.isEmpty();
    }

    /** Creates an account and converts the response into DragonView session data. */
    public AuthResult signUp(String displayName, String email, String password) throws SupabaseException {
        requireConfigured();
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("display_name", displayName);
        body.add("data", metadata);
        return parseAuth(execute(api.signUp(apiKey, body), false), displayName);
    }

    /** Exchanges credentials for the current user and cloud tokens. */
    public AuthResult signIn(String email, String password) throws SupabaseException {
        requireConfigured();
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        return parseAuth(execute(api.token(apiKey, "password", body), false), null);
    }

    public void requestPasswordReset(String email) throws SupabaseException {
        requireConfigured();
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        execute(api.recover(apiKey, PASSWORD_RECOVERY_REDIRECT, body), false);
    }

    public void updateRecoveredPassword(String recoveryAccessToken, String password)
            throws SupabaseException {
        requireConfigured();
        JsonObject body = new JsonObject();
        body.addProperty("password", password);
        execute(api.updatePassword(apiKey, bearer(recoveryAccessToken), body), false);
    }

    /** Invokes a read-only database function with a valid bearer token. */
    public JsonElement read(String functionName, Map<String, Object> arguments)
            throws SupabaseException {
        return executeAuthenticated(functionName, arguments);
    }

    /** Sends a mutation with an operation ID that makes retries idempotent. */
    public JsonElement mutate(String functionName, JsonElement payload, String operationId)
            throws SupabaseException {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("p_operation_id", operationId);
        arguments.put("p_payload", payload);
        return executeAuthenticated(functionName, arguments);
    }

    public void logout() {
        logout(session.getAccessToken());
    }

    public void logout(String accessToken) {
        if (!isConfigured() || accessToken == null) return;
        try { api.logout(apiKey, bearer(accessToken)).execute(); }
        catch (IOException ignored) { }
    }

    /** Refreshes authentication when needed, then retries one unauthorized RPC once. */
    private JsonElement executeAuthenticated(String functionName, Map<String, Object> arguments)
            throws SupabaseException {
        requireConfigured();
        ensureFreshSession();
        Call<JsonElement> call = api.rpc(functionName, apiKey,
                bearer(Objects.requireNonNull(session.getAccessToken())), arguments);
        Response<JsonElement> response;
        try { response = call.execute(); }
        catch (IOException error) { throw new SupabaseException("DragonView cannot reach the cloud service.", true, error); }
        if (response.code() == 401) {
            refreshSession();
            call = api.rpc(functionName, apiKey,
                    bearer(Objects.requireNonNull(session.getAccessToken())), arguments);
            return execute(call, true);
        }
        return bodyOrThrow(response);
    }

    /** Refreshes shortly before expiry so a request does not begin with a stale token. */
    private void ensureFreshSession() throws SupabaseException {
        if (session.getAccessToken() == null || session.getExpiresAt() <= System.currentTimeMillis() + 60_000L) {
            refreshSession();
        }
    }

    /** Serializes token refresh so simultaneous requests cannot overwrite newer tokens. */
    private synchronized void refreshSession() throws SupabaseException {
        String refreshToken = session.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            session.expire();
            throw new SupabaseException("Your session expired. Sign in again.", false);
        }
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);
        AuthResult result;
        try {
            result = parseAuth(execute(api.token(apiKey, "refresh_token", body), false), null);
        } catch (SupabaseException error) {
            if (error.getStatusCode() == 400 || error.getStatusCode() == 401) {
                session.expire();
                throw new SupabaseException("Your session expired. Sign in again.",
                        false, error.getStatusCode());
            }
            throw error;
        }
        session.saveAuthenticated(result.user, result.accessToken, result.refreshToken, result.expiresAt);
    }

    private AuthResult parseAuth(JsonObject root, String fallbackName) throws SupabaseException {
        JsonObject sessionObject = root.has("session") && root.get("session").isJsonObject()
                ? root.getAsJsonObject("session") : root;
        JsonObject user = root.has("user") && root.get("user").isJsonObject()
                ? root.getAsJsonObject("user")
                : sessionObject.has("user") && sessionObject.get("user").isJsonObject()
                ? sessionObject.getAsJsonObject("user") : null;
        if (user == null || !sessionObject.has("access_token")) {
            throw new SupabaseException("Check your email to confirm the account, then sign in.", false);
        }
        String id = string(user, "id");
        String email = string(user, "email");
        String name = fallbackName;
        if (user.has("user_metadata") && user.get("user_metadata").isJsonObject()) {
            String metadataName = string(user.getAsJsonObject("user_metadata"), "display_name");
            if (!metadataName.isEmpty()) name = metadataName;
        }
        if (name == null || name.trim().isEmpty()) name = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        long expiresIn = sessionObject.has("expires_in") ? sessionObject.get("expires_in").getAsLong() : 3600L;
        return new AuthResult(new SessionUser(id, email, name), string(sessionObject, "access_token"),
                string(sessionObject, "refresh_token"), System.currentTimeMillis() + expiresIn * 1000L);
    }

    private <T> T execute(Call<T> call, boolean authenticated) throws SupabaseException {
        try { return bodyOrThrow(call.execute()); }
        catch (IOException error) { throw new SupabaseException("DragonView cannot reach the cloud service.", true, error); }
    }

    /** Returns a successful response body or translates HTTP failure into a domain error. */
    private <T> T bodyOrThrow(Response<T> response) throws SupabaseException {
        if (response.isSuccessful() && response.body() != null) return response.body();
        String message = "Cloud request failed (" + response.code() + ").";
        if (response.errorBody() != null) {
            try {
                JsonObject error = gson.fromJson(response.errorBody().string(), JsonObject.class);
                if (error != null) {
                    String detail = string(error, "message");
                    if (detail.isEmpty()) detail = string(error, "error_description");
                    if (!detail.isEmpty()) message = detail;
                }
            } catch (Exception ignored) { }
        }
        boolean retryable = response.code() == 408 || response.code() == 429 || response.code() >= 500;
        throw new SupabaseException(message, retryable, response.code());
    }

    private void requireConfigured() throws SupabaseException {
        if (!isConfigured()) {
            throw new SupabaseException("Supabase is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties.", false);
        }
    }

    private static String bearer(@NonNull String token) { return "Bearer " + token; }
    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
