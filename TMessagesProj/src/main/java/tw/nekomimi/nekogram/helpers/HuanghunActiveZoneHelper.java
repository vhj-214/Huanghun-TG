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

/**
 * 自动为新消息添加表情 reaction。
 * 方向：0=对方消息，1=自己消息，2=对方和自己。
 * 对象：0=全部用户，1=配置中的指定用户。
 */
public final class HuanghunActiveZoneHelper extends BaseController implements NotificationCenter.NotificationCenterDelegate {
    public static final int DIRECTION_OTHER = 0;
    public static final int DIRECTION_SELF = 1;
    public static final int DIRECTION_BOTH = 2;
    public static final int TARGET_ALL = 0;
    public static final int TARGET_SELECTED = 1;
    public static final int SCOPE_ALL = 0;
    public static final int SCOPE_CHATS = 1;
    public static final int SCOPE_CONTACTS = 2;
    public static final int SCOPE_NON_CONTACTS = 3;
    public static final int SCOPE_BOTS = 4;

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
        if (id != NotificationCenter.didReceiveNewMessages || !NekoConfig.huanghunActiveZoneEnabled.Bool() || args == null || args.length < 2) {
            return;
        }
        if (args.length > 2 && args[2] instanceof Boolean && (Boolean) args[2]) {
            return;
        }
        if (!(args[1] instanceof ArrayList)) {
            return;
        }
        ArrayList<?> objects = (ArrayList<?>) args[1];
        for (Object object : objects) {
            if (object instanceof MessageObject) {
                consider((MessageObject) object);
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
        if (hasChosenEmoji(message, emoji)) {
            return;
        }
        String key = message.getDialogId() + ":" + message.getId();
        synchronized (pendingMessages) {
            if (pendingMessages.containsKey(key)) {
                return;
            }
            pendingMessages.put(key, Boolean.TRUE);
        }
        sendReaction(message, emoji, key);
    }

    private boolean matchesPeerScope(MessageObject message) {
        int scope = NekoConfig.huanghunActiveZonePeerScope.Int();
        if (scope == SCOPE_ALL) return true;
        boolean chat = message.getDialogId() < 0;
        if (scope == SCOPE_CHATS) return chat;
        if (chat) return false;
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(message.getSenderId());
        if (user == null) return false;
        if (scope == SCOPE_BOTS) return user.bot;
        boolean contact = user.contact || user.mutual_contact;
        return scope == SCOPE_CONTACTS ? contact : !contact;
    }

    private boolean matchesTarget(MessageObject message) {
        if (NekoConfig.huanghunActiveZoneTargetMode.Int() != TARGET_SELECTED) {
            return true;
        }
        String raw = NekoConfig.huanghunActiveZoneTargetUsers.String();
        if (TextUtils.isEmpty(raw)) {
            return false;
        }
        long senderId = message.getSenderId();
        for (String value : raw.split(",")) {
            String token = value.trim().toLowerCase(Locale.ROOT);
            if (token.startsWith("@")) {
                token = token.substring(1);
            }
            if (String.valueOf(senderId).equals(token)) {
                return true;
            }
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(senderId);
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

    private void sendReaction(MessageObject message, String emoji, String key) {
        TLRPC.InputPeer peer = MessagesController.getInstance(currentAccount).getInputPeer(message.getDialogId());
        if (peer == null) {
            pendingMessages.remove(key);
            return;
        }
        TLRPC.TL_messages_sendReaction request = new TLRPC.TL_messages_sendReaction();
        request.peer = peer;
        request.msg_id = message.getId();
        request.flags |= 1;
        TLRPC.TL_reactionEmoji reaction = new TLRPC.TL_reactionEmoji();
        reaction.emoticon = emoji;
        request.reaction.add(reaction);
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error == null && response instanceof TLRPC.Updates) {
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
            }
            pendingMessages.remove(key);
        });
    }

    public static String getTargetSummary() {
        if (NekoConfig.huanghunActiveZoneTargetMode.Int() == TARGET_SELECTED) {
            String users = NekoConfig.huanghunActiveZoneTargetUsers.String();
            return TextUtils.isEmpty(users) ? "未选择用户" : "指定用户：" + users;
        }
        switch (NekoConfig.huanghunActiveZonePeerScope.Int()) {
            case SCOPE_CHATS: return "群或频道";
            case SCOPE_CONTACTS: return "联系人";
            case SCOPE_NON_CONTACTS: return "非联系人";
            case SCOPE_BOTS: return "机器人";
            default: return "全部对象（默认）";
        }
    }
}
