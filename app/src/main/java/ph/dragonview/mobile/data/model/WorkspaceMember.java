package ph.dragonview.mobile.data.model;

import androidx.annotation.NonNull;

public final class WorkspaceMember {
    private String userId;
    private String displayName;
    private String email;
    private String role;

    @NonNull public String getUserId() { return userId == null ? "" : userId; }
    @NonNull public String getDisplayName() { return displayName == null ? "Farm user" : displayName; }
    @NonNull public String getEmail() { return email == null ? "" : email; }
    @NonNull public String getRole() { return role == null ? "FARMER" : role; }
    public boolean isOwner() { return "OWNER".equals(getRole()); }
}
