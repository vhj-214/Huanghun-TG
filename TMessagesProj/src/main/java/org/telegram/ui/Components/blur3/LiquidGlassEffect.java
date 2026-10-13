package org.telegram.ui.Components.blur3;

import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

@RequiresApi(api = 33)
public class LiquidGlassEffect {

    /**
     * Huanghun: values used when the caller does not supply the extra tuning
     * knobs. They are the reference values of the original shader.
     */
    private static final float DEFAULT_SPECULAR_STRENGTH = 1f;
    private static final float DEFAULT_DISPERSION_STRENGTH = 1f;
    private static final float DEFAULT_FROST_AMOUNT = 0f;

    /**
     * Sentinel used so the very first {@link #update} call always pushes every
     * uniform into the shader, even when the caller passes the default values.
     * (An unset float uniform reads as 0 inside AGSL, which would silently
     * disable the highlights and the dispersion.)
     */
    private static final float UNSET = -1000f;

    private final RenderNode node;
    private final RuntimeShader shader;
    private RenderEffect effect;

    public LiquidGlassEffect(RenderNode node) {
        this.node = node;
        final String code = AndroidUtilities.readRes(R.raw.liquid_glass_shader);
        shader = new RuntimeShader(code);
        node.setRenderEffect(effect = RenderEffect.createRuntimeShaderEffect(shader, "img"));
    }

    private float resolutionX, resolutionY;
    private float centerX, centerY;
    private float sizeX, sizeY;
    private float radiusLeftTop;
    private float radiusRightTop;
    private float radiusRightBottom;
    private float radiusLeftBottom;
    private float thickness;
    private float intensity;
    private float index;
    private int foregroundColor;
    private float specularStrength = UNSET;
    private float dispersionStrength = UNSET;
    private float frostAmount = UNSET;

    public void update(
        float left, float top, float right, float bottom,
        float radiusLeftTop, float radiusRightTop, float radiusRightBottom, float radiusLeftBottom,

        float thickness,
        float intensity,
        float index,
        int foregroundColor
    ) {
        update(
            left, top, right, bottom,
            radiusLeftTop, radiusRightTop, radiusRightBottom, radiusLeftBottom,
            thickness, intensity, index, foregroundColor,
            DEFAULT_SPECULAR_STRENGTH, DEFAULT_DISPERSION_STRENGTH, DEFAULT_FROST_AMOUNT
        );
    }

    /**
     * Huanghun: extended variant adding the three user tunable parameters
     * (highlight strength, edge dispersion, frosted body).
     */
    public void update(
        float left, float top, float right, float bottom,
        float radiusLeftTop, float radiusRightTop, float radiusRightBottom, float radiusLeftBottom,

        float thickness,
        float intensity,
        float index,
        int foregroundColor,

        float specularStrength,
        float dispersionStrength,
        float frostAmount
    ) {
        float resolutionX = node.getWidth();
        float resolutionY = node.getHeight();
        float centerX = (left + right) / 2;
        float centerY = (top + bottom) / 2;
        float width = right - left, height = bottom - top;
        float sizeX = width / 2;
        float sizeY = height / 2;

        if (radiusLeftTop + radiusLeftBottom > height) {
            float a = radiusLeftTop / (radiusLeftTop + radiusLeftBottom);
            radiusLeftTop = height * a;
            radiusLeftBottom = height * (1.0f - a);
        }
        if (radiusRightTop + radiusRightBottom > height) {
            float a = radiusRightTop / (radiusRightTop + radiusRightBottom);
            radiusRightTop = height * a;
            radiusRightBottom = height * (1.0f - a);
        }

        if (
            Math.abs(this.resolutionX - resolutionX) > 0.1f ||
            Math.abs(this.resolutionY - resolutionY) > 0.1f ||
            Math.abs(this.centerX - centerX) > 0.1f ||
            Math.abs(this.centerY - centerY) > 0.1f ||
            Math.abs(this.sizeX - sizeX) > 0.1f ||
            Math.abs(this.sizeY - sizeY) > 0.1f ||
            Math.abs(this.radiusLeftTop - radiusLeftTop) > 0.1f ||
            Math.abs(this.radiusRightTop - radiusRightTop) > 0.1f ||
            Math.abs(this.radiusRightBottom - radiusRightBottom) > 0.1f ||
            Math.abs(this.radiusLeftBottom - radiusLeftBottom) > 0.1f ||
            Math.abs(this.thickness - thickness) > 0.1f ||
            Math.abs(this.intensity - intensity) > 0.1f ||
            Math.abs(this.index - index) > 0.1f ||
            Math.abs(this.specularStrength - specularStrength) > 0.001f ||
            Math.abs(this.dispersionStrength - dispersionStrength) > 0.001f ||
            Math.abs(this.frostAmount - frostAmount) > 0.001f ||
            this.foregroundColor != foregroundColor
        ) {
            this.foregroundColor = foregroundColor;

            final float a = Color.alpha(foregroundColor) / 255f;
            final float r = Color.red(foregroundColor) / 255f * a;
            final float g = Color.green(foregroundColor) / 255f * a;
            final float b = Color.blue(foregroundColor) / 255f * a;

            shader.setFloatUniform("resolution", this.resolutionX = resolutionX, this.resolutionY = resolutionY);
            shader.setFloatUniform("center", this.centerX = centerX, this.centerY = centerY);
            shader.setFloatUniform("size", this.sizeX = sizeX, this.sizeY = sizeY);
            shader.setFloatUniform("radius", this.radiusRightBottom = radiusRightBottom, this.radiusRightTop = radiusRightTop, this.radiusLeftBottom = radiusLeftBottom, this.radiusLeftTop = radiusLeftTop);
            shader.setFloatUniform("thickness", this.thickness = thickness);
            shader.setFloatUniform("refract_intensity", this.intensity = intensity);
            shader.setFloatUniform("refract_index", this.index = index);
            shader.setFloatUniform("foreground_color_premultiplied", r, g, b, a);
            shader.setFloatUniform("specular_strength", this.specularStrength = specularStrength);
            shader.setFloatUniform("dispersion_strength", this.dispersionStrength = dispersionStrength);
            shader.setFloatUniform("frost_amount", this.frostAmount = frostAmount);
            node.setRenderEffect(effect = RenderEffect.createRuntimeShaderEffect(shader, "img"));
        }
    }

}
