package tw.nekomimi.nekogram.helpers;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Local, opt-in message-response automation. It only reacts to an exact outgoing DM trigger. */
public final class HuanghunComebackHelper extends BaseController implements NotificationCenter.NotificationCenterDelegate {
    private static final String PREFIX = "huanghun_comeback_";
    private static final String DEFAULT_TRIGGER = "cnm";
    private static final String FALLBACK_DEFAULT_CORPUS = "先别急，我们把问题说清楚。\n收到，我先确认一下具体情况。";
    private static volatile String defaultCorpusCache;
    private static final int MAX_LINES_PER_TRIGGER = 500;
    private static final Random RANDOM = new Random();
    private final ArrayDeque<QueuedMessage> queue = new ArrayDeque<>();
    private final Set<String> seenTriggers = new LinkedHashSet<>();
    private boolean sending;

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
    public void setEnabled(boolean enabled) { prefs().edit().putBoolean(key("enabled"), enabled).apply(); }
    public String getTrigger() { return prefs().getString(key("trigger"), DEFAULT_TRIGGER); }
    public boolean setTrigger(String trigger) {
        if (TextUtils.isEmpty(trigger) || trigger.trim().isEmpty() || trigger.length() > 64) return false;
        prefs().edit().putString(key("trigger"), trigger.trim()).apply();
        return true;
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
        if (id != NotificationCenter.messageReceivedByServer || !isEnabled() || args == null || args.length < 3 || !(args[2] instanceof TLRPC.Message)) return;
        TLRPC.Message message = (TLRPC.Message) args[2];
        if (!message.out || message.post || TextUtils.isEmpty(message.message)) return;
        long dialogId = MessageObject.getDialogId(message);
        long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        // Positive dialog IDs are private user conversations; groups/channels are excluded.
        if (dialogId <= 0 || dialogId == selfId || !message.message.trim().equals(getTrigger())) return;
        String dedupe = dialogId + ":" + message.id;
        synchronized (seenTriggers) {
            if (!seenTriggers.add(dedupe)) return;
            while (seenTriggers.size() > 256) seenTriggers.remove(seenTriggers.iterator().next());
        }
        ArrayList<String> lines = new ArrayList<>();
        if (isDefaultEnabled()) addLines(lines, getDefaultCorpus());
        for (Corpus corpus : getCorpora()) if (corpus.enabled) addLines(lines, corpus.text);
        if (lines.isEmpty()) return;
        synchronized (queue) {
            if (sending) return;
            for (String line : lines) queue.add(new QueuedMessage(dialogId, line));
            sending = true;
        }
        sendNext();
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

    private void sendNext() {
        if (!isEnabled()) {
            synchronized (queue) { queue.clear(); sending = false; }
            return;
        }
        final QueuedMessage next;
        synchronized (queue) {
            next = queue.poll();
            if (next == null) { sending = false; return; }
        }
        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(next.text, next.dialogId));
        // Keep a noticeable but responsive pause between replies: 0.32–1.70 seconds.
        long delay = 320L + RANDOM.nextInt(1381);
        AndroidUtilities.runOnUIThread(this::sendNext, delay);
    }

    private static final class QueuedMessage {
        final long dialogId;
        final String text;
        QueuedMessage(long dialogId, String text) { this.dialogId = dialogId; this.text = text; }
    }
}
