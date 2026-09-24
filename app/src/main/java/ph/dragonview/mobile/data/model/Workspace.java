package ph.dragonview.mobile.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Workspace {
    private String id;
    private String name;
    private String role;
    private boolean active;
    private int memberCount;
    private String inviteCode;

    public Workspace(String id, String name, String role, boolean active,
                     int memberCount, @Nullable String inviteCode) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.active = active;
        this.memberCount = memberCount;
        this.inviteCode = inviteCode;
    }

    @NonNull public String getId() { return id == null ? "" : id; }
    @NonNull public String getName() { return name == null ? "Workspace" : name; }
    @NonNull public String getRole() { return role == null ? "FARMER" : role; }
    public boolean isActive() { return active; }
    public int getMemberCount() { return memberCount; }
    @Nullable public String getInviteCode() { return inviteCode; }
    public boolean isOwner() { return "OWNER".equals(getRole()); }
}
