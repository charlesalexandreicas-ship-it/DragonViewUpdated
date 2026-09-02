package ph.dragonview.mobile.ui.archive;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import ph.dragonview.mobile.databinding.DialogArchiveRecordBinding;

public final class ArchiveDialog {
    public interface ConfirmListener { void confirm(String reason); }

    private ArchiveDialog() { }

    public static void show(Fragment fragment, String title,
                            String explanation, ConfirmListener listener) {
        DialogArchiveRecordBinding form = DialogArchiveRecordBinding.inflate(
                fragment.getLayoutInflater());
        form.explanationText.setText(explanation);
        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(title)
                .setView(form.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String reason = form.reasonInput.getText() == null ? ""
                            : form.reasonInput.getText().toString().trim();
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    dialog.dismiss();
                    listener.confirm(reason);
                }));
        dialog.show();
    }
}
