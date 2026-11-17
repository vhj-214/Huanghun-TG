package org.telegram.ui.Components.glass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Layered fallback glass for views that do not have a captured backdrop source.
 * It is intentionally shader-free, but keeps the same visual grammar as the
 * RenderNode material: milky body, directional volume light, dark lower edge,
 * thin inner rim and a broad reflected highlight.
 */
public final class LiquidGlassSurfaceDrawable extends Drawable {
    private final float radius;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lowerShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private LinearGradient bodyGradient;
    private LinearGradient lowerGradient;
    private LinearGradient highlightGradient;
    private RadialGradient glowGradient;
    private int gradientWidth = -1;
    private int gradientHeight = -1;
    private boolean gradientDark;
    private int drawableAlpha = 255;

    public LiquidGlassSurfaceDrawable(float radius) {
        this.radius = Math.max(0f, radius);
        body.setStyle(Paint.Style.FILL);
        cornerGlow.setStyle(Paint.Style.FILL);
        lowerShade.setStyle(Paint.Style.FILL);
        highlight.setStyle(Paint.Style.FILL);
        rim.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (getBounds().isEmpty() || drawableAlpha == 0) return;
        rect.set(getBounds());
        final boolean dark = Theme.isCurrentThemeDark();
        final float a = drawableAlpha / 255f;
        ensureGradients(dark);

        body.setShader(bodyGradient);
        body.setAlpha(drawableAlpha);
        canvas.drawRoundRect(rect, radius, radius, body);

        // A localized upper-left glow creates the inflated, optical thickness
        // that a flat diagonal gradient cannot provide.
        cornerGlow.setShader(glowGradient);
        cornerGlow.setAlpha(drawableAlpha);
        canvas.drawRoundRect(rect, radius, radius, cornerGlow);

        lowerShade.setShader(lowerGradient);
        lowerShade.setAlpha(drawableAlpha);
        canvas.drawRoundRect(rect, radius, radius, lowerShade);

        // Broad reflected band, clipped by the same rounded surface.
        highlight.setShader(highlightGradient);
        highlight.setAlpha(drawableAlpha);
        canvas.drawRoundRect(rect, radius, radius, highlight);

        float outer = Math.max(1f, AndroidUtilities.dpf2(0.9f));
        rim.setStrokeWidth(outer);
        rim.setColor(LiquidGlassMaterial.withAlpha(LiquidGlassMaterial.outerRim(dark), a));
        canvas.drawRoundRect(rect, radius, radius, rim);
        rim.setStrokeWidth(Math.max(1f, AndroidUtilities.dpf2(2.0f)));
        rim.setColor(LiquidGlassMaterial.withAlpha(LiquidGlassMaterial.innerRim(dark), a));
        canvas.drawRoundRect(rect, radius, radius, rim);
    }

    private void ensureGradients(boolean dark) {
        int width = getBounds().width();
        int height = getBounds().height();
        if (bodyGradient != null && width == gradientWidth && height == gradientHeight && dark == gradientDark) return;

        gradientWidth = width;
        gradientHeight = height;
        gradientDark = dark;
        float left = getBounds().left;
        float top = getBounds().top;
        float right = getBounds().right;
        float bottom = getBounds().bottom;
        float cx = left + width * 0.20f;
        float cy = top + height * 0.12f;
        float glowRadius = Math.max(width, height) * 0.92f;

        bodyGradient = new LinearGradient(left, top, right, bottom,
                new int[] { LiquidGlassMaterial.bodyTop(dark), LiquidGlassMaterial.bodyMiddle(dark), LiquidGlassMaterial.bodyBottom(dark) },
                new float[] { 0f, 0.42f, 1f }, Shader.TileMode.CLAMP);
        glowGradient = new RadialGradient(cx, cy, glowRadius,
                new int[] { LiquidGlassMaterial.cornerGlow(dark), dark ? 0x10000000 : 0x1EFFFFFF, 0x00000000 },
                new float[] { 0f, 0.32f, 1f }, Shader.TileMode.CLAMP);
        lowerGradient = new LinearGradient(left, top, left, bottom,
                0x00000000, LiquidGlassMaterial.lowerShade(dark), Shader.TileMode.CLAMP);
        highlightGradient = new LinearGradient(left, top, right, bottom,
                LiquidGlassMaterial.broadReflection(dark), 0x00000000, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onBoundsChange(@NonNull android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        bodyGradient = null;
        glowGradient = null;
        lowerGradient = null;
        highlightGradient = null;
    }

    @Override public void setAlpha(int alpha) { drawableAlpha = Math.max(0, Math.min(255, alpha)); invalidateSelf(); }
    @Override public int getAlpha() { return drawableAlpha; }
    @Override public void setColorFilter(ColorFilter colorFilter) {
        body.setColorFilter(colorFilter);
        cornerGlow.setColorFilter(colorFilter);
        lowerShade.setColorFilter(colorFilter);
        highlight.setColorFilter(colorFilter);
        rim.setColorFilter(colorFilter);
        invalidateSelf();
    }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
