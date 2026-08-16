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
    public static final String CATEGORY_AVATAR = "avatar";
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
            "红移协议", "蓝屏回声", "失真频道", "零界载荷", "霓虹崩解",
            "镜面漂流", "钛银边界", "流银反应", "铬光容器", "熔融回声",
            "机库回路", "超频栅格", "推进单元", "黑钛指令", "机械蜂巢",
            "影刃对白", "血月分镜", "黑焰旁白", "荆棘气泡", "夜幕暴走",
            "云海回信", "月球棉花", "失重气泡", "梦核电台", "极光漂流",
            "果冻弹窗", "桃汽泡泡", "紫葡闪光", "青柠透明", "糖晶回信",
            "街头宣言", "涂鸦泡泡", "贴纸爆闪", "滑板对白", "喷漆回响",
            "街机频道", "像素反弹", "霓虹存档", "8-bit 回信", "方块心跳",
            "鸭力气泡", "毛球频道", "香蕉漂流", "软胶怪谈", "云朵发疯",
            "霓墨书信", "流光折扇", "山海弹幕", "青龙回声", "锦纹气泡"
    };

    private static final String[] AVATAR_NAMES = new String[]{
            "信号环", "量子裂片", "数据之眼", "像素偏移", "霓虹脉冲",
            "钛银星环", "流银冠冕", "铬光双轨", "熔融月环", "镜面引力",
            "反应堆", "机甲瞳孔", "推进环", "黑钛护框", "浮动齿轮",
            "影翼边框", "黑焰荆棘", "月蚀冠", "赤瞳利爪", "暗域裂隙",
            "星云泡泡", "月环失重", "极光碎片", "梦境轨道", "云海王冠",
            "果冻天线", "糖晶蝴蝶", "透明音符", "彩胶星轨", "桃汽心跳",
            "喷漆光环", "涂鸦小怪", "贴纸火焰", "滑板星标", "玩具雷达",
            "像素王冠", "街机光圈", "方块羽翼", "存档星环", "霓虹光标",
            "鸭头星环", "毛球触手", "香蕉天线", "软胶独角", "云朵爪印",
            "霓墨龙环", "折扇星轨", "山海流光", "青龙护框", "锦纹月冠"
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
            "数据跃迁", "霓虹破壁", "信号穿梭", "像素降临", "故障入场",
            "液银疾驰", "铬光落地", "金属波纹", "钛银穿云", "镜面降临",
            "机甲空投", "推进尾焰", "工业脉冲", "飞行甲板", "动力入场",
            "暗影突袭", "黑焰踏场", "月蚀降落", "漫画破框", "赤瞳入场",
            "星云漫步", "极光落幕", "月面漂移", "云海跃迁", "星尘进场",
            "果冻滑入", "糖晶弹跳", "桃汽降落", "彩胶飞行", "透明进场",
            "涂鸦冲入", "贴纸弹射", "滑板漂移", "喷漆爆场", "潮玩开路",
            "像素传送", "街机开场", "方块跃迁", "霓虹加载", "存档降临",
            "鸭头狂奔", "毛球弹跳", "香蕉滑行", "软胶降落", "云朵闯入",
            "青龙巡游", "山海踏浪", "折扇开场", "霓墨落幕", "锦纹入群"
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
        if (CATEGORY_AVATAR.equals(category)) return "头像挂件";
        if (CATEGORY_CALL.equals(category)) return "来电铃声";
        if (CATEGORY_JOIN.equals(category)) return "进群特效";
        return "黄昏装扮";
    }

    public static String categoryDescription(String category) {
        if (CATEGORY_BUBBLE.equals(category)) return "动态气泡、渐变描边与高光粒子";
        if (CATEGORY_AVATAR.equals(category)) return "环绕头像的动态光环、纹理与粒子";
        if (CATEGORY_CALL.equals(category)) return "全屏来电视觉与本地铃声试听";
        if (CATEGORY_JOIN.equals(category)) return "人物、载具、粒子与轨迹组成的入场动画";
        return "仅在本机显示";
    }

    private static String[] namesFor(String category) {
        if (CATEGORY_BUBBLE.equals(category)) return BUBBLE_NAMES;
        if (CATEGORY_AVATAR.equals(category)) return AVATAR_NAMES;
        if (CATEGORY_CALL.equals(category)) return CALL_NAMES;
        if (CATEGORY_JOIN.equals(category)) return JOIN_NAMES;
        return null;
    }

    static {
        if (BUBBLE_NAMES.length != 50 || AVATAR_NAMES.length != 50 || CALL_NAMES.length != 50 || JOIN_NAMES.length != 50) {
            throw new IllegalStateException("黄昏装扮目录必须包含四类各 50 套模板");
        }
    }
}
