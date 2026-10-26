package org.telegram.ui.Components.blur3.drawable.color.impl;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

public class BlurredBackgroundProviderImpl {
    public static BlurredBackgroundProvider mainTabs(Theme.ResourcesProvider resourcesProvider) {
        if (LiteMode.isLiquidGlassEnabled()) {
            return mainNavigationTransparentGlass(resourcesProvider);
        }
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                final int colorTarget = Theme.getColor(Theme.key_glass_targetMainTabs, r);
                return solveSrcColor(colorBg, colorTarget, alpha);
            })
            .setStrokeColorTop(0x11000000, 0x06FFFFFF)
            .setStrokeColorBottom(0x20000000, 0x11FFFFFF)
            .setShadowColor(0x20000000, 0x04FFFFFF)
            .setShadowLayer(dpf2(2.667f), 0, dpf2(0.85f))
            .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
            .build();
    }

    public static BlurredBackgroundProvider topPanel(Theme.ResourcesProvider resourcesProvider) {
        if (LiteMode.isLiquidGlassEnabled()) {
            return mainNavigationTransparentGlass(resourcesProvider);
        }
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                final int colorTarget = Theme.getColor(Theme.key_glass_targetMainTopPanel, r);
                return solveSrcColor(colorBg, colorTarget, alpha);
            })
            .setStrokeColorTop(0x11000000, 0x06FFFFFF)
            .setStrokeColorBottom(0x20000000, 0x11FFFFFF)
            .setShadowColor(0x20000000, 0x04FFFFFF)
            .setShadowLayer(dpf2(2.667f), 0, dpf2(0.85f))
            .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
            .build();
    }

    /** 液态玻璃主材质：保留壁纸透光，并使用轻微的主题色玻璃体。 */
    private static BlurredBackgroundProvider mainNavigationTransparentGlass(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> 0x00000000)
            .setLiquidGlassTintColor((r, isDark) -> Theme.multAlpha(
                    Theme.getColor(Theme.key_windowBackgroundWhite, r), isDark ? 0.12f : 0.08f))
            .setStrokeColorTop(0x62FFFFFF, 0x3CFFFFFF)
            .setStrokeColorBottom(0x26000000, 0x24000000)
            // These transparent overlays should not cast a panel-like shadow
            // over the wallpaper; the edge strokes still separate the surfaces.
            .setShadowColor(0, 0)
            .setShadowLayer(0, 0, 0)
            .setStrokeWidth(dpf2(0.7f), dpf2(0.7f))
            .build();
    }

    /** Separate live-source glass surface for each dialog row. */
    public static BlurredBackgroundProvider mainChatRowGlass(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> Theme.getColor(Theme.key_windowBackgroundWhite, r))
                .setLiquidGlassTintColor((r, isDark) -> Theme.multAlpha(
                        Theme.getColor(Theme.key_windowBackgroundWhite, r), isDark ? 0.13f : 0.09f))
                .setStrokeColorTop(0x78FFFFFF, 0x58FFFFFF)
                .setStrokeColorBottom(0x35000000, 0x26FFFFFF)
                .setShadowColor(0x35000000, 0x46000000)
                .setShadowLayer(dpf2(3.2f), 0, dpf2(1f))
                .setStrokeWidth(dpf2(0.75f), dpf2(0.6f))
                .build();
    }

    /** Semi-transparent search rows that keep a glass tint even without a wallpaper source. */
    public static BlurredBackgroundProvider mainSearchResultsGlass(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> Theme.multAlpha(
                        Theme.getColor(Theme.key_windowBackgroundWhite, r), isDark ? 0.18f : 0.10f))
                .setLiquidGlassTintColor((r, isDark) -> Theme.multAlpha(
                        Theme.getColor(Theme.key_windowBackgroundWhite, r), isDark ? 0.12f : 0.08f))
                .setStrokeColorTop(0x62FFFFFF, 0x3CFFFFFF)
                .setStrokeColorBottom(0x26000000, 0x24000000)
                .setShadowColor(0, 0)
                .setShadowLayer(0, 0, 0)
                .setStrokeWidth(dpf2(0.6f), dpf2(0.6f))
                .build();
    }

    public static BlurredBackgroundProvider mainTabsTransparent(Theme.ResourcesProvider resourcesProvider) {
        return mainNavigationTransparentGlass(resourcesProvider);
    }

    public static BlurredBackgroundProvider mainFoldersTransparent(Theme.ResourcesProvider resourcesProvider) {
        return mainNavigationTransparentGlass(resourcesProvider);
    }

    /** Full-width home/archive/forward header uses the same transparent glass as search and tabs. */
    public static BlurredBackgroundProvider mainDialogsHeaderGlass(Theme.ResourcesProvider resourcesProvider) {
        return mainNavigationTransparentGlass(resourcesProvider);
    }

    /** Translucent liquid glass for the in-app emoji/sticker panel. */
    public static BlurredBackgroundProvider chatEmojiPanelGlass(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                // Keep the chat wallpaper visible through the blur, while supplying a
                // small theme-aware fallback tint so the panel never falls back to black.
                .setBackgroundColor((r, isDark) -> Theme.multAlpha(
                        Theme.getColor(Theme.key_windowBackgroundWhite, r), isDark ? 0.28f : 0.14f))
                .setLiquidGlassTintColor((r, isDark) -> Theme.multAlpha(
                        Theme.getColor(Theme.key_windowBackgroundWhite, r), isDark ? 0.22f : 0.12f))
                .setStrokeColorTop(0x42FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x1FFFFFFF, 0x18FFFFFF)
                .setShadowColor(0, 0)
                .setShadowLayer(0, 0, 0)
                .setStrokeWidth(dpf2(0.5f), dpf2(0.5f))
                .build();
    }

    public static BlurredBackgroundProvider emojiViewButton(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    return Theme.multAlpha(colorBg, alpha);
                })
                .setStrokeColorTop(0xFFFFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
                .setShadowColor(0x40000000, 0)
                .setShadowLayer(dpf2(11 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(0.5f), dpf2(0.5f))
                .build();
    }

    public static BlurredBackgroundProvider counterMini(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = 0.075f;
                    final int colorBg = Color.BLACK;
                    return Theme.multAlpha(colorBg, alpha);
                })
                .setStrokeColorTop(0x60FFFFFF, 0x50FFFFFF)
                .setStrokeColorBottom(0x24000000, 0x24000000)
                .setShadowColor(0, 0)
                .setShadowLayer(0, 0, 0)
                .setStrokeWidth(dpf2(0.43f), dpf2(0.43f))
                .build();
    }

    public static BlurredBackgroundProvider scrimMenuBackground(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            // Keep the captured background visible through the panel. The shader
            // supplies the refraction and reflection; this tint only establishes
            // the milky iOS glass body and adapts to dark mode.
            .setBackgroundColor((r, isDark) ->
                Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), isDark ? 0.52f : 0.38f))
            .setStrokeColorTop(0x72FFFFFF, 0x5AFFFFFF)
            .setStrokeColorBottom(0x22000000, 0x28FFFFFF)
            .setShadowColor(0x52000000, 0x22000000)
            .setShadowLayer(dpf2(10f), 0, dpf2(3f))
            .setStrokeWidth(dpf2(0.8f), dpf2(0.8f))
            .build();
    }

    public static BlurredBackgroundProvider attachMenuSearch(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    return Theme.multAlpha(colorBg, alpha);
                })
                .setStrokeColorTop(0x17000000, 0x17FFFFFF)
                .setStrokeColorBottom(0x17000000, 0x17FFFFFF)
                .setShadowColor(0x11000000, 0x04FFFFFF)
                .setShadowLayer(dpf2(2), 0, dpf2(1 / 3f))
                .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
                .build();
    }

    public static BlurredBackgroundProvider searchFloatingDate(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> 0x33000000)
                .setStrokeColorTop(0x17000000, 0x17FFFFFF)
                .setStrokeColorBottom(0x17000000, 0x17FFFFFF)
                .setShadowColor(0, 0)
                .setStrokeWidth(1, 1)
                .build();
    }

    public static BlurredBackgroundProvider bottomPanelChatActivity(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    if (!checkBlurEnabled(resourcesProvider)) {
                        return ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_messagePanelBackground, r), 255);
                    }

                    final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_chat_messagePanelBackground, r);
                    return Theme.multAlpha(colorBg, alpha);
                })
                .setStrokeColorTop(0xFFFFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
                .setShadowColor(0x20000000, 0)
                //.setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(0.5f), dpf2(0.5f))
                .build();
    }

    public static BlurredBackgroundProvider topPanelChatActivity(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    if (!checkBlurEnabled(resourcesProvider)) {
                        return ColorUtils.setAlphaComponent(Theme.getColor(isDark ?
                            Theme.key_actionBarDefault : Theme.key_chat_topPanelBackground, r), 255);
                    }

                    final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_chat_topPanelBackground, r);
                    return Theme.multAlpha(colorBg, alpha);
                })
                .setStrokeColorTop(0xFFFFFFFF, 0x20FFFFFF)
                .setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
                .setShadowColor(0x20000000, 0)
                //.setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(0.55f), dpf2(0.55f))
                .build();
    }

    public static BlurredBackgroundProvider attachMenuActionBar(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(isDark ? Theme.key_windowBackgroundGray : Theme.key_dialogBackgroundGray, r);
                    final int colorTarget = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    return solveSrcColor(colorBg, colorTarget, alpha);
                })
                .setStrokeColorTop(0xFFFFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
                .setShadowColor(0x20000000, 0)
                //.setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(1), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider topPanelChatActivityTags(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                if (!checkBlurEnabled(resourcesProvider)) {
                    return ColorUtils.setAlphaComponent(Theme.getColor(isDark ?
                            Theme.key_actionBarDefault : Theme.key_chat_topPanelBackground, r), 255);
                }

                final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                final int colorBg = Theme.getColor(Theme.key_chat_topPanelBackground, r);
                return Theme.multAlpha(colorBg, alpha);
            })
            .setStrokeColorTop(0, 0)
            .setStrokeColorBottom(0, 0)
            .setShadowColor(0, 0)
            .setShadowLayer(0, 0, 0)
            .setStrokeWidth(0, 0)
            .build();
    }

    /**
     * Search results live above the chat wallpaper.  Do not paint a white/grey
     * scrim here: the search list must use the same transparent glass surface
     * as the rest of the chat chrome, including on devices without blur.
     */
    public static BlurredBackgroundProvider topPanelChatActivitySearchListBg(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                // No black/white tint: the surface must inherit the actual
                // wallpaper/chat colors underneath the blur.
                .setBackgroundColor((r, isDark) -> 0x00000000)
                .setStrokeColorTop(0, 0)
                .setStrokeColorBottom(0, 0)
                .setShadowColor(0, 0)
                .setShadowLayer(0, 0, 0)
                .setStrokeWidth(0, 0)
                .build();
    }

    public static BlurredBackgroundProvider bulletin(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                final int colorBg = Theme.getColor(Theme.key_undo_background, r);
                return Theme.multAlpha(colorBg, alpha);
            })
            //.setStrokeColorTop(0xFFFFFFFF, 0x28FFFFFF)
            //.setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
            //.setShadowColor(0x20000000, 0)
            //.setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
            .setStrokeWidth(dpf2(0.5f), dpf2(0.5f))
            .build();
    }

    public static BlurredBackgroundProvider inputFieldDialogActivity(Theme.ResourcesProvider resourcesProvider) {
        return topPanel(resourcesProvider);
    }

    public static BlurredBackgroundProvider inputFieldShareAlert(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    final int colorTarget = Theme.getColor(Theme.key_chat_messagePanelBackground, r);
                    return solveSrcColor(colorBg, colorTarget, alpha);
                })
                .setStrokeColorTop(0x28FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x14FFFFFF, 0x14FFFFFF)
                .setShadowColor(0x20000000, 0)
                .setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(1), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider photoViewer(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isLiquidGlassEnabled() ? 0.85f : 0.76f;
                    final int colorBg = 0xFF000000;
                    final int colorTarget = 0xFF1A1A1A;
                    return 0; // solveSrcColor(colorBg, colorTarget, alpha);
                })
                .setStrokeColorTop(0x28FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x14FFFFFF, 0x14FFFFFF)
                .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider photoViewerMenu(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> 0x40000000)
                .setStrokeColorTop(0x28FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x14FFFFFF, 0x14FFFFFF)
                .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider premiumButton(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) ->
                Theme.multAlpha(Theme.getColor(Theme.key_dialogBackground, r), 0.78f))
            .setStrokeColorTop(0xFFFFFFFF, 0x20FFFFFF)
            .setStrokeColorBottom(0, 0x20FFFFFF)
            .setShadowColor(0x30000000, 0x04FFFFFF)
            .setShadowLayer(dpf2(12 / 3f), 0, dpf2(1 / 3f))
            .setStrokeWidth(dpf2(0.67f), dpf2(0.67f))
            .build();
    }

    public static BlurredBackgroundProvider shadow(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setStrokeColorTop(0, 0x28FFFFFF)
            .setStrokeColorBottom(0, 0x14FFFFFF)
            .setShadowColor(0x30000000, 0x04FFFFFF)
            .setShadowLayer(dpf2(12 / 3f), 0, dpf2(1 / 3f))
            .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
            .build();
    }

    public static int solveSrcColor(int bgColor, int outColor, float alpha) {
        alpha = MathUtils.clamp(alpha, 0, 1);

        // Edge cases
        if (alpha <= 0f) {
            return Color.argb(0, 0, 0, 0);
        }
        if (alpha >= 1f) {
            return Color.argb(255, Color.red(outColor), Color.green(outColor), Color.blue(outColor));
        }

        final int bgR = Color.red(bgColor);
        final int bgG = Color.green(bgColor);
        final int bgB = Color.blue(bgColor);

        final int outR = Color.red(outColor);
        final int outG = Color.green(outColor);
        final int outB = Color.blue(outColor);

        final float invA = 1f - alpha;

        final int srcR = MathUtils.clamp(Math.round((outR - bgR * invA) / alpha), 0, 255);
        final int srcG = MathUtils.clamp(Math.round((outG - bgG * invA) / alpha), 0, 255);
        final int srcB = MathUtils.clamp(Math.round((outB - bgB * invA) / alpha), 0, 255);

        final int a8 = MathUtils.clamp(Math.round(alpha * 255f), 0, 255);

        return Color.argb(a8, srcR, srcG, srcB);
    }

    public static boolean checkBlurEnabled(Theme.ResourcesProvider resourcesProvider) {
        return checkBlurEnabled(UserConfig.selectedAccount, resourcesProvider);
    }

    public static boolean checkBlurEnabled(int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        final boolean isLight = !isDark;
        boolean blurEnabled = SharedConfig.chatBlurEnabled();
        if (blurEnabled && isLight) {
            if (MessagesController.getInstance(currentAccount).config.disableBlurInLightTheme.get()) {
                blurEnabled = false;
            }
        }
        if (blurEnabled && isDark) {
            if (MessagesController.getInstance(currentAccount).config.disableBlurInDarkTheme.get()) {
                blurEnabled = false;
            }
        }
        return blurEnabled;
    }
}
