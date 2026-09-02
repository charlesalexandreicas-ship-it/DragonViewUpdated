package ph.dragonview.mobile.ui.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Exercises Android decoding and EXIF transforms using only generated, app-cache fixtures. */
@RunWith(AndroidJUnit4.class)
public final class ScannerImageLoaderInstrumentedTest {
    private static final int FIXTURE_WIDTH = 120;
    private static final int FIXTURE_HEIGHT = 80;
    private static final int JPEG_COLOR_TOLERANCE = 24;

    private final List<File> createdFiles = new ArrayList<>();
    private final List<Bitmap> loadedBitmaps = new ArrayList<>();
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @After
    public void cleanUpGeneratedFixtures() {
        for (Bitmap bitmap : loadedBitmaps) {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
        for (File file : createdFiles) {
            assertTrue("Could not remove test-created cache file: " + file.getName(),
                    !file.exists() || file.delete());
        }
    }

    @Test
    public void pngWithoutExifRetainsDimensionsAndPixels() throws IOException {
        File image = writeQuadrantImage(FIXTURE_WIDTH, FIXTURE_HEIGHT,
                Bitmap.CompressFormat.PNG);

        Bitmap decoded = load(image, 224);

        assertEquals(FIXTURE_WIDTH, decoded.getWidth());
        assertEquals(FIXTURE_HEIGHT, decoded.getHeight());
        assertQuadrants(decoded, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, 0);
    }

    @Test
    public void largeImageIsDownsampledWithinMaximumEdge() throws IOException {
        File image = writeQuadrantImage(1280, 800, Bitmap.CompressFormat.PNG);

        Bitmap decoded = load(image, 320);

        assertEquals(320, decoded.getWidth());
        assertEquals(200, decoded.getHeight());
        assertTrue(Math.max(decoded.getWidth(), decoded.getHeight()) <= 320);
        assertQuadrants(decoded, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, 0);
    }

    @Test
    public void nonImageFileFailsWithIOException() throws IOException {
        File file = newFixtureFile(".txt");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write("This is not an image".getBytes(StandardCharsets.UTF_8));
        }

        assertThrows(IOException.class,
                () -> ScannerImageLoader.load(context, Uri.fromFile(file), 224));
    }

    @Test
    public void jpegNormalOrientationRetainsQuadrants() throws IOException {
        assertExifOrientation(ExifInterface.ORIENTATION_NORMAL, false,
                Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW);
    }

    @Test
    public void jpegHorizontalFlipReversesColumns() throws IOException {
        assertExifOrientation(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, false,
                Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE);
    }

    @Test
    public void jpegRotate180ReversesRowsAndColumns() throws IOException {
        assertExifOrientation(ExifInterface.ORIENTATION_ROTATE_180, false,
                Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED);
    }

    @Test
    public void jpegVerticalFlipReversesRows() throws IOException {
        assertExifOrientation(ExifInterface.ORIENTATION_FLIP_VERTICAL, false,
                Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN);
    }

    @Test
    public void jpegTransposeSwapsAxesAcrossMainDiagonal() throws IOException {
        assertExifOrientation(ExifInterface.ORIENTATION_TRANSPOSE, true,
                Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW);
    }

    @Test
    public void jpegRotate90SwapsDimensionsAndRotatesClockwise() throws IOException {
        assertExifOrientation(ExifInterface.ORIENTATION_ROTATE_90, true,
                Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN);
    }

    @Test
    public void jpegTransverseSwapsAxesAcrossOppositeDiagonal() throws IOException {
        assertExifOrientation(ExifInterface.ORIENTATION_TRANSVERSE, true,
                Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED);
    }

    @Test
    public void jpegRotate270SwapsDimensionsAndRotatesCounterclockwise() throws IOException {
        assertExifOrientation(ExifInterface.ORIENTATION_ROTATE_270, true,
                Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE);
    }

    private void assertExifOrientation(int orientation, boolean swapsDimensions,
            int topLeft, int topRight, int bottomLeft, int bottomRight) throws IOException {
        File image = writeQuadrantImage(FIXTURE_WIDTH, FIXTURE_HEIGHT,
                Bitmap.CompressFormat.JPEG);
        ExifInterface exif = new ExifInterface(image.getAbsolutePath());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
        exif.saveAttributes();

        Bitmap decoded = load(image, 224);

        assertEquals(swapsDimensions ? FIXTURE_HEIGHT : FIXTURE_WIDTH, decoded.getWidth());
        assertEquals(swapsDimensions ? FIXTURE_WIDTH : FIXTURE_HEIGHT, decoded.getHeight());
        assertQuadrants(decoded, topLeft, topRight, bottomLeft, bottomRight,
                JPEG_COLOR_TOLERANCE);
    }

    private File writeQuadrantImage(int width, int height, Bitmap.CompressFormat format)
            throws IOException {
        File file = newFixtureFile(format == Bitmap.CompressFormat.JPEG ? ".jpg" : ".png");
        Bitmap source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(source);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            canvas.drawRect(0, 0, width / 2f, height / 2f, paint);
            paint.setColor(Color.GREEN);
            canvas.drawRect(width / 2f, 0, width, height / 2f, paint);
            paint.setColor(Color.BLUE);
            canvas.drawRect(0, height / 2f, width / 2f, height, paint);
            paint.setColor(Color.YELLOW);
            canvas.drawRect(width / 2f, height / 2f, width, height, paint);
            try (FileOutputStream output = new FileOutputStream(file)) {
                assertTrue("Generated fixture could not be compressed",
                        source.compress(format, 100, output));
            }
        } finally {
            source.recycle();
        }
        return file;
    }

    private File newFixtureFile(String suffix) throws IOException {
        File file = File.createTempFile("scanner-loader-test-", suffix, context.getCacheDir());
        createdFiles.add(file);
        return file;
    }

    private Bitmap load(File file, int maximumEdge) throws IOException {
        Bitmap bitmap = ScannerImageLoader.load(context, Uri.fromFile(file), maximumEdge);
        loadedBitmaps.add(bitmap);
        return bitmap;
    }

    private static void assertQuadrants(Bitmap bitmap, int topLeft, int topRight,
            int bottomLeft, int bottomRight, int tolerance) {
        // Sample well inside each quadrant, away from JPEG compression and transform edges.
        int left = bitmap.getWidth() / 4;
        int right = bitmap.getWidth() * 3 / 4;
        int top = bitmap.getHeight() / 4;
        int bottom = bitmap.getHeight() * 3 / 4;
        assertColor("top-left", topLeft, bitmap.getPixel(left, top), tolerance);
        assertColor("top-right", topRight, bitmap.getPixel(right, top), tolerance);
        assertColor("bottom-left", bottomLeft, bitmap.getPixel(left, bottom), tolerance);
        assertColor("bottom-right", bottomRight, bitmap.getPixel(right, bottom), tolerance);
    }

    private static void assertColor(String quadrant, int expected, int actual, int tolerance) {
        assertEquals(quadrant + " alpha", Color.alpha(expected), Color.alpha(actual));
        assertEquals(quadrant + " red", Color.red(expected), Color.red(actual), tolerance);
        assertEquals(quadrant + " green", Color.green(expected), Color.green(actual), tolerance);
        assertEquals(quadrant + " blue", Color.blue(expected), Color.blue(actual), tolerance);
    }
}
