package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local, account-scoped recurring message tasks used by Huanghun's sign-in page. */
public final class HuanghunSignInHelper {
    private static final String PREFS = "huanghun_sign_in";
    private static final String KEY_ENABLED = "enabled_";
    private static final String KEY_TASKS = "tasks_";
    private static final TimeZone BEIJING = TimeZone.getTimeZone("Asia/Shanghai");
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm", Locale.US);
    private static final Pattern INPUT_TIME = Pattern.compile("^(\\d{1,2}):([0-5]\\d)$");
    private static final HashMap<Integer, StatusObserver> STATUS_OBSERVERS = new HashMap<>();
    static { TIME.setTimeZone(BEIJING); }

    private HuanghunSignInHelper() {}

    public static final class Task {
        public long id;
        public long dialogId;
        public String target = "";
        public String content = "";
        public String time = "00:01";
        public String status = "未执行";
        public long lastRunDay;
        public long retryDay;
        public int pendingMessageId;
    }

    private static android.content.SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(int account) { return prefs().getBoolean(KEY_ENABLED + account, false); }
    public static void setEnabled(int account, boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED + account, enabled).apply();
        HuanghunSignInScheduler.schedule(account);
    }

    public static ArrayList<Task> getTasks(int account) {
        ensureStatusObserver(account);
        ArrayList<Task> result = new ArrayList<>();
        String raw = prefs().getString(KEY_TASKS + account, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Task task = new Task();
                task.id = object.optLong("id", System.nanoTime());
                task.dialogId = object.optLong("dialogId", 0L);
                task.target = object.optString("target", "");
                task.content = object.optString("content", "");
                task.time = normalizeTime(object.optString("time", "00:01"));
                task.status = object.optString("status", "未执行");
                task.lastRunDay = object.optLong("lastRunDay", 0L);
                task.retryDay = object.optLong("retryDay", 0L);
                task.pendingMessageId = object.optInt("pendingMessageId", 0);
                if (task.dialogId != 0 && !TextUtils.isEmpty(task.content)) result.add(task);
            }
        } catch (Throwable e) { FileLog.e(e); }
        return result;
    }

    public static void saveTasks(int account, ArrayList<Task> tasks) {
        JSONArray array = new JSONArray();
        try {
            for (Task task : tasks) {
                JSONObject object = new JSONObject();
                object.put("id", task.id); object.put("dialogId", task.dialogId);
                object.put("target", task.target); object.put("content", task.content);
                object.put("time", normalizeTime(task.time)); object.put("status", task.status);
                object.put("lastRunDay", task.lastRunDay); object.put("retryDay", task.retryDay); object.put("pendingMessageId", task.pendingMessageId); array.put(object);
            }
        } catch (Throwable e) { FileLog.e(e); }
        prefs().edit().putString(KEY_TASKS + account, array.toString()).apply();
        HuanghunSignInScheduler.schedule(account);
    }

    public static String normalizeTime(String value) {
        if (value == null) return "";
        String candidate = value.trim();
        Matcher matcher = INPUT_TIME.matcher(candidate);
        if (!matcher.matches()) return candidate;
        try {
            int hour = Integer.parseInt(matcher.group(1));
            if (hour > 23) return candidate;
            return String.format(Locale.US, "%02d:%s", hour, matcher.group(2));
        } catch (Throwable ignore) {
            return candidate;
        }
    }

    public static long todayKey(Calendar calendar) {
        return calendar.get(Calendar.YEAR) * 10000L + (calendar.get(Calendar.MONTH) + 1) * 100L + calendar.get(Calendar.DAY_OF_MONTH);
    }

    public static long nextTriggerMillis(String time, long now) {
        Calendar next = Calendar.getInstance(BEIJING);
        next.setTimeInMillis(now);
        String[] parts = normalizeTime(time).split(":");
        if (parts.length != 2) return now + 60_000L;
        try { next.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0])); next.set(Calendar.MINUTE, Integer.parseInt(parts[1])); next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0); }
        catch (Throwable ignore) { return now + 60_000L; }
        if (next.getTimeInMillis() <= now) next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis();
    }

    public static synchronized boolean resendTaskNow(int account, long taskId) {
        ArrayList<Task> tasks = getTasks(account);
        Task task = null;
        for (Task candidate : tasks) {
            if (candidate.id == taskId) {
                task = candidate;
                break;
            }
        }
        if (task == null) return false;
        SendMessagesHelper helper = AccountInstance.getInstance(account).getSendMessagesHelper();
        if (task.pendingMessageId != 0) {
            int queuedMessageId = helper.getSendingMessageId(task.dialogId);
            if (queuedMessageId != 0 && queuedMessageId != task.pendingMessageId) {
                task.pendingMessageId = queuedMessageId;
                task.status = "重新发送中";
                saveTasksForResend(account, tasks);
                return false;
            } else if (task.pendingMessageId == -1 && queuedMessageId == 0) {
                // -1 is the pre-send lock. If the process died before a queue
                // entry appeared, release it so the next tap can retry safely.
                task.pendingMessageId = 0;
                task.status = "等待执行";
                saveTasksForResend(account, tasks);
            } else {
                return false;
            }
        }
        // Persist the lock before entering SendMessagesHelper. The send call can
        // return before its temporary ID is visible, so using only that ID made
        // the button appear idle and allowed duplicate taps.
        task.pendingMessageId = -1;
        task.status = "重新发送中";
        saveTasksForResend(account, tasks);
        try {
            int previousMessageId = helper.getSendingMessageId(task.dialogId);
            helper.sendMessage(SendMessagesHelper.SendMessageParams.of(task.content, task.dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false));
            int messageId = helper.getSendingMessageId(task.dialogId);
            task.pendingMessageId = messageId != 0 && messageId != previousMessageId ? messageId : 0;
            task.status = task.pendingMessageId == 0 ? "重新发送请求已提交，等待确认" : "重新发送中";
        } catch (Throwable e) {
            task.pendingMessageId = 0;
            task.status = "失败【目标永远存在禁言无法发送】";
            FileLog.e(e);
        }
        saveTasksForResend(account, tasks);
        return true;
    }

    public static synchronized void executeDueTasks(int account) {
        if (!isEnabled(account) || !UserConfig.getInstance(account).isClientActivated()) return;
        ensureStatusObserver(account);
        ArrayList<Task> tasks = getTasks(account);
        Calendar now = Calendar.getInstance(BEIJING);
        int minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        long day = todayKey(now);
        boolean changed = false;
        for (Task task : tasks) {
            String[] parts = normalizeTime(task.time).split(":");
            if (parts.length != 2) { task.status = "失败：时间格式错误"; changed = true; continue; }
            try {
                int targetMinute = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                // AlarmManager may deliver an inexact/idle alarm late. Treat a
                // task as due once its minute has passed today, otherwise a
                // delayed wake-up would silently skip the task until tomorrow.
                if (task.retryDay != 0 && task.retryDay != day) {
                    task.retryDay = 0;
                    if (task.status.startsWith("失败")) task.status = "等待执行";
                    changed = true;
                }
                // sendMessage() can enqueue a message before its temporary ID is
                // visible through getSendingMessageId(). Persisting lastRunDay in
                // that window used to make a process restart silently lose the
                // task. First recover an existing queued message; if there is no
                // queue entry, make the uncertain attempt due again.
                if (targetMinute <= minutes && task.lastRunDay == day
                        && task.pendingMessageId == 0 && isUnconfirmedSubmission(task.status)) {
                    SendMessagesHelper helper = AccountInstance.getInstance(account).getSendMessagesHelper();
                    int queuedMessageId = helper.getSendingMessageId(task.dialogId);
                    if (queuedMessageId != 0) {
                        task.pendingMessageId = queuedMessageId;
                        task.status = "发送中";
                        changed = true;
                        continue;
                    }
                    task.lastRunDay = 0;
                    task.status = "等待执行";
                    changed = true;
                }
                if (targetMinute <= minutes && task.lastRunDay != day) {
                    if (!isReadyToSend(account)) {
                        task.retryDay = day;
                        task.pendingMessageId = 0;
                        task.status = "失败：无网络或 Telegram 未连接，等待重试";
                        changed = true;
                        continue;
                    }
                    // Write an at-least-once marker before touching the send
                    // queue. If the process dies during sendMessage(), the next
                    // foreground/connection callback will retry this task
                    // instead of treating it as already completed.
                    task.lastRunDay = day;
                    task.retryDay = 0;
                    task.pendingMessageId = 0;
                    task.status = "发送请求已提交";
                    saveTasksWithoutReschedule(account, tasks);
                    try {
                        SendMessagesHelper helper = AccountInstance.getInstance(account).getSendMessagesHelper();
                        int previousMessageId = helper.getSendingMessageId(task.dialogId);
                        helper.sendMessage(
                                SendMessagesHelper.SendMessageParams.of(task.content, task.dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false));
                        int messageId = helper.getSendingMessageId(task.dialogId);
                        // Keep the day marker and replace the uncertain marker
                        // with the real queue ID when SendMessagesHelper exposes
                        // it. A network/mute failure must remain due so the next
                        // foreground with connectivity retries it.
                        task.lastRunDay = day;
                        task.retryDay = 0;
                        task.pendingMessageId = messageId != 0 && messageId != previousMessageId ? messageId : 0;
                        task.status = task.pendingMessageId == 0 ? "发送请求已提交" : "发送中";
                    } catch (Throwable e) { task.pendingMessageId = 0; task.retryDay = day; task.status = "失败【目标永远存在禁言无法发送】"; FileLog.e(e); }
                    changed = true;
                }
            } catch (Throwable e) { task.status = "失败：时间格式错误"; changed = true; }
        }
        if (changed) saveTasksWithoutReschedule(account, tasks);
    }

    private static boolean isUnconfirmedSubmission(String status) {
        return "发送请求已提交".equals(status)
                || "已提交发送".equals(status)
                || "已提交发送，等待确认".equals(status);
    }

    private static boolean isReadyToSend(int account) {
        if (!ApplicationLoader.isNetworkOnlineFast()) return false;
        int state = AccountInstance.getInstance(account).getConnectionsManager().getConnectionState();
        return state == ConnectionsManager.ConnectionStateConnected
                || state == ConnectionsManager.ConnectionStateUpdating;
    }

    private static void saveTasksWithoutReschedule(int account, ArrayList<Task> tasks) {
        JSONArray array = new JSONArray();
            try { for (Task task : tasks) { JSONObject o = new JSONObject(); o.put("id", task.id); o.put("dialogId", task.dialogId); o.put("target", task.target); o.put("content", task.content); o.put("time", task.time); o.put("status", task.status); o.put("lastRunDay", task.lastRunDay); o.put("retryDay", task.retryDay); o.put("pendingMessageId", task.pendingMessageId); array.put(o); } } catch (Throwable e) { FileLog.e(e); }
        // This method is also used for the marker written immediately before
        // sendMessage(). Do not use apply(): the process may be killed before
        // Android flushes the asynchronous preference write.
        prefs().edit().putString(KEY_TASKS + account, array.toString()).commit();
    }

    private static void saveTasksForResend(int account, ArrayList<Task> tasks) {
        JSONArray array = new JSONArray();
        try {
            for (Task task : tasks) {
                JSONObject object = new JSONObject();
                object.put("id", task.id);
                object.put("dialogId", task.dialogId);
                object.put("target", task.target);
                object.put("content", task.content);
                object.put("time", task.time);
                object.put("status", task.status);
                object.put("lastRunDay", task.lastRunDay);
                object.put("retryDay", task.retryDay);
                object.put("pendingMessageId", task.pendingMessageId);
                array.put(object);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        // The UI must not wait for disk I/O before SendMessagesHelper is called.
        // apply() updates the in-memory preferences immediately and flushes in
        // the background, while the -1 lock prevents duplicate taps now.
        prefs().edit().putString(KEY_TASKS + account, array.toString()).apply();
    }

    private static synchronized void ensureStatusObserver(int account) {
        if (STATUS_OBSERVERS.containsKey(account)) return;
        StatusObserver observer = new StatusObserver(account);
        STATUS_OBSERVERS.put(account, observer);
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(observer, NotificationCenter.messageReceivedByServer);
        center.addObserver(observer, NotificationCenter.messageSendError);
        center.addObserver(observer, NotificationCenter.didUpdateConnectionState);
    }

    private static final class StatusObserver implements NotificationCenter.NotificationCenterDelegate {
        private final int account;

        StatusObserver(int account) {
            this.account = account;
        }

        @Override
        public void didReceivedNotification(int event, int eventAccount, Object... args) {
            if (eventAccount != account) return;
            if (event == NotificationCenter.didUpdateConnectionState) {
                int state = AccountInstance.getInstance(account).getConnectionsManager().getConnectionState();
                if (state == ConnectionsManager.ConnectionStateConnected
                        || state == ConnectionsManager.ConnectionStateUpdating) {
                    executeDueTasks(account);
                }
                return;
            }
            if (args == null || args.length == 0 || !(args[0] instanceof Number)) return;
            int messageId = ((Number) args[0]).intValue();
            if (messageId == 0) return;
            ArrayList<Task> tasks = getTasksWithoutObserver(account);
            boolean changed = false;
            for (Task task : tasks) {
                if (task.pendingMessageId == messageId) {
                    task.pendingMessageId = 0;
                    if (event == NotificationCenter.messageSendError) {
                        task.retryDay = task.lastRunDay;
                        task.lastRunDay = 0;
                    }
                    task.status = event == NotificationCenter.messageReceivedByServer
                            ? "成功（" + formatCurrentTime() + "）"
                            : "失败【目标永远存在禁言无法发送】";
                    changed = true;
                }
            }
            if (changed) saveTasksWithoutReschedule(account, tasks);
        }
    }

    private static ArrayList<Task> getTasksWithoutObserver(int account) {
        ArrayList<Task> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs().getString(KEY_TASKS + account, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Task task = new Task();
                task.id = object.optLong("id", 0);
                task.dialogId = object.optLong("dialogId", 0);
                task.target = object.optString("target", "");
                task.content = object.optString("content", "");
                task.time = normalizeTime(object.optString("time", "00:01"));
                task.status = object.optString("status", "未执行");
                task.lastRunDay = object.optLong("lastRunDay", 0);
                task.retryDay = object.optLong("retryDay", 0);
                task.pendingMessageId = object.optInt("pendingMessageId", 0);
                if (task.dialogId != 0 && !TextUtils.isEmpty(task.content)) result.add(task);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    private static String formatCurrentTime() {
        synchronized (TIME) {
            return TIME.format(new Date());
        }
    }
}
