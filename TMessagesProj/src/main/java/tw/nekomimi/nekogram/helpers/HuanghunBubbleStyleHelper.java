package tw.nekomimi.nekogram.helpers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * Local-only custom bubble style helpers.
 *
 * Style 0 is the existing Huanghun liquid-glass bubble.  Positive style IDs are deliberately
 * bounded so corrupt imports or stale preferences can never affect message drawing.
 */
public final class HuanghunBubbleStyleHelper {

    public static final int DEFAULT_STYLE = 0;
    // User-provided complete local catalog. Positive IDs map one-to-one to a stable drawable
    // resource name so a message keeps the same skin after restart or delivery acknowledgement.
    public static final int STYLE_COUNT = 85;

    private static final String[] STYLE_NAMES = new String[]{
            "液态玻璃",
            "亡灵之梦",
            "蓝金星鹿鲸",
            "神明幽龙",
            "洛天依",
            "松懒了",
            "阿巴说",
            "猫猫大厨",
            "春晓时",
            "鲸鱼玫瑰",
            "天使与恶魔",
            "夜空星辰",
            "桃源深处有人家",
            "熊出没",
            "可爱蓝兔兔",
            "线条小狗_彩框",
            "弹弹小考拉",
            "望仔纸飞机",
            "小熊猫啊中",
            "鼠球球",
            "樱桃喵布丁",
            "可爱团子",
            "深海奶龙",
            "东方小女孩",
            "龙龙喂龙龙",
            "严莉莉线框",
            "小狼白子",
            "斗鸡贴贴",
            "敦煌",
            "北极熊阿俄",
            "绵绵约会日",
            "彩虹独角兽",
            "家乐温温变",
            "初音未来",
            "Phigros",
            "恶魔肥肥叮",
            "红蓝小绿趴",
            "日系猫猫趴",
            "毛毛暹罗猫",
            "小圆欣喜",
            "白光莹",
            "水豚噜噜",
            "亡灵之梦_再录",
            "菜狗气泡",
            "小伊猫猫",
            "第五人格",
            "线条小狗_粉色收音机",
            "瘦子丑丑",
            "少爷因因喵",
            "绒团小薯",
            "繁花小因",
            "猫耳小艾萌",
            "小艾少爷",
            "因为有艾",
            "艾因游戏喵",
            "套麻袋猫猫",
            "红蝶飞飞",
            "研究叫叫",
            "惊讶猫猫",
            "未显示名称_绿气泡",
            "未显示名称_黄白角色",
            "未显示名称_紫色猫咪",
            "朵萌小白_狗猫",
            "萌萌的小白_蓝底",
            "救世小白狗_白底",
            "阴胖恶魔猫_蓝底",
            "朵毛小白_黄尾",
            "Q萌小白_薄荷",
            "星星小白狗_紫色",
            "双色倒影汪_火箭",
            "救世小白狗_重复样式",
            "阴胖恶魔猫_重复样式",
            "阳光萨摩耶_骨头",
            "白尼比格嗷_天气猫",
            "星星小白狗_重复样式",
            "双色倒影汪_重复样式",
            "阴阳萨摩耶_蓝发",
            "白尼QQ狼_天气花",
            "炸毛金渐层_羊皮纸",
            "金色炸毛猫_金框",
            "黄猫蓝狗贴_雨滴",
            "蓝色小白耶_太阳",
            "悲伤破碎狗_流泪",
            "粥米敖世耶_金星",
            "小白耶守护_蓝色",
            "双拼萨摩耶_黄白",
    };

    private HuanghunBubbleStyleHelper() {
    }

    public static int normalizeStyle(int style) {
        return style >= DEFAULT_STYLE && style <= STYLE_COUNT ? style : DEFAULT_STYLE;
    }

    /**
     * Returns the style that will be frozen on the next newly created local outgoing message.
     * Unlike {@link #getNextMessageStyle()}, this method never advances a batch queue and is safe
     * for UI binding, previews and repeated RecyclerView measurement.
     */
    public static synchronized int getPendingMessageStyle() {
        ArrayList<Integer> rotation = readRotation();
        if (!rotation.isEmpty()) {
            return rotation.get(normalizeRotationIndex(NekoConfig.huanghunBubbleRotationIndex.Int(), rotation.size()));
        }
        return normalizeStyle(NekoConfig.huanghunBubbleStyle.Int());
    }

    /**
     * Freezes the current batch entry for a new local outgoing message and only then advances the
     * queue. Retry paths never call this method, so the style already stored on a failed message
     * remains unchanged.
     */
    public static synchronized int getNextMessageStyle() {
        ArrayList<Integer> rotation = readRotation();
        if (rotation.isEmpty()) {
            return normalizeStyle(NekoConfig.huanghunBubbleStyle.Int());
        }
        int index = normalizeRotationIndex(NekoConfig.huanghunBubbleRotationIndex.Int(), rotation.size());
        int style = rotation.get(index);
        NekoConfig.huanghunBubbleRotationIndex.setConfigInt((index + 1) % rotation.size());
        return style;
    }

    public static synchronized void setRotationStyles(Iterable<Integer> styles) {
        String serialized = writeStyles(styles);
        NekoConfig.huanghunBubbleRotation.setConfigString(serialized);
        NekoConfig.huanghunBubbleRotationIndex.setConfigInt(0);
        ArrayList<Integer> rotation = readRotation();
        // An empty batch is an explicit request to stop custom rotation and return to the existing
        // liquid-glass default, never to leave a stale single-choice skin active.
        NekoConfig.huanghunBubbleStyle.setConfigInt(rotation.isEmpty() ? DEFAULT_STYLE : rotation.get(0));
    }

    public static synchronized void clearRotationStyles() {
        NekoConfig.huanghunBubbleRotation.setConfigString("");
        NekoConfig.huanghunBubbleRotationIndex.setConfigInt(0);
    }

    public static boolean isCustomStyle(int style) {
        return normalizeStyle(style) != DEFAULT_STYLE;
    }

    public static String getStyleDisplayName(int style) {
        int normalized = normalizeStyle(style);
        return STYLE_NAMES[normalized];
    }

    public static String getSkinResourceName(int style) {
        int normalized = normalizeStyle(style);
        return normalized == DEFAULT_STYLE ? null : String.format(Locale.US, "huanghun_bubble_skin_%03d", normalized);
    }

    public static ArrayList<Integer> readFavorites() {
        return readStyles(NekoConfig.huanghunBubbleFavorites.String());
    }

    public static ArrayList<Integer> readRotation() {
        return readStyles(NekoConfig.huanghunBubbleRotation.String());
    }

    public static String writeFavorites(Iterable<Integer> styles) {
        return writeStyles(styles);
    }

    private static ArrayList<Integer> readStyles(String raw) {
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        if (raw != null && !raw.isEmpty()) {
            String[] split = raw.split(",");
            for (String item : split) {
                try {
                    int style = normalizeStyle(Integer.parseInt(item.trim()));
                    if (style != DEFAULT_STYLE) {
                        values.add(style);
                    }
                } catch (NumberFormatException ignore) {
                    // Ignore only the malformed item and keep the rest of the user's collection.
                }
            }
        }
        return new ArrayList<>(values);
    }

    private static String writeStyles(Iterable<Integer> styles) {
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        for (Integer style : styles) {
            if (style == null) {
                continue;
            }
            int normalized = normalizeStyle(style);
            if (normalized != DEFAULT_STYLE) {
                values.add(normalized);
            }
        }
        StringBuilder result = new StringBuilder();
        for (Integer style : values) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(style);
        }
        return result.toString();
    }

    private static int normalizeRotationIndex(int index, int size) {
        return size <= 0 ? 0 : Math.floorMod(index, size);
    }
}
