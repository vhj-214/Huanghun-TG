package tw.nekomimi.nekogram.helpers;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.util.HashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Local, opt-in message-response automation with an optional exact-trigger group/channel mode. */
public final class HuanghunComebackHelper extends BaseController implements NotificationCenter.NotificationCenterDelegate {
    private static final String PREFIX = "huanghun_comeback_";
    private static final String AUTOMATED_MESSAGE_PARAM = "huanghun_comeback_auto";
    private static final String DEFAULT_TRIGGER = "cnm";
    private static final String DEFAULT_STOP_TRIGGER = "sb";
    /** Maximum number of messages dispatched in one send round. */
    public static final int MAX_SEND_SPEED = 10;
    public static final int SEND_MODE_SEQUENTIAL = 0;
    public static final int SEND_MODE_RANDOM = 1;
    /**
     * Chats compiled into the build. They are never exposed in the settings UI and are
     * protected: a trigger fired inside one of them is refused instead of bombed.
     */
    private static final String[] PROTECTED_BOMB_CHAT_USERNAMES = {"hqsh_dbtz", "hqsh_db"};
    private static final String PROTECTED_BOMB_CHAT_TITLE = "群聊保护";
    private static final String PROTECTED_BOMB_CHAT_NOTICE = "该群聊已启用保护，当前账号无法在此触发群聊轰炸。";
    private static final String FALLBACK_DEFAULT_CORPUS = "先别急，我们把问题说清楚。\n收到，我先确认一下具体情况。";
    private static volatile String defaultCorpusCache;
    private static final int MAX_LINES_PER_TRIGGER = 500;
    private static final Random RANDOM = new Random();
    private final ArrayDeque<QueuedMessage> queue = new ArrayDeque<>();
    private final Set<String> seenTriggers = new LinkedHashSet<>();
    private boolean sending;
    /** True while the running queue was started from a group/channel trigger. */
    private boolean activeGroupBomb;
    private long activeRandomDialogId;
    private int lastRandomCorpusIndex = -1;
    private int queueGeneration;

    public static final class Corpus {
        public final String id;
        public final String name;
        public final String text;
        public final boolean enabled;
        Corpus(String id, String name, String text, boolean enabled) {
            this.id = id;
            this.name = name;
            this.text = text;
            this.enabled = enabled;
        }
    }

    private static final HuanghunComebackHelper[] INSTANCES = new HuanghunComebackHelper[UserConfig.MAX_ACCOUNT_COUNT];

    private HuanghunComebackHelper(int account) {
        super(account);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messageWillSend);
    }

    public static HuanghunComebackHelper getInstance(int account) {
        synchronized (INSTANCES) {
            if (INSTANCES[account] == null) INSTANCES[account] = new HuanghunComebackHelper(account);
            return INSTANCES[account];
        }
    }

    private String key(String name) { return PREFIX + currentAccount + "_" + name; }
    private SharedPreferences prefs() { return MessagesController.getGlobalMainSettings(); }

    public boolean isEnabled() { return prefs().getBoolean(key("enabled"), false); }
    public void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(key("enabled"), enabled).apply();
        if (!enabled) stopPendingMessages();
    }
    public String getTrigger() { return prefs().getString(key("trigger"), DEFAULT_TRIGGER); }
    public boolean setTrigger(String trigger) {
        if (TextUtils.isEmpty(trigger) || trigger.trim().isEmpty() || trigger.trim().length() > 64
                || trigger.trim().equals(getStopTrigger())) return false;
        prefs().edit().putString(key("trigger"), trigger.trim()).apply();
        return true;
    }
    public String getStopTrigger() { return prefs().getString(key("stop_trigger"), DEFAULT_STOP_TRIGGER); }
    public boolean setStopTrigger(String trigger) {
        if (TextUtils.isEmpty(trigger) || trigger.trim().isEmpty() || trigger.trim().length() > 64
                || trigger.trim().equals(getTrigger())) return false;
        prefs().edit().putString(key("stop_trigger"), trigger.trim()).apply();
        return true;
    }
    public int getSendMode() {
        int mode = prefs().getInt(key("send_mode"), SEND_MODE_SEQUENTIAL);
        return mode == SEND_MODE_RANDOM ? SEND_MODE_RANDOM : SEND_MODE_SEQUENTIAL;
    }
    public void setSendMode(int mode) {
        prefs().edit().putInt(key("send_mode"), mode == SEND_MODE_RANDOM ? SEND_MODE_RANDOM : SEND_MODE_SEQUENTIAL).apply();
    }
    public int getSendSpeed() {
        return Math.max(1, Math.min(MAX_SEND_SPEED, prefs().getInt(key("send_speed"), 1)));
    }
    public void setSendSpeed(int speed) {
        prefs().edit().putInt(key("send_speed"), Math.max(1, Math.min(MAX_SEND_SPEED, speed))).apply();
    }
    /** Opt-in group/channel bombing. Off by default; only groups/channels are affected. */
    public boolean isGroupBombEnabled() { return prefs().getBoolean(key("group_bomb"), false); }
    public void setGroupBombEnabled(boolean enabled) {
        prefs().edit().putBoolean(key("group_bomb"), enabled).apply();
        if (!enabled) {
            synchronized (queue) {
                // Only the group queue is cancelled; a running private-chat session keeps going.
                if (activeGroupBomb) stopPendingMessages();
            }
        }
    }
    private void stopPendingMessages() {
        synchronized (queue) {
            queueGeneration++;
            queue.clear();
            sending = false;
            activeGroupBomb = false;
            activeRandomDialogId = 0;
            lastRandomCorpusIndex = -1;
        }
    }
    public boolean isDefaultEnabled() { return prefs().getBoolean(key("default_enabled"), true); }
    public void setDefaultEnabled(boolean enabled) { prefs().edit().putBoolean(key("default_enabled"), enabled).apply(); }

    public List<Corpus> getCorpora() {
        ArrayList<Corpus> result = new ArrayList<>();
        JSONArray array;
        try { array = new JSONArray(prefs().getString(key("corpora"), "[]")); }
        catch (Exception e) { array = new JSONArray(); }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            String name = item.optString("name", "词库" + (i + 1));
            String text = item.optString("text", "");
            if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(text)) {
                result.add(new Corpus(id, name, text, item.optBoolean("enabled", true)));
            }
        }
        return result;
    }

    public void addCorpus(String name, String text) {
        JSONArray array = readCorpora();
        JSONObject item = new JSONObject();
        String id = java.util.UUID.randomUUID().toString();
        try {
            item.put("id", id);
            item.put("name", name);
            item.put("text", text);
            item.put("enabled", true);
            array.put(item);
            prefs().edit().putString(key("corpora"), array.toString()).apply();
        } catch (Exception ignored) { }
    }

    public void setCorpusEnabled(String id, boolean enabled) {
        JSONArray array = readCorpora();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) {
                try { item.put("enabled", enabled); } catch (Exception ignored) { }
            }
        }
        prefs().edit().putString(key("corpora"), array.toString()).apply();
    }

    public void deleteCorpus(String id) {
        JSONArray old = readCorpora();
        JSONArray updated = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !id.equals(item.optString("id"))) updated.put(item);
        }
        prefs().edit().putString(key("corpora"), updated.toString()).apply();
    }

    public void setAllCorporaEnabled(boolean enabled) {
        JSONArray array = readCorpora();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                try { item.put("enabled", enabled); } catch (Exception ignored) { }
            }
        }
        prefs().edit().putBoolean(key("default_enabled"), enabled).putString(key("corpora"), array.toString()).apply();
    }

    public void deleteAllCorpora() { prefs().edit().putString(key("corpora"), "[]").apply(); }

    private JSONArray readCorpora() {
        try { return new JSONArray(prefs().getString(key("corpora"), "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (!isEnabled() || args == null) return;
        if (id == NotificationCenter.messageWillSend) {
            if (args.length < 1 || !(args[0] instanceof SendMessagesHelper.SendMessageParams)) return;
            SendMessagesHelper.SendMessageParams params = (SendMessagesHelper.SendMessageParams) args[0];
            if (params.params != null && "1".equals(params.params.get(AUTOMATED_MESSAGE_PARAM))) return;
            long dialogId = params.peer;
            long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            if (params.scheduleDate == 0 && dialogId != selfId && (dialogId > 0 || isGroupBombEnabled())
                    && !TextUtils.isEmpty(params.message) && params.message.trim().equals(getStopTrigger())) {
                stopPendingMessages();
            }
            return;
        }
        if (id != NotificationCenter.messageReceivedByServer || args.length < 3 || !(args[2] instanceof TLRPC.Message)) return;
        TLRPC.Message message = (TLRPC.Message) args[2];
        if (!message.out || TextUtils.isEmpty(message.message)) return;
        if (message.params != null && "1".equals(message.params.get(AUTOMATED_MESSAGE_PARAM))) return;
        long dialogId = MessageObject.getDialogId(message);
        long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (dialogId == selfId) return;
        final boolean groupTarget = dialogId < 0;
        if (groupTarget) {
            // Groups and channels are only served while the opt-in group bomb switch is on.
            if (!isGroupBombEnabled()) return;
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat == null) return;
            if (isProtectedBombChat(chat)) {
                // Built-in protected chats are refused with a notice instead of being bombed.
                showProtectedChatNotice();
                return;
            }
        } else if (message.post) {
            return;
        }
        String sentText = message.message.trim();
        if (sentText.equals(getStopTrigger())) {
            stopPendingMessages();
            return;
        }
        if (!sentText.equals(getTrigger())) return;
        String dedupe = dialogId + ":" + message.id;
        synchronized (seenTriggers) {
            if (!seenTriggers.add(dedupe)) return;
            while (seenTriggers.size() > 256) seenTriggers.remove(seenTriggers.iterator().next());
        }
        final boolean randomMode = getSendMode() == SEND_MODE_RANDOM;
        ArrayList<String> lines = new ArrayList<>();
        if (randomMode) {
            if (getEnabledCorpusLines().isEmpty()) return;
        } else {
            if (isDefaultEnabled()) addLines(lines, getDefaultCorpus());
            for (Corpus corpus : getCorpora()) if (corpus.enabled) addLines(lines, corpus.text);
        }
        if (!randomMode && lines.isEmpty()) return;
        final int generation;
        synchronized (queue) {
            if (sending) return;
            generation = ++queueGeneration;
            activeRandomDialogId = randomMode ? dialogId : 0;
            lastRandomCorpusIndex = -1;
            activeGroupBomb = groupTarget;
            for (String line : lines) queue.add(new QueuedMessage(dialogId, line));
            sending = true;
        }
        sendNext(generation);
    }

    /** True when the chat is one of the built-in protected targets that are never bombed. */
    private boolean isProtectedBombChat(TLRPC.Chat chat) {
        if (chat == null || PROTECTED_BOMB_CHAT_USERNAMES.length == 0) return false;
        if (matchesProtectedUsername(chat.username)) return true;
        if (chat.usernames != null) {
            for (TLRPC.TL_username item : chat.usernames) {
                if (item != null && matchesProtectedUsername(item.username)) return true;
            }
        }
        return false;
    }

    private static boolean matchesProtectedUsername(String username) {
        if (TextUtils.isEmpty(username)) return false;
        String normalized = username.trim();
        if (normalized.startsWith("@")) normalized = normalized.substring(1);
        for (String protectedName : PROTECTED_BOMB_CHAT_USERNAMES) {
            if (protectedName.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    /** Announces that the current chat is protected and refuses to bomb it. */
    private void showProtectedChatNotice() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                LaunchActivity activity = LaunchActivity.instance;
                if (activity != null && !activity.isFinishing()) {
                    new AlertDialog.Builder(activity)
                            .setTitle(PROTECTED_BOMB_CHAT_TITLE)
                            .setMessage(PROTECTED_BOMB_CHAT_NOTICE)
                            .setPositiveButton("我知道了", null)
                            .show();
                    return;
                }
            } catch (Throwable error) {
                FileLog.e(error);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(
                    NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, PROTECTED_BOMB_CHAT_NOTICE);
        });
    }

    private ArrayList<List<String>> getEnabledCorpusLines() {
        ArrayList<List<String>> enabledCorpora = new ArrayList<>();
        if (isDefaultEnabled()) addCorpusLines(enabledCorpora, getDefaultCorpus());
        for (Corpus corpus : getCorpora()) {
            if (corpus.enabled) addCorpusLines(enabledCorpora, corpus.text);
        }
        return enabledCorpora;
    }

    private void addCorpusLines(List<List<String>> into, String text) {
        ArrayList<String> lines = new ArrayList<>();
        addLines(lines, text);
        if (!lines.isEmpty()) into.add(lines);
    }

    private String getRandomEnabledLine() {
        ArrayList<List<String>> enabledCorpora = getEnabledCorpusLines();
        if (enabledCorpora.isEmpty()) return null;
        int corpusIndex;
        if (enabledCorpora.size() == 1 || lastRandomCorpusIndex < 0 || lastRandomCorpusIndex >= enabledCorpora.size()) {
            corpusIndex = RANDOM.nextInt(enabledCorpora.size());
        } else {
            corpusIndex = RANDOM.nextInt(enabledCorpora.size() - 1);
            if (corpusIndex >= lastRandomCorpusIndex) corpusIndex++;
        }
        lastRandomCorpusIndex = corpusIndex;
        List<String> selectedCorpus = enabledCorpora.get(corpusIndex);
        return selectedCorpus.get(RANDOM.nextInt(selectedCorpus.size()));
    }

    private void addLines(List<String> into, String text) {
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (!value.isEmpty() && into.size() < MAX_LINES_PER_TRIGGER) into.add(value);
        }
    }

    private static String getDefaultCorpus() {
        String cached = defaultCorpusCache;
        if (cached != null) return cached;
        synchronized (HuanghunComebackHelper.class) {
            if (defaultCorpusCache != null) return defaultCorpusCache;
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    ApplicationLoader.applicationContext.getAssets().open("词库.txt"), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line).append('\n');
            } catch (Throwable ignored) {
                // Keep a safe fallback if the packaged test corpus is unavailable.
            }
            defaultCorpusCache = text.length() == 0 ? FALLBACK_DEFAULT_CORPUS : text.toString();
            return defaultCorpusCache;
        }
    }

    private void sendNext(final int generation) {
        if (!isEnabled()) {
            stopPendingMessages();
            return;
        }
        final int speed = getSendSpeed();
        final ArrayList<QueuedMessage> batch = new ArrayList<>(speed);
        synchronized (queue) {
            if (generation != queueGeneration) return;
            if (queue.isEmpty() && getSendMode() == SEND_MODE_RANDOM && activeRandomDialogId != 0) {
                // Refill each random send round from the currently enabled corpora.
                // Random mode is a loop; it ends only when stopped or no usable corpus remains.
                for (int i = 0; i < speed; i++) {
                    String text = getRandomEnabledLine();
                    if (TextUtils.isEmpty(text)) break;
                    queue.add(new QueuedMessage(activeRandomDialogId, text));
                }
            }
            for (int i = 0; i < speed; i++) {
                QueuedMessage next = queue.poll();
                if (next == null) break;
                batch.add(next);
            }
            if (batch.isEmpty()) {
                sending = false;
                activeRandomDialogId = 0;
                return;
            }
        }
        dispatchBatch(batch, speed, 0, generation);
    }
    private void dispatchBatch(final ArrayList<QueuedMessage> batch, final int speed, final int index, final int generation) {
        if (!isEnabled()) {
            stopPendingMessages();
            return;
        }
        synchronized (queue) {
            if (generation != queueGeneration) return;
        }
        if (index >= batch.size()) {
            scheduleNextRound(generation);
            return;
        }
        QueuedMessage message = batch.get(index);
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(message.text, message.dialogId);
        params.params = new HashMap<>();
        params.params.put(AUTOMATED_MESSAGE_PARAM, "1");
        SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
        if (index + 1 < batch.size()) {
            long stagger = Math.max(30L, Math.min(140L, 320L / Math.max(1, speed)));
            AndroidUtilities.runOnUIThread(() -> dispatchBatch(batch, speed, index + 1, generation), stagger);
        } else {
            scheduleNextRound(generation);
        }
    }
    private void scheduleNextRound(final int generation) {
        if (!isEnabled()) {
            stopPendingMessages();
            return;
        }
        // Keep the existing 0.32–1.70 second interval between send rounds.
        long delay = 320L + RANDOM.nextInt(1381);
        AndroidUtilities.runOnUIThread(() -> sendNext(generation), delay);
    }

    private static final class QueuedMessage {
        final long dialogId;
        final String text;
        QueuedMessage(long dialogId, String text) { this.dialogId = dialogId; this.text = text; }
    }
}
