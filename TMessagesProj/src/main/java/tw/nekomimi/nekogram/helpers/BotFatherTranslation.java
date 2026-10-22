package tw.nekomimi.nekogram.helpers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Local labels for BotFather's stable command-menu buttons. */
public final class BotFatherTranslation {
    private static final Map<String, String> LABELS;

    static {
        HashMap<String, String> labels = new HashMap<>();
        add(labels, "New Bot", "新建机器人");
        add(labels, "Create New Bot", "创建新机器人");
        add(labels, "My Bots", "我的机器人");
        add(labels, "Bot Settings", "机器人设置");
        add(labels, "Edit Bot", "编辑机器人");
        add(labels, "Edit Name", "编辑名称");
        add(labels, "Edit Description", "编辑描述");
        add(labels, "Edit About", "编辑简介");
        add(labels, "Edit Botpic", "编辑机器人头像");
        add(labels, "Edit Commands", "编辑命令");
        add(labels, "Set Name", "设置名称");
        add(labels, "Set Description", "设置描述");
        add(labels, "Set About Text", "设置简介");
        add(labels, "Set Userpic", "设置头像");
        add(labels, "Set Commands", "设置命令");
        add(labels, "Set Domain", "设置域名");
        add(labels, "Set Inline Placeholder", "设置内联提示");
        add(labels, "Inline Mode", "内联模式");
        add(labels, "Group Privacy", "群组隐私");
        add(labels, "Allow Groups", "允许加入群组");
        add(labels, "Payments", "付款设置");
        add(labels, "Games", "游戏设置");
        add(labels, "Mini Apps", "小程序");
        add(labels, "Web Apps", "网页应用");
        add(labels, "Transfer Ownership", "转移所有权");
        add(labels, "Delete Bot", "删除机器人");
        add(labels, "Configure", "配置");
        add(labels, "Enable", "启用");
        add(labels, "Disable", "禁用");
        add(labels, "Cancel", "取消");
        add(labels, "Back", "返回");
        add(labels, "Done", "完成");
        add(labels, "Yes", "是");
        add(labels, "No", "否");
        LABELS = Collections.unmodifiableMap(labels);
    }

    private BotFatherTranslation() {
    }

    private static void add(Map<String, String> labels, String source, String target) {
        labels.put(source.toLowerCase(Locale.ROOT), target);
    }

    public static String translate(String text) {
        if (text == null) {
            return null;
        }
        return LABELS.get(text.trim().toLowerCase(Locale.ROOT));
    }
}
