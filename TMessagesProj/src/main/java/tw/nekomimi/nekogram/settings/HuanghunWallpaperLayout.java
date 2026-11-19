package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Color;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import tw.nekomimi.nekogram.helpers.DynamicVideoWallpaperHelper;

/** Shared transparent root for Huanghun feature pages, exposing the active Telegram wallpaper. */
public class HuanghunWallpaperLayout extends SizeNotifierFrameLayout {
    private final int account;
    private DynamicVideoWallpaperHelper.Player player;
    private boolean listening;
    private boolean playerAttachScheduled;
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
        removeCallbacks(attachPlayerRunnable);
        playerAttachScheduled = false;
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

    /**
     * MediaPlayer decoding is asynchronous, but setDataSource and Surface setup
     * can still block the UI thread on some vendor codecs. Let the page draw its
     * first frame with the cached static wallpaper before attaching video.
     */
    private final Runnable attachPlayerRunnable = () -> {
        playerAttachScheduled = false;
        if (!isAttachedToWindow() || player != null) {
            return;
        }
        player = DynamicVideoWallpaperHelper.attach(this, getContext(), account, 0L);
        if (player != null) {
            player.setFallbackBackgroundColor(Color.TRANSPARENT);
        }
        setBackgroundColor(hasWallpaperLayer()
                ? Color.TRANSPARENT
                : Theme.getColor(Theme.key_windowBackgroundGray));
    };

    private void schedulePlayerAttach() {
        if (!isAttachedToWindow() || playerAttachScheduled) {
            return;
        }
        playerAttachScheduled = true;
        post(attachPlayerRunnable);
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
            schedulePlayerAttach();
        }
        setBackgroundColor(hasWallpaperLayer()
                ? Color.TRANSPARENT
                : Theme.getColor(Theme.key_windowBackgroundGray));
    }
}
