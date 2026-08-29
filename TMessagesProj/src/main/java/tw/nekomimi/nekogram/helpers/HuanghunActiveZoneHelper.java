package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.telegram.messenger.BaseController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.helper.LocalMessageReactionHelper;

/**
 * 自动为新消息添加仅本机可见的表情点赞。
 * 方向：0=对方消息，1=自己消息，2=对方和自己。
 * 对象范围：0=全部对象，1=群或频道，2=所有用户（联系人、非联系人和机器人）。
 */
public final class HuanghunActiveZoneHelper extends BaseController implements NotificationCenter.NotificationCenterDelegate {
    public static final int DIRECTION_OTHER = 0;
    public static final int DIRECTION_SELF = 1;
    public static final int DIRECTION_BOTH = 2;
    public static final int TARGET_ALL = 0;
    public static final int TARGET_SELECTED = 1;
    public static final int SCOPE_ALL = 0;
    public static final int SCOPE_CHATS = 1;
    public static final int SCOPE_USERS = 2;

    private static final int MAX_PENDING_MESSAGES = 512;
    private final Map<String, Boolean> pendingMessages = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(MAX_PENDING_MESSAGES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_PENDING_MESSAGES;
                }
            });

    private HuanghunActiveZoneHelper(int account) {
        super(account);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.didReceiveNewMessages);
        // 已发送消息不会进入 didReceiveNewMessages；确认送达后从这里处理“自己消息”方向。
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messageReceivedByServer);
    }

    private static final HuanghunActiveZoneHelper[] INSTANCES = new HuanghunActiveZoneHelper[UserConfig.MAX_ACCOUNT_COUNT];

    public static HuanghunActiveZoneHelper getInstance(int account) {
        HuanghunActiveZoneHelper instance = INSTANCES[account];
        if (instance == null) {
            synchronized (INSTANCES) {
                instance = INSTANCES[account];
                if (instance == null) {
                    instance = INSTANCES[account] = new HuanghunActiveZoneHelper(account);
                }
            }
        }
        return instance;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (!NekoConfig.huanghunActiveZoneEnabled.Bool() || args == null) {
            return;
        }
        if (id == NotificationCenter.didReceiveNewMessages) {
            if (args.length < 2 || args.length > 2 && args[2] instanceof Boolean && (Boolean) args[2] || !(args[1] instanceof ArrayList)) {
                return;
            }
            ArrayList<?> objects = (ArrayList<?>) args[1];
            for (Object object : objects) {
                if (object instanceof MessageObject) {
                    consider((MessageObject) object);
                }
            }
        } else if (id == NotificationCenter.messageReceivedByServer && args.length > 2 && args[2] instanceof TLRPC.Message) {
            TLRPC.Message sentMessage = (TLRPC.Message) args[2];
            if (sentMessage.out) {
                consider(new MessageObject(currentAccount, sentMessage, false, false));
            }
        }
    }

    private void consider(MessageObject message) {
        if (message == null || message.messageOwner == null || message.getId() <= 0 || message.isOutOwner() && message.messageOwner.post) {
            return;
        }
        if (message.messageOwner.action != null || message.messageOwner.reactions == null && message.messageOwner.media == null && TextUtils.isEmpty(message.messageOwner.message)) {
            return;
        }
        boolean self = message.isOutOwner();
        int direction = NekoConfig.huanghunActiveZoneDirection.Int();
        if (direction == DIRECTION_OTHER && self || direction == DIRECTION_SELF && !self) {
            return;
        }
        if (!matchesTarget(message) || !matchesPeerScope(message)) {
            return;
        }
        String emoji = NekoConfig.huanghunActiveZoneEmoji.String();
        if (TextUtils.isEmpty(emoji)) {
            emoji = "👍";
        }
        String key = message.getDialogId() + ":" + message.getId();
        synchronized (pendingMessages) {
            if (pendingMessages.containsKey(key)) {
                return;
            }
            pendingMessages.put(key, Boolean.TRUE);
        }
        applyLocalReaction(message, emoji, key);
    }

    private boolean matchesPeerScope(MessageObject message) {
        int scope = NekoConfig.huanghunActiveZonePeerScope.Int();
        if (scope == SCOPE_ALL) return true;
        boolean chat = message.getDialogId() < 0;
        if (scope == SCOPE_CHATS) return chat;
        // SCOPE_USERS 同时包含联系人、非联系人和机器人；旧的 3/4 值也兼容为用户范围。
        return !chat;
    }

    private boolean matchesTarget(MessageObject message) {
        if (NekoConfig.huanghunActiveZoneTargetMode.Int() != TARGET_SELECTED) {
            return true;
        }
        String raw = NekoConfig.huanghunActiveZoneTargetUsers.String();
        if (TextUtils.isEmpty(raw)) {
            return false;
        }
        // 私聊中，无论消息由谁发送，指定对象都是对话另一方；群/频道中则匹配实际发送者。
        long targetUserId = message.getDialogId() > 0 ? message.getDialogId() : message.getSenderId();
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(targetUserId);
        for (String value : raw.split("[,\\n\\r;]+")) {
            String token = value.trim().toLowerCase(Locale.ROOT);
            if (token.startsWith("@")) {
                token = token.substring(1);
            }
            if (String.valueOf(targetUserId).equals(token)) {
                return true;
            }
            if (user != null && !TextUtils.isEmpty(user.username) && user.username.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasChosenEmoji(MessageObject message, String emoji) {
        if (message.messageOwner.reactions == null || message.messageOwner.reactions.results == null) {
            return false;
        }
        for (TLRPC.ReactionCount count : message.messageOwner.reactions.results) {
            if (count != null && count.chosen && count.reaction instanceof TLRPC.TL_reactionEmoji
                    && emoji.equals(((TLRPC.TL_reactionEmoji) count.reaction).emoticon)) {
                return true;
            }
        }
        return false;
    }

    private void applyLocalReaction(MessageObject message, String reaction, String key) {
        try {
            LocalMessageReactionHelper.set(currentAccount, message.getDialogId(), message.getId(), reaction);
            LocalMessageReactionHelper.apply(message.messageOwner, currentAccount);
            // 复用官方反应刷新通知，让当前聊天立即重绘；不会写入数据库或发送网络请求。
            getNotificationCenter().postNotificationName(NotificationCenter.didUpdateReactions,
                    message.getDialogId(), message.getId(), message.messageOwner.reactions);
        } finally {
            pendingMessages.remove(key);
        }
    }

    public static String getTargetSummary() {
        if (NekoConfig.huanghunActiveZoneTargetMode.Int() == TARGET_SELECTED) {
            String users = NekoConfig.huanghunActiveZoneTargetUsers.String();
            return TextUtils.isEmpty(users) ? "未选择用户" : "指定用户：" + users;
        }
        switch (NekoConfig.huanghunActiveZonePeerScope.Int()) {
            case SCOPE_CHATS: return "群或频道";
            case SCOPE_USERS:
            default: return NekoConfig.huanghunActiveZonePeerScope.Int() == SCOPE_ALL
                    ? "全部对象（默认）"
                    : "用户（联系人、非联系人、机器人）";
        }
    }
}
