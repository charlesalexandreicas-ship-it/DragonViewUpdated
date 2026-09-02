package ph.dragonview.mobile.ui.scanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Rational;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.databinding.FragmentScannerBinding;

/** Camera and gallery UI; the ViewModel owns decoding and optional model inference. */
public final class ScannerFragment extends Fragment {
    @Nullable private FragmentScannerBinding binding;
    @Nullable private ProcessCameraProvider cameraProvider;
    @Nullable private Preview preview;
    @Nullable private ImageCapture imageCapture;
    @Nullable private LiveData<CameraState> cameraStates;
    @Nullable private Observer<CameraState> cameraStateObserver;
    @Nullable private ScannerViewModel.State scanState;
    private ScannerViewModel viewModel;
    private boolean cameraStarting;
    private boolean cameraReady;
    private boolean cameraFailed;
    private int viewVersion;
    private int cameraRequestVersion;
    @StringRes private int cameraMessage = R.string.scanner_camera_permission;

    private final ActivityResultLauncher<String[]> imagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && viewModel != null) viewModel.loadImage(uri, null);
            });

    private final ActivityResultLauncher<String> permissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (binding == null) return;
                if (granted) {
                    cameraFailed = false;
                    startCamera();
                } else {
                    cameraMessage = R.string.scanner_camera_permission;
                    renderCameraControls();
                    Snackbar snackbar = Snackbar.make(binding.getRoot(),
                            R.string.scanner_permission_denied, Snackbar.LENGTH_LONG);
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        snackbar.setAction(R.string.scanner_settings, view -> openAppSettings());
                    }
                    snackbar.show();
                }
            });

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        viewVersion++;
        binding = FragmentScannerBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (binding == null) return;
        viewModel = new ViewModelProvider(this).get(ScannerViewModel.class);
        binding.cameraPreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        binding.cameraPreview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        binding.uploadButton.setOnClickListener(clicked -> imagePicker.launch(new String[]{"image/*"}));
        binding.captureButton.setOnClickListener(clicked -> captureOrRequestPermission());
        binding.retakeButton.setOnClickListener(clicked -> {
            cameraFailed = false;
            viewModel.resetImage();
            binding.getRoot().smoothScrollTo(0, 0);
        });
        viewModel.getState().observe(getViewLifecycleOwner(), this::renderState);
    }

    @Override
    public void onResume() {
        super.onResume();
        cameraFailed = false;
        if (hasCameraPermission()) startCamera();
        else {
            stopCamera();
            cameraMessage = R.string.scanner_camera_permission;
            renderCameraControls();
        }
    }

    private void captureOrRequestPermission() {
        if (binding == null) return;
        if (!hasCameraPermission()) {
            permissionRequest.launch(Manifest.permission.CAMERA);
            return;
        }
        if (!cameraReady || imageCapture == null) {
            cameraFailed = false;
            startCamera();
            return;
        }

        final File captureFile;
        try {
            captureFile = File.createTempFile("scanner_", ".jpg", requireContext().getCacheDir());
        } catch (IOException exception) {
            viewModel.captureFailed();
            return;
        }
        imageCapture.setTargetRotation(binding.cameraPreview.getDisplay() == null
                ? Surface.ROTATION_0 : binding.cameraPreview.getDisplay().getRotation());
        viewModel.captureStarted();
        ScannerViewModel captureViewModel = viewModel;
        try {
            imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(captureFile).build(),
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        captureViewModel.loadImage(Uri.fromFile(captureFile), captureFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        captureFile.delete();
                        captureViewModel.captureFailed();
                    }
                });
        } catch (RuntimeException exception) {
            captureFile.delete();
            captureViewModel.captureFailed();
        }
    }

    private void startCamera() {
        if (binding == null || cameraStarting || cameraReady || cameraFailed
                || !hasCameraPermission() || (scanState != null && scanState.image != null)) return;
        cameraStarting = true;
        cameraMessage = R.string.scanner_camera_starting;
        renderCameraControls();
        int requestedView = viewVersion;
        int requestedCamera = ++cameraRequestVersion;
        final ListenableFuture<ProcessCameraProvider> future;
        try {
            future = ProcessCameraProvider.getInstance(requireContext());
        } catch (RuntimeException exception) {
            showCameraFailure(R.string.scanner_camera_error);
            return;
        }
        future.addListener(() -> {
            if (binding == null || requestedView != viewVersion
                    || requestedCamera != cameraRequestVersion) return;
            cameraStarting = false;
            if (scanState != null && scanState.image != null) return;
            try {
                cameraProvider = future.get();
                CameraSelector selector;
                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    selector = CameraSelector.DEFAULT_BACK_CAMERA;
                } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    selector = CameraSelector.DEFAULT_FRONT_CAMERA;
                } else {
                    showCameraFailure(R.string.scanner_camera_unavailable);
                    return;
                }
                preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
                        .build();
                int rotation = binding.cameraPreview.getDisplay() == null
                        ? Surface.ROTATION_0 : binding.cameraPreview.getDisplay().getRotation();
                imageCapture.setTargetRotation(rotation);
                // A shared viewport keeps capture and preview on the same sensor region.
                UseCaseGroup useCases = new UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(imageCapture)
                        .setViewPort(new ViewPort.Builder(new Rational(1, 1), rotation).build())
                        .build();
                Camera camera = cameraProvider.bindToLifecycle(getViewLifecycleOwner(), selector, useCases);
                cameraReady = true;
                cameraMessage = R.string.scanner_camera_ready;
                renderCameraControls();
                cameraStates = camera.getCameraInfo().getCameraState();
                cameraStateObserver = cameraState -> {
                    if (binding == null || requestedView != viewVersion
                            || requestedCamera != cameraRequestVersion) return;
                    // Camera opening can fail asynchronously (in use, disabled, or disconnected).
                    if (cameraState.getError() != null) {
                        showCameraFailure(R.string.scanner_camera_error);
                    }
                };
                cameraStates.observe(getViewLifecycleOwner(), cameraStateObserver);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                showCameraFailure(R.string.scanner_camera_error);
            } catch (ExecutionException | RuntimeException | androidx.camera.core.CameraInfoUnavailableException exception) {
                showCameraFailure(R.string.scanner_camera_error);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void showCameraFailure(@StringRes int message) {
        stopCamera();
        cameraFailed = true;
        cameraMessage = message;
        renderCameraControls();
    }

    private void stopCamera() {
        cameraRequestVersion++;
        if (cameraStates != null && cameraStateObserver != null) {
            cameraStates.removeObserver(cameraStateObserver);
        }
        cameraStates = null;
        cameraStateObserver = null;
        if (cameraProvider != null) {
            if (preview != null) cameraProvider.unbind(preview);
            if (imageCapture != null) cameraProvider.unbind(imageCapture);
        }
        preview = null;
        imageCapture = null;
        cameraReady = false;
        cameraStarting = false;
    }

    private void renderState(@NonNull ScannerViewModel.State state) {
        scanState = state;
        if (binding == null) return;
        boolean hasImage = state.image != null;
        binding.selectedImagePreview.setImageBitmap(state.image);
        binding.selectedImagePreview.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        binding.classificationProgress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        binding.retakeButton.setEnabled(hasImage && !state.busy);
        binding.retakeButton.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        binding.uploadButton.setEnabled(!state.busy);
        if (hasImage) stopCamera();

        boolean modelReady = state.modelAvailability == MobileNetV2Classifier.Availability.READY;
        binding.modelStatusBadge.setText(modelReady
                ? R.string.scanner_model_ready : R.string.scanner_model_pending);
        if (state.busy) {
            binding.resultLabelText.setText(R.string.scanner_processing);
            binding.resultDetailsText.setText(R.string.scanner_processing_detail);
        } else if (state.error != ScannerViewModel.ScanError.NONE) {
            binding.resultLabelText.setText(R.string.scanner_operation_failed);
            binding.resultDetailsText.setText(state.error == ScannerViewModel.ScanError.CAPTURE
                    ? R.string.scanner_capture_error : state.error == ScannerViewModel.ScanError.IMAGE_READ
                    ? R.string.scanner_image_error : R.string.scanner_inference_error);
        } else if (state.classification != null) {
            binding.resultLabelText.setText(state.classification.label);
            binding.resultDetailsText.setText(getString(R.string.scanner_prediction_detail,
                    state.classification.confidence * 100.0f, state.classification.inferenceMillis));
        } else if (hasImage) {
            binding.resultLabelText.setText(R.string.scanner_image_ready);
            binding.resultDetailsText.setText(state.modelAvailability == MobileNetV2Classifier.Availability.INVALID_MODEL
                    ? R.string.scanner_invalid_model : R.string.scanner_model_missing_detail);
        } else {
            binding.resultLabelText.setText(R.string.scanner_awaiting_image);
            binding.resultDetailsText.setText(modelReady
                    ? R.string.scanner_awaiting_ready_detail : R.string.scanner_awaiting_detail);
        }
        renderCameraControls();
        if (!hasImage && !state.busy) startCamera();
    }

    private void renderCameraControls() {
        if (binding == null) return;
        boolean hasImage = scanState != null && scanState.image != null;
        boolean busy = scanState != null && scanState.busy;
        binding.cameraPreview.setVisibility(hasImage ? View.GONE : View.VISIBLE);
        binding.cameraPlaceholder.setVisibility(!hasImage && !cameraReady ? View.VISIBLE : View.GONE);
        binding.cameraStatusText.setText(hasImage ? R.string.scanner_photo_selected : cameraMessage);
        binding.captureButton.setText(!hasCameraPermission()
                ? R.string.scanner_enable_camera : cameraFailed
                ? R.string.scanner_retry_camera : R.string.scanner_capture);
        binding.captureButton.setEnabled(!hasImage && !busy && !cameraStarting);
    }

    private boolean hasCameraPermission() {
        return getContext() != null && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null)));
    }

    @Override
    public void onDestroyView() {
        viewVersion++;
        stopCamera();
        if (binding != null) binding.selectedImagePreview.setImageDrawable(null);
        binding = null;
        super.onDestroyView();
    }
}

