package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Outline;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;
import java.util.Locale;

import tw.nekomimi.nekogram.glass.HuanghunGlass;
import tw.nekomimi.nekogram.glass.HuanghunGlassPreviewView;

/**
 * Huanghun - Liquid Glass tuning screen.
 *
 * <p>Reached from the drawer entry placed right below "Night mode". The preview
 * at the top is a real glass panel rendered with the same shader as the app, so
 * every slider move can be judged before leaving the screen. Touching any
 * control implicitly switches the custom parameters on, which keeps the flow
 * from "I found something I like" to "it is applied" as short as possible.</p>
 */
public class HuanghunGlassTuningActivity extends BaseFragment {

    private interface ValueCallback {
        void onValue(float value);
    }

    private HuanghunGlassPreviewView preview;
    private TextView enablePill;
    private final ArrayList<SliderRow> sliderRows = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        HuanghunGlass.isEnabled();
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        syncAll(false);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        HuanghunGlass.refreshVisibleUi(getParentActivity());
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.HuanghunGlassTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(30));
        scrollView.addView(content, LayoutHelper.createScroll(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        // ------------------------------------------------------------ preview
        preview = new HuanghunGlassPreviewView(context);
        preview.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(18));
            }
        });
        preview.setClipToOutline(true);
        content.addView(preview, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, dp(190), Gravity.CENTER_HORIZONTAL,
            dp(12), dp(6), dp(12), dp(6)));

        content.addView(
            makeCaption(context, LocaleController.getString(R.string.HuanghunGlassPreviewHint)),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                dp(20), 0, dp(20), dp(10)));

        // ------------------------------------------------------- enable switch
        final LinearLayout enableRow = new LinearLayout(context);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.setPadding(dp(20), dp(12), dp(20), dp(12));
        enableRow.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        final TextView enableLabel = new TextView(context);
        enableLabel.setText(LocaleController.getString(R.string.HuanghunGlassEnable));
        enableLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        enableLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        enableLabel.setSingleLine(true);
        enableLabel.setEllipsize(TextUtils.TruncateAt.END);
        enableRow.addView(enableLabel, LayoutHelper.createLinear(
            0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        enablePill = new TextView(context);
        enablePill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        enablePill.setGravity(Gravity.CENTER);
        enablePill.setPadding(dp(14), dp(7), dp(14), dp(7));
        enableRow.addView(enablePill, LayoutHelper.createLinear(
            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL,
            dp(10), 0, 0, 0));

        enableRow.setOnClickListener(v -> {
            HuanghunGlass.setEnabled(!HuanghunGlass.isEnabled());
            syncAll(true);
        });
        content.addView(enableRow, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, dp(2), 0, dp(2)));

        // --------------------------------------------------------- parameters
        content.addView(
            makeSection(context, LocaleController.getString(R.string.HuanghunGlassSectionParams)),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addSliderRow(context, content, R.string.HuanghunGlassThickness,
            HuanghunGlass.THICKNESS_MIN, HuanghunGlass.THICKNESS_MAX, 0, " dp",
            value -> HuanghunGlass.setThickness(value));

        addSliderRow(context, content, R.string.HuanghunGlassIntensity,
            HuanghunGlass.INTENSITY_MIN, HuanghunGlass.INTENSITY_MAX, 2, "",
            value -> HuanghunGlass.setIntensity(value));

        addSliderRow(context, content, R.string.HuanghunGlassIndex,
            HuanghunGlass.INDEX_MIN, HuanghunGlass.INDEX_MAX, 2, "",
            value -> HuanghunGlass.setIndex(value));

        addSliderRow(context, content, R.string.HuanghunGlassSpecular,
            HuanghunGlass.SPECULAR_MIN, HuanghunGlass.SPECULAR_MAX, 2, "",
            value -> HuanghunGlass.setSpecular(value));

        addSliderRow(context, content, R.string.HuanghunGlassDispersion,
            HuanghunGlass.DISPERSION_MIN, HuanghunGlass.DISPERSION_MAX, 2, "",
            value -> HuanghunGlass.setDispersion(value));

        addSliderRow(context, content, R.string.HuanghunGlassFrost,
            HuanghunGlass.FROST_MIN, HuanghunGlass.FROST_MAX, 2, "",
            value -> HuanghunGlass.setFrost(value));

        addSliderRow(context, content, R.string.HuanghunGlassFresnel,
            HuanghunGlass.FRESNEL_MIN, HuanghunGlass.FRESNEL_MAX, 2, "",
            value -> HuanghunGlass.setFresnel(value));

        addSliderRow(context, content, R.string.HuanghunGlassReflection,
            HuanghunGlass.REFLECTION_MIN, HuanghunGlass.REFLECTION_MAX, 2, "",
            value -> HuanghunGlass.setReflection(value));

        addSliderRow(context, content, R.string.HuanghunGlassGrain,
            HuanghunGlass.GRAIN_MIN, HuanghunGlass.GRAIN_MAX, 2, "",
            value -> HuanghunGlass.setGrain(value));

        // ------------------------------------------------------------ presets
        content.addView(
            makeSection(context, LocaleController.getString(R.string.HuanghunGlassSectionPresets)),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final HorizontalScrollView presetScroll = new HorizontalScrollView(context);
        presetScroll.setHorizontalScrollBarEnabled(false);
        presetScroll.setClipChildren(false);

        final LinearLayout presetRow = new LinearLayout(context);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(dp(14), dp(2), dp(14), dp(2));
        presetScroll.addView(presetRow, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int preset = 0; preset < HuanghunGlass.PRESET_COUNT; preset++) {
            presetRow.addView(makePresetChip(context, preset), LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL,
                dp(4), 0, dp(4), 0));
        }
        content.addView(presetScroll, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, dp(4), 0, dp(8)));

        // -------------------------------------------------------------- reset
        final TextView reset = new TextView(context);
        reset.setText(LocaleController.getString(R.string.HuanghunGlassReset));
        reset.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        reset.setGravity(Gravity.CENTER);
        reset.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        reset.setBackground(Theme.AdaptiveRipple.filledRect(
            Theme.getColor(Theme.key_featuredStickers_addButton), dp(10)));
        reset.setOnClickListener(v -> {
            HuanghunGlass.resetToDefaults();
            syncAll(true);
        });
        content.addView(reset, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, dp(44), Gravity.CENTER_HORIZONTAL,
            dp(18), dp(6), dp(18), dp(4)));

        content.addView(
            makeCaption(context, LocaleController.getString(R.string.HuanghunGlassFooter)),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                dp(20), dp(8), dp(20), 0));

        fragmentView = scrollView;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        syncAll(false);
        return fragmentView;
    }

    // --------------------------------------------------------------- builders

    private TextView makeSection(Context context, String text) {
        final TextView header = new TextView(context);
        header.setText(text);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        header.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        header.setTextColor(hhColor(Theme.key_dialogTextBlue2));
        header.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(6));
        return header;
    }

    private TextView makeCaption(Context context, String text) {
        final TextView caption = new TextView(context);
        caption.setText(text);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        caption.setTextColor(hhColor(Theme.key_windowBackgroundWhiteGrayText2));
        caption.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        caption.setLineSpacing(dp(2), 1f);
        return caption;
    }

    private View makePresetChip(Context context, final int preset) {
        final TextView chip = new TextView(context);
        chip.setText(presetName(preset));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(hhColor(Theme.key_featuredStickers_buttonText));
        chip.setPadding(dp(16), dp(8), dp(16), dp(8));
        chip.setBackground(Theme.AdaptiveRipple.filledRect(
            hhColor(Theme.key_featuredStickers_addButton), dp(17)));
        chip.setOnClickListener(v -> {
            HuanghunGlass.applyPreset(preset);
            HuanghunGlass.setEnabled(true);
            syncAll(true);
        });
        return chip;
    }

    private String presetName(int preset) {
        switch (preset) {
            case HuanghunGlass.PRESET_IOS:
                return LocaleController.getString(R.string.HuanghunGlassPresetIos);
            case HuanghunGlass.PRESET_CLEAR:
                return LocaleController.getString(R.string.HuanghunGlassPresetClear);
            case HuanghunGlass.PRESET_THICK:
                return LocaleController.getString(R.string.HuanghunGlassPresetThick);
            case HuanghunGlass.PRESET_SOFT:
                return LocaleController.getString(R.string.HuanghunGlassPresetSoft);
            case HuanghunGlass.PRESET_DEFAULT:
            default:
                return LocaleController.getString(R.string.HuanghunGlassPresetDefault);
        }
    }

    private void addSliderRow(Context context, LinearLayout parent, int nameRes,
                              float min, float max, final int decimals, String suffix,
                              final ValueCallback callback) {
        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(10), dp(20), 0);

        final TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        label.setTextColor(hhColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        container.addView(label, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final SeekBarView seekBar = new SeekBarView(context);
        seekBar.setReportChanges(true);
        container.addView(seekBar, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, dp(36), 0, -dp(2), 0, 0));

        final SliderRow row = new SliderRow(LocaleController.getString(nameRes), label, seekBar,
            min, max, decimals, suffix, callback);

        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                row.applyProgress(progress, true);
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }
        });

        sliderRows.add(row);
        parent.addView(container, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    // ------------------------------------------------------------------ state

    private class SliderRow {
        final String name;
        final TextView label;
        final SeekBarView seekBar;
        final float min;
        final float max;
        final int decimals;
        final String suffix;
        final ValueCallback callback;

        SliderRow(String name, TextView label, SeekBarView seekBar,
                  float min, float max, int decimals, String suffix, ValueCallback callback) {
            this.name = name;
            this.label = label;
            this.seekBar = seekBar;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.suffix = suffix;
            this.callback = callback;
        }

        float toProgress(float value) {
            return max > min ? (value - min) / (max - min) : 0f;
        }

        void bind(float value) {
            applyProgress(toProgress(value), false);
        }

        void applyProgress(float progress, boolean notify) {
            final float clamped = Math.max(0f, Math.min(1f, progress));
            final float value = min + clamped * (max - min);
            label.setText(name + "   " + format(value));
            seekBar.setProgress(clamped);
            if (notify) {
                callback.onValue(value);
                onValueChanged();
            }
        }

        String format(float value) {
            return String.format(Locale.US, "%." + decimals + "f", value) + suffix;
        }
    }

    private void updatePreview() {
        if (preview == null) {
            return;
        }
        preview.setTuning(
            HuanghunGlass.getThickness(),
            HuanghunGlass.getIntensity(),
            HuanghunGlass.getIndex(),
            HuanghunGlass.getSpecular(),
            HuanghunGlass.getDispersion(),
            HuanghunGlass.getFrost(),
            HuanghunGlass.getFresnel(),
            HuanghunGlass.getReflection(),
            HuanghunGlass.getGrain()
        );
    }

    /** Called on every slider move: keeps the preview and the host UI honest. */
    private void onValueChanged() {
        if (!HuanghunGlass.isEnabled()) {
            HuanghunGlass.setEnabled(true);
            updateEnablePill();
        }
        updatePreview();
        HuanghunGlass.refreshVisibleUi(getParentActivity());
    }

    private void updateEnablePill() {
        if (enablePill == null) {
            return;
        }
        final boolean enabled = HuanghunGlass.isEnabled();
        enablePill.setText(LocaleController.getString(
            enabled ? R.string.HuanghunGlassOn : R.string.HuanghunGlassOff));
        if (enabled) {
            enablePill.setTextColor(hhColor(Theme.key_featuredStickers_buttonText));
            enablePill.setBackground(Theme.AdaptiveRipple.filledRect(
                hhColor(Theme.key_featuredStickers_addButton), dp(16)));
        } else {
            final int gray = hhColor(Theme.key_windowBackgroundWhiteGrayText2);
            enablePill.setTextColor(gray);
            enablePill.setBackground(Theme.AdaptiveRipple.filledRect(
                Theme.multAlpha(gray, 0.22f), dp(16)));
        }
    }

    private void syncAll(boolean notifyHost) {
        if (sliderRows.size() == 9) {
            sliderRows.get(0).bind(HuanghunGlass.getThickness());
            sliderRows.get(1).bind(HuanghunGlass.getIntensity());
            sliderRows.get(2).bind(HuanghunGlass.getIndex());
            sliderRows.get(3).bind(HuanghunGlass.getSpecular());
            sliderRows.get(4).bind(HuanghunGlass.getDispersion());
            sliderRows.get(5).bind(HuanghunGlass.getFrost());
            sliderRows.get(6).bind(HuanghunGlass.getFresnel());
            sliderRows.get(7).bind(HuanghunGlass.getReflection());
            sliderRows.get(8).bind(HuanghunGlass.getGrain());
        }
        updateEnablePill();
        updatePreview();
        if (notifyHost) {
            HuanghunGlass.refreshVisibleUi(getParentActivity());
        }
    }

    private int hhColor(int key) {
        final Theme.ResourcesProvider provider = resourceProvider;
        if (provider != null) {
            return Theme.getColor(key, provider);
        }
        return Theme.getColor(key);
    }
}
