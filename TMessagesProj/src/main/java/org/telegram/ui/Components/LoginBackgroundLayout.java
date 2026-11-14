package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.FileLog;
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

/** Neutral material login surface. It intentionally does not reveal chat wallpaper. */
public class LoginBackgroundLayout extends SizeNotifierFrameLayout {
    private final WallpaperBitmapProvider wallpaperProvider = new WallpaperBitmapProvider();
    private final BlurredBackgroundSourceWrapped glassSource = new BlurredBackgroundSourceWrapped();
    private final BlurredBackgroundSourceColor fallbackSource = new BlurredBackgroundSourceColor();
    private final ViewPositionWatcher positionWatcher = new ViewPositionWatcher(this);
    private final BlurredBackgroundDrawableViewFactory glassFactory =
            new BlurredBackgroundDrawableViewFactory(positionWatcher, this, glassSource);
    private final Paint scrimPaint = new Paint();
    private Drawable fallbackWallpaper;
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
        return new BlurredBackgroundColorProvider() {
            @Override
            public int getShadowColor() {
                return 0;
            }

            @Override
            public int getBackgroundColor() {
                return scrimPaint.getColor();
            }

            @Override
            public int getStrokeColorTop() {
                return 0;
            }

            @Override
            public int getStrokeColorBottom() {
                return 0;
            }
        };
    }

    public int getStatusBarSurfaceColor() {
        return statusBarColor;
    }

    public int getNavigationBarSurfaceColor() {
        return navigationBarColor;
    }

    public void refreshWallpaper() {
        // Use a stable theme-colored source instead of the account wallpaper.
        // Glass controls still receive a material source, but no chat image or
        // dynamic wallpaper can leak into the login surface.
        Drawable surface = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
        int scrimAlpha = Theme.isCurrentThemeDark() ? 0x46 : 0x38;
        scrimPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), scrimAlpha));
        setBackgroundColor(Color.TRANSPARENT);
        boolean changed = getBackgroundImage() == null
                || !(getBackgroundImage() instanceof ColorDrawable)
                || ((ColorDrawable) getBackgroundImage()).getColor() != ((ColorDrawable) surface).getColor();
        setBackgroundImage(surface, false);
        if (!changed) {
            updateSurfaceColors();
        }
        invalidate();
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
    }

    private void updateSourceSize() {
        if (glassSource.getSource() instanceof BlurredBackgroundSourceBitmap) {
            ((BlurredBackgroundSourceBitmap) glassSource.getSource()).setParentSize(
                    getMeasuredWidth(), getMeasuredHeight(), 0);
        }
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
        // This is a material surface, not a readability scrim. Keep the tint
        // light enough for the neutral source to remain visibly translucent.
        int tint = Theme.getColor(Theme.key_windowBackgroundWhite);
        int alpha = Theme.isCurrentThemeDark() ? 0x46 : 0x38;
        scrimPaint.setColor(ColorUtils.setAlphaComponent(tint, alpha));
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
            // Contrast tint belongs behind the controls, not above their text.
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
