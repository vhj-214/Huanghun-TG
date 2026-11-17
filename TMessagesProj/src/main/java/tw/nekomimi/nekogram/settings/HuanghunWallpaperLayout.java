package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import tw.nekomimi.nekogram.helpers.DynamicVideoWallpaperHelper;
import tw.nekomimi.nekogram.ui.glass.HuanghunGlassSurface;

/**
 * Shared root for Huanghun feature pages.
 *
 * <p>On a static wallpaper the root paints the blurred iOS backdrop instead of
 * the sharp wallpaper, which turns every translucent row, inset and empty area
 * of the page into one continuous frosted material. Animated/video wallpapers
 * keep their own live layer and are not blurred (a per-frame blur would stutter
 * and the live wallpaper is the content the user chose).</p>
 */
public class HuanghunWallpaperLayout extends SizeNotifierFrameLayout {
    private final int account;
    private DynamicVideoWallpaperHelper.Player player;
    private boolean listening;
    private HuanghunGlassSurface glassSurface;
    private final DynamicVideoWallpaperHelper.WallpaperChangeListener wallpaperChangeListener;

    public HuanghunWallpaperLayout(Context context, int account) {
        super(context);
        this.account = account;
        wallpaperChangeListener = (changedAccount, dialogId) -> {
            if (changedAccount == this.account && dialogId == 0L && isAttachedToWindow()) {
                AndroidUtilities.runOnUIThread(this::refreshWallpaper);
            }
        };
        refreshWallpaper();
    }

    public @Nullable HuanghunGlassSurface getGlassSurface() {
        return glassSurface;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!listening) {
            DynamicVideoWallpaperHelper.addChangeListener(wallpaperChangeListener);
            listening = true;
        }
        refreshWallpaper();
    }

    private boolean hasWallpaperLayer() {
        return player != null || getBackgroundImage() != null;
    }

    @Override
    protected boolean isActionBarVisible() {
        // Huanghun pages always own the surface behind the action bar. When there
        // is no wallpaper, refreshWallpaper() supplies the themed fallback color.
        return false;
    }

    @Override
    protected boolean isStatusBarVisible() {
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (listening) {
            DynamicVideoWallpaperHelper.removeChangeListener(wallpaperChangeListener);
            listening = false;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (glassSurface != null) {
            glassSurface.setViewportSize(width, height, 0);
            // A page can be created before the theme worker publishes the
            // wallpaper; retry here so the first real layout still gets glass.
            glassSurface.setWallpaper(Theme.getCachedWallpaperNonBlocking());
        }
    }

    private void refreshWallpaper() {
        setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        if (isAttachedToWindow()) {
            if (player != null && player.matchesCurrentSource(getContext(), account, 0L)) {
                player.resume();
                setBackgroundColor(Color.TRANSPARENT);
            } else {
                if (player != null) {
                    player.release();
                    player = null;
                }
                player = DynamicVideoWallpaperHelper.attach(this, getContext(), account, 0L);
                if (player != null) {
                    player.setFallbackBackgroundColor(Color.TRANSPARENT);
                }
            }
        }
        setBackgroundColor(hasWallpaperLayer()
                ? Color.TRANSPARENT
                : Theme.getColor(Theme.key_windowBackgroundGray));

        if (player == null) {
            // Static wallpaper: prepare the shared blurred backdrop once.
            glassSurface = HuanghunGlassSurface.get(this);
            glassSurface.setViewportSize(getWidth(), getHeight(), 0);
            glassSurface.setWallpaper(Theme.getCachedWallpaperNonBlocking());
            glassSurface.registerBackdropView(this);
        } else {
            glassSurface = null;
        }
        invalidate();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == backgroundView) {
            drawFrostedBackdrop(canvas);
        }
        return result;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (backgroundView == null) {
            // No wallpaper view at all: paint the frosted backdrop before any
            // child so the page never shows the window background.
            drawFrostedBackdrop(canvas);
        }
        super.dispatchDraw(canvas);
    }

    private void drawFrostedBackdrop(Canvas canvas) {
        final HuanghunGlassSurface surface = glassSurface;
        if (surface == null || player != null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        final int save = canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        if (!surface.drawBackdrop(canvas, getWidth(), getHeight())) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray));
        }
        canvas.restoreToCount(save);
    }
}
