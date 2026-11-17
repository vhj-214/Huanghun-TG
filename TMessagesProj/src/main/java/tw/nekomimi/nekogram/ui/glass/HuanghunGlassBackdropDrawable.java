package tw.nekomimi.nekogram.ui.glass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Page background that paints the blurred wallpaper backdrop produced by
 * {@link HuanghunGlassSurface}.
 *
 * <p>This drawable always paints something: either the blurred bitmap or an
 * opaque theme colour. That guarantee is what removes the black flash on page
 * open and on wallpaper change, because the window background can never show
 * through a page that fills its own bounds.</p>
 */
public final class HuanghunGlassBackdropDrawable extends Drawable {

    private final HuanghunGlassSurface surface;
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha = 255;

    public HuanghunGlassBackdropDrawable(@NonNull HuanghunGlassSurface surface) {
        this.surface = surface;
    }

    @NonNull
    public HuanghunGlassSurface getSurface() {
        return surface;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        if (alpha > 0) {
            final int save = canvas.save();
            if (alpha < 255) {
                // A translucent backdrop is used by page-open animations only.
                // Painting the opaque fallback first keeps the frame composed.
                fallbackPaint.setColor(surface.getSafeFallbackColor());
                fallbackPaint.setAlpha(alpha);
                canvas.drawRect(bounds, fallbackPaint);
                fallbackPaint.setAlpha(255);
            }
            if (surface.drawBackdrop(canvas, bounds.width(), bounds.height())) {
                canvas.restoreToCount(save);
                return;
            }
            canvas.restoreToCount(save);
        }
        fallbackPaint.setColor(surface.getSafeFallbackColor());
        canvas.drawRect(bounds, fallbackPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha != alpha) {
            this.alpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
