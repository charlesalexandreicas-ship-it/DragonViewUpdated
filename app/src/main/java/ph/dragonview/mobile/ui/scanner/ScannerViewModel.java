package ph.dragonview.mobile.ui.scanner;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns image decoding, dragon-fruit identification, and serialized offline grading. */
public final class ScannerViewModel extends AndroidViewModel {
    static final int MAX_FRUIT_COUNT = 8;
    enum ScanError { NONE, IMAGE_READ, INFERENCE, CAPTURE }

    static final class ScanResult {
        @NonNull final List<DragonFruitDetector.Detection> detections;
        final int classACount;
        final int classBCount;
        final int classCCount;
        final long detectorMillis;
        final long gradingMillis;

        ScanResult(@NonNull List<DragonFruitDetector.Detection> detections,
                   int classACount, int classBCount, int classCCount, long detectorMillis,
                   long gradingMillis) {
            this.detections = Collections.unmodifiableList(new ArrayList<>(detections));
            this.classACount = classACount;
            this.classBCount = classBCount;
            this.classCCount = classCCount;
            this.detectorMillis = detectorMillis;
            this.gradingMillis = gradingMillis;
        }

        int fruitCount() { return detections.size(); }
        boolean isGraded() {
            return isSupportedFruitCount(fruitCount())
                    && classACount + classBCount + classCCount == fruitCount();
        }
    }

    static final class State {
        @Nullable final Bitmap image;
        @Nullable final DragonFruitDetector.Availability detectorAvailability;
        @Nullable final MobileNetV2Classifier.Availability qualityAvailability;
        @Nullable final ScanResult result;
        @NonNull final ScanError error;
        final boolean busy;

        State(@Nullable Bitmap image,
              @Nullable DragonFruitDetector.Availability detectorAvailability,
              @Nullable MobileNetV2Classifier.Availability qualityAvailability,
              @Nullable ScanResult result, @NonNull ScanError error, boolean busy) {
            this.image = image;
            this.detectorAvailability = detectorAvailability;
            this.qualityAvailability = qualityAvailability;
            this.result = result;
            this.error = error;
            this.busy = busy;
        }

        boolean modelsReady() {
            return detectorAvailability == DragonFruitDetector.Availability.READY
                    && qualityAvailability == MobileNetV2Classifier.Availability.READY;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(null, null, null, null, ScanError.NONE, false));
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger requestVersion = new AtomicInteger();
    @Nullable private DragonFruitDetector detector;
    @Nullable private MobileNetV2Classifier classifier;
    private volatile boolean cleared;

    public ScannerViewModel(@NonNull Application application) {
        super(application);
        worker.execute(() -> {
            DragonFruitDetector.LoadResult detectorLoad = DragonFruitDetector.load(application);
            MobileNetV2Classifier.LoadResult qualityLoad = MobileNetV2Classifier.load(application);
            detector = detectorLoad.detector;
            classifier = qualityLoad.classifier;
            mainHandler.post(() -> {
                if (cleared) return;
                State current = currentState();
                state.setValue(new State(current.image, detectorLoad.availability,
                        qualityLoad.availability, current.result, current.error, current.busy));
            });
        });
    }

    @NonNull LiveData<State> getState() { return state; }

    void captureStarted() {
        State current = currentState();
        state.setValue(new State(current.image, current.detectorAvailability,
                current.qualityAvailability, null, ScanError.NONE, true));
    }

    void captureFailed() {
        if (cleared) return;
        State current = currentState();
        state.setValue(new State(current.image, current.detectorAvailability,
                current.qualityAvailability, null, ScanError.CAPTURE, false));
    }

    void loadImage(@NonNull Uri uri, @Nullable File temporaryCapture) {
        if (cleared) {
            if (temporaryCapture != null) temporaryCapture.delete();
            return;
        }
        int version = requestVersion.incrementAndGet();
        State current = currentState();
        state.setValue(new State(current.image, current.detectorAvailability,
                current.qualityAvailability, null, ScanError.NONE, true));
        worker.execute(() -> runScan(uri, temporaryCapture, version));
    }

    private void runScan(Uri uri, @Nullable File temporaryCapture, int version) {
        Bitmap image = null;
        ScanResult result = null;
        ScanError error = ScanError.NONE;
        try {
            image = ScannerImageLoader.load(getApplication(), uri, 1600);
            if (!cleared && detector != null) {
                DragonFruitDetector.Result detected = detector.detect(image);
                List<DragonFruitDetector.Detection> detections = detected.detections;
                int classA = 0;
                int classB = 0;
                int classC = 0;
                long gradingMillis = 0;
                if (isSupportedFruitCount(detections.size()) && classifier != null) {
                    List<DragonFruitDetector.Detection> graded = new ArrayList<>();
                    for (DragonFruitDetector.Detection detection : detections) {
                        Bitmap crop = DragonFruitDetector.crop(image, detection.bounds);
                        MobileNetV2Classifier.Classification grade;
                        try {
                            grade = classifier.classify(crop);
                        } finally {
                            crop.recycle();
                        }
                        gradingMillis += grade.inferenceMillis;
                        if ("Class A".equals(grade.label)) {
                            classA++;
                        } else if ("Class B".equals(grade.label)) {
                            classB++;
                        } else if ("Class C".equals(grade.label)) {
                            classC++;
                        } else {
                            throw new IllegalStateException("Unsupported quality label: " + grade.label);
                        }
                        graded.add(detection.withGrade(grade));
                    }
                    detections = graded;
                }
                result = new ScanResult(detections, classA, classB, classC,
                        detected.inferenceMillis, gradingMillis);
            }
        } catch (IOException | SecurityException exception) {
            error = ScanError.IMAGE_READ;
        } catch (RuntimeException exception) {
            error = image == null ? ScanError.IMAGE_READ : ScanError.INFERENCE;
        } finally {
            if (temporaryCapture != null) temporaryCapture.delete();
        }
        Bitmap loadedImage = image;
        ScanResult scanResult = result;
        ScanError scanError = error;
        mainHandler.post(() -> {
            if (cleared || requestVersion.get() != version) return;
            State latest = currentState();
            state.setValue(new State(loadedImage, latest.detectorAvailability,
                    latest.qualityAvailability, scanResult, scanError, false));
        });
    }

    void resetImage() {
        requestVersion.incrementAndGet();
        State current = currentState();
        state.setValue(new State(null, current.detectorAvailability,
                current.qualityAvailability, null, ScanError.NONE, false));
    }

    @NonNull private State currentState() {
        State current = state.getValue();
        if (current == null) throw new IllegalStateException("Scanner state is missing");
        return current;
    }

    static boolean isSupportedFruitCount(int count) {
        return count >= 1 && count <= MAX_FRUIT_COUNT;
    }

    @Override protected void onCleared() {
        cleared = true;
        requestVersion.incrementAndGet();
        worker.execute(() -> {
            if (detector != null) { detector.close(); detector = null; }
            if (classifier != null) { classifier.close(); classifier = null; }
        });
        worker.shutdown();
        super.onCleared();
    }
}
