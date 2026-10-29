package com.example.nt118_project.ui.profile;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Chuẩn bị ảnh chọn từ thư viện để gửi POST /auth/me/avatar (server nhận JPEG/PNG/WebP tối đa 2MB).
 * Ảnh đủ nhỏ và đúng định dạng thì gửi nguyên bản (server tự xoay theo EXIF và cắt 512x512);
 * ngược lại thu nhỏ, xoay theo EXIF rồi nén JPEG. Chạy trên thread nền.
 */
public final class AvatarImage {

    public static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final List<String> ACCEPTED = Arrays.asList("image/jpeg", "image/png", "image/webp");
    /** Server cắt về 512x512, giữ 1024 cạnh ngắn là đủ nét. */
    private static final int TARGET_SIZE = 1024;

    public final byte[] bytes;
    public final String mimeType;

    private AvatarImage(byte[] bytes, String mimeType) {
        this.bytes = bytes;
        this.mimeType = mimeType;
    }

    @NonNull
    public static AvatarImage prepare(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        String mime = resolver.getType(uri);
        byte[] original = read(resolver, uri);
        if (mime != null && ACCEPTED.contains(mime) && original.length <= MAX_BYTES) {
            return new AvatarImage(original, mime);
        }
        Bitmap bitmap = decodeScaled(original);
        if (bitmap == null) {
            throw new IOException("Không đọc được ảnh");
        }
        bitmap = rotate(bitmap, orientation(original));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int quality = 90; quality >= 50; quality -= 10) {
            out.reset();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            if (out.size() <= MAX_BYTES) {
                break;
            }
        }
        bitmap.recycle();
        if (out.size() > MAX_BYTES) {
            throw new IOException("Ảnh quá lớn");
        }
        return new AvatarImage(out.toByteArray(), "image/jpeg");
    }

    private static byte[] read(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Không mở được ảnh");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                // Chặn file khổng lồ (video đổi đuôi, ảnh RAW) làm tràn bộ nhớ
                if (out.size() > 40L * 1024 * 1024) {
                    throw new IOException("Ảnh quá lớn");
                }
            }
            return out.toByteArray();
        }
    }

    @Nullable
    private static Bitmap decodeScaled(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_SIZE) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static int orientation(byte[] data) {
        try (InputStream in = new java.io.ByteArrayInputStream(data)) {
            return new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap rotate(Bitmap bitmap, int orientation) {
        int degrees;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270;
                break;
            default:
                return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }
}
