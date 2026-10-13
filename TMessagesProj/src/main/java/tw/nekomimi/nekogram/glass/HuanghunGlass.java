package tw.nekomimi.nekogram.glass;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.annotation.Keep;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.LaunchActivity;

import java.util.List;

/**
 * Huanghun - Liquid Glass tuning.
 *
 * <p>Single source of truth for the user adjustable liquid glass parameters
 * (thickness / refraction intensity / refraction index / highlight strength /
 * edge dispersion / frosted body). Everything is persisted in its own
 * preference file so uninstalling or clearing data resets it.</p>
 *
 * <p>When {@link #isEnabled()} is {@code false} every resolver returns the
 * caller supplied fallback, which means the renderer behaves byte for byte
 * like the stock implementation. That keeps the feature strictly opt-in.</p>
 */
@Keep
public final class HuanghunGlass {

    public static final float THICKNESS_MIN = 2f;
    public static final float THICKNESS_MAX = 24f;
    public static final float THICKNESS_DEFAULT = 11f;

    public static final float INTENSITY_MIN = 0f;
    public static final float INTENSITY_MAX = 1.2f;
    public static final float INTENSITY_DEFAULT = 0.75f;

    public static final float INDEX_MIN = 1.05f;
    public static final float INDEX_MAX = 2.2f;
    public static final float INDEX_DEFAULT = 1.5f;

    public static final float SPECULAR_MIN = 0f;
    public static final float SPECULAR_MAX = 2f;
    public static final float SPECULAR_DEFAULT = 1f;

    public static final float DISPERSION_MIN = 0f;
    public static final float DISPERSION_MAX = 2f;
    public static final float DISPERSION_DEFAULT = 1f;

    public static final float FROST_MIN = 0f;
    public static final float FROST_MAX = 1f;
    public static final float FROST_DEFAULT = 0f;

    /** Preview material opacity is intentionally not exposed. */
    private static final String PREFS_NAME = "huanghun_glass_tuning";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_THICKNESS = "thickness";
    private static final String KEY_INTENSITY = "intensity";
    private static final String KEY_INDEX = "index";
    private static final String KEY_SPECULAR = "specular";
    private static final String KEY_DISPERSION = "dispersion";
    private static final String KEY_FROST = "frost";

    private static boolean loaded;
    private static boolean enabled;

    private static float thickness = THICKNESS_DEFAULT;
    private static float intensity = INTENSITY_DEFAULT;
    private static float index = INDEX_DEFAULT;
    private static float specular = SPECULAR_DEFAULT;
    private static float dispersion = DISPERSION_DEFAULT;
    private static float frost = FROST_DEFAULT;
    private static boolean surfaceRebuildPending;

    /**
     * Bumped on every mutation. The renderer compares it against the value it
     * used for its last frame and refreshes the display list when it differs,
     * which is what makes the sliders apply to already visible glass surfaces
     * without having to reach into every fragment.
     */
    private static int generation;

    private HuanghunGlass() {
    }

    private static SharedPreferences prefs() {
        try {
            final Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        final SharedPreferences preferences = prefs();
        if (preferences == null) {
            return;
        }
        try {
            enabled = preferences.getBoolean(KEY_ENABLED, false);
            thickness = clamp(preferences.getFloat(KEY_THICKNESS, THICKNESS_DEFAULT), THICKNESS_MIN, THICKNESS_MAX);
            intensity = clamp(preferences.getFloat(KEY_INTENSITY, INTENSITY_DEFAULT), INTENSITY_MIN, INTENSITY_MAX);
            index = clamp(preferences.getFloat(KEY_INDEX, INDEX_DEFAULT), INDEX_MIN, INDEX_MAX);
            specular = clamp(preferences.getFloat(KEY_SPECULAR, SPECULAR_DEFAULT), SPECULAR_MIN, SPECULAR_MAX);
            dispersion = clamp(preferences.getFloat(KEY_DISPERSION, DISPERSION_DEFAULT), DISPERSION_MIN, DISPERSION_MAX);
            frost = clamp(preferences.getFloat(KEY_FROST, FROST_DEFAULT), FROST_MIN, FROST_MAX);
        } catch (Throwable ignored) {
        }
    }

    private static void persist() {
        final SharedPreferences preferences = prefs();
        if (preferences == null) {
            return;
        }
        try {
            preferences.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putFloat(KEY_THICKNESS, thickness)
                .putFloat(KEY_INTENSITY, intensity)
                .putFloat(KEY_INDEX, index)
                .putFloat(KEY_SPECULAR, specular)
                .putFloat(KEY_DISPERSION, dispersion)
                .putFloat(KEY_FROST, frost)
                .apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isEnabled() {
        ensureLoaded();
        return enabled;
    }

    public static void setEnabled(boolean value) {
        ensureLoaded();
        if (enabled == value) {
            return;
        }
        final boolean wasLiquidGlassActive = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) || enabled;
        enabled = value;
        if (value && !wasLiquidGlassActive) {
            surfaceRebuildPending = true;
        }
        persist();
        bump();
    }

    public static float getThickness() {
        ensureLoaded();
        return thickness;
    }

    public static void setThickness(float value) {
        ensureLoaded();
        thickness = clamp(value, THICKNESS_MIN, THICKNESS_MAX);
        persist();
        bump();
    }

    public static float getIntensity() {
        ensureLoaded();
        return intensity;
    }

    public static void setIntensity(float value) {
        ensureLoaded();
        intensity = clamp(value, INTENSITY_MIN, INTENSITY_MAX);
        persist();
        bump();
    }

    public static float getIndex() {
        ensureLoaded();
        return index;
    }

    public static void setIndex(float value) {
        ensureLoaded();
        index = clamp(value, INDEX_MIN, INDEX_MAX);
        persist();
        bump();
    }

    public static float getSpecular() {
        ensureLoaded();
        return specular;
    }

    public static void setSpecular(float value) {
        ensureLoaded();
        specular = clamp(value, SPECULAR_MIN, SPECULAR_MAX);
        persist();
        bump();
    }

    public static float getDispersion() {
        ensureLoaded();
        return dispersion;
    }

    public static void setDispersion(float value) {
        ensureLoaded();
        dispersion = clamp(value, DISPERSION_MIN, DISPERSION_MAX);
        persist();
        bump();
    }

    public static float getFrost() {
        ensureLoaded();
        return frost;
    }

    public static void setFrost(float value) {
        ensureLoaded();
        frost = clamp(value, FROST_MIN, FROST_MAX);
        persist();
        bump();
    }

    public static boolean isCustomized() {
        ensureLoaded();
        return Math.abs(thickness - THICKNESS_DEFAULT) > 0.01f
            || Math.abs(intensity - INTENSITY_DEFAULT) > 0.001f
            || Math.abs(index - INDEX_DEFAULT) > 0.001f
            || Math.abs(specular - SPECULAR_DEFAULT) > 0.001f
            || Math.abs(dispersion - DISPERSION_DEFAULT) > 0.001f
            || Math.abs(frost - FROST_DEFAULT) > 0.001f;
    }

    /** Restores the reference values without touching the enable switch. */
    public static void resetToDefaults() {
        ensureLoaded();
        thickness = THICKNESS_DEFAULT;
        intensity = INTENSITY_DEFAULT;
        index = INDEX_DEFAULT;
        specular = SPECULAR_DEFAULT;
        dispersion = DISPERSION_DEFAULT;
        frost = FROST_DEFAULT;
        persist();
        bump();
    }

    public static void bump() {
        generation++;
    }

    public static int getGeneration() {
        return generation;
    }

    // ---------------------------------------------------------------- resolve

    /**
     * @param fallback thickness in pixels requested by the caller
     * @return the tuned thickness in pixels, or {@code fallback} when disabled
     */
    public static int resolveThicknessPx(int fallback) {
        ensureLoaded();
        if (!enabled) {
            return fallback;
        }
        return Math.max(1, Math.round(thickness));
    }

    public static float resolveIntensity(float fallback) {
        ensureLoaded();
        return enabled ? intensity : fallback;
    }

    public static float resolveIndex(float fallback) {
        ensureLoaded();
        return enabled ? index : fallback;
    }

    public static float resolveSpecular() {
        ensureLoaded();
        return enabled ? specular : SPECULAR_DEFAULT;
    }

    public static float resolveDispersion() {
        ensureLoaded();
        return enabled ? dispersion : DISPERSION_DEFAULT;
    }

    public static float resolveFrost() {
        ensureLoaded();
        return enabled ? frost : FROST_DEFAULT;
    }

    // ------------------------------------------------------------------ presets

    public static final int PRESET_DEFAULT = 0;
    public static final int PRESET_IOS = 1;
    public static final int PRESET_CLEAR = 2;
    public static final int PRESET_THICK = 3;
    public static final int PRESET_SOFT = 4;
    public static final int PRESET_COUNT = 5;

    public static void applyPreset(int preset) {
        ensureLoaded();
        switch (preset) {
            case PRESET_IOS:
                // Crisp rim, strong edge refraction, almost no frost: this is
                // the look of the iOS control centre material.
                thickness = 13f;
                intensity = 1.05f;
                index = 1.42f;
                specular = 1.25f;
                dispersion = 1.15f;
                frost = 0.06f;
                break;
            case PRESET_CLEAR:
                thickness = 7f;
                intensity = 0.5f;
                index = 1.2f;
                specular = 0.85f;
                dispersion = 0.7f;
                frost = 0f;
                break;
            case PRESET_THICK:
                thickness = 20f;
                intensity = 1.2f;
                index = 1.75f;
                specular = 1.1f;
                dispersion = 1.2f;
                frost = 0.25f;
                break;
            case PRESET_SOFT:
                thickness = 15f;
                intensity = 0.65f;
                index = 1.35f;
                specular = 0.6f;
                dispersion = 0.5f;
                frost = 0.55f;
                break;
            case PRESET_DEFAULT:
            default:
                thickness = THICKNESS_DEFAULT;
                intensity = INTENSITY_DEFAULT;
                index = INDEX_DEFAULT;
                specular = SPECULAR_DEFAULT;
                dispersion = DISPERSION_DEFAULT;
                frost = FROST_DEFAULT;
                break;
        }
        persist();
        bump();
    }

    // ------------------------------------------------------------------ refresh

    /**
     * Best effort refresh of everything currently on screen. The renderers also
     * pick the new values up through {@link #getGeneration()}, this only makes
     * the change immediate instead of waiting for the next redraw.
     */
    public static void refreshVisibleUi(Activity activity) {
        if (activity == null) {
            return;
        }
        if (surfaceRebuildPending) {
            surfaceRebuildPending = false;
            try {
                activity.recreate();
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            final View decorView = activity.getWindow() != null
                ? activity.getWindow().getDecorView()
                : null;
            if (decorView != null) {
                decorView.invalidate();
            }
        } catch (Throwable ignored) {
        }
        if (!(activity instanceof LaunchActivity)) {
            return;
        }
        try {
            final List<BaseFragment> stack = ((LaunchActivity) activity).getActionBarLayout().getFragmentStack();
            if (stack == null) {
                return;
            }
            for (int i = 0; i < stack.size(); i++) {
                final View view = stack.get(i).getFragmentView();
                if (view instanceof SizeNotifierFrameLayout) {
                    ((SizeNotifierFrameLayout) view).invalidateBlurredViews();
                    view.invalidate();
                } else if (view != null) {
                    view.invalidate();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
