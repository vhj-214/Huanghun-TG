package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.List;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

public class TypefaceHelper {

    private static final String TEST_TEXT;
    private static final int CANVAS_SIZE = 40;
    private static final Paint PAINT = new Paint() {{
        setTextSize(20);
        setAntiAlias(false);
        setSubpixelText(false);
        setFakeBoldText(false);
    }};

    private static Boolean mediumWeightSupported = null;
    private static Boolean italicSupported = null;

    static {
        var lang = LocaleController.getInstance().getCurrentLocale().getLanguage();
        if (List.of("zh", "ja", "ko").contains(lang)) {
            TEST_TEXT = "你好";
        } else if (List.of("ar", "fa").contains(lang)) {
            TEST_TEXT = "مرحبا";
        } else if ("iw".equals(lang)) {
            TEST_TEXT = "שלום";
        } else if ("th".equals(lang)) {
            TEST_TEXT = "สวัสดี";
        } else if ("hi".equals(lang)) {
            TEST_TEXT = "नमस्ते";
        } else if (List.of("ru", "uk", "ky", "be", "sr").contains(lang)) {
            TEST_TEXT = "Привет";
        } else {
            TEST_TEXT = "R";
        }
    }

    private static final String HUANGHUN_FONT_PREFIX = "fonts/huanghun/";
    private static final String[] HUANGHUN_FONT_ASSETS = new String[] {
            "",
            HUANGHUN_FONT_PREFIX + "zcool_kuaile.ttf",
            HUANGHUN_FONT_PREFIX + "zcool_xiaowei.ttf",
            HUANGHUN_FONT_PREFIX + "mashan_zheng.ttf",
            HUANGHUN_FONT_PREFIX + "zhimang_xing.ttf",
            HUANGHUN_FONT_PREFIX + "long_cang.ttf",
            HUANGHUN_FONT_PREFIX + "huninn.ttf",
            HUANGHUN_FONT_PREFIX + "hanzi_pinyin_top.ttf",
            HUANGHUN_FONT_PREFIX + "zcool_qingke_huangyou.ttf",
            HUANGHUN_FONT_PREFIX + "smiley_sans.ttf",
            HUANGHUN_FONT_PREFIX + "lxgw_wenkai.ttf",
            HUANGHUN_FONT_PREFIX + "liu_jian_mao_cao.ttf"
    };
    // Each font file can be tens of megabytes. Retain normal/bold/italic variants so list
    // layouts and message cells never repeatedly reopen an asset while the user scrolls.
    private static final Typeface[][] HUANGHUN_TYPEFACE_CACHE = new Typeface[HUANGHUN_FONT_ASSETS.length][4];

    public static Typeface createTypeface(String assetPath) {
        final int selectedTypeface = NekoConfig.huanghunCustomTypeface.Int();
        if (selectedTypeface > 0 && selectedTypeface < HUANGHUN_FONT_ASSETS.length) {
            Typeface selected = getHuanghunTypeface(assetPath);
            if (selected != null) {
                return selected;
            }
        }
        return switch (assetPath) {
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM ->
                    isMediumWeightSupported() ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.BOLD);
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC ->
                    isMediumWeightSupported() ? Typeface.create("sans-serif-medium", Typeface.ITALIC) : Typeface.create("sans-serif", Typeface.BOLD_ITALIC);
            case AndroidUtilities.TYPEFACE_RCONDENSED_BOLD ->
                    Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD ->
                    createTypeface(800, false);
            case AndroidUtilities.TYPEFACE_RITALIC ->
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? Typeface.create(Typeface.SANS_SERIF, 400, true) : Typeface.create("sans-serif", Typeface.ITALIC);
            case AndroidUtilities.TYPEFACE_ROBOTO_MONO ->
                    Typeface.MONOSPACE;
            case "fonts/rregular.ttf" ->
                    Typeface.create("sans-serif", Typeface.NORMAL);
            default -> createTypefaceFromAsset(assetPath);
        };
    }

    /**
     * Keeps Telegram's regular/bold/italic call sites on the selected Huanghun family.
     * The bundled display fonts provide a single weight, so style is applied by Android.
     */
    @Nullable
    public static Typeface getHuanghunTypeface(String requestedAssetPath) {
        final int selectedTypeface = NekoConfig.huanghunCustomTypeface.Int();
        if (selectedTypeface <= 0 || selectedTypeface >= HUANGHUN_FONT_ASSETS.length) {
            return null;
        }
        try {
            int style = Typeface.NORMAL;
            if (requestedAssetPath.contains("italic")) {
                style = requestedAssetPath.contains("medium") || requestedAssetPath.contains("bold") ? Typeface.BOLD_ITALIC : Typeface.ITALIC;
            } else if (requestedAssetPath.contains("medium") || requestedAssetPath.contains("bold") || requestedAssetPath.contains("rextrabold")) {
                style = Typeface.BOLD;
            }
            synchronized (HUANGHUN_TYPEFACE_CACHE) {
                Typeface cached = HUANGHUN_TYPEFACE_CACHE[selectedTypeface][style];
                if (cached != null) {
                    return cached;
                }
                Typeface baseTypeface = HUANGHUN_TYPEFACE_CACHE[selectedTypeface][Typeface.NORMAL];
                if (baseTypeface == null) {
                    baseTypeface = createTypefaceFromAsset(HUANGHUN_FONT_ASSETS[selectedTypeface]);
                    HUANGHUN_TYPEFACE_CACHE[selectedTypeface][Typeface.NORMAL] = baseTypeface;
                }
                Typeface resolved = style == Typeface.NORMAL ? baseTypeface : Typeface.create(baseTypeface, style);
                HUANGHUN_TYPEFACE_CACHE[selectedTypeface][style] = resolved;
                return resolved;
            }
        } catch (Throwable e) {
            FileLog.e("Could not load Huanghun font '" + HUANGHUN_FONT_ASSETS[selectedTypeface] + "'", e);
            return null;
        }
    }

    @Nullable
    public static Typeface getHuanghunTypeface(boolean bold) {
        return getHuanghunTypeface(bold ? AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM : "fonts/rregular.ttf");
    }

    /** Releases cached bundled typefaces before an explicit app restart or memory cleanup. */
    public static void clearHuanghunTypefaceCache() {
        synchronized (HUANGHUN_TYPEFACE_CACHE) {
            for (int index = 0; index < HUANGHUN_TYPEFACE_CACHE.length; index++) {
                for (int style = 0; style < HUANGHUN_TYPEFACE_CACHE[index].length; style++) {
                    HUANGHUN_TYPEFACE_CACHE[index][style] = null;
                }
            }
        }
    }

    public static Typeface createTypefaceFromAsset(String assetPath) {
        Typeface.Builder builder = new Typeface.Builder(ApplicationLoader.applicationContext.getAssets(), assetPath);
        if (assetPath.contains("rextrabold")) {
            builder.setWeight(800);
        }
        if (assetPath.contains("medium") || assetPath.contains("rbold")) {
            builder.setWeight(700);
        }
        if (assetPath.contains("italic")) {
            builder.setItalic(true);
        }
        return builder.build();
    }

    public static boolean isMediumWeightSupported() {
        if (mediumWeightSupported == null) {
            mediumWeightSupported = testTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            FileLog.d("mediumWeightSupported = " + mediumWeightSupported);
        }
        return mediumWeightSupported;
    }

    public static boolean isItalicSupported() {
        if (italicSupported == null) {
            italicSupported = testTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            FileLog.d("italicSupported = " + italicSupported);
        }
        return italicSupported;
    }

    private static boolean testTypeface(Typeface typeface) {
        Canvas canvas = new Canvas();

        Bitmap bitmap1 = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap1);
        PAINT.setTypeface(null);
        canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);

        Bitmap bitmap2 = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap2);
        PAINT.setTypeface(typeface);
        canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);

        boolean supported = !bitmap1.sameAs(bitmap2);
        AndroidUtilities.recycleBitmaps(List.of(bitmap1, bitmap2));
        return supported;
    }

    public static Typeface createTypeface(int weight, boolean italic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(null, weight, italic);
        }
        var family = switch (weight) {
            case 800 -> "sans-serif-black";
            case 500 -> "sans-serif-medium";
            default -> "sans-serif";
        };
        return Typeface.create(family, italic ? Typeface.ITALIC : Typeface.NORMAL);
    }

    public static SpannableStringBuilder getTitleText(int currentAccount) {
        String title = NaConfig.INSTANCE.getCustomTitle().String();
        if (NaConfig.INSTANCE.getCustomTitleUserName().Bool()) {
            TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();
            if (self != null && self.first_name != null) {
                title = self.first_name;
            }
        }
        var builder = new SpannableStringBuilder(title);
        builder.setSpan(new LeadingMarginSpan.Standard(dp(2), 0), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new TypefaceSpan(TypefaceHelper.createTypeface(600, false), 0, Theme.key_telegram_color_dialogsLogo, null), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

}
