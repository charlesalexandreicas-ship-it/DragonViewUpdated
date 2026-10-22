package ph.dragonview.mobile.ui.historical;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.databinding.FragmentHistoricalCaptureBinding;
import ph.dragonview.mobile.ui.scanner.ScannerImageLoader;

/** Captures a page and transcribes locally; no photo is uploaded. */
public final class HistoricalCaptureFragment extends Fragment {
    private FragmentHistoricalCaptureBinding binding;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Uri selectedPhoto;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else if (binding != null) binding.messageText.setText(
                        "Camera permission is off. You can still choose an existing photo.");
            });
    private final ActivityResultLauncher<String[]> gallery = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::showSelectedPhoto);

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
                                        Bundle state) {
        binding = FragmentHistoricalCaptureBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (!LocalRepository.get(requireContext()).isWorkspaceOwner()) {
            binding.messageText.setText("Workspace owner permission is required.");
            binding.captureButton.setEnabled(false);
            binding.galleryButton.setEnabled(false);
            return;
        }
        binding.captureButton.setOnClickListener(v -> capture());
        binding.retakeButton.setOnClickListener(v -> retake());
        binding.usePhotoButton.setOnClickListener(v -> recognize());
        binding.galleryButton.setOnClickListener(v -> gallery.launch(new String[]{"image/*"}));
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) startCamera();
        else permission.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        if (binding == null || selectedPhoto != null) return;
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            if (binding == null) return;
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(),
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                binding.captureButton.setEnabled(true);
            } catch (Exception error) {
                binding.messageText.setText(
                        "Camera unavailable. Choose an existing photo or try again.");
                binding.captureButton.setEnabled(false);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void capture() {
        if (imageCapture == null || binding == null) return;
        try {
            File file = File.createTempFile("historical_page_", ".jpg",
                    requireContext().getCacheDir());
            binding.captureButton.setEnabled(false);
            imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(requireContext()),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override public void onImageSaved(
                                @NonNull ImageCapture.OutputFileResults result) {
                            showSelectedPhoto(Uri.fromFile(file));
                        }
                        @Override public void onError(@NonNull ImageCaptureException error) {
                            file.delete();
                            if (binding == null) return;
                            binding.captureButton.setEnabled(true);
                            binding.messageText.setText("Could not capture the page. Try again.");
                        }
                    });
        } catch (IOException error) {
            binding.messageText.setText("Could not prepare the camera photo.");
        }
    }

    private void showSelectedPhoto(Uri uri) {
        if (uri == null || binding == null) return;
        selectedPhoto = uri;
        binding.cameraPreview.setVisibility(View.GONE);
        binding.photoPreview.setVisibility(View.VISIBLE);
        binding.retakeButton.setVisibility(View.VISIBLE);
        binding.captureButton.setVisibility(View.GONE);
        binding.usePhotoButton.setVisibility(View.VISIBLE);
        binding.galleryButton.setVisibility(View.GONE);
        binding.messageText.setText("Check the photo, then review all extracted values.");
        Context appContext = requireContext().getApplicationContext();
        imageExecutor.execute(() -> {
            try {
                Bitmap bitmap = ScannerImageLoader.load(appContext, uri, 1600);
                mainThread.post(() -> {
                    if (binding != null && uri.equals(selectedPhoto)) {
                        binding.photoPreview.setImageBitmap(bitmap);
                    } else bitmap.recycle();
                });
            } catch (Exception error) {
                mainThread.post(() -> {
                    if (binding != null) binding.messageText.setText(
                            "Photo preview unavailable. You can still try to use it.");
                });
            }
        });
    }

    private void retake() {
        selectedPhoto = null;
        binding.photoPreview.setImageDrawable(null);
        binding.photoPreview.setVisibility(View.GONE);
        binding.cameraPreview.setVisibility(View.VISIBLE);
        binding.retakeButton.setVisibility(View.GONE);
        binding.usePhotoButton.setVisibility(View.GONE);
        binding.captureButton.setVisibility(View.VISIBLE);
        binding.galleryButton.setVisibility(View.VISIBLE);
        binding.captureButton.setEnabled(imageCapture != null);
        binding.messageText.setText("Fit the complete record inside the frame.");
        if (imageCapture == null) startCamera();
    }

    private void recognize() {
        Uri photo = selectedPhoto;
        if (photo == null || binding == null) return;
        binding.progress.setVisibility(View.VISIBLE);
        binding.usePhotoButton.setEnabled(false);
        binding.messageText.setText("Reading the page on this device…");
        try {
            InputImage image = InputImage.fromFilePath(requireContext(), photo);
            TextRecognizer recognizer = TextRecognition.getClient(
                    TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        recognizer.close();
                        openReview(text.getText(), photo);
                    })
                    .addOnFailureListener(error -> {
                        recognizer.close();
                        openReview("", photo);
                    });
        } catch (IOException error) {
            openReview("", photo);
        }
    }

    private void openReview(String text, Uri photo) {
        if (binding == null || !isAdded()) return;
        binding.progress.setVisibility(View.GONE);
        Bundle args = new Bundle();
        args.putString("source", "PHOTO");
        args.putString("recognizedText", text);
        args.putString("photoUri", photo.toString());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_capture_to_editor, args);
    }

    @Override public void onDestroyView() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        imageCapture = null;
        binding = null;
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        imageExecutor.shutdown();
        super.onDestroy();
    }
}
