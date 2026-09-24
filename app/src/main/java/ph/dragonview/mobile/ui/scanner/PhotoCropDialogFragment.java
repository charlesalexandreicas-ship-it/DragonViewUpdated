package ph.dragonview.mobile.ui.scanner;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ph.dragonview.mobile.R;

/** Lets the user select the part of an uploaded photo sent to the offline scanner. */
public final class PhotoCropDialogFragment extends DialogFragment {
    static final String TAG = "photo_crop";
    static final String RESULT_KEY = "scanner_crop_result";
    static final String RESULT_PATH = "scanner_crop_path";
    private static final String PHOTO_URI = "photo_uri";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable private Bitmap photo;
    private boolean active = true;

    @NonNull
    static PhotoCropDialogFragment newInstance(@NonNull Uri uri) {
        PhotoCropDialogFragment fragment = new PhotoCropDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putString(PHOTO_URI, uri.toString());
        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_scanner_crop, null);
        PhotoCropView cropView = content.findViewById(R.id.scannerCropView);
        ProgressBar progress = content.findViewById(R.id.scannerCropProgress);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.scanner_crop_title)
                .setView(content)
                .setNegativeButton(R.string.scanner_crop_cancel, (ignored, which) -> {})
                .setPositiveButton(R.string.scanner_crop_scan, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(photo != null);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    button -> saveSelection(cropView, progress, dialog));
        });

        String uriText = requireArguments().getString(PHOTO_URI);
        if (uriText == null) throw new IllegalArgumentException("Missing uploaded photo");
        Uri uri = Uri.parse(uriText);
        android.content.Context app = requireContext().getApplicationContext();
        worker.execute(() -> {
            try {
                Bitmap loaded = ScannerImageLoader.load(app, uri, 1600);
                mainHandler.post(() -> {
                    if (!active || !isAdded()) return;
                    photo = loaded;
                    cropView.setPhoto(loaded);
                    progress.setVisibility(View.GONE);
                    if (dialog.isShowing()) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    }
                });
            } catch (IOException | RuntimeException error) {
                mainHandler.post(() -> {
                    if (!active || !isAdded()) return;
                    Toast.makeText(requireContext(), R.string.scanner_image_error, Toast.LENGTH_LONG).show();
                    dismissAllowingStateLoss();
                });
            }
        });
        return dialog;
    }

    private void saveSelection(PhotoCropView cropView, ProgressBar progress, AlertDialog dialog) {
        Bitmap source = photo;
        if (source == null) return;
        Rect bounds = cropView.cropBounds();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        android.content.Context app = requireContext().getApplicationContext();
        worker.execute(() -> {
            File cropFile = null;
            try {
                Bitmap crop = Bitmap.createBitmap(source, bounds.left, bounds.top,
                        bounds.width(), bounds.height());
                cropFile = File.createTempFile("scanner_crop_", ".jpg", app.getCacheDir());
                try (FileOutputStream output = new FileOutputStream(cropFile)) {
                    if (!crop.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                        throw new IOException("Could not save selected region");
                    }
                }
                if (crop != source) crop.recycle();
                File readyFile = cropFile;
                mainHandler.post(() -> {
                    if (!active || !isAdded()) {
                        readyFile.delete();
                        return;
                    }
                    Bundle result = new Bundle();
                    result.putString(RESULT_PATH, readyFile.getAbsolutePath());
                    getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
                    dismissAllowingStateLoss();
                });
            } catch (IOException | RuntimeException error) {
                if (cropFile != null) cropFile.delete();
                mainHandler.post(() -> {
                    if (!active || !isAdded()) return;
                    progress.setVisibility(View.GONE);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                    Toast.makeText(requireContext(), R.string.scanner_crop_error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        active = false;
        worker.shutdownNow();
        photo = null;
        super.onDestroy();
    }
}
