package tw.nekomimi.nekogram.ui.glass;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Lightweight glass painter for canvases that cannot host a RenderNode
 * (recycler list sections, dividers, transient overlays).
 *
 * <p>These panels sit on top of the blurred page backdrop installed by
 * {@link HuanghunGlassSurface#drawBackdrop}. They only add the milky tint, the
 * bright top rim, the darker bottom rim and a subtle vertical sheen, which is
 * what makes a flat translucent rectangle read as glass once the backdrop
 * behind it is already blurred. Everything is reused between calls so drawing
 * during scroll performs no allocation.</p>
 */
public final class HuanghunGlassPainter {

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RIM = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SHEEN = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final RectF TMP = new RectF();

    private HuanghunGlassPainter() {
    }

    public static void drawPanel(Canvas canvas, Rect rect, float radius, HuanghunGlassPalette palette) {
        if (rect == null || rect.isEmpty()) {
            return;
        }
        TMP.set(rect);
        drawPanel(canvas, TMP, radius, palette);
    }

    /**
     * Draws one glass panel. The caller is expected to draw the blurred
     * backdrop first; this method only paints the material on top of it.
     */
    public static void drawPanel(Canvas canvas, RectF rect, float radius, HuanghunGlassPalette palette) {
        if (canvas == null || rect == null || rect.isEmpty()) {
            return;
        }
        final float r = Math.max(0f, Math.min(radius, Math.min(rect.width(), rect.height()) / 2f));
        final int tint = palette.getTintColor();
        final int strokeTop = palette.getStrokeColorTop();
        final int strokeBottom = palette.getStrokeColorBottom();

        PATH.rewind();
        PATH.addRoundRect(rect, r, r, Path.Direction.CW);

        final int save = canvas.save();
        canvas.clipPath(PATH);

        FILL.reset();
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(tint);
        canvas.drawRect(rect, FILL);

        // Vertical sheen: iOS glass is slightly brighter at the top because the
        // key light comes from above. Kept very low so text contrast never
        // depends on the wallpaper.
        final int sheenTop = Color.argb(palette.isDark() ? 0x14 : 0x1F, 0xFF, 0xFF, 0xFF);
        SHEEN.reset();
        SHEEN.setShader(new LinearGradient(0, rect.top, 0, rect.top + rect.height() * 0.62f,
                sheenTop, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(rect, SHEEN);
        SHEEN.setShader(null);

        canvas.restoreToCount(save);

        // One-pixel rims drawn outside the clip so the highlight keeps its
        // antialiased edge instead of being cut in half.
        final float stroke = Math.max(1f, palette.getStrokeWidthTop());
        RIM.reset();
        RIM.setStyle(Paint.Style.STROKE);
        RIM.setStrokeWidth(stroke);
        RIM.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                strokeTop, strokeBottom, Shader.TileMode.CLAMP));
        canvas.drawPath(PATH, RIM);
        RIM.setShader(null);
    }

    /** Horizontal glass strip (action bar, tab bar, composer surfaces). */
    public static void drawStrip(Canvas canvas, float left, float top, float right, float bottom, float topRadius, float bottomRadius, HuanghunGlassPalette palette) {
        if (canvas == null || right <= left || bottom <= top) {
            return;
        }
        TMP.set(left, top, right, bottom);
        PATH.rewind();
        final float[] radii = new float[]{
                topRadius, topRadius, topRadius, topRadius,
                bottomRadius, bottomRadius, bottomRadius, bottomRadius
        };
        PATH.addRoundRect(TMP, radii, Path.Direction.CW);

        final int save = canvas.save();
        canvas.clipPath(PATH);
        FILL.reset();
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(palette.getTintColor());
        canvas.drawRect(TMP, FILL);
        SHEEN.reset();
        SHEEN.setShader(new LinearGradient(0, top, 0, top + (bottom - top) * 0.55f,
                Color.argb(palette.isDark() ? 0x12 : 0x1C, 0xFF, 0xFF, 0xFF),
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(TMP, SHEEN);
        SHEEN.setShader(null);
        canvas.restoreToCount(save);

        RIM.reset();
        RIM.setStyle(Paint.Style.STROKE);
        RIM.setStrokeWidth(Math.max(1f, palette.getStrokeWidthTop()));
        RIM.setShader(new LinearGradient(0, top, 0, bottom,
                palette.getStrokeColorTop(), palette.getStrokeColorBottom(), Shader.TileMode.CLAMP));
        canvas.drawPath(PATH, RIM);
        RIM.setShader(null);
    }
}
