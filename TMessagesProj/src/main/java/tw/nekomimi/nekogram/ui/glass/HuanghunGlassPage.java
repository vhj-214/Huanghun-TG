package tw.nekomimi.nekogram.ui.glass;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import java.util.WeakHashMap;

/**
 * Small facade used by the Huanghun feature screens so every page applies the
 * same iOS-style material with one call.
 */
public final class HuanghunGlassPage {

    private static final WeakHashMap<View, BlurredBackgroundDrawable> ACTION_BAR_GLASS = new WeakHashMap<>();

    private HuanghunGlassPage() {
    }

    /** Returns the page glass surface, refreshed against the current wallpaper. */
    @NonNull
    public static HuanghunGlassSurface surfaceOf(@NonNull ViewGroup root) {
        final HuanghunGlassSurface surface = HuanghunGlassSurface.get(root);
        surface.setViewportSize(root.getWidth(), root.getHeight(), 0);
        surface.setWallpaper(Theme.getCachedWallpaperNonBlocking());
        surface.onThemeChanged();
        return surface;
    }

    /**
     * Replaces the previous "wallpaper + 12% scrim" action bar with a real
     * frosted bar: blurred backdrop, milky body, bright top rim, soft shadow.
     */
    public static void applyActionBar(@Nullable ActionBar actionBar, @Nullable ViewGroup root) {
        applyActionBar(actionBar, root, HuanghunGlassPalette.VARIANT_ACTION_BAR);
    }

    public static void applyActionBar(@Nullable ActionBar actionBar, @Nullable ViewGroup root, int variant) {
        if (actionBar == null || root == null) {
            return;
        }
        actionBar.disableAdaptiveBackground();
        actionBar.setCastShadows(false);

        final HuanghunGlassSurface surface = surfaceOf(root);
        BlurredBackgroundDrawable current = ACTION_BAR_GLASS.get(actionBar);
        if (current == null || actionBar.getBackground() != current) {
            current = surface.createBackground(actionBar, variant, 0);
            if (current != null) {
                ACTION_BAR_GLASS.put(actionBar, current);
                actionBar.setWallpaperBackground(current);
                return;
            }
        }
        if (current != null) {
            return;
        }
        // Safe last resort: a visible translucent colour, never transparency.
        actionBar.setBackgroundColor(Theme.isCurrentThemeDark() ? 0x331C1C1E : 0x30FFFFFF);
    }

    /** Applies the blurred backdrop directly to a root that hosts the wallpaper. */
    public static void applyBackdrop(@Nullable ViewGroup root) {
        if (root == null) {
            return;
        }
        final HuanghunGlassSurface surface = surfaceOf(root);
        surface.registerBackdropView(root);
        root.setBackground(new HuanghunGlassBackdropDrawable(surface));
    }

    /** Palette for canvases that draw their own glass panels. */
    @NonNull
    public static HuanghunGlassPalette palette(@NonNull ViewGroup root, int variant) {
        final HuanghunGlassSurface surface = HuanghunGlassSurface.get(root);
        return new HuanghunGlassPalette(variant, Theme.isCurrentThemeDark(), surface.getWallpaperLuminance());
    }
}
