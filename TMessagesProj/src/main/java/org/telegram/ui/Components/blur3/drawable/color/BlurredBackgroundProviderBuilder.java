package org.telegram.ui.Components.blur3.drawable.color;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.os.Build;

import androidx.annotation.ColorInt;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

public class BlurredBackgroundProviderBuilder implements BlurredBackgroundProvider {
    private final Theme.ResourcesProvider resourcesProvider;

    public BlurredBackgroundProviderBuilder(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        setShadowLayer(dpf2(1), 0, dpf2(1 / 3f));
        setStrokeWidth(dpf2(1), dpf2(2 / 3f));
    }

    public interface ColorProvider {
        int getColor(Theme.ResourcesProvider resourcesProvider, boolean isDark);
    }

    private ColorProvider shadowColor;
    private ColorProvider strokeColorTop;
    private ColorProvider strokeColorBottom;
    private ColorProvider backgroundColor;
    private ColorProvider liquidGlassTintColor;
    private float strokeWidthTop, strokeWidthBottom, shadowRadius, shadowDx, shadowDy;

    public BlurredBackgroundProviderBuilder setShadowColor(@ColorInt int light, @ColorInt int dark) {
        shadowColor = create(light, dark);
        return this;
    }

    public BlurredBackgroundProviderBuilder setStrokeColorTop(@ColorInt int light, @ColorInt int dark) {
        strokeColorTop = create(light, dark);
        return this;
    }

    public BlurredBackgroundProviderBuilder setStrokeColorBottom(@ColorInt int light, @ColorInt int dark) {
        strokeColorBottom = create(light, dark);
        return this;
    }

    public BlurredBackgroundProviderBuilder setBackgroundColor(ColorProvider colorProvider) {
        backgroundColor = colorProvider;
        return this;
    }

    public BlurredBackgroundProviderBuilder setLiquidGlassTintColor(ColorProvider colorProvider) {
        liquidGlassTintColor = colorProvider;
        return this;
    }

    public BlurredBackgroundProviderBuilder setShadowLayer(float radius, float dx, float dy) {
        shadowRadius = radius;
        shadowDx = dx;
        shadowDy = dy;
        return this;
    }

    public BlurredBackgroundProviderBuilder setStrokeWidth(float top, float bottom) {
        strokeWidthTop = top;
        strokeWidthBottom = bottom;
        return this;
    }



    @Override
    public int getShadowColor() {
        if (useLiquidGlassMaterial()) {
            return Theme.multAlpha(get(shadowColor, 0), 0.32f);
        }
        return get(shadowColor, 0);
    }

    @Override
    public int getBackgroundColor() {
        if (useLiquidGlassMaterial()) {
            // Pass only an explicitly selected low-alpha tint to the shader;
            // keep the live source visible instead of covering it with a panel.
            return get(liquidGlassTintColor, 0);
        }
        if (useLiquidGlassFallbackMaterial()) {
            final int color = get(backgroundColor, 0);
            // Android 12 has the live blur source but not the AGSL refraction
            // effect. Keep the source visible and add only a milky glass veil.
            return color != 0 ? Theme.multAlpha(color, 0.24f)
                : (isDark() ? 0x24000000 : 0x24FFFFFF);
        }
        return get(backgroundColor, 0);
    }

    @Override
    public int getStrokeColorTop() {
        if (useLiquidGlassMaterial()) {
            return 0;
        }
        return get(strokeColorTop, 0);
    }

    @Override
    public int getStrokeColorBottom() {
        if (useLiquidGlassMaterial()) {
            return 0;
        }
        return get(strokeColorBottom, 0);
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

    private static boolean useLiquidGlassMaterial() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
    }

    private static boolean useLiquidGlassFallbackMaterial() {
        return !useLiquidGlassMaterial()
            && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
    }


    public BlurredBackgroundProvider build() {
        return this;
    }

    private int get(ColorProvider provider, int defaultValue) {
        return provider != null ? provider.getColor(resourcesProvider, isDark()) : defaultValue;
    }

    private boolean isDark() {
        return resourcesProvider instanceof DarkThemeResourceProvider ||
            resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
    }

    private static ColorProvider create(@ColorInt int colorInLightMode, @ColorInt int colorInDarkMode) {
        return (p, idDark) -> (idDark ? colorInDarkMode : colorInLightMode);
    }
}
