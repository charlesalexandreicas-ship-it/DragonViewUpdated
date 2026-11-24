package ph.dragonview.mobile.ui.scanner;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs the three-class MobileNetV2 classifier through LiteRT.
 *
 * <p>The camera feature intentionally remains usable when the model assets are absent. Add
 * {@code assets/ml/mobilenet_v2.tflite} and {@code assets/ml/labels.txt} to activate inference.
 * The model must accept one RGB FLOAT32 tensor and use MobileNetV2's [-1, 1] normalization.</p>
 */
final class MobileNetV2Classifier implements AutoCloseable {
    static final String MODEL_ASSET = "ml/mobilenet_v2.tflite";
    static final String LABELS_ASSET = "ml/labels.txt";
    private static final String CLASS_A_LABEL = "Class A";
    private static final String CLASS_B_LABEL = "Class B";
    private static final String CLASS_C_LABEL = "Class C";
    // Chosen on held-out validation crops; keep the model's Class C decision.
    static final float CLASS_B_TO_A_RATIO = 0.40f;
    private static final float CLASS_A_MODEL_THRESHOLD = 0.89f;
    private static final float CLASS_A_SHADE_THRESHOLD = 0.78f;
    private static final float CLASS_A_MODEL_FLOOR = 0.04f;
    private static final float MIN_SHADE_SATURATION = 55.0f / 255.0f;
    private static final float MIN_SHADE_VALUE = 35.0f / 255.0f;
    private static final float RED_HUE_MAX = 8.0f * 360.0f / 255.0f;
    private static final float MAGENTA_HUE_MIN = 225.0f * 360.0f / 255.0f;

    enum Availability {
        READY,
        MISSING_MODEL,
        MISSING_LABELS,
        INVALID_MODEL
    }

    static final class LoadResult {
        @NonNull final Availability availability;
        @Nullable final MobileNetV2Classifier classifier;
        @Nullable final String technicalMessage;

        private LoadResult(@NonNull Availability availability,
                           @Nullable MobileNetV2Classifier classifier,
                           @Nullable String technicalMessage) {
            this.availability = availability;
            this.classifier = classifier;
            this.technicalMessage = technicalMessage;
        }

        @NonNull
        static LoadResult ready(@NonNull MobileNetV2Classifier classifier) {
            return new LoadResult(Availability.READY, classifier, null);
        }

        @NonNull
        static LoadResult unavailable(@NonNull Availability availability,
                                      @Nullable String technicalMessage) {
            return new LoadResult(availability, null, technicalMessage);
        }
    }

    static final class Classification {
        @NonNull final String label;
        final float confidence;
        final float redMagentaRatio;
        final long inferenceMillis;

        private Classification(@NonNull String label, float confidence,
                               float redMagentaRatio, long inferenceMillis) {
            this.label = label;
            this.confidence = confidence;
            this.redMagentaRatio = redMagentaRatio;
            this.inferenceMillis = inferenceMillis;
        }
    }

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int inputWidth;
    private final int inputHeight;
    private boolean closed;

    private MobileNetV2Classifier(@NonNull Interpreter interpreter,
                                  @NonNull List<String> labels,
                                  int inputWidth,
                                  int inputHeight) {
        this.interpreter = interpreter;
        this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
        this.inputWidth = inputWidth;
        this.inputHeight = inputHeight;
    }

    @NonNull
    static LoadResult load(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (!assetExists(appContext, MODEL_ASSET)) {
            return LoadResult.unavailable(Availability.MISSING_MODEL, MODEL_ASSET + " is absent");
        }
        if (!assetExists(appContext, LABELS_ASSET)) {
            return LoadResult.unavailable(Availability.MISSING_LABELS, LABELS_ASSET + " is absent");
        }

        Interpreter interpreter = null;
        try {
            List<String> labels = readLabels(appContext);
            if (labels.size() < 2) {
                return LoadResult.unavailable(
                        Availability.INVALID_MODEL, "At least two class labels are required");
            }

            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(Math.max(2, Math.min(4,
                    Runtime.getRuntime().availableProcessors())));
            interpreter = new Interpreter(mapModel(appContext, MODEL_ASSET), options);

            if (interpreter.getInputTensorCount() != 1 || interpreter.getOutputTensorCount() != 1) {
                throw new IllegalArgumentException("Exactly one input and one output tensor are required");
            }

            Tensor input = interpreter.getInputTensor(0);
            Tensor output = interpreter.getOutputTensor(0);
            int[] inputShape = input.shape();
            int[] outputShape = output.shape();

            if (input.dataType() != DataType.FLOAT32
                    || inputShape.length != 4
                    || inputShape[0] != 1
                    || inputShape[1] != 224
                    || inputShape[2] != 224
                    || inputShape[3] != 3) {
                throw new IllegalArgumentException(
                        "Expected FLOAT32 input [1, 224, 224, 3], found "
                                + input.dataType() + " " + Arrays.toString(inputShape));
            }
            if (output.dataType() != DataType.FLOAT32
                    || outputShape.length != 2
                    || outputShape[0] != 1
                    || outputShape[1] != labels.size()) {
                throw new IllegalArgumentException(
                        "Expected FLOAT32 output [1, " + labels.size() + "], found "
                                + output.dataType() + " " + Arrays.toString(outputShape));
            }

            MobileNetV2Classifier classifier = new MobileNetV2Classifier(
                    interpreter, labels, inputShape[2], inputShape[1]);
            interpreter = null;
            return LoadResult.ready(classifier);
        } catch (IOException | RuntimeException | LinkageError exception) {
            return LoadResult.unavailable(Availability.INVALID_MODEL, exception.getMessage());
        } finally {
            if (interpreter != null) interpreter.close();
        }
    }

    @NonNull
    synchronized Classification classify(@NonNull Bitmap source) {
        if (closed) throw new IllegalStateException("Classifier is closed");
        if (source.isRecycled()) throw new IllegalArgumentException("Source bitmap is recycled");

        Bitmap inputBitmap = createCenterCrop(source, inputWidth, inputHeight);
        ByteBuffer inputBuffer;
        float redMagentaRatio;
        try {
            inputBuffer = bitmapToInputBuffer(inputBitmap);
            redMagentaRatio = redMagentaRatio(inputBitmap);
        } finally {
            if (inputBitmap != source) inputBitmap.recycle();
        }

        float[][] output = new float[1][labels.size()];
        long startedAt = System.nanoTime();
        interpreter.run(inputBuffer, output);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        float[] probabilities = toProbabilities(output[0]);
        int bestIndex = topIndex(probabilities);
        int classAIndex = labels.indexOf(CLASS_A_LABEL);
        int classBIndex = labels.indexOf(CLASS_B_LABEL);
        int classCIndex = labels.indexOf(CLASS_C_LABEL);
        float confidence = probabilities[bestIndex];
        if (classAIndex >= 0 && classBIndex >= 0 && classCIndex >= 0) {
            bestIndex = chooseThreeClass(probabilities,
                    classAIndex, classBIndex, classCIndex);
            confidence = probabilities[bestIndex];
        }
        if (labels.size() == 2 && classAIndex >= 0 && classBIndex >= 0) {
            bestIndex = isClassA(probabilities[classAIndex], redMagentaRatio)
                    ? classAIndex : classBIndex;
            confidence = probabilities[bestIndex];
        }
        return new Classification(labels.get(bestIndex), confidence,
                redMagentaRatio, elapsedMillis);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        interpreter.close();
    }

    static int chooseThreeClass(@NonNull float[] probabilities,
                                    int classAIndex, int classBIndex, int classCIndex) {
        if (probabilities[classCIndex] >= Math.max(probabilities[classAIndex],
                probabilities[classBIndex])) return classCIndex;
        return probabilities[classBIndex] >= CLASS_B_TO_A_RATIO * probabilities[classAIndex]
                ? classBIndex : classAIndex;
    }

    @NonNull
    private ByteBuffer bitmapToInputBuffer(@NonNull Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3 * 4)
                .order(ByteOrder.nativeOrder());
        int[] pixels = new int[inputWidth * inputHeight];
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);
        for (int pixel : pixels) {
            buffer.putFloat(normalizeChannel((pixel >> 16) & 0xFF));
            buffer.putFloat(normalizeChannel((pixel >> 8) & 0xFF));
            buffer.putFloat(normalizeChannel(pixel & 0xFF));
        }
        buffer.rewind();
        return buffer;
    }

    @NonNull
    private static Bitmap createCenterCrop(@NonNull Bitmap source, int width, int height) {
        float targetRatio = width / (float) height;
        float sourceRatio = source.getWidth() / (float) source.getHeight();
        int cropWidth = source.getWidth();
        int cropHeight = source.getHeight();
        if (sourceRatio > targetRatio) {
            cropWidth = Math.round(source.getHeight() * targetRatio);
        } else {
            cropHeight = Math.round(source.getWidth() / targetRatio);
        }
        int left = Math.max(0, (source.getWidth() - cropWidth) / 2);
        int top = Math.max(0, (source.getHeight() - cropHeight) / 2);
        Bitmap crop = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, width, height, true);
        if (crop != source && crop != scaled) crop.recycle();
        return scaled;
    }

    @NonNull
    static float[] toProbabilities(@NonNull float[] values) {
        if (values.length == 0) throw new IllegalArgumentException("Model output is empty");
        double sum = 0.0;
        for (float value : values) {
            if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
                throw new IllegalArgumentException("Model output must contain finite softmax probabilities");
            }
            sum += value;
        }
        // The export contract requires softmax, never logits or a one-output sigmoid.
        if (Math.abs(sum - 1.0) > 0.02) {
            throw new IllegalArgumentException("Softmax probabilities must sum to one");
        }
        float[] probabilities = Arrays.copyOf(values, values.length);
        for (int index = 0; index < probabilities.length; index++) {
            probabilities[index] /= (float) sum;
        }
        return probabilities;
    }

    static float normalizeChannel(int value) {
        return (value / 127.5f) - 1.0f;
    }

    static boolean isClassA(float classAProbability, float redMagentaRatio) {
        return classAProbability >= CLASS_A_MODEL_THRESHOLD
                || (redMagentaRatio >= CLASS_A_SHADE_THRESHOLD
                && classAProbability >= CLASS_A_MODEL_FLOOR);
    }

    static float redMagentaRatio(@NonNull Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return redMagentaRatio(pixels, width, height);
    }

    static float redMagentaRatio(@NonNull int[] pixels, int width, int height) {
        if (width <= 0 || height <= 0 || pixels.length != width * height) {
            throw new IllegalArgumentException("Pixels must match the image dimensions");
        }
        float centerX = (width - 1) / 2.0f;
        float centerY = (height - 1) / 2.0f;
        float radiusX = width * (90.0f / 224.0f);
        float radiusY = height * (90.0f / 224.0f);
        int eligible = 0;
        int redMagenta = 0;
        for (int y = 0; y < height; y++) {
            float normalizedY = (y - centerY) / radiusY;
            for (int x = 0; x < width; x++) {
                float normalizedX = (x - centerX) / radiusX;
                if (normalizedX * normalizedX + normalizedY * normalizedY > 1.0f) continue;

                int pixel = pixels[y * width + x];
                float red = ((pixel >> 16) & 0xFF) / 255.0f;
                float green = ((pixel >> 8) & 0xFF) / 255.0f;
                float blue = (pixel & 0xFF) / 255.0f;
                float maximum = Math.max(red, Math.max(green, blue));
                float minimum = Math.min(red, Math.min(green, blue));
                float difference = maximum - minimum;
                float saturation = maximum == 0.0f ? 0.0f : difference / maximum;
                if (saturation <= MIN_SHADE_SATURATION || maximum <= MIN_SHADE_VALUE) continue;

                eligible++;
                float hue;
                if (difference == 0.0f) {
                    hue = 0.0f;
                } else if (maximum == red) {
                    hue = 60.0f * (((green - blue) / difference) % 6.0f);
                } else if (maximum == green) {
                    hue = 60.0f * (((blue - red) / difference) + 2.0f);
                } else {
                    hue = 60.0f * (((red - green) / difference) + 4.0f);
                }
                if (hue < 0.0f) hue += 360.0f;
                if (hue <= RED_HUE_MAX || hue >= MAGENTA_HUE_MIN) redMagenta++;
            }
        }
        return eligible == 0 ? 0.0f : redMagenta / (float) eligible;
    }

    static int topIndex(@NonNull float[] values) {
        if (values.length == 0) throw new IllegalArgumentException("Values are empty");
        int bestIndex = 0;
        for (int index = 1; index < values.length; index++) {
            if (values[index] > values[bestIndex]) bestIndex = index;
        }
        return bestIndex;
    }

    private static boolean assetExists(@NonNull Context context, @NonNull String assetName) {
        try (java.io.InputStream ignored = context.getAssets().open(assetName)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    @NonNull
    private static List<String> readLabels(@NonNull Context context) throws IOException {
        List<String> labels = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(LABELS_ASSET), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String label = line.trim();
                if (!label.isEmpty()) labels.add(label);
            }
        }
        return labels;
    }

    @NonNull
    private static MappedByteBuffer mapModel(@NonNull Context context,
                                             @NonNull String modelAsset) throws IOException {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(modelAsset);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            return stream.getChannel().map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.getStartOffset(),
                    descriptor.getDeclaredLength());
        }
    }
}
