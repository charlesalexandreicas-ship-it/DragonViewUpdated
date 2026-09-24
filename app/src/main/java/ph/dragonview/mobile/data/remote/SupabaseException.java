package ph.dragonview.mobile.data.remote;

public final class SupabaseException extends Exception {
    private final boolean retryable;
    private final int statusCode;

    public SupabaseException(String message, boolean retryable) {
        this(message, retryable, -1, null);
    }

    public SupabaseException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, -1, cause);
    }

    public SupabaseException(String message, boolean retryable, int statusCode) {
        this(message, retryable, statusCode, null);
    }

    private SupabaseException(String message, boolean retryable, int statusCode,
                              Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.statusCode = statusCode;
    }

    public boolean isRetryable() { return retryable; }
    public int getStatusCode() { return statusCode; }
}
