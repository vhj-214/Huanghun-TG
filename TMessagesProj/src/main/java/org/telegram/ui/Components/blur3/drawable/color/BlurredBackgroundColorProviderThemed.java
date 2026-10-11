package org.telegram.ui.Components.blur3.drawable.color;

import android.os.Build;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;

public class BlurredBackgroundColorProviderThemed implements BlurredBackgroundColorProvider {

    private final Theme.ResourcesProvider resourcesProvider;
    private final int backgroundColorId;
    private float alpha;

    public BlurredBackgroundColorProviderThemed(Theme.ResourcesProvider resourcesProvider, int backgroundColorId) {
        this(resourcesProvider, backgroundColorId, LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f);
    }

    public BlurredBackgroundColorProviderThemed(Theme.ResourcesProvider resourcesProvider, int backgroundColorId, float alpha) {
        this.resourcesProvider = resourcesProvider;
        this.backgroundColorId = backgroundColorId;
        this.alpha = alpha;

        updateColors();
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
        updateColors();
    }

    private int backgroundColor, shadowColor, strokeColorTop, strokeColorBottom;

    public boolean isDark() {
        final int color = Theme.getColor(backgroundColorId, resourcesProvider);
        return AndroidUtilities.computePerceivedBrightness(color) < .721f;
    }

    public void updateColors() {
        final int color = Theme.getColor(backgroundColorId, resourcesProvider);
        backgroundColor = Theme.multAlpha(color, alpha);

        if (isDark()) {
            strokeColorTop = 0x28FFFFFF;
            strokeColorBottom = 0x14FFFFFF;
            shadowColor = 0;
        } else {
            strokeColorTop = 0xFFFFFFFF;
            strokeColorBottom = 0xFFFFFFFF;
            shadowColor = 0x20000000; //0x19000000;
        }
    }

    @Override
    public int getShadowColor() {
        if (useLiquidGlassMaterial()) {
            return 0;
        }
        return shadowColor;
    }

    @Override
    public int getBackgroundColor() {
        if (useLiquidGlassMaterial()) {
            return 0;
        }
        if (useLiquidGlassFallbackMaterial()) {
            // Android 12/12L can provide the live blur source, but cannot run
            // the Android 13 AGSL refraction pass. Preserve that source and
            // use a light glass veil instead of the old opaque surface.
            return ColorUtils.setAlphaComponent(backgroundColor,
                Math.min(Color.alpha(backgroundColor), 0x38));
        }
        return backgroundColor;
    }

    @Override
    public int getStrokeColorTop() {
        if (useLiquidGlassMaterial()) {
            return 0;
        }
        return strokeColorTop;
    }

    @Override
    public int getStrokeColorBottom() {
        if (useLiquidGlassMaterial()) {
            return 0;
        }
        return strokeColorBottom;
    }

    private static boolean useLiquidGlassMaterial() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
    }

    private static boolean useLiquidGlassFallbackMaterial() {
        return !useLiquidGlassMaterial()
            && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
    }
}
