package tw.nekomimi.nekogram.helpers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.UserConfig;

public class HuanghunSignInReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int account = intent.getIntExtra("account", -1);
        if (account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT) {
            HuanghunSignInHelper.executeDueTasks(account);
            HuanghunSignInScheduler.schedule(account);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            HuanghunSignInScheduler.scheduleAll();
        }
    }
}
