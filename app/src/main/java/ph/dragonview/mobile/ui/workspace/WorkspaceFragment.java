package ph.dragonview.mobile.ui.workspace;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.Workspace;
import ph.dragonview.mobile.data.model.WorkspaceMember;
import ph.dragonview.mobile.databinding.FragmentWorkspaceBinding;

public final class WorkspaceFragment extends Fragment {
    private FragmentWorkspaceBinding binding;
    private LocalRepository repository;
    private SessionManager session;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentWorkspaceBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repository = LocalRepository.get(requireContext());
        session = new SessionManager(requireContext());
        binding.createWorkspaceButton.setOnClickListener(v -> textDialog(
                "Create workspace", "Workspace name", false,
                value -> repository.createWorkspace(value, reloadAfterMutation())));
        binding.joinWorkspaceButton.setOnClickListener(v -> textDialog(
                "Join workspace", "8-character invite code", true,
                value -> repository.joinWorkspace(value, reloadAfterMutation())));
        binding.regenerateInviteButton.setOnClickListener(v -> repository.regenerateWorkspaceInvite(
                new LocalRepository.Callback<>() {
                    @Override public void onSuccess(String code) { showInvite(code); }
                    @Override public void onError(String message) { showError(message); }
                }));
        binding.inviteCodeText.setOnClickListener(v -> copyInvite());
        binding.leaveWorkspaceButton.setOnClickListener(v -> confirmLeave());
        binding.deleteWorkspaceButton.setOnClickListener(v -> confirmDelete());
        load();
    }

    private void load() {
        setLoading(true);
        binding.workspaceNameText.setText(value(session.getActiveWorkspaceName(), "Workspace"));
        binding.workspaceRoleText.setText(roleLabel(session.getActiveWorkspaceRole()));
        repository.workspaces(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<Workspace> workspaces) {
                if (binding == null) return;
                Workspace active = renderWorkspaces(
                        workspaces == null ? Collections.emptyList() : workspaces);
                if (active == null) {
                    renderMembers(Collections.emptyList());
                    binding.messageText.setText("Create or join a workspace to continue.");
                    setLoading(false);
                } else loadMembers();
            }
            @Override public void onError(String message) { if (binding != null) { setLoading(false); showError(message); } }
        });
    }

    private void loadMembers() {
        repository.workspaceMembers(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<WorkspaceMember> members) {
                if (binding == null) return;
                renderMembers(members == null ? Collections.emptyList() : members);
                setLoading(false);
            }
            @Override public void onError(String message) { if (binding != null) { setLoading(false); showError(message); } }
        });
    }

    @Nullable private Workspace renderWorkspaces(List<Workspace> workspaces) {
        binding.workspaceListContainer.removeAllViews();
        Workspace active = null;
        for (Workspace workspace : workspaces) {
            MaterialButton button = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(workspace.getName() + "  ·  " + roleLabel(workspace.getRole()));
            button.setAllCaps(false);
            button.setEnabled(!workspace.isActive());
            button.setContentDescription((workspace.isActive() ? "Active workspace " : "Switch to workspace ") + workspace.getName());
            button.setOnClickListener(v -> repository.selectWorkspace(workspace, reloadAfterMutation()));
            binding.workspaceListContainer.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (workspace.isActive()) active = workspace;
        }
        boolean owner = active != null && active.isOwner();
        binding.invitePanel.setVisibility(owner ? View.VISIBLE : View.GONE);
        binding.deleteWorkspaceButton.setVisibility(owner ? View.VISIBLE : View.GONE);
        binding.leaveWorkspaceButton.setVisibility(active != null && !owner ? View.VISIBLE : View.GONE);
        if (active != null) {
            session.setActiveWorkspace(active.getId(), active.getName(), active.getRole());
            binding.workspaceNameText.setText(active.getName());
            binding.workspaceRoleText.setText(roleLabel(active.getRole()));
            if (owner) showInvite(active.getInviteCode());
        } else {
            session.clearActiveWorkspace();
            binding.workspaceNameText.setText("No active workspace");
            binding.workspaceRoleText.setText("Create or join one below");
        }
        return active;
    }

    private void renderMembers(List<WorkspaceMember> members) {
        binding.membersContainer.removeAllViews();
        boolean owner = session.isWorkspaceOwner();
        for (WorkspaceMember member : members) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(16, 12, 8, 12);
            TextView text = new TextView(requireContext());
            text.setText(member.getDisplayName() + "\n" + member.getEmail() + " · " + roleLabel(member.getRole()));
            text.setTextColor(requireContext().getColor(R.color.ink));
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (owner && !member.isOwner()) {
                MaterialButton remove = new MaterialButton(requireContext());
                remove.setText("Remove");
                remove.setAllCaps(false);
                remove.setOnClickListener(v -> confirmRemove(member));
                row.addView(remove);
            }
            binding.membersContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void confirmRemove(WorkspaceMember member) {
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Remove farmer?")
                .setMessage(member.getDisplayName() + " will lose access to this workspace.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialogInterface, which) -> repository.removeWorkspaceMember(
                        member.getUserId(), reloadAfterMutation())).create();
        showFlat(dialog);
    }

    private void confirmLeave() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Leave workspace?")
                .setMessage("You will lose access unless the owner shares a new invite code.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Leave", (dialogInterface, which) ->
                        repository.leaveWorkspace(reloadAfterMutation())).create();
        showFlat(dialog);
    }

    private void confirmDelete() {
        String workspaceName = value(session.getActiveWorkspaceName(), "this workspace");
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Delete workspace?")
                .setMessage(workspaceName + " will disappear for every member. Its farm records will remain stored for recovery.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialogInterface, which) ->
                        repository.deleteWorkspace(reloadAfterMutation()))
                .create();
        showFlat(dialog);
    }

    private LocalRepository.Callback<Void> reloadAfterMutation() {
        setLoading(true);
        return new LocalRepository.Callback<>() {
            @Override public void onSuccess(Void ignored) { if (binding != null) requireActivity().recreate(); }
            @Override public void onError(String message) { if (binding != null) { setLoading(false); showError(message); } }
        };
    }

    private void textDialog(String title, String hint, boolean invite, TextSubmission submission) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setPadding(48, 16, 48, 16);
        if (invite) {
            input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(8)});
        } else input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle(title)
                .setView(ph.dragonview.mobile.ui.ScrollableForm.wrap(input))
                .setNegativeButton("Cancel", null).setPositiveButton(invite ? "Join" : "Create", null).create();
        dialog.setOnShowListener(ignored -> {
            flattenButton(dialog, AlertDialog.BUTTON_NEGATIVE);
            flattenButton(dialog, AlertDialog.BUTTON_POSITIVE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                if ((!invite && value.length() < 2) || (invite && value.length() != 8)) {
                    input.setError(invite ? "Enter the complete 8-character code." : "Enter at least 2 characters.");
                    return;
                }
                dialog.dismiss(); submission.submit(value);
            });
        });
        dialog.show();
    }

    private static void showFlat(AlertDialog dialog) {
        dialog.setOnShowListener(ignored -> {
            flattenButton(dialog, AlertDialog.BUTTON_NEGATIVE);
            flattenButton(dialog, AlertDialog.BUTTON_POSITIVE);
        });
        dialog.show();
    }

    private static void flattenButton(AlertDialog dialog, int which) {
        View button = dialog.getButton(which);
        if (button == null) return;
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setTranslationZ(0f);
    }

    private void showInvite(@Nullable String code) {
        binding.inviteCodeText.setText(value(code, "--------"));
    }

    private void copyInvite() {
        String code = binding.inviteCodeText.getText().toString();
        if (code.contains("-")) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("DragonView farmer invite", code));
        Snackbar.make(binding.getRoot(), "Invite code copied.", Snackbar.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.createWorkspaceButton.setEnabled(!loading);
        binding.joinWorkspaceButton.setEnabled(!loading);
        binding.deleteWorkspaceButton.setEnabled(!loading);
        if (loading) binding.messageText.setText("");
    }

    private void showError(String message) { binding.messageText.setText(message); }
    private static String roleLabel(String role) {
        return "OWNER".equals(role) ? "Owner" : "Farmer";
    }
    private static String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
    private interface TextSubmission { void submit(String value); }
    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }
}
