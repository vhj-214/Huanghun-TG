package tw.nekomimi.nekogram.helpers;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;

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
        if (!Theme.isDefaultThemeActive()) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(backdropColor);
            drawable.setCornerRadius(Math.max(1f, radiusPx));
            return drawable;
        }
        final boolean dark = ColorUtils.calculateLuminance(backdropColor) < .42d;
        // 默认主题使用真正透光的液态玻璃：只保留淡淡折射，不再形成白色实底。
        final int whiteMix = dark ? 34 : 82;
        final int tintMix = dark ? 52 : 64;
        final int top = ColorUtils.setAlphaComponent(blend(Color.WHITE, tintColor, .14f), whiteMix);
        final int bottom = ColorUtils.setAlphaComponent(blend(backdropColor, tintColor, .20f), tintMix);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        drawable.setCornerRadius(Math.max(1f, radiusPx));
        int outline = ColorUtils.setAlphaComponent(Color.WHITE, dark ? 104 : 168);
        drawable.setStroke(Math.max(1, Math.round(radiusPx * .036f)), outline);
        return drawable;
    }

    public static GradientDrawable createPill(int backdropColor, int tintColor, float heightPx) {
        return createSurface(backdropColor, tintColor, Math.max(1f, heightPx * .50f));
    }

    /** 默认页面内容底板：保持轻度透光但不对消息正文、列表正文额外描边。 */
    public static GradientDrawable createContentSurface(int backdropColor) {
        if (!Theme.isDefaultThemeActive()) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(backdropColor);
            return drawable;
        }
        final boolean dark = ColorUtils.calculateLuminance(backdropColor) < .42d;
        final int top = ColorUtils.setAlphaComponent(backdropColor, dark ? 58 : 52);
        final int bottom = ColorUtils.setAlphaComponent(blend(backdropColor, Color.WHITE, dark ? .05f : .16f), dark ? 72 : 68);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        drawable.setCornerRadius(0f);
        return drawable;
    }

    /** 全宽导航和工具栏使用的液态玻璃：保留透光与高光，避免把系统顶部栏错误做成圆角卡片。 */
    public static GradientDrawable createNavigationSurface(int backdropColor) {
        if (!Theme.isDefaultThemeActive()) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(backdropColor);
            return drawable;
        }
        final boolean dark = ColorUtils.calculateLuminance(backdropColor) < .42d;
        final int tint = dark ? Color.rgb(160, 185, 226) : Color.rgb(124, 148, 193);
        final int top = ColorUtils.setAlphaComponent(blend(Color.WHITE, tint, .12f), dark ? 46 : 74);
        final int bottom = ColorUtils.setAlphaComponent(blend(backdropColor, tint, .22f), dark ? 62 : 68);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        drawable.setCornerRadius(0f);
        drawable.setStroke(1, ColorUtils.setAlphaComponent(Color.WHITE, dark ? 64 : 122));
        return drawable;
    }

    public static void applySurface(View view, int backdropColor, int tintColor, float radiusPx) {
        if (view != null) {
            view.setBackground(createSurface(backdropColor, tintColor, radiusPx));
        }
    }

    public static int glassBackdropColor(int color) {
        final boolean dark = ColorUtils.calculateLuminance(color) < .42d;
        return ColorUtils.setAlphaComponent(color, dark ? 74 : 62);
    }

    public static int readableTextColor(int backdropColor) {
        return ColorUtils.calculateLuminance(backdropColor) < .42d ? Color.WHITE : Color.rgb(27, 31, 40);
    }

    private static int blend(int from, int to, float amount) {
        return ColorUtils.blendARGB(from, to, Math.max(0f, Math.min(1f, amount)));
    }
}
