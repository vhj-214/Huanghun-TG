package tw.nekomimi.nekogram.helpers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Local labels for BotFather's stable command-menu buttons. */
public final class BotFatherTranslation {
    private static final Map<String, String> LABELS;
    private static final Map<String, String> COMMAND_DESCRIPTIONS;

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

        HashMap<String, String> descriptions = new HashMap<>();
        add(descriptions, "create a new bot", "创建新机器人");
        add(descriptions, "edit your bots", "管理你的机器人");
        add(descriptions, "edit your web apps", "管理你的网页应用");
        add(descriptions, "edit your games", "管理你的游戏");
        add(descriptions, "get authorization token", "获取授权令牌");
        add(descriptions, "revoke bot access token", "撤销机器人访问令牌");
        add(descriptions, "delete an existing bot", "删除现有机器人");
        add(descriptions, "change a bot's name", "修改机器人的名称");
        add(descriptions, "change bot name", "修改机器人名称");
        add(descriptions, "change a bot's description", "修改机器人描述");
        add(descriptions, "change bot description", "修改机器人描述");
        add(descriptions, "change bot about text", "修改机器人简介");
        add(descriptions, "change bot profile photo", "修改机器人头像");
        add(descriptions, "change bot userpic", "修改机器人头像");
        add(descriptions, "change the list of commands", "修改机器人命令列表");
        add(descriptions, "delete commands", "删除机器人命令");
        add(descriptions, "toggle group privacy", "切换群组隐私模式");
        add(descriptions, "can your bot be added to groups?", "设置机器人是否允许加入群组");
        add(descriptions, "toggle inline mode", "切换内联模式");
        add(descriptions, "toggle inline location requests", "切换内联位置请求");
        add(descriptions, "change inline feedback settings", "修改内联反馈设置");
        add(descriptions, "set inline placeholder", "设置内联模式占位提示");
        add(descriptions, "configure the menu button", "配置菜单按钮");
        add(descriptions, "configure domain", "配置域名");
        add(descriptions, "transfer ownership", "转移机器人所有权");
        add(descriptions, "revoke a token", "撤销访问令牌");
        COMMAND_DESCRIPTIONS = Collections.unmodifiableMap(descriptions);
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

    public static String translateCommandDescription(String text) {
        if (text == null) {
            return null;
        }
        return COMMAND_DESCRIPTIONS.get(text.trim().toLowerCase(Locale.ROOT));
    }
}
