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
        if (!Theme.isDefaultThemeSelected()) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(backdropColor);
            drawable.setCornerRadius(Math.max(1f, radiusPx));
            return drawable;
        }
        final boolean dark = ColorUtils.calculateLuminance(backdropColor) < .42d;
        // 默认主题使用真正透光的液态玻璃：卡片只有轻微白色折射，绝不形成灰色阴影面板。
        final int whiteMix = dark ? 26 : 54;
        final int tintMix = dark ? 38 : 42;
        final int top = ColorUtils.setAlphaComponent(blend(Color.WHITE, tintColor, .14f), whiteMix);
        final int bottom = ColorUtils.setAlphaComponent(blend(backdropColor, tintColor, .20f), tintMix);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        drawable.setCornerRadius(Math.max(1f, radiusPx));
        int outline = ColorUtils.setAlphaComponent(Color.WHITE, dark ? 92 : 142);
        drawable.setStroke(Math.max(1, Math.round(radiusPx * .036f)), outline);
        return drawable;
    }

    public static GradientDrawable createPill(int backdropColor, int tintColor, float heightPx) {
        return createSurface(backdropColor, tintColor, Math.max(1f, heightPx * .50f));
    }

    /**
     * 默认页面内容层必须完全透光。此前这里使用接近不透明的白色渐变，导致所有功能页
     * 看起来像一整块灰白阴影面板，也遮挡了聊天动态视频。具体控件由 createSurface() 单独提供玻璃折射。
     */
    public static GradientDrawable createContentSurface(int backdropColor) {
        GradientDrawable drawable = new GradientDrawable();
        if (!Theme.isDefaultThemeSelected()) {
            drawable.setColor(backdropColor);
        } else {
            drawable.setColor(Color.TRANSPARENT);
        }
        drawable.setCornerRadius(0f);
        return drawable;
    }

    /**
     * 页面最底层采用不透明的冷白渐变，而不是透明根视图直接露出系统窗口的纯白底。
     * 列表、导航、输入框和弹窗继续以低透明度白色材质叠在其上，形成稳定的 iOS 液态玻璃层次，
     * 同时不改变 RecyclerView 子项的测量、绘制和复用行为。
     */
    public static GradientDrawable createPageBackdrop(int fallbackColor) {
        if (!Theme.isDefaultThemeSelected()) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(fallbackColor);
            return drawable;
        }
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                0xFFF4F8FF,
                0xFFE5EFFD,
                0xFFF8FBFF
        });
        drawable.setCornerRadius(0f);
        return drawable;
    }

    /**
     * 动态视频存在时必须使用该透明内容层，不能受主题选择状态影响而退回实体背景。
     * 具体列表项仍使用 ThemeColors 中的低透明白色材质，保证文字可读。
     */
    public static GradientDrawable createVideoContentSurface() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(0f);
        return drawable;
    }

    /** 全宽导航和工具栏使用的液态玻璃：保留透光与高光，避免把系统顶部栏错误做成圆角卡片。 */
    public static GradientDrawable createNavigationSurface(int backdropColor) {
        if (!Theme.isDefaultThemeSelected()) {
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
