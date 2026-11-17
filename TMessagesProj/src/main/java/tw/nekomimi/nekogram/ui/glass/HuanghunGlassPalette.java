package tw.nekomimi.nekogram.ui.glass;

import android.graphics.Color;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;

/**
 * Huanghun unified iOS-style liquid-glass palette.
 *
 * <p>One palette instance is bound to exactly one glass surface. Every surface
 * captures the palette values at creation time, so two panels on screen may use
 * different variants without stepping on each other.</p>
 *
 * <p>The look follows the translucent material used by iOS system surfaces:
 * a heavily blurred backdrop, a milky body tint between 25% and 50% opacity, a
 * bright one-pixel top rim, a dark one-pixel bottom rim and a soft ambient
 * shadow. Values react to the theme and to the wallpaper luminance so text
 * stays readable on bright wallpapers without turning the panel into an opaque
 * sheet on dark ones.</p>
 */
public final class HuanghunGlassPalette implements BlurredBackgroundProvider {

    public static final int VARIANT_ACTION_BAR = 0;
    public static final int VARIANT_PANEL = 1;
    public static final int VARIANT_CARD = 2;
    public static final int VARIANT_OVERLAY = 3;
    public static final int VARIANT_KEYBOARD = 4;

    private int variant = VARIANT_PANEL;
    private boolean dark;
    private float wallpaperLuminance = 0.5f;

    private int tintColor;
    private int strokeColorTop;
    private int strokeColorBottom;
    private int shadowColor;
    private float strokeWidthTop;
    private float strokeWidthBottom;
    private float shadowRadius;
    private float shadowDx;
    private float shadowDy;
    private float backdropAlpha = 1f;

    public HuanghunGlassPalette() {
        update(VARIANT_PANEL, false, 0.5f);
    }

    public HuanghunGlassPalette(int variant, boolean dark, float wallpaperLuminance) {
        update(variant, dark, wallpaperLuminance);
    }

    public int getVariant() {
        return variant;
    }

    public boolean isDark() {
        return dark;
    }

    /**
     * Recomputes every token. Safe to call on every theme change; it only
     * writes fields and never allocates a Drawable or a Bitmap.
     */
    public void update(int variant, boolean dark, float wallpaperLuminance) {
        this.variant = variant;
        this.dark = dark;
        this.wallpaperLuminance = Float.isNaN(wallpaperLuminance) ? 0.5f : Math.max(0f, Math.min(1f, wallpaperLuminance));

        int alpha;
        switch (variant) {
            case VARIANT_ACTION_BAR:
                alpha = 0x66;
                break;
            case VARIANT_CARD:
                alpha = 0x59;
                break;
            case VARIANT_OVERLAY:
                alpha = 0x40;
                break;
            case VARIANT_KEYBOARD:
                alpha = 0x6B;
                break;
            case VARIANT_PANEL:
            default:
                alpha = 0x73;
                break;
        }

        // Bright wallpapers need a denser body so dark text keeps contrast;
        // very dark wallpapers can stay more transparent in light themes and
        // must stay a bit lighter in dark themes to keep the panel visible.
        if (this.wallpaperLuminance > 0.72f) {
            alpha += dark ? 0x1A : 0x14;
        } else if (this.wallpaperLuminance < 0.22f) {
            alpha -= dark ? 0x0D : 0x0D;
        }
        alpha = Math.max(0x2E, Math.min(0x8C, alpha));

        if (dark) {
            tintColor = Color.argb(alpha, 0x1C, 0x1C, 0x1E);
            strokeColorTop = Color.argb(this.wallpaperLuminance > 0.6f ? 0x3D : 0x2E, 0xFF, 0xFF, 0xFF);
            strokeColorBottom = Color.argb(0x52, 0x00, 0x00, 0x00);
            shadowColor = Color.argb(0x66, 0x00, 0x00, 0x00);
            shadowRadius = AndroidUtilities.dp(20);
            shadowDx = 0;
            shadowDy = AndroidUtilities.dp(8);
        } else {
            tintColor = Color.argb(alpha, 0xFF, 0xFF, 0xFF);
            strokeColorTop = Color.argb(this.wallpaperLuminance > 0.6f ? 0x8C : 0x70, 0xFF, 0xFF, 0xFF);
            strokeColorBottom = Color.argb(0x12, 0x00, 0x00, 0x00);
            shadowColor = Color.argb(0x1C, 0x00, 0x00, 0x00);
            shadowRadius = AndroidUtilities.dp(18);
            shadowDx = 0;
            shadowDy = AndroidUtilities.dp(6);
        }

        final float thin = Math.max(1f, AndroidUtilities.dpf2(1f));
        strokeWidthTop = thin;
        strokeWidthBottom = thin;
        backdropAlpha = 1f;
    }

    /** Denser variant used by text-heavy panels such as the number pad. */
    public void makeMoreOpaque() {
        final int alpha = Math.min(0xFF, Color.alpha(tintColor) + 0x1A);
        tintColor = Color.argb(alpha, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor));
    }

    public int getTintColor() {
        return tintColor;
    }

    public float getWallpaperLuminance() {
        return wallpaperLuminance;
    }

    public float getLiquidThicknessDp() {
        switch (variant) {
            case VARIANT_ACTION_BAR:
                return 12f;
            case VARIANT_KEYBOARD:
                return 10f;
            default:
                return 16f;
        }
    }

    public float getLiquidIntensity() {
        return variant == VARIANT_OVERLAY ? 0.7f : 0.92f;
    }

    public float getLiquidIndex() {
        return 1.55f;
    }

    /* BlurredBackgroundProvider */

    @Override
    public int getShadowColor() {
        return shadowColor;
    }

    @Override
    public int getBackgroundColor() {
        return tintColor;
    }

    @Override
    public int getStrokeColorTop() {
        return strokeColorTop;
    }

    @Override
    public int getStrokeColorBottom() {
        return strokeColorBottom;
    }

    @Override
    public float getStrokeWidthTop() {
        return strokeWidthTop;
    }

    @Override
    public float getStrokeWidthBottom() {
        return strokeWidthBottom;
    }

    @Override
    public float getShadowRadius() {
        return shadowRadius;
    }

    @Override
    public float getShadowDx() {
        return shadowDx;
    }

    @Override
    public float getShadowDy() {
        return shadowDy;
    }

    @Override
    public float getBackdropAlpha() {
        return backdropAlpha;
    }
}
