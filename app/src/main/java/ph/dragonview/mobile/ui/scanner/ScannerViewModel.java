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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns image decoding and serialized inference without retaining a Fragment or camera. */
public final class ScannerViewModel extends AndroidViewModel {
    enum ScanError { NONE, IMAGE_READ, INFERENCE, CAPTURE }

    static final class State {
        @Nullable final Bitmap image;
        @Nullable final MobileNetV2Classifier.Availability modelAvailability;
        @Nullable final MobileNetV2Classifier.Classification classification;
        @NonNull final ScanError error;
        final boolean busy;

        State(@Nullable Bitmap image,
              @Nullable MobileNetV2Classifier.Availability modelAvailability,
              @Nullable MobileNetV2Classifier.Classification classification,
              @NonNull ScanError error, boolean busy) {
            this.image = image;
            this.modelAvailability = modelAvailability;
            this.classification = classification;
            this.error = error;
            this.busy = busy;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(null, null, null, ScanError.NONE, false));
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger requestVersion = new AtomicInteger();
    @Nullable private MobileNetV2Classifier classifier;
    private volatile boolean cleared;

    public ScannerViewModel(@NonNull Application application) {
        super(application);
        worker.execute(() -> {
            MobileNetV2Classifier.LoadResult result = MobileNetV2Classifier.load(application);
            classifier = result.classifier;
            mainHandler.post(() -> {
                if (cleared) return;
                State current = currentState();
                state.setValue(new State(current.image, result.availability,
                        current.classification, current.error, current.busy));
            });
        });
    }

    @NonNull LiveData<State> getState() { return state; }

    void captureStarted() {
        State current = currentState();
        state.setValue(new State(current.image, current.modelAvailability,
                null, ScanError.NONE, true));
    }

    void captureFailed() {
        if (cleared) return;
        State current = currentState();
        state.setValue(new State(current.image, current.modelAvailability,
                null, ScanError.CAPTURE, false));
    }

    void loadImage(@NonNull Uri uri, @Nullable File temporaryCapture) {
        if (cleared) {
            if (temporaryCapture != null) temporaryCapture.delete();
            return;
        }
        int version = requestVersion.incrementAndGet();
        State current = currentState();
        state.setValue(new State(current.image, current.modelAvailability,
                null, ScanError.NONE, true));
        worker.execute(() -> {
            Bitmap image = null;
            MobileNetV2Classifier.Classification classification = null;
            ScanError error = ScanError.NONE;
            try {
                image = ScannerImageLoader.load(getApplication(), uri, 1200);
                if (!cleared && classifier != null) classification = classifier.classify(image);
            } catch (IOException | SecurityException exception) {
                error = ScanError.IMAGE_READ;
            } catch (RuntimeException exception) {
                error = image == null ? ScanError.IMAGE_READ : ScanError.INFERENCE;
            } finally {
                // Only the app-created capture file is removed; uploaded documents are untouched.
                if (temporaryCapture != null) temporaryCapture.delete();
            }
            Bitmap loadedImage = image;
            MobileNetV2Classifier.Classification prediction = classification;
            ScanError loadError = error;
            mainHandler.post(() -> {
                if (cleared || requestVersion.get() != version) return;
                state.setValue(new State(loadedImage, currentState().modelAvailability,
                        prediction, loadError, false));
            });
        });
    }

    void resetImage() {
        requestVersion.incrementAndGet();
        state.setValue(new State(null, currentState().modelAvailability,
                null, ScanError.NONE, false));
    }

    @NonNull
    private State currentState() {
        State current = state.getValue();
        if (current == null) throw new IllegalStateException("Scanner state is missing");
        return current;
    }

    @Override
    protected void onCleared() {
        cleared = true;
        requestVersion.incrementAndGet();
        worker.execute(() -> {
            if (classifier != null) {
                classifier.close();
                classifier = null;
            }
        });
        worker.shutdown();
        super.onCleared();
    }
}
