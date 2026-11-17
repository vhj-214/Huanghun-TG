package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.WallpaperBitmapProvider;
import tw.nekomimi.nekogram.helpers.LoginWallpaperHelper;
import tw.nekomimi.nekogram.ui.glass.HuanghunGlassPalette;
import tw.nekomimi.nekogram.ui.glass.HuanghunGlassSurface;

/** Login surface with a dedicated portrait wallpaper and translucent glass controls. */
public class LoginBackgroundLayout extends SizeNotifierFrameLayout {
    private final HuanghunGlassSurface backdropSurface = HuanghunGlassSurface.get(this);
    private final HuanghunGlassPalette glassPalette = new HuanghunGlassPalette(
            HuanghunGlassPalette.VARIANT_OVERLAY, Theme.isCurrentThemeDark(), 0.5f);
    private final WallpaperBitmapProvider wallpaperProvider = new WallpaperBitmapProvider();
    private final BlurredBackgroundSourceWrapped glassSource = new BlurredBackgroundSourceWrapped();
    private final BlurredBackgroundSourceColor fallbackSource = new BlurredBackgroundSourceColor();
    private final ViewPositionWatcher positionWatcher = new ViewPositionWatcher(this);
    private final BlurredBackgroundDrawableViewFactory glassFactory =
            new BlurredBackgroundDrawableViewFactory(positionWatcher, this, glassSource);
    private final Paint scrimPaint = new Paint();
    private Drawable loginWallpaper;
    private int statusBarColor;
    private int navigationBarColor;

    public LoginBackgroundLayout(Context context) {
        super(context);
        setDrawBackgroundBehindTopBars(true);
        refreshWallpaper();
    }

    public BlurredBackgroundDrawableViewFactory getGlassFactory() {
        return glassFactory;
    }

    public BlurredBackgroundColorProvider getGlassColorProvider() {
        // The iOS-style palette doubles as a BlurredBackgroundProvider, so every
        // login control also receives the one-pixel bright top rim and the soft
        // ambient shadow instead of a flat translucent rectangle.
        return glassPalette;
    }

    public int getStatusBarSurfaceColor() {
        return statusBarColor;
    }

    public int getNavigationBarSurfaceColor() {
        return navigationBarColor;
    }

    public void refreshWallpaper() {
        // Use a user-selected portrait as a dedicated login wallpaper. Keep a
        // theme-colored fallback so login remains usable if resource decoding fails.
        if (!isWallpaperUsable(loginWallpaper)) {
            loginWallpaper = loadUserWallpaper();
        }
        if (!isWallpaperUsable(loginWallpaper)) {
            try {
                loginWallpaper = getResources().getDrawable(R.drawable.huanghun_login_wallpaper).mutate();
            } catch (RuntimeException | OutOfMemoryError e) {
                FileLog.e(e);
                loginWallpaper = null;
            }
        }
        if (!isWallpaperUsable(loginWallpaper)) {
            loginWallpaper = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
        }
        Drawable surface = loginWallpaper;
        glassPalette.update(HuanghunGlassPalette.VARIANT_OVERLAY, Theme.isCurrentThemeDark(),
                backdropSurface.getWallpaperLuminance());
        scrimPaint.setColor(glassPalette.getTintColor());
        // Keep an opaque surface behind the separately drawn wallpaper. The
        // wallpaper view can be temporarily empty (for example during measure)
        // or unable to draw a recycled bitmap; transparency would expose black.
        setBackgroundColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundGray), 0xFF));
        boolean changed = getBackgroundImage() != surface;
        setBackgroundImage(surface, false);
        if (!changed) {
            updateSurfaceColors();
        }
        invalidate();
    }

    private Drawable loadUserWallpaper() {
        if (!LoginWallpaperHelper.isSet(getContext())) return null;
        try {
            String path = LoginWallpaperHelper.getFile(getContext()).getAbsolutePath();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            int maxDimension = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxDimension / sample > 2048) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            return bitmap == null ? null : new BitmapDrawable(getResources(), bitmap);
        } catch (RuntimeException | OutOfMemoryError error) {
            FileLog.e(error);
            return null;
        }
    }

    private static boolean isWallpaperUsable(Drawable drawable) {
        // Validate the underlying bitmap before both SizeNotifierFrameLayout and
        // WallpaperBitmapProvider see it. Catching only the blur preparation is
        // insufficient: the root would still try to draw a recycled wallpaper.
        for (int i = 0; i < 4 && drawable instanceof ChatBackgroundDrawable; i++) {
            drawable = ((ChatBackgroundDrawable) drawable).getDrawable(false);
        }
        if (drawable == null || drawable instanceof ChatBackgroundDrawable) {
            return false;
        }
        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else if (drawable instanceof MotionBackgroundDrawable) {
            bitmap = ((MotionBackgroundDrawable) drawable).getBitmap();
        } else {
            return true;
        }
        return bitmap != null && !bitmap.isRecycled();
    }

    @Override
    public void onUpdateBackgroundDrawable(Drawable drawable) {
        super.onUpdateBackgroundDrawable(drawable);
        try {
            BlurredBackgroundSource source = wallpaperProvider.updateSourceFromBackgroundViewDrawable(drawable);
            glassSource.setSource(source);
        } catch (RuntimeException | OutOfMemoryError e) {
            // A damaged/oversized user wallpaper must not block account access.
            FileLog.e(e);
            fallbackSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            glassSource.setSource(fallbackSource);
        }
        updateSourceSize();
        updateSurfaceColors();
        try {
            backdropSurface.setWallpaper(drawable);
        } catch (RuntimeException | OutOfMemoryError error) {
            FileLog.e(error);
        }
    }

    private void updateSourceSize() {
        if (glassSource.getSource() instanceof BlurredBackgroundSourceBitmap) {
            ((BlurredBackgroundSourceBitmap) glassSource.getSource()).setParentSize(
                    getMeasuredWidth(), getMeasuredHeight(), 0);
        }
        backdropSurface.setViewportSize(getMeasuredWidth(), getMeasuredHeight(), 0);
    }

    private void updateSurfaceColors() {
        int top = wallpaperProvider.getStatusBarColor(glassSource);
        int bottom = wallpaperProvider.getNavigationBarColor(glassSource);
        if (Color.alpha(top) == 0) {
            top = Theme.getColor(Theme.key_windowBackgroundWhite);
        }
        if (Color.alpha(bottom) == 0) {
            bottom = Theme.getColor(Theme.key_windowBackgroundWhite);
        }
        // The palette tint is the milky body of the material. It is applied to
        // the controls and to their shadow, not painted flat over the photo.
        glassPalette.update(HuanghunGlassPalette.VARIANT_OVERLAY, Theme.isCurrentThemeDark(),
                backdropSurface.getWallpaperLuminance());
        scrimPaint.setColor(glassPalette.getTintColor());
        statusBarColor = ColorUtils.compositeColors(scrimPaint.getColor(), top);
        navigationBarColor = ColorUtils.compositeColors(scrimPaint.getColor(), bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateSourceSize();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == backgroundView && !isWallpaperUsable(getBackgroundImage())) {
            // Also cover a shared bitmap being recycled after it was installed.
            refreshWallpaper();
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == backgroundView) {
            // Replace the sharp portrait with the frosted backdrop, then keep a
            // very light tint so white glass controls still have contrast.
            final int save = canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            if (!backdropSurface.drawBackdrop(canvas, getWidth(), getHeight())) {
                canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray));
            }
            canvas.restoreToCount(save);
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        }
        return result;
    }

    @Override
    protected boolean isActionBarVisible() {
        return false;
    }

    @Override
    protected boolean isStatusBarVisible() {
        return false;
    }

    @Override
    protected boolean useRootView() {
        return false;
    }

    @Override
    protected Drawable getNewDrawable() {
        // Wallpaper changes are applied explicitly, never while recording/drawing.
        return getBackgroundImage();
    }

    @Override
    protected boolean getNewDrawableMotion() {
        return false;
    }

    public void dispose() {
        positionWatcher.shutdown();
    }
}
