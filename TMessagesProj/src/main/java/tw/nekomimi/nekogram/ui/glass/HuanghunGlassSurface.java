package tw.nekomimi.nekogram.ui.glass;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableRenderNode;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;
import org.telegram.ui.Components.chat.ViewPositionWatcher;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * One iOS-style frosted glass viewport per page.
 *
 * <p>Design notes (these matter for the "no black frame, no stutter" goal):</p>
 * <ul>
 *     <li>The backdrop is blurred <b>once</b> per wallpaper into a small bitmap,
 *     so scrolling never runs a blur pass and never touches a bitmap
 *     allocation;</li>
 *     <li>Every surface is fed by the same backdrop. On API 29+ it is uploaded
 *     into a RenderNode, which lets the existing AGSL liquid-glass shader add
 *     refraction, rim light and dispersion on API 33+ without any per-frame
 *     Java work;</li>
 *     <li>When the shader is unavailable the very same RenderNode is drawn with
 *     a plain tint and one-pixel rim, so the material still reads as glass and
 *     is never fully transparent (a fully transparent surface is what exposes
 *     the black window background);</li>
 *     <li>Every public method swallows render/allocation failures and degrades
 *     to an opaque theme colour instead of letting the UI crash.</li>
 * </ul>
 */
public final class HuanghunGlassSurface {

    private static final WeakHashMap<ViewGroup, HuanghunGlassSurface> INSTANCES = new WeakHashMap<>();

    @NonNull
    public static HuanghunGlassSurface get(@NonNull ViewGroup root) {
        HuanghunGlassSurface surface = INSTANCES.get(root);
        if (surface == null) {
            surface = new HuanghunGlassSurface(root);
            INSTANCES.put(root, surface);
        }
        return surface;
    }

    private final ViewGroup root;
    private final ViewPositionWatcher positionWatcher;
    private final BlurredBackgroundSourceWrapped source = new BlurredBackgroundSourceWrapped();
    private final BlurredBackgroundSourceColor colorSource = new BlurredBackgroundSourceColor();
    private final BlurredBackgroundDrawableViewFactory factory;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final ArrayList<View> backdropViews = new ArrayList<>();

    private final Matrix drawMatrix = new Matrix();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private @Nullable Object renderNodeHolder;
    private @Nullable Bitmap backdropBitmap;
    private @Nullable Drawable sourceWallpaper;
    private boolean dark;
    private float wallpaperLuminance = 0.5f;
    private int viewportWidth;
    private int viewportHeight;
    private int viewportTopInset;
    private boolean hasSource;
    private boolean released;

    private HuanghunGlassSurface(@NonNull ViewGroup root) {
        this.root = root;
        this.positionWatcher = new ViewPositionWatcher(root);
        this.factory = new BlurredBackgroundDrawableViewFactory(positionWatcher, root, source);
        this.factory.setLiquidGlassEffectAllowed(true);
        this.dark = Theme.isCurrentThemeDark();
    }

    public boolean isDark() {
        return dark;
    }

    public float getWallpaperLuminance() {
        return wallpaperLuminance;
    }

    public boolean isReady() {
        return !released && hasSource;
    }

    public @Nullable Bitmap getBackdropBitmap() {
        return backdropBitmap;
    }

    /**
     * Registers a view whose background drawable paints this backdrop, so the
     * view is repainted as soon as a new backdrop bitmap (or a new viewport
     * size) becomes available. Keeping this list short is intentional: only the
     * page root should register.
     */
    public void registerBackdropView(@NonNull View view) {
        if (!backdropViews.contains(view)) {
            backdropViews.add(view);
        }
    }

    private void invalidateBackdropViews() {
        for (int i = backdropViews.size() - 1; i >= 0; i--) {
            final View view = backdropViews.get(i);
            if (view == null || view.getParent() == null) {
                backdropViews.remove(i);
            } else {
                view.invalidate();
            }
        }
    }

    /**
     * Installs the wallpaper behind the glass. The wallpaper is converted into
     * one blurred backdrop bitmap; repeated calls with the same drawable are
     * cheap no-ops.
     */
    public void setWallpaper(@Nullable Drawable wallpaper) {
        if (released) {
            return;
        }
        if (wallpaper == sourceWallpaper && hasSource) {
            return;
        }
        sourceWallpaper = wallpaper;
        Bitmap blurred = null;
        int solidColor = 0;
        try {
            if (wallpaper instanceof ColorDrawable) {
                solidColor = ((ColorDrawable) wallpaper).getColor();
            } else if (wallpaper != null) {
                blurred = obtainBlurredBackdrop(wallpaper);
            }
        } catch (Throwable error) {
            FileLog.e(error);
            blurred = null;
        }
        if (blurred == null && solidColor == 0) {
            // No usable wallpaper: use the theme surface so the glass is a
            // tinted material over a known colour instead of transparency.
            solidColor = Theme.getColor(Theme.key_windowBackgroundGray);
        }
        backdropBitmap = blurred;
        wallpaperLuminance = blurred != null ? averageLuminance(blurred)
                : luminanceOfColor(solidColor);
        rebuildSource(solidColor);
    }

    /** Installs a ready-made blurred backdrop bitmap (used by the login page). */
    public void setBackdropBitmap(@Nullable Bitmap bitmap) {
        if (released) {
            return;
        }
        if (bitmap == backdropBitmap && hasSource) {
            return;
        }
        sourceWallpaper = null;
        backdropBitmap = bitmap;
        wallpaperLuminance = bitmap != null ? averageLuminance(bitmap) : 0.5f;
        rebuildSource(bitmap == null ? Theme.getColor(Theme.key_windowBackgroundGray) : 0);
    }

    /**
     * Must be called when the viewport size is known or changed. An unchanged
     * size is a no-op, so it is safe inside {@code onLayout}.
     */
    public void setViewportSize(int width, int height, int topInset) {
        if (released) {
            return;
        }
        if (width == viewportWidth && height == viewportHeight && topInset == viewportTopInset && hasSource) {
            return;
        }
        viewportWidth = Math.max(0, width);
        viewportHeight = Math.max(0, height);
        viewportTopInset = Math.max(0, topInset);
        rebuildSource(0);
    }

    public void onThemeChanged() {
        if (released) {
            return;
        }
        final boolean newDark = Theme.isCurrentThemeDark();
        dark = newDark;
        for (Entry entry : entries) {
            entry.palette.update(entry.variant, dark, wallpaperLuminance);
            entry.apply();
        }
        if (!hasSource) {
            rebuildSource(0);
        }
    }

    /**
     * Creates a glass background bound to {@code target}. Returns {@code null}
     * when the caller should keep its own background.
     */
    @Nullable
    public BlurredBackgroundDrawable createBackground(@NonNull View target, int variant, float radiusPx) {
        if (released) {
            return null;
        }
        try {
            final HuanghunGlassPalette palette = new HuanghunGlassPalette(variant, dark, wallpaperLuminance);
            final BlurredBackgroundDrawable drawable = factory.create(target, palette);
            drawable.setRadius(radiusPx);
            final Entry entry = new Entry(drawable, palette, variant);
            entries.add(entry);
            entry.apply();
            return drawable;
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    /** Re-applies palette values to every surface after a theme change. */
    public void refreshSurfaces() {
        for (Entry entry : entries) {
            entry.palette.update(entry.variant, dark, wallpaperLuminance);
            entry.apply();
        }
        invalidateBackdropViews();
    }

    /** Colour that is always safe to paint when glass is unavailable. */
    public int getSafeFallbackColor() {
        final int base = Theme.getColor(Theme.key_windowBackgroundWhite);
        return Color.argb(Theme.isCurrentThemeDark() ? 0xF0 : 0xF6,
                Color.red(base), Color.green(base), Color.blue(base));
    }

    /**
     * Paints the blurred backdrop as the page background. Using the blurred
     * bitmap (instead of the sharp wallpaper) is what turns the whole page into
     * frosted glass: every translucent row, divider and empty area then reads
     * as part of the same material.
     */
    public boolean drawBackdrop(@NonNull Canvas canvas, int width, int height) {
        final Bitmap bitmap = backdropBitmap;
        if (bitmap == null || bitmap.isRecycled() || width <= 0 || height <= 0) {
            return false;
        }
        try {
            buildCenterCrop(drawMatrix, bitmap.getWidth(), bitmap.getHeight(), width, height);
            canvas.drawBitmap(bitmap, drawMatrix, bitmapPaint);
            return true;
        } catch (Throwable error) {
            FileLog.e(error);
            return false;
        }
    }

    public void dispose() {
        if (released) {
            return;
        }
        released = true;
        entries.clear();
        backdropViews.clear();
        try {
            positionWatcher.shutdown();
        } catch (Throwable error) {
            FileLog.e(error);
        }
        INSTANCES.remove(root);
        renderNodeHolder = null;
        backdropBitmap = null;
        sourceWallpaper = null;
    }

    private void rebuildSource(int solidColor) {
        if (released) {
            return;
        }
        if (backdropBitmap == null) {
            colorSource.setColor(solidColor == 0 ? getSafeFallbackColor() : solidColor);
            source.setSource(colorSource);
            hasSource = true;
            refreshSurfaces();
            return;
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            // Layout has not happened yet. Keep the backdrop ready and let the
            // next setViewportSize() upload it; do not flash a flat colour.
            if (!hasSource) {
                colorSource.setColor(solidColor == 0 ? getSafeFallbackColor() : solidColor);
                source.setSource(colorSource);
                hasSource = true;
            }
            invalidateBackdropViews();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final RenderNodeSourceHolder holder;
                if (renderNodeHolder instanceof RenderNodeSourceHolder) {
                    holder = (RenderNodeSourceHolder) renderNodeHolder;
                } else {
                    holder = RenderNodeSourceHolder.create();
                    renderNodeHolder = holder;
                }
                holder.bind(source, backdropBitmap, viewportWidth, viewportHeight, viewportTopInset, drawMatrix, bitmapPaint);
            } else {
                // API 27–28: draw the blurred bitmap directly. It is already
                // blurred and small, so this is a single stretched draw call.
                bindLegacyBitmap();
            }
            hasSource = true;
        } catch (Throwable error) {
            FileLog.e(error);
            colorSource.setColor(solidColor == 0 ? getSafeFallbackColor() : solidColor);
            source.setSource(colorSource);
            hasSource = true;
        }
        refreshSurfaces();
    }

    /**
     * API 27–28 path: the wrapped colour source cannot stretch a bitmap, so the
     * blurred backdrop is published through a dedicated bitmap source.
     */
    private void bindLegacyBitmap() {
        if (legacyBitmapSource == null) {
            legacyBitmapSource = new org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap();
        }
        legacyBitmapSource.setBitmap(backdropBitmap);
        legacyBitmapSource.setParentSize(viewportWidth, viewportHeight, 0);
        source.setSource(legacyBitmapSource);
    }

    private org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap legacyBitmapSource;

    /* Backdrop construction */

    /**
     * One blurred backdrop is shared by every page that uses the same wallpaper
     * object. Without this, opening five settings pages in a row would run the
     * blur five times and each run is a visible main-thread hiccup.
     */
    private static java.lang.ref.WeakReference<Drawable> lastWallpaperRef;
    private static Bitmap lastBlurredBackdrop;

    private static @Nullable Bitmap obtainBlurredBackdrop(@NonNull Drawable wallpaper) {
        final Drawable last = lastWallpaperRef != null ? lastWallpaperRef.get() : null;
        if (last == wallpaper && lastBlurredBackdrop != null && !lastBlurredBackdrop.isRecycled()) {
            return lastBlurredBackdrop;
        }
        final Bitmap blurred = buildBlurredBackdrop(wallpaper);
        if (blurred != null) {
            lastWallpaperRef = new java.lang.ref.WeakReference<>(wallpaper);
            lastBlurredBackdrop = blurred;
        }
        return blurred;
    }

    private static @Nullable Bitmap buildBlurredBackdrop(@NonNull Drawable wallpaper) {
        Bitmap source = extractSourceBitmap(wallpaper);
        if (source == null) {
            source = rasterizeWallpaper(wallpaper);
        }
        if (source == null || source.isRecycled() || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return null;
        }
        try {
            // Cap the working size: blur cost and memory stay bounded on huge
            // camera photos while the result is blurred anyway.
            final int maxSide = Math.max(source.getWidth(), source.getHeight());
            if (maxSide > 1440) {
                final float scale = 1440f / maxSide;
                final Bitmap scaled = Bitmap.createScaledBitmap(source,
                        Math.max(1, Math.round(source.getWidth() * scale)),
                        Math.max(1, Math.round(source.getHeight() * scale)), true);
                if (scaled != source) {
                    source = scaled;
                }
            }
            return Utilities.blurWallpaper(source);
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    private static @Nullable Bitmap extractSourceBitmap(@NonNull Drawable drawable) {
        Drawable current = drawable;
        for (int i = 0; i < 4 && current instanceof ChatBackgroundDrawable; i++) {
            current = ((ChatBackgroundDrawable) current).getDrawable(false);
        }
        if (current instanceof BitmapDrawable) {
            return ((BitmapDrawable) current).getBitmap();
        }
        if (current instanceof MotionBackgroundDrawable) {
            return ((MotionBackgroundDrawable) current).getBitmap();
        }
        return null;
    }

    private static @Nullable Bitmap rasterizeWallpaper(@NonNull Drawable drawable) {
        try {
            final int width = 360;
            final int height = 640;
            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            final Rect previous = drawable.copyBounds();
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            drawable.setBounds(previous);
            return bitmap;
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    private static float averageLuminance(@NonNull Bitmap bitmap) {
        if (bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return 0.5f;
        }
        try {
            float sum = 0f;
            int samples = 0;
            final int stepX = Math.max(1, bitmap.getWidth() / 8);
            final int stepY = Math.max(1, bitmap.getHeight() / 8);
            for (int y = stepY / 2; y < bitmap.getHeight(); y += stepY) {
                for (int x = stepX / 2; x < bitmap.getWidth(); x += stepX) {
                    final int color = bitmap.getPixel(x, y);
                    sum += luminanceOfColor(color);
                    samples++;
                }
            }
            return samples == 0 ? 0.5f : sum / samples;
        } catch (Throwable error) {
            FileLog.e(error);
            return 0.5f;
        }
    }

    private static float luminanceOfColor(int color) {
        return (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f;
    }

    private static void buildCenterCrop(
            @NonNull Matrix matrix,
            int bitmapWidth,
            int bitmapHeight,
            int width,
            int height
    ) {
        matrix.reset();
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || width <= 0 || height <= 0) {
            return;
        }
        final float scale = Math.max((float) width / bitmapWidth, (float) height / bitmapHeight);
        final float dx = (width - bitmapWidth * scale) * 0.5f;
        final float dy = (height - bitmapHeight * scale) * 0.5f;
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
    }

    private final class Entry {
        final BlurredBackgroundDrawable drawable;
        final HuanghunGlassPalette palette;
        final int variant;

        Entry(BlurredBackgroundDrawable drawable, HuanghunGlassPalette palette, int variant) {
            this.drawable = drawable;
            this.palette = palette;
            this.variant = variant;
        }

        void apply() {
            drawable.setColorProvider(palette);
            drawable.setThickness(AndroidUtilities.dp(palette.getLiquidThicknessDp()));
            drawable.setIntensity(palette.getLiquidIntensity());
            drawable.setBackdropAlpha(palette.getBackdropAlpha());
            if (drawable instanceof BlurredBackgroundDrawableRenderNode) {
                ((BlurredBackgroundDrawableRenderNode) drawable).invalidateDisplayList();
            }
        }
    }

    /** Isolated so API 27–28 devices never load RenderNode classes. */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static final class RenderNodeSourceHolder {
        private final BlurredBackgroundSourceRenderNode renderNodeSource =
                new BlurredBackgroundSourceRenderNode(null);
        private Bitmap currentBitmap;
        private int currentWidth;
        private int currentHeight;
        private int currentTopInset;

        static RenderNodeSourceHolder create() {
            return new RenderNodeSourceHolder();
        }

        void bind(
                @NonNull BlurredBackgroundSourceWrapped targetSource,
                @NonNull Bitmap bitmap,
                int width,
                int height,
                int topInset,
                @NonNull Matrix matrix,
                @NonNull Paint paint
        ) {
            final boolean needsRecording = currentBitmap != bitmap
                    || currentWidth != width
                    || currentHeight != height
                    || currentTopInset != topInset
                    || !renderNodeSource.needUpdateDisplayList(width, height);
            if (needsRecording) {
                currentBitmap = bitmap;
                currentWidth = width;
                currentHeight = height;
                currentTopInset = topInset;

                renderNodeSource.setSize(width, height);
                final RecordingCanvas canvas = renderNodeSource.beginRecording(width, height);
                try {
                    canvas.drawColor(Color.BLACK);
                    buildCenterCrop(matrix, bitmap.getWidth(), bitmap.getHeight(), width, height);
                    canvas.drawBitmap(bitmap, matrix, paint);
                } finally {
                    renderNodeSource.endRecording();
                }
            }
            targetSource.setSource(renderNodeSource);
        }
    }
}
