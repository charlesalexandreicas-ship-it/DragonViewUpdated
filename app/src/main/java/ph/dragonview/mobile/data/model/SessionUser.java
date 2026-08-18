package ph.dragonview.mobile.data.model;

public final class SessionUser {
    private String id;
    private String email;
    private String displayName;

    public SessionUser(String id, String email, String displayName) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
}
