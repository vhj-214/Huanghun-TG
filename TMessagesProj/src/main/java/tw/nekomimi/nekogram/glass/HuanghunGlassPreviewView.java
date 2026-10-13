package tw.nekomimi.nekogram.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.LiquidGlassEffect;

/**
 * Huanghun - live preview for the liquid glass tuning screen.
 *
 * <p>It renders a synthetic "wallpaper" and pushes a real glass panel on top of
 * it using exactly the same shader pipeline as the app (RenderNode +
 * {@link LiquidGlassEffect}), so what the user sees here is the material they
 * are going to get, not an approximation.</p>
 */
public class HuanghunGlassPreviewView extends View {

    private final Paint backdropPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect backdropDst = new Rect();
    private final Path panelPath = new Path();
    private final RectF panelRect = new RectF();

    private Bitmap backdrop;
    private int backdropWidth;
    private int backdropHeight;

    private RenderNode node;
    private LiquidGlassEffect effect;
    private int nodeWidth;
    private int nodeHeight;
    private boolean shaderUnavailable;

    private float thicknessDp = HuanghunGlass.THICKNESS_DEFAULT;
    private float intensity = HuanghunGlass.INTENSITY_DEFAULT;
    private float index = HuanghunGlass.INDEX_DEFAULT;
    private float specular = HuanghunGlass.SPECULAR_DEFAULT;
    private float dispersion = HuanghunGlass.DISPERSION_DEFAULT;
    private float frost = HuanghunGlass.FROST_DEFAULT;

    public HuanghunGlassPreviewView(Context context) {
        super(context);
        setWillNotDraw(false);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setColor(0x42FFFFFF);
        fallbackPaint.setColor(0x33FFFFFF);
        fallbackRimPaint.setStyle(Paint.Style.STROKE);
        fallbackRimPaint.setColor(0x33FFFFFF);
    }

    public void setTuning(float thicknessDp, float intensity, float index,
                          float specular, float dispersion, float frost) {
        this.thicknessDp = thicknessDp;
        this.intensity = intensity;
        this.index = index;
        this.specular = specular;
        this.dispersion = dispersion;
        this.frost = frost;
        invalidate();
    }

    /** Drops the cached wallpaper, e.g. after a day/night theme switch. */
    public void invalidateBackdrop() {
        backdrop = null;
        backdropWidth = 0;
        backdropHeight = 0;
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void ensureBackdrop(int width, int height) {
        if (backdrop != null && backdropWidth == width && backdropHeight == height) {
            return;
        }
        backdropWidth = width;
        backdropHeight = height;
        backdrop = buildBackdrop(width, height);
    }

    /**
     * A synthetic wallpaper: a few vivid gradients plus fine diagonal lines.
     * The lines are what makes the refraction, the dispersion and the frosted
     * blur readable while dragging the sliders.
     */
    private Bitmap buildBackdrop(int width, int height) {
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        canvas.drawColor(0xFF11131C);

        paint.setShader(new RadialGradient(
            width * 0.18f, height * 0.12f, Math.max(width, height) * 0.75f,
            new int[]{0xFF3D63FF, 0x003D63FF}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setShader(new RadialGradient(
            width * 0.88f, height * 0.2f, Math.max(width, height) * 0.62f,
            new int[]{0xFFFF5FA2, 0x00FF5FA2}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setShader(new RadialGradient(
            width * 0.35f, height * 1.02f, Math.max(width, height) * 0.72f,
            new int[]{0xFF19D3C5, 0x0019D3C5}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setShader(new LinearGradient(
            0, 0, width, height,
            new int[]{0x22000000, 0x00000000, 0x33000000}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        final float step = Math.max(dp(10f), 1f);
        final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(dp(1.4f), 1f));
        for (float x = -height; x < width + height; x += step) {
            linePaint.setColor(0x2AFFFFFF);
            canvas.drawLine(x, 0, x + height, height, linePaint);
        }

        final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(0x55FFFFFF);
        final float dotStep = Math.max(dp(26f), 1f);
        final float dotRadius = Math.max(dp(1.6f), 1f);
        for (float y = dotStep * 0.5f; y < height; y += dotStep) {
            for (float x = dotStep * 0.5f; x < width; x += dotStep) {
                canvas.drawCircle(x, y, dotRadius, dotPaint);
            }
        }
        return bitmap;
    }

    private void updatePanelGeometry(int width, int height) {
        final float insetH = dp(12f);
        final float insetV = dp(14f);
        panelRect.set(insetH, insetV, width - insetH, height - insetV);
        panelPath.reset();
        final float radius = Math.min(dp(26f), Math.min(panelRect.width(), panelRect.height()) / 2f);
        panelPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final int width = getWidth();
        final int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        ensureBackdrop(width, height);
        backdropDst.set(0, 0, width, height);
        canvas.drawBitmap(backdrop, null, backdropDst, backdropPaint);

        updatePanelGeometry(width, height);

        if (!shaderUnavailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                drawGlass(canvas, width, height);
                canvas.drawPath(panelPath, rimPaint);
                return;
            } catch (Throwable error) {
                shaderUnavailable = true;
            }
        }
        drawFallback(canvas);
    }

    private void drawGlass(Canvas canvas, int width, int height) {
        if (node == null || nodeWidth != width || nodeHeight != height) {
            node = new RenderNode("huanghunGlassPreview");
            node.setPosition(0, 0, width, height);
            effect = new LiquidGlassEffect(node);
            nodeWidth = width;
            nodeHeight = height;

            final Canvas nodeCanvas = node.beginRecording();
            nodeCanvas.drawBitmap(backdrop, null, backdropDst, backdropPaint);
            node.endRecording();
        }

        final float radius = Math.min(dp(26f), Math.min(panelRect.width(), panelRect.height()) / 2f);
        effect.update(
            panelRect.left, panelRect.top, panelRect.right, panelRect.bottom,
            radius, radius, radius, radius,
            dp(thicknessDp),
            intensity,
            index,
            0x1CFFFFFF,
            specular,
            dispersion,
            frost
        );

        canvas.save();
        canvas.clipPath(panelPath);
        canvas.drawRenderNode(node);
        canvas.restore();
    }

    private void drawFallback(Canvas canvas) {
        fallbackPaint.setColor(Color.argb(
            (int) (24 + 52 * Math.min(1f, thicknessDp / HuanghunGlass.THICKNESS_MAX)),
            255, 255, 255));
        canvas.drawPath(panelPath, fallbackPaint);
    }
}
