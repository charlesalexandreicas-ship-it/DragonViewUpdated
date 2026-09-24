package ph.dragonview.mobile.ui.scanner;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Offline single-class YOLO detector used before crop grading. */
final class DragonFruitDetector implements AutoCloseable {
    static final String MODEL_ASSET = "ml/dragon_fruit_detector.tflite";
    static final String CONFIG_ASSET = "ml/detector_config.json";

    enum Availability { READY, MISSING_MODEL, MISSING_CONFIG, INVALID_MODEL }

    static final class LoadResult {
        @NonNull final Availability availability;
        @Nullable final DragonFruitDetector detector;

        private LoadResult(@NonNull Availability availability,
                           @Nullable DragonFruitDetector detector) {
            this.availability = availability;
            this.detector = detector;
        }

        static LoadResult ready(@NonNull DragonFruitDetector detector) {
            return new LoadResult(Availability.READY, detector);
        }

        static LoadResult unavailable(@NonNull Availability availability) {
            return new LoadResult(availability, null);
        }
    }

    static final class Detection {
        @NonNull final RectF bounds;
        final float confidence;
        @Nullable final MobileNetV2Classifier.Classification grade;

        Detection(@NonNull RectF bounds, float confidence,
                  @Nullable MobileNetV2Classifier.Classification grade) {
            this.bounds = new RectF(bounds);
            this.confidence = confidence;
            this.grade = grade;
        }

        @NonNull Detection withGrade(@NonNull MobileNetV2Classifier.Classification value) {
            return new Detection(bounds, confidence, value);
        }
    }

    static final class Result {
        @NonNull final List<Detection> detections;
        final long inferenceMillis;

        Result(@NonNull List<Detection> detections, long inferenceMillis) {
            this.detections = Collections.unmodifiableList(new ArrayList<>(detections));
            this.inferenceMillis = inferenceMillis;
        }
    }

    private final Interpreter interpreter;
    private final int inputSize;
    private final float confidenceThreshold;
    private final float nmsThreshold;
    private final int[] outputShape;
    private boolean closed;

    private DragonFruitDetector(Interpreter interpreter, int inputSize,
                                float confidenceThreshold, float nmsThreshold,
                                int[] outputShape) {
        this.interpreter = interpreter;
        this.inputSize = inputSize;
        this.confidenceThreshold = confidenceThreshold;
        this.nmsThreshold = nmsThreshold;
        this.outputShape = Arrays.copyOf(outputShape, outputShape.length);
    }

    @NonNull
    static LoadResult load(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (!assetExists(app, MODEL_ASSET)) return LoadResult.unavailable(Availability.MISSING_MODEL);
        if (!assetExists(app, CONFIG_ASSET)) return LoadResult.unavailable(Availability.MISSING_CONFIG);
        Interpreter interpreter = null;
        try {
            JSONObject config = new JSONObject(readText(app, CONFIG_ASSET));
            int size = config.getInt("input_size");
            float confidence = (float) config.getDouble("confidence_threshold");
            float nms = (float) config.getDouble("nms_iou_threshold");
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
            interpreter = new Interpreter(mapModel(app, MODEL_ASSET), options);
            if (interpreter.getInputTensorCount() != 1 || interpreter.getOutputTensorCount() != 1) {
                throw new IllegalArgumentException("Detector requires one input and one output");
            }
            Tensor input = interpreter.getInputTensor(0);
            Tensor output = interpreter.getOutputTensor(0);
            int[] inputShape = input.shape();
            int[] outputShape = output.shape();
            if (input.dataType() != DataType.FLOAT32 || inputShape.length != 4
                    || inputShape[0] != 1 || inputShape[1] != size
                    || inputShape[2] != size || inputShape[3] != 3) {
                throw new IllegalArgumentException("Unexpected detector input " + Arrays.toString(inputShape));
            }
            if (output.dataType() != DataType.FLOAT32 || outputShape.length != 3
                    || outputShape[0] != 1
                    || !((outputShape[1] == 5 || outputShape[2] == 5)
                    || (outputShape[1] == 6 || outputShape[2] == 6))) {
                throw new IllegalArgumentException("Unexpected detector output " + Arrays.toString(outputShape));
            }
            DragonFruitDetector detector = new DragonFruitDetector(
                    interpreter, size, confidence, nms, outputShape);
            interpreter = null;
            return LoadResult.ready(detector);
        } catch (Exception | LinkageError ignored) {
            return LoadResult.unavailable(Availability.INVALID_MODEL);
        } finally {
            if (interpreter != null) interpreter.close();
        }
    }

    @NonNull
    synchronized Result detect(@NonNull Bitmap source) {
        if (closed) throw new IllegalStateException("Detector is closed");
        if (source.isRecycled()) throw new IllegalArgumentException("Source bitmap is recycled");
        Letterbox letterbox = letterbox(source, inputSize);
        ByteBuffer input = bitmapToBuffer(letterbox.bitmap);
        int elements = 1;
        for (int dimension : outputShape) elements *= dimension;
        ByteBuffer output = ByteBuffer.allocateDirect(elements * 4).order(ByteOrder.nativeOrder());
        long started = System.nanoTime();
        interpreter.run(input, output);
        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        output.rewind();
        float[] values = new float[elements];
        output.asFloatBuffer().get(values);
        letterbox.bitmap.recycle();
        List<Detection> candidates = decode(values, outputShape, inputSize,
                source.getWidth(), source.getHeight(), letterbox.scale,
                letterbox.padX, letterbox.padY, confidenceThreshold);
        return new Result(nonMaximumSuppression(candidates, nmsThreshold), elapsed);
    }

    @Override public synchronized void close() {
        if (!closed) {
            closed = true;
            interpreter.close();
        }
    }

    @NonNull
    static List<Detection> decode(float[] output, int[] shape, int inputSize,
                                  int sourceWidth, int sourceHeight, float scale,
                                  float padX, float padY, float threshold) {
        boolean endToEnd = shape[1] == 6 || shape[2] == 6;
        boolean channelsFirst = shape[1] == (endToEnd ? 6 : 5);
        int count = channelsFirst ? shape[2] : shape[1];
        List<Detection> detections = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            float first = value(output, shape, channelsFirst, index, 0);
            float second = value(output, shape, channelsFirst, index, 1);
            float third = value(output, shape, channelsFirst, index, 2);
            float fourth = value(output, shape, channelsFirst, index, 3);
            float confidence = value(output, shape, channelsFirst, index, 4);
            if (!Float.isFinite(confidence) || confidence < threshold) continue;
            float centerX = endToEnd ? (first + third) / 2f : first;
            float centerY = endToEnd ? (second + fourth) / 2f : second;
            float width = endToEnd ? third - first : third;
            float height = endToEnd ? fourth - second : fourth;
            // TensorFlow-family YOLO exports use normalized coordinates; accept pixel output too.
            if (Math.max(Math.max(Math.abs(centerX), Math.abs(centerY)),
                    Math.max(Math.abs(width), Math.abs(height))) <= 2.0f) {
                centerX *= inputSize;
                centerY *= inputSize;
                width *= inputSize;
                height *= inputSize;
            }
            float left = clamp((centerX - width / 2f - padX) / scale, 0f, sourceWidth);
            float top = clamp((centerY - height / 2f - padY) / scale, 0f, sourceHeight);
            float right = clamp((centerX + width / 2f - padX) / scale, 0f, sourceWidth);
            float bottom = clamp((centerY + height / 2f - padY) / scale, 0f, sourceHeight);
            if (right > left && bottom > top) {
                detections.add(new Detection(new RectF(left, top, right, bottom), confidence, null));
            }
        }
        return detections;
    }

    @NonNull
    static List<Detection> nonMaximumSuppression(@NonNull List<Detection> candidates,
                                                 float threshold) {
        List<Detection> sorted = new ArrayList<>(candidates);
        sorted.sort((first, second) -> Float.compare(second.confidence, first.confidence));
        List<Detection> selected = new ArrayList<>();
        for (Detection candidate : sorted) {
            boolean overlaps = false;
            for (Detection kept : selected) {
                if (intersectionOverUnion(candidate.bounds, kept.bounds) >= threshold) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) selected.add(candidate);
            if (selected.size() == 12) break;
        }
        selected.sort(Comparator.comparingDouble(item -> item.bounds.centerY() * 10000.0
                + item.bounds.centerX()));
        return selected;
    }

    static float intersectionOverUnion(RectF first, RectF second) {
        float left = Math.max(first.left, second.left);
        float top = Math.max(first.top, second.top);
        float right = Math.min(first.right, second.right);
        float bottom = Math.min(first.bottom, second.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float union = first.width() * first.height() + second.width() * second.height() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    @NonNull
    static Bitmap crop(@NonNull Bitmap source, @NonNull RectF bounds) {
        float paddingX = bounds.width() * 0.06f;
        float paddingY = bounds.height() * 0.06f;
        int left = Math.max(0, (int) Math.floor(bounds.left - paddingX));
        int top = Math.max(0, (int) Math.floor(bounds.top - paddingY));
        int right = Math.min(source.getWidth(), (int) Math.ceil(bounds.right + paddingX));
        int bottom = Math.min(source.getHeight(), (int) Math.ceil(bounds.bottom + paddingY));
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }

    private static float value(float[] values, int[] shape, boolean channelsFirst,
                               int detection, int channel) {
        int index = channelsFirst ? channel * shape[2] + detection : detection * shape[2] + channel;
        return values[index];
    }

    private static final class Letterbox {
        final Bitmap bitmap;
        final float scale;
        final float padX;
        final float padY;
        Letterbox(Bitmap bitmap, float scale, float padX, float padY) {
            this.bitmap = bitmap; this.scale = scale; this.padX = padX; this.padY = padY;
        }
    }

    private static Letterbox letterbox(Bitmap source, int size) {
        float scale = Math.min(size / (float) source.getWidth(), size / (float) source.getHeight());
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        float padX = (size - width) / 2f;
        float padY = (size - height) / 2f;
        Bitmap resized = Bitmap.createScaledBitmap(source, width, height, true);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.rgb(114, 114, 114));
        canvas.drawBitmap(resized, padX, padY, new Paint(Paint.FILTER_BITMAP_FLAG));
        if (resized != source) resized.recycle();
        return new Letterbox(output, scale, padX, padY);
    }

    private static ByteBuffer bitmapToBuffer(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        ByteBuffer buffer = ByteBuffer.allocateDirect(pixels.length * 3 * 4)
                .order(ByteOrder.nativeOrder());
        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xff) / 255f);
            buffer.putFloat(((pixel >> 8) & 0xff) / 255f);
            buffer.putFloat((pixel & 0xff) / 255f);
        }
        buffer.rewind();
        return buffer;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean assetExists(Context context, String path) {
        try (InputStream ignored = context.getAssets().open(path)) { return true; }
        catch (IOException ignored) { return false; }
    }

    private static String readText(Context context, String path) throws IOException {
        try (InputStream input = context.getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static MappedByteBuffer mapModel(Context context, String path) throws IOException {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(path);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            return stream.getChannel().map(FileChannel.MapMode.READ_ONLY,
                    descriptor.getStartOffset(), descriptor.getDeclaredLength());
        }
    }
}
