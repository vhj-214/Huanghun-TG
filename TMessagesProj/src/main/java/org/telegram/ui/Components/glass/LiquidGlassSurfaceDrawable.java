package org.telegram.ui.Components.glass;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Lightweight glass surface for views that do not have a captured blur source.
 * It deliberately uses two cheap gradient passes instead of RuntimeShader or
 * bitmap blur, so settings/custom pages cannot trigger GPU stalls or black-frame
 * failures. Live wallpaper remains visible through the low-alpha body.
 */
public final class LiquidGlassSurfaceDrawable extends Drawable {
    private final float radius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private LinearGradient fillGradient;
    private LinearGradient sheenGradient;
    private int gradientWidth = -1;
    private int gradientHeight = -1;
    private boolean gradientDark;
    private int drawableAlpha = 255;

    public LiquidGlassSurfaceDrawable(float radius) {
        this.radius = Math.max(0f, radius);
        fill.setStyle(Paint.Style.FILL);
        sheen.setStyle(Paint.Style.FILL);
        rim.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (getBounds().isEmpty() || drawableAlpha == 0) return;
        rect.set(getBounds());
        final boolean dark = Theme.isCurrentThemeDark();
        final float a = drawableAlpha / 255f;
        final float width = Math.max(1f, AndroidUtilities.dpf2(0.72f));

        ensureGradients(dark);
        fill.setShader(fillGradient);
        fill.setAlpha(drawableAlpha);
        canvas.drawRoundRect(rect, radius, radius, fill);

        // One additional low-alpha highlight gives the surface depth without
        // adding texture samples or an off-screen bitmap.
        sheen.setShader(sheenGradient);
        sheen.setAlpha(drawableAlpha);
        canvas.drawRoundRect(rect, radius, radius, sheen);

        rim.setStrokeWidth(width);
        rim.setColor(withAlpha(dark ? 0x72FFFFFF : 0x88FFFFFF, a));
        canvas.drawRoundRect(rect, radius, radius, rim);
        rim.setStrokeWidth(Math.max(1f, width * 0.72f));
        rim.setColor(withAlpha(dark ? 0x30000000 : 0x26000000, a));
        canvas.drawRoundRect(rect, radius, radius, rim);
    }

    private void ensureGradients(boolean dark) {
        int width = getBounds().width();
        int height = getBounds().height();
        if (fillGradient != null && width == gradientWidth && height == gradientHeight && dark == gradientDark) return;
        gradientWidth = width;
        gradientHeight = height;
        gradientDark = dark;
        fillGradient = new LinearGradient(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom,
                dark ? new int[] { 0x421F3039, 0x14121C24, 0x26000000 }
                     : new int[] { 0x52FFFFFF, 0x12FFFFFF, 0x12000000 },
                new float[] { 0f, 0.44f, 1f }, Shader.TileMode.CLAMP);
        sheenGradient = new LinearGradient(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom,
                dark ? 0x22000000 : 0x2AFFFFFF, 0x00000000, Shader.TileMode.CLAMP);
    }

    @Override protected void onBoundsChange(@NonNull android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        fillGradient = null;
        sheenGradient = null;
    }

    private static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(Color.alpha(color) * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override public void setAlpha(int alpha) { drawableAlpha = Math.max(0, Math.min(255, alpha)); invalidateSelf(); }
    @Override public int getAlpha() { return drawableAlpha; }
    @Override public void setColorFilter(ColorFilter colorFilter) { fill.setColorFilter(colorFilter); sheen.setColorFilter(colorFilter); rim.setColorFilter(colorFilter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
