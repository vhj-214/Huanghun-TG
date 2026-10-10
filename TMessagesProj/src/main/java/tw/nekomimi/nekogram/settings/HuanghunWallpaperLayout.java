package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Color;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import tw.nekomimi.nekogram.helpers.DynamicVideoWallpaperHelper;

/** Shared transparent root for Huanghun feature pages, exposing the active Telegram wallpaper. */
final class HuanghunWallpaperLayout extends SizeNotifierFrameLayout {
    private final int account;
    private DynamicVideoWallpaperHelper.Player player;
    private boolean listening;
    private final DynamicVideoWallpaperHelper.WallpaperChangeListener wallpaperChangeListener;

    HuanghunWallpaperLayout(Context context, int account) {
        super(context);
        this.account = account;
        wallpaperChangeListener = (changedAccount, dialogId) -> {
            if (changedAccount == this.account && dialogId == 0L && isAttachedToWindow()) {
                AndroidUtilities.runOnUIThread(this::refreshWallpaper);
            }
        };
        refreshWallpaper();
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
        // Draw wallpaper behind the action bar instead of exposing a black window strip.
        return !hasWallpaperLayer();
    }

    @Override
    protected boolean isStatusBarVisible() {
        // Keep the status-bar area on the same wallpaper-backed surface.
        return !hasWallpaperLayer();
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

    private void refreshWallpaper() {
        setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        if (isAttachedToWindow()) {
            if (player != null && player.matchesCurrentSource(getContext(), account, 0L)) {
                player.resume();
                setBackgroundColor(Color.TRANSPARENT);
                return;
            }
            if (player != null) {
                player.release();
                player = null;
            }
            player = DynamicVideoWallpaperHelper.attach(this, getContext(), account, 0L);
            if (player != null) {
                player.setFallbackBackgroundColor(Color.TRANSPARENT);
            }
        }
        setBackgroundColor(hasWallpaperLayer()
                ? Color.TRANSPARENT
                : Theme.getColor(Theme.key_windowBackgroundGray));
    }
}
