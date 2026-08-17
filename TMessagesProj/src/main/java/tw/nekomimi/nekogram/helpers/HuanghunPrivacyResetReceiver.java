package tw.nekomimi.nekogram.helpers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 仅由应用自身的本机闹钟触发。到达忘记密码的安全等待期后，
 * 清除本机隐私文件夹、密码哈希和受保护聊天列表，不访问或修改 Telegram 云端数据。
 */
public final class HuanghunPrivacyResetReceiver extends BroadcastReceiver {
    public static final String EXTRA_ACCOUNT = "account";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        int account = intent.getIntExtra(EXTRA_ACCOUNT, -1);
        if (account >= 0) {
            HuanghunPrivacyFolderHelper.completePasswordResetIfDue(context.getApplicationContext(), account);
        }
    }
}
