package org.telegram.ui.Components.blur3.drawable;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.LiquidGlassEffect;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class BlurredBackgroundDrawableRenderNode extends BlurredBackgroundDrawable {
    private final BlurredBackgroundSource source;
    private final Outline outline = new Outline();
    private final Rect outlineRect = new Rect();

    private final RenderNode renderNode;
    private final RenderNode renderNodeFill;

    private final Paint paintShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintSoftwareFallback = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean renderNodeInvalidated;

    public BlurredBackgroundDrawableRenderNode(BlurredBackgroundSource source) {
        this.renderNode = new RenderNode("BlurredNode");
        this.renderNodeFill = new RenderNode("BlurredFill");
        this.renderNode.setClipToOutline(true);
        this.renderNode.setClipToBounds(true);

        this.source = source;

        this.paintShadow.setColor(0);
        this.paintStrokeTop.setStyle(Paint.Style.STROKE);
        this.paintStrokeBottom.setStyle(Paint.Style.STROKE);
    }

    @Override
    public BlurredBackgroundDrawable setClipToOutline(boolean clipToOutline) {
        renderNode.setClipToOutline(clipToOutline);
        return super.setClipToOutline(clipToOutline);
    }

    private LiquidGlassEffect liquidGlassEffect;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void setLiquidGlassEffectAllowed() {
        try {
            liquidGlassEffect = new LiquidGlassEffect(renderNodeFill);
        } catch (Throwable error) {
            disableLiquidGlassEffect(error);
        }
    }

    private void disableLiquidGlassEffect(Throwable error) {
        liquidGlassEffect = null;
        try {
            renderNodeFill.setRenderEffect(null);
        } catch (RuntimeException ignored) {
        }
        // A shader can fail during RuntimeShader construction on devices whose
        // GPU/system implementation does not support this AGSL program. Do not
        // leave the persisted switch enabled: otherwise every new drawable,
        // including the first screen after launch, retries the same failure.
        try {
            if (LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS)) {
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, false);
            }
        } catch (Throwable ignored) {
            // The visual fallback below must remain available even if settings
            // are not ready yet during app startup.
        }
        Log.e("BlurredBackground", "Liquid Glass shader unavailable; using translucent glass fallback", error);
    }


    @Override
    public BlurredBackgroundSource getSource() {
        return source;
    }

    @Override
    protected void onBoundPropsChanged() {
        super.onBoundPropsChanged();

        paintStrokeTop.setStrokeWidth(boundProps.strokeWidthTop);
        paintStrokeBottom.setStrokeWidth(boundProps.strokeWidthBottom);

        outlineRect.set(0, 0,
            boundProps.boundsWithPadding.width(),
            boundProps.boundsWithPadding.height()
        );
        getOutline(outline, outlineRect, boundProps.radii);
        outline.setAlpha(1);

        if (!boundProps.boundsWithPadding.isEmpty()) {
            renderNodeFill.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNode.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNode.setOutline(outline);

            renderNodeInvalidated = true;
        }
    }

    @Override
    protected void onSourceOffsetChange(float sourceOffsetX, float sourceOffsetY) {
        super.onSourceOffsetChange(sourceOffsetX, sourceOffsetY);
        renderNodeInvalidated = true;
    }

    public boolean hasDisplayList() {
        return renderNode.hasDisplayList();
    }

    private void updateDisplayList() {
        final float offsetX = sourceOffsetX;
        final float offsetY = sourceOffsetY;

        final boolean liquidGlassEnabled = LiteMode.isLiquidGlassEnabled();
        if (liquidGlassEffect == null && liquidGlassEnabled && Build.VERSION.SDK_INT >= 33) {
            setLiquidGlassEffectAllowed();
        } else if (liquidGlassEffect != null && !liquidGlassEnabled) {
            liquidGlassEffect = null;
            try {
                renderNodeFill.setRenderEffect(null);
            } catch (Throwable ignored) {
            }
        }

        Canvas c;

        final float sL = boundProps.boundsWithPadding.left + offsetX;
        final float sT = boundProps.boundsWithPadding.top + offsetY;
        final float sR = boundProps.boundsWithPadding.right + offsetX;
        final float sB = boundProps.boundsWithPadding.bottom + offsetY;

        c = renderNodeFill.beginRecording();
        c.save();
        c.translate(-sL, -sT);
        if (liquidGlassEffect != null && Build.VERSION.SDK_INT >= 33) {
            final int thickness = Math.max(Math.min(
                boundProps.liquidThickness <= 0 ? dp(11) : boundProps.liquidThickness,
                Math.min(boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height()) / 5), 1);

            try {
                liquidGlassEffect.update(
                    0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height(),
                    boundProps.shaderRadii[0], boundProps.shaderRadii[2], boundProps.shaderRadii[4], boundProps.shaderRadii[6],
                    thickness,
                    boundProps.liquidIntensity,
                    boundProps.liquidIndex,
                    backgroundColor,
                    // Huanghun iOS-style material: stronger rim light, a much
                    // softer frosted body (this is the parameter that actually
                    // reads as "milky glass"), restrained dispersion and low
                    // grain so the surface stays clean on top of photos.
                    1.55f, // specular highlight strength
                    0.55f, // edge dispersion
                    0.58f, // frosted body
                    1.45f, // Fresnel edge reflection
                    1.25f, // environment reflection
                    0.06f  // micro-surface grain
                );
            } catch (Throwable error) {
                disableLiquidGlassEffect(error);
            }
        }
        source.draw(c, sL, sT, sR, sB);
        if (liquidGlassEffect == null && liquidGlassEnabled) {
            drawCrossVersionLiquidGlassRect(c, 0, 0,
                    boundProps.boundsWithPadding.width(),
                    boundProps.boundsWithPadding.height(),
                    boundProps.radii[0]);
        }
        c.restore();
        renderNodeFill.endRecording();


        c = renderNode.beginRecording();
        if (Color.alpha(backgroundColor) == 255) {
            c.drawColor(backgroundColor);
        } else {
            // Let the sharp wallpaper show through the frosted snapshot. The
            // fill node contains the blurred source, the milky liquid-glass
            // veil and (for AGSL) the refraction effect, so fading this node
            // keeps every layer consistent instead of turning the surface into
            // an opaque sheet.
            renderNodeFill.setAlpha(backdropAlpha);
            c.drawRenderNode(renderNodeFill);
            if (liquidGlassEffect == null && Color.alpha(backgroundColor) != 0) {
                c.drawColor(Theme.multAlpha(backgroundColor, backdropAlpha));
            }
        }
        if (strokeColorTop != 0) {
            drawStroke(c, 0, 0, boundProps.boundsWithPadding.width(),
                    boundProps.boundsWithPadding.height(), boundProps.radii,
                    boundProps.strokeWidthTop, true, paintStrokeTop);
        }
        if (strokeColorBottom != 0) {
            drawStroke(c, 0, 0, boundProps.boundsWithPadding.width(),
                    boundProps.boundsWithPadding.height(), boundProps.radii,
                    boundProps.strokeWidthBottom, false, paintStrokeBottom);
        }
        renderNode.endRecording();
    }

    @Override
    public void updateColors() {
        super.updateColors();

        paintShadow.setShadowLayer(shadowLayerRadius, shadowLayerDx, shadowLayerDy, shadowColor);
        paintStrokeTop.setColor(strokeColorTop);
        paintStrokeBottom.setColor(strokeColorBottom);

        renderNodeInvalidated = true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (boundProps.boundsWithPadding.isEmpty()) {
            return;
        }

        if (!canvas.isHardwareAccelerated()) {
            // Software canvases (screenshot, some custom views, transition
            // renderers) cannot replay a RenderNode. Draw the source, then the
            // translucent body and rims so the surface is never fully
            // transparent — a transparent action bar is what exposed the black
            // window background in earlier builds.
            drawSource(canvas, source);
            if (!boundProps.boundsWithPadding.isEmpty()) {
                canvas.save();
                canvas.translate(boundProps.boundsWithPadding.left, boundProps.boundsWithPadding.top);
                if (Color.alpha(backgroundColor) != 0) {
                    paintSoftwareFallback.setStyle(Paint.Style.FILL);
                    paintSoftwareFallback.setColor(backgroundColor);
                    boundProps.draw(canvas, paintSoftwareFallback);
                }
                if (Color.alpha(strokeColorTop) != 0 || Color.alpha(strokeColorBottom) != 0) {
                    paintSoftwareFallback.setStyle(Paint.Style.STROKE);
                    paintSoftwareFallback.setStrokeWidth(Math.max(1f, boundProps.strokeWidthTop));
                    paintSoftwareFallback.setColor(strokeColorTop != 0 ? strokeColorTop : strokeColorBottom);
                    canvas.drawPath(boundProps.path, paintSoftwareFallback);
                }
                canvas.restore();
            }
            return;
        }

        if (!renderNode.hasDisplayList()) {
            source.dispatchOnDrawablesRelativePositionChange();
            updateDisplayList();
        } else if (renderNodeInvalidated) {
            updateDisplayList();
        }
        renderNodeInvalidated = false;

        int color = Theme.multAlpha(shadowColor, renderNode.getAlpha() * shadowAlpha);
        if (Color.alpha(color) != 0) {
            paintShadow.setShadowLayer(shadowLayerRadius, shadowLayerDx, shadowLayerDy, color);
            boundProps.drawShadows(canvas, paintShadow, inAppKeyboardOptimization);
        }

        canvas.save();
        canvas.translate(boundProps.boundsWithPadding.left, boundProps.boundsWithPadding.top);
        canvas.drawRenderNode(renderNode);
        canvas.restore();
    }

    public void invalidateDisplayList() {
        renderNodeInvalidated = true;
    }

    @Override
    public BlurredBackgroundDrawable setBackdropAlpha(float backdropAlpha) {
        super.setBackdropAlpha(backdropAlpha);
        renderNodeInvalidated = true;
        return this;
    }

    @Override
    public void setAlpha(int alpha) {
        final int oldAlpha = getAlpha();

        super.setAlpha(alpha);
        renderNode.setAlpha(alpha / 255f);
        renderNodeInvalidated = true;

        if (oldAlpha == 0 && alpha > 0) {
            source.dispatchOnDrawablesRelativePositionChange();
        }
    }

    @Override
    protected void onSourceRelativePositionChanged(RectF position) {
        super.onSourceRelativePositionChanged(position);
        source.dispatchOnDrawablesRelativePositionChange();
    }
}
