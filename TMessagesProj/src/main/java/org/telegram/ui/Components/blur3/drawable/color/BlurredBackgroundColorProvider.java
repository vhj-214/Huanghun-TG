package org.telegram.ui.Components.blur3.drawable.color;

import androidx.annotation.ColorInt;

public interface BlurredBackgroundColorProvider {
    @ColorInt int getShadowColor();
    @ColorInt int getBackgroundColor();
    @ColorInt int getStrokeColorTop();
    @ColorInt int getStrokeColorBottom();

    /**
     * Opacity of the whole glass fill (blurred backdrop + milky liquid-glass
     * layer + tint) in the range [0, 1].
     *
     * <p>Values below 1 make the sharp wallpaper underneath the surface show
     * through, which is what the Huanghun chat header / top panel need on top
     * of a wallpaper. The default keeps the original fully composed glass so
     * existing surfaces are not affected.</p>
     */
    default float getBackdropAlpha() {
        return 1f;
    }
}
