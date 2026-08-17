package tw.nekomimi.nekogram.helpers;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.core.graphics.ColorUtils;

/**
 * 黄昏定制版的通用液态玻璃外观。
 *
 * 该工具只负责半透明材质、高光描边和深浅主题对比度；实际模糊由各页面已有的
 * SizeNotifier/BlurredBackground 容器提供，因此不会破坏 Telegram 原有的滑动和触摸层级。
 */
public final class HuanghunLiquidGlass {
    private HuanghunLiquidGlass() {
    }

    public static GradientDrawable createSurface(int backdropColor, int tintColor, float radiusPx) {
        final boolean dark = ColorUtils.calculateLuminance(backdropColor) < .42d;
        final int whiteMix = dark ? 28 : 194;
        final int tintMix = dark ? 48 : 122;
        final int top = ColorUtils.setAlphaComponent(blend(Color.WHITE, tintColor, .14f), whiteMix);
        final int bottom = ColorUtils.setAlphaComponent(blend(backdropColor, tintColor, .20f), tintMix);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        drawable.setCornerRadius(Math.max(1f, radiusPx));
        int outline = ColorUtils.setAlphaComponent(Color.WHITE, dark ? 100 : 222);
        drawable.setStroke(Math.max(1, Math.round(radiusPx * .036f)), outline);
        return drawable;
    }

    public static GradientDrawable createPill(int backdropColor, int tintColor, float heightPx) {
        return createSurface(backdropColor, tintColor, Math.max(1f, heightPx * .50f));
    }

    public static void applySurface(View view, int backdropColor, int tintColor, float radiusPx) {
        if (view != null) {
            view.setBackground(createSurface(backdropColor, tintColor, radiusPx));
        }
    }

    public static int glassBackdropColor(int color) {
        final boolean dark = ColorUtils.calculateLuminance(color) < .42d;
        return ColorUtils.setAlphaComponent(color, dark ? 172 : 118);
    }

    public static int readableTextColor(int backdropColor) {
        return ColorUtils.calculateLuminance(backdropColor) < .42d ? Color.WHITE : Color.rgb(27, 31, 40);
    }

    private static int blend(int from, int to, float amount) {
        return ColorUtils.blendARGB(from, to, Math.max(0f, Math.min(1f, amount)));
    }
}
