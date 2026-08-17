package com.Huanghun.outfit;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 黄昏本地个性装扮的单机配置。
 * 所有选择仅保存到应用私有 SharedPreferences，不上传、不同步，也不伪造远端账号状态。
 */
public final class HuanghunOutfitConfig {

    public static final String CATEGORY_BUBBLE = "bubble";
    public static final String CATEGORY_CALL = "call";
    public static final String CATEGORY_JOIN = "join";

    private static final String PREFS = "huanghun_outfit";
    private static final String KEY_PREFIX = "huanghun_outfit_";

    private static final int[][] PALETTES = new int[][]{
            {Color.rgb(0, 245, 255), Color.rgb(126, 40, 255), Color.rgb(255, 50, 180)},
            {Color.rgb(238, 243, 255), Color.rgb(127, 145, 170), Color.rgb(65, 216, 255)},
            {Color.rgb(255, 151, 47), Color.rgb(255, 209, 95), Color.rgb(22, 27, 37)},
            {Color.rgb(255, 47, 77), Color.rgb(45, 0, 19), Color.rgb(255, 202, 77)},
            {Color.rgb(173, 225, 255), Color.rgb(163, 126, 255), Color.rgb(255, 255, 255)},
            {Color.rgb(255, 152, 208), Color.rgb(155, 244, 255), Color.rgb(255, 244, 130)},
            {Color.rgb(102, 255, 123), Color.rgb(255, 89, 149), Color.rgb(26, 26, 31)},
            {Color.rgb(122, 255, 228), Color.rgb(29, 64, 255), Color.rgb(255, 58, 199)},
            {Color.rgb(255, 232, 96), Color.rgb(255, 126, 35), Color.rgb(255, 107, 168)},
            {Color.rgb(40, 222, 196), Color.rgb(57, 112, 255), Color.rgb(245, 219, 148)}
    };

    private static final String[] BUBBLE_NAMES = new String[]{
            "皇家徽章·静态收藏", "紫晶唱盘·律动", "黑金剧场·追光", "金属吊坠·摆动", "星光礼装·碎光",
            "冰晶星空·静态收藏", "雪板山谷·飘雪", "极光守卫·脉冲", "霜蓝路标·星轨", "雪夜信使·航线",
            "黑玫瑰·静态收藏", "铜币遗迹·沙尘", "沙岩拱门·微光", "夜金蔷薇·花瓣", "红宝石书信·呼吸",
            "街机机台·静态收藏", "全息频谱·律动", "像素飞船·巡航", "磁带舞台·光标", "霓虹存档·粒子",
            "海獭抱窗·静态收藏", "纸飞机云朵·滑翔", "植物角标·萤火", "水泡小物·上浮", "漂浮信笺·回环",
            "红色超跑·静态收藏", "极光机甲·核心", "金属道路·扫光", "竞速光带·飞驰", "机库控制台·轨迹",
            "银蓝守卫·静态收藏", "音律机甲·频谱", "冰蓝星舰·巡航", "极光舷窗·星雨", "晶核甲板·脉冲",
            "黑金剧场·静态收藏", "茶具书信·蒸汽", "灯光幕布·追光", "金属票根·闪耀", "夜场珠光·灯序",
            "花园藤蔓·静态收藏", "云端邮差·滑翔", "海獭水岸·水光", "花灯小物·浮动", "叶片相框·落叶",
            "纸飞机·静态收藏", "雪板冰原·飘雪", "冰晶路标·星轨", "云层邮局·航线", "夜航灯塔·光束"
    };

    private static final String[] CALL_NAMES = new String[]{
            "深夜信号", "赛博呼吸", "数据雨", "零界接通", "光栅回铃",
            "钛银来电", "铬光漫游", "流体共振", "金属余温", "镜面回铃",
            "机库接通", "燃料心跳", "超频回铃", "推进准备", "黑钛夜航",
            "血月来电", "夜行铃声", "黑焰呼叫", "暗域接通", "影刃回声",
            "月球来电", "极光低语", "失重回铃", "云层接通", "星尘耳语",
            "糖晶来电", "果冻回铃", "桃汽接通", "青柠心动", "透明宇宙",
            "街头来电", "贴纸回铃", "涂鸦接通", "滑板节拍", "喷漆呼叫",
            "街机来电", "像素回铃", "存档接通", "8-bit 心跳", "霓虹投币",
            "鸭力来电", "毛球回铃", "香蕉接通", "软胶心跳", "云朵呼叫",
            "山海来电", "霓墨回铃", "流光接通", "折扇心跳", "锦纹夜话"
    };

    private static final String[] JOIN_NAMES = new String[]{
            "液态玻璃·轻入场", "紫晶唱盘·登场", "黑金剧场·揭幕", "礼装吊坠·入场", "星光礼装·闪耀",
            "冰晶星空·抵达", "雪板山谷·滑入", "极光守卫·降临", "霜蓝路标·穿行", "雪夜信使·报到",
            "黑玫瑰·启幕", "铜币遗迹·抵达", "沙岩拱门·通行", "夜金蔷薇·绽放", "红宝石书信·送达",
            "街机机台·开场", "全息频谱·上线", "像素飞船·靠港", "磁带舞台·开播", "霓虹存档·载入",
            "海獭水岸·抵达", "纸飞机云朵·送达", "植物角标·入群", "水泡小物·漂入", "漂浮信笺·报到",
            "红色超跑·疾驰", "极光机甲·落地", "金属道路·通行", "竞速光带·掠过", "机库控制台·接入",
            "银蓝守卫·抵达", "音律机甲·登场", "冰蓝星舰·靠泊", "极光舷窗·通行", "晶核甲板·落点",
            "黑金剧场·启幕", "茶具书信·送达", "灯光幕布·开场", "金属票根·验票", "夜场珠光·抵达",
            "花园藤蔓·入群", "云端邮差·送达", "海獭水岸·报到", "花灯小物·飘入", "叶片相框·抵达",
            "纸飞机·抵达", "雪板冰原·滑入", "冰晶路标·通行", "云层邮局·送达", "夜航灯塔·靠岸"
    };

    private HuanghunOutfitConfig() {
    }

    public static final class OutfitItem {
        public final String category;
        public final String id;
        public final String name;
        public final int group;
        public final int variant;
        public final int primary;
        public final int secondary;
        public final int accent;

        private OutfitItem(String category, String id, String name, int group, int variant) {
            this.category = category;
            this.id = id;
            this.name = name;
            this.group = group;
            this.variant = variant;
            int[] colors = PALETTES[group % PALETTES.length];
            this.primary = colors[variant % 3];
            this.secondary = colors[(variant + 1) % 3];
            this.accent = colors[(variant + 2) % 3];
        }
    }

    public static List<OutfitItem> getItems(String category) {
        String[] names = namesFor(category);
        if (names == null) {
            return Collections.emptyList();
        }
        ArrayList<OutfitItem> result = new ArrayList<>(names.length);
        for (int i = 0; i < names.length; i++) {
            result.add(new OutfitItem(category, category + "_" + i, names[i], i / 5, i % 5));
        }
        return result;
    }

    public static OutfitItem find(String category, String id) {
        for (OutfitItem item : getItems(category)) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return null;
    }

    public static String getSelected(Context context, String category) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREFIX + category, "");
    }

    public static void saveSelected(Context context, String category, String id) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PREFIX + category, id == null ? "" : id).apply();
    }

    public static void clearSelected(Context context, String category) {
        saveSelected(context, category, "");
    }

    public static boolean isSelected(Context context, OutfitItem item) {
        return item != null && item.id.equals(getSelected(context, item.category));
    }

    public static String categoryTitle(String category) {
        if (CATEGORY_BUBBLE.equals(category)) return "设置气泡";
        if (CATEGORY_CALL.equals(category)) return "来电主题";
        if (CATEGORY_JOIN.equals(category)) return "进群提示特效";
        return "黄昏装扮";
    }

    public static String categoryDescription(String category) {
        if (CATEGORY_BUBBLE.equals(category)) return "50 套独立场景气泡：仅我方消息显示，40 套完整动态、10 套静态收藏款";
        if (CATEGORY_CALL.equals(category)) return "整屏动态来电视觉与本地铃声试听";
        if (CATEGORY_JOIN.equals(category)) return "50 套真实材质主题主物件的顶部群聊入场提示";
        return "仅在本机显示";
    }

    private static String[] namesFor(String category) {
        if (CATEGORY_BUBBLE.equals(category)) return BUBBLE_NAMES;
        if (CATEGORY_CALL.equals(category)) return CALL_NAMES;
        if (CATEGORY_JOIN.equals(category)) return JOIN_NAMES;
        return null;
    }

    static {
        if (BUBBLE_NAMES.length != 50 || CALL_NAMES.length != 50 || JOIN_NAMES.length != 50) {
            throw new IllegalStateException("黄昏装扮目录必须包含三类各 50 套模板");
        }
    }
}
