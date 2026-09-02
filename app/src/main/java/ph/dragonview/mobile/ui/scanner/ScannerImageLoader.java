package ph.dragonview.mobile.ui.scanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;

/** Decodes a bounded, correctly oriented bitmap from a camera or document URI. */
final class ScannerImageLoader {
    private ScannerImageLoader() {
    }

    @NonNull
    static Bitmap load(@NonNull Context context, @NonNull Uri uri, int maximumEdge)
            throws IOException {
        if (maximumEdge <= 0) throw new IllegalArgumentException("maximumEdge must be positive");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = requireStream(context, uri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("The selected file is not a readable image");
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateSampleSize(
                bounds.outWidth, bounds.outHeight, maximumEdge);
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap decoded;
        try (InputStream stream = requireStream(context, uri)) {
            decoded = BitmapFactory.decodeStream(stream, null, decodeOptions);
        }
        if (decoded == null) throw new IOException("The selected image could not be decoded");

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream stream = requireStream(context, uri)) {
            ExifInterface exif = new ExifInterface(stream);
            orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
            // A valid image can omit EXIF data; in that case its decoded orientation is retained.
        }

        Matrix transform = orientationTransform(orientation);
        if (transform.isIdentity()) return decoded;
        Bitmap oriented = Bitmap.createBitmap(
                decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), transform, true);
        if (oriented != decoded) decoded.recycle();
        return oriented;
    }

    static int calculateSampleSize(int width, int height, int maximumEdge) {
        if (width <= 0 || height <= 0 || maximumEdge <= 0) {
            throw new IllegalArgumentException("Image dimensions and maximumEdge must be positive");
        }
        int sampleSize = 1;
        int largestEdge = Math.max(width, height);
        while ((largestEdge + (long) sampleSize - 1) / sampleSize > maximumEdge) {
            if (sampleSize > Integer.MAX_VALUE / 2) {
                throw new IllegalArgumentException("Image dimensions are too large");
            }
            sampleSize *= 2;
        }
        return sampleSize;
    }

    @NonNull
    private static InputStream requireStream(@NonNull Context context, @NonNull Uri uri)
            throws IOException {
        InputStream stream = context.getContentResolver().openInputStream(uri);
        if (stream == null) throw new IOException("The selected image cannot be opened");
        return stream;
    }

    @NonNull
    private static Matrix orientationTransform(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1.0f, 1.0f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180.0f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180.0f);
                matrix.postScale(-1.0f, 1.0f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90.0f);
                matrix.postScale(-1.0f, 1.0f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90.0f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90.0f);
                matrix.postScale(-1.0f, 1.0f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90.0f);
                break;
            default:
                break;
        }
        return matrix;
    }
}
