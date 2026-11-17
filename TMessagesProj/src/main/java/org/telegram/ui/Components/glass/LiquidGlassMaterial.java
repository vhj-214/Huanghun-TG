package org.telegram.ui.Components.glass;

import android.graphics.Color;

/**
 * Single visual contract for every Huanghun liquid-glass surface.
 *
 * Backdrop-aware surfaces use these values in the RuntimeShader; surfaces that
 * cannot capture a backdrop use the same values for their software layers.
 * Keeping the numbers here prevents the Telegram chrome and Huanghun pages
 * from drifting into two unrelated glass styles.
 */
public final class LiquidGlassMaterial {
    public static final float REFRACTION_INDEX = 1.14f;
    public static final float REFRACTION_INTENSITY = 0.78f;
    public static final float SPECULAR_STRENGTH = 1.85f;
    public static final float DISPERSION_STRENGTH = 1.00f;
    public static final float FROST_AMOUNT = 0.62f;
    public static final float FRESNEL_STRENGTH = 1.65f;
    public static final float REFLECTION_STRENGTH = 1.55f;
    public static final float GRAIN_AMOUNT = 0.10f;
    public static final float BODY_ALPHA = 0.16f;
    public static final float BACKDROP_ALPHA = 0.82f;

    private LiquidGlassMaterial() {
    }

    public static int bodyTop(boolean dark) {
        return dark ? 0x642A3944 : 0x82FFFFFF;
    }

    public static int bodyMiddle(boolean dark) {
        return dark ? 0x2618212A : 0x2CFFFFFF;
    }

    public static int bodyBottom(boolean dark) {
        return dark ? 0x3A10151A : 0x261C2730;
    }

    public static int cornerGlow(boolean dark) {
        return dark ? 0x42FFFFFF : 0x76FFFFFF;
    }

    public static int lowerShade(boolean dark) {
        return dark ? 0x3C000000 : 0x18000000;
    }

    public static int broadReflection(boolean dark) {
        return dark ? 0x1EFFFFFF : 0x38FFFFFF;
    }

    public static int outerRim(boolean dark) {
        return dark ? 0xA8FFFFFF : 0xD0FFFFFF;
    }

    public static int innerRim(boolean dark) {
        return dark ? 0x42000000 : 0x30000000;
    }

    public static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(Color.alpha(color) * alpha),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
