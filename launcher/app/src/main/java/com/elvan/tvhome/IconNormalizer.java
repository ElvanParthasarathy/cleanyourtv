package com.elvan.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Industry-standard launcher icon normalizer following Google AOSP Launcher3 / Pixel Launcher standards.
 *
 * 1. Legacy / Transparent Icons (e.g. Google Play Store): Detects transparent border alpha,
 *    draws an official clean white circular badge (#FFFFFF), and centers the glyph within the
 *    AOSP safe-zone ratio (~62% size) so corners are never clipped.
 * 2. Full-bleed Solid Icons (e.g. Netflix, Prime Video, YouTube): Clips the full solid icon to the circle.
 */
public final class IconNormalizer {

    private IconNormalizer() {}

    public static Drawable normalize(Context ctx, Drawable rawIcon) {
        if (rawIcon == null) return null;

        float density = ctx.getResources().getDisplayMetrics().density;
        int targetSize = Math.max(160, Math.round(96 * density));

        Bitmap srcBitmap = drawableToBitmap(rawIcon, targetSize, targetSize);
        if (srcBitmap == null) return rawIcon;

        Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        // Anti-aliased circular clip path
        Path circlePath = new Path();
        circlePath.addCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f, Path.Direction.CW);

        boolean isTransparent = hasTransparentBorders(srcBitmap);

        if (isTransparent) {
            // Draw clean white circular base (industry standard for Google Play & floating glyphs)
            Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            whitePaint.setColor(Color.WHITE);
            canvas.drawCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f, whitePaint);

            // AOSP safe zone ratio (~62% of diameter)
            int glyphSize = Math.round(targetSize * 0.62f);
            int offset = (targetSize - glyphSize) / 2;
            Rect dest = new Rect(offset, offset, offset + glyphSize, offset + glyphSize);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(srcBitmap, null, dest, paint);
        } else {
            // Full-bleed icon: clip to circle
            canvas.save();
            canvas.clipPath(circlePath);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(srcBitmap, null, new Rect(0, 0, targetSize, targetSize), paint);
            canvas.restore();
        }

        return new BitmapDrawable(ctx.getResources(), output);
    }

    private static boolean hasTransparentBorders(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w < 4 || h < 4) return false;

        // Sample corners (inset slightly to avoid edge anti-aliasing artifacts)
        int insetX = Math.max(1, w / 16);
        int insetY = Math.max(1, h / 16);

        int[] samplePixels = new int[] {
            bitmap.getPixel(insetX, insetY),
            bitmap.getPixel(w - 1 - insetX, insetY),
            bitmap.getPixel(insetX, h - 1 - insetY),
            bitmap.getPixel(w - 1 - insetX, h - 1 - insetY),
            bitmap.getPixel(w / 2, insetY),
            bitmap.getPixel(w / 2, h - 1 - insetY),
            bitmap.getPixel(insetX, h / 2),
            bitmap.getPixel(w - 1 - insetX, h / 2)
        };

        int transparentCount = 0;
        for (int p : samplePixels) {
            int alpha = (p >>> 24);
            if (alpha < 40) {
                transparentCount++;
            }
        }
        // If 2 or more sample points on perimeter/corners are transparent, it's a shaped/floating icon
        return transparentCount >= 2;
    }

    private static Bitmap drawableToBitmap(Drawable drawable, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Rect old = drawable.copyBounds();
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        drawable.setBounds(old);
        return bitmap;
    }
}
