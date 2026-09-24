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
import android.widget.FrameLayout;

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
import java.util.Locale;
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
    @Nullable private DetectionOverlayView detectionOverlay;
    private ScannerViewModel viewModel;
    private boolean cameraStarting;
    private boolean cameraReady;
    private boolean cameraFailed;
    private int viewVersion;
    private int cameraRequestVersion;
    @StringRes private int cameraMessage = R.string.scanner_camera_permission;

    private final ActivityResultLauncher<String[]> imagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && isAdded()) {
                    PhotoCropDialogFragment.newInstance(uri).show(
                            getChildFragmentManager(), PhotoCropDialogFragment.TAG);
                }
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
        int overlayId = getResources().getIdentifier(
                "detectionOverlay", "id", requireContext().getPackageName());
        detectionOverlay = binding.getRoot().findViewById(overlayId);
        // Keep upgraded APKs compatible with an older compiled scanner layout.
        if (detectionOverlay == null
                && binding.selectedImagePreview.getParent() instanceof FrameLayout) {
            FrameLayout previewFrame = (FrameLayout) binding.selectedImagePreview.getParent();
            detectionOverlay = new DetectionOverlayView(requireContext(), null);
            detectionOverlay.setVisibility(View.GONE);
            previewFrame.addView(detectionOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (binding == null) return;
        viewModel = new ViewModelProvider(this).get(ScannerViewModel.class);
        getChildFragmentManager().setFragmentResultListener(
                PhotoCropDialogFragment.RESULT_KEY, getViewLifecycleOwner(), (key, result) -> {
                    String path = result.getString(PhotoCropDialogFragment.RESULT_PATH);
                    if (path == null || viewModel == null) return;
                    File cropFile = new File(path);
                    viewModel.loadImage(Uri.fromFile(cropFile), cropFile);
                });
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
        if (detectionOverlay != null) {
            detectionOverlay.setVisibility(hasImage ? View.VISIBLE : View.GONE);
            detectionOverlay.setDetections(state.result == null ? null : state.result.detections,
                    hasImage ? state.image.getWidth() : 0, hasImage ? state.image.getHeight() : 0);
        }
        binding.classificationProgress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        binding.retakeButton.setEnabled(hasImage && !state.busy);
        binding.retakeButton.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        binding.uploadButton.setEnabled(!state.busy);
        if (hasImage) stopCamera();

        boolean modelReady = state.modelsReady();
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
        } else if (state.result != null) {
            int count = state.result.fruitCount();
            if (state.result.isGraded()) {
                binding.resultLabelText.setText(scannerText("scanner_fruits_identified", count));
                binding.resultDetailsText.setText(scannerText("scanner_batch_result_detail",
                        state.result.classACount, state.result.classBCount,
                        state.result.classCCount,
                        state.result.detectorMillis, state.result.gradingMillis));
            } else if (count == 0) {
                binding.resultLabelText.setText(scannerText("scanner_object_unidentified"));
                binding.resultDetailsText.setText(scannerText("scanner_object_unidentified_detail"));
            } else if (count > ScannerViewModel.MAX_FRUIT_COUNT) {
                binding.resultLabelText.setText(scannerText("scanner_too_many", count));
                binding.resultDetailsText.setText(scannerText("scanner_maximum_eight"));
            } else {
                binding.resultLabelText.setText(R.string.scanner_image_ready);
                binding.resultDetailsText.setText(R.string.scanner_model_missing_detail);
            }
        } else if (hasImage) {
            binding.resultLabelText.setText(R.string.scanner_image_ready);
            binding.resultDetailsText.setText(state.detectorAvailability == DragonFruitDetector.Availability.INVALID_MODEL
                    || state.qualityAvailability == MobileNetV2Classifier.Availability.INVALID_MODEL
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

    @NonNull
    private String scannerText(@NonNull String name, Object... arguments) {
        int identifier = getResources().getIdentifier(
                name, "string", requireContext().getPackageName());
        if (identifier == 0) {
            String fallback;
            switch (name) {
                case "scanner_fruits_identified":
                    fallback = "%1$d dragon fruit(s) identified";
                    break;
                case "scanner_batch_result_detail":
                    fallback = "Class A: %1$d  •  Class B: %2$d  •  Class C: %3$d\nIdentification: %4$d ms  •  Grading: %5$d ms";
                    break;
                case "scanner_object_unidentified":
                    fallback = "Object unidentified";
                    break;
                case "scanner_object_unidentified_detail":
                    fallback = "No dragon fruit was identified. Use a clear photo and try again.";
                    break;
                case "scanner_too_many":
                    fallback = "%1$d dragon fruits detected";
                    break;
                case "scanner_maximum_eight":
                    fallback = "Reframe the photo with no more than 8 visible dragon fruits.";
                    break;
                default:
                    fallback = name;
            }
            return arguments.length == 0 ? fallback
                    : String.format(Locale.getDefault(), fallback, arguments);
        }
        return arguments.length == 0 ? getString(identifier) : getString(identifier, arguments);
    }

    @Override
    public void onDestroyView() {
        viewVersion++;
        stopCamera();
        if (binding != null) binding.selectedImagePreview.setImageDrawable(null);
        if (detectionOverlay != null) detectionOverlay.setDetections(null, 0, 0);
        detectionOverlay = null;
        binding = null;
        super.onDestroyView();
    }
}

