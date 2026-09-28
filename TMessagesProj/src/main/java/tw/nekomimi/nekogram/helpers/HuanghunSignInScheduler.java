package tw.nekomimi.nekogram.helpers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

/** Schedules one minute-level alarm per account and always reschedules after delivery. */
public final class HuanghunSignInScheduler {
    private static final String ACTION = "tw.nekomimi.nekogram.HUANGHUN_SIGN_IN";
    private static final int REQUEST_BASE = 48120;
    private HuanghunSignInScheduler() {}

    public static void scheduleAll() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) schedule(account);
        }
    }

    public static void schedule(int account) {
        try {
            Context context = ApplicationLoader.applicationContext;
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarm == null) return;
            PendingIntent pending = pendingIntent(context, account);
            alarm.cancel(pending);
            if (!HuanghunSignInHelper.isEnabled(account) || HuanghunSignInHelper.getTasks(account).isEmpty()) return;
            long trigger = HuanghunSignInHelper.nextTriggerMillis("00:01", System.currentTimeMillis());
            for (HuanghunSignInHelper.Task task : HuanghunSignInHelper.getTasks(account)) {
                trigger = Math.min(trigger, HuanghunSignInHelper.nextTriggerMillis(task.time, System.currentTimeMillis()));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
            else alarm.setExact(AlarmManager.RTC_WAKEUP, trigger, pending);
        } catch (Throwable e) { FileLog.e(e); }
    }

    private static PendingIntent pendingIntent(Context context, int account) {
        Intent intent = new Intent(context, HuanghunSignInReceiver.class).setAction(ACTION).putExtra("account", account);
        return PendingIntent.getBroadcast(context, REQUEST_BASE + account, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

}
