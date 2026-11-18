package com.Huanghun.protocol.maker;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.LoginActivity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 功能三：外部账号提取。
 *
 * <p>走客户端自带的登录流程（号码登录 / 扫码登录），登录成功后自动把该账号导出成协议包。
 * 客户端自己的密码页会被原样复用，有二级密码时由用户按正常流程输入。</p>
 */
public final class ExternalAccountLogin {

    /** 登录成功后最多等这么久拿 auth_key（native 建链是异步的）。 */
    private static final long KEY_WAIT_TIMEOUT_MS = 20_000L;
    private static final long KEY_RETRY_INTERVAL_MS = 500L;

    private static volatile int pendingAccount = -1;
    private static final AtomicBoolean exporting = new AtomicBoolean(false);

    private ExternalAccountLogin() {
    }

    public static synchronized void markPending(int accountNum) {
        pendingAccount = accountNum;
    }

    public static synchronized void clearPending() {
        pendingAccount = -1;
    }

    private static synchronized boolean consumePending(int accountNum) {
        if (pendingAccount != accountNum) {
            return false;
        }
        pendingAccount = -1;
        return true;
    }

    public static boolean hasPending() {
        return pendingAccount >= 0;
    }

    /**
     * 登录成功回调。这里只做一件事：等授权密钥可用后导出协议包。
     */
    public static void onLoginSuccess(LoginActivity activity, int accountNum) {
        if (activity == null || !consumePending(accountNum)) {
            return;
        }
        startExport(activity, accountNum, "登录成功，正在生成协议文件…");
    }

    /** 登录到已有账号槽位时，原槽位可用；不能丢掉本次显式发起的导出请求。 */
    public static void onDuplicateAccount(LoginActivity activity, int attemptedAccount, int existingAccount) {
        if (activity == null || !consumePending(attemptedAccount)) {
            return;
        }
        startExport(activity, existingAccount, "检测到该账号已登录，正在导出已有账号…");
    }

    private static void startExport(LoginActivity activity, int accountNum, String toastText) {
        if (!exporting.compareAndSet(false, true)) {
            return;
        }
        final Activity parent = activity.getParentActivity();
        if (parent == null || parent.isFinishing() || parent.isDestroyed()) {
            exporting.set(false);
            FileLog.e("Cannot export protocol after login: parent activity is unavailable");
            return;
        }
        final Context context = parent.getApplicationContext();
        final java.lang.ref.WeakReference<Activity> activityRef = new java.lang.ref.WeakReference<>(parent);
        final Handler handler = new Handler(Looper.getMainLooper());
        final long deadline = System.currentTimeMillis() + KEY_WAIT_TIMEOUT_MS;

        Toast.makeText(parent, toastText, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            ProtocolExporter.Result result = null;
            String failure = null;
            while (System.currentTimeMillis() < deadline) {
                try {
                    if (UserConfig.getInstance(accountNum).isClientActivated()) {
                        byte[] keyProbe = ConnectionsManager.getInstance(accountNum).getCurrentAuthKey();
                        boolean keyReady = keyProbe != null && keyProbe.length == ProtocolAccount.KEY_SIZE;
                        if (keyProbe != null) {
                            java.util.Arrays.fill(keyProbe, (byte) 0);
                        }
                        if (keyReady) {
                            result = ProtocolExporter.export(context, accountNum, ProtocolExporter.FORMAT_ALL);
                            break;
                        }
                    }
                } catch (Throwable error) {
                    failure = ProtocolConverter.describe(error);
                    FileLog.e(error);
                    break;
                }
                try {
                    Thread.sleep(KEY_RETRY_INTERVAL_MS);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            final ProtocolExporter.Result exported = result;
            final String reason = failure;
            handler.post(() -> {
                exporting.set(false);
                Activity owner = activityRef.get();
                if (owner == null || owner.isFinishing() || owner.isDestroyed()) {
                    return;
                }
                if (exported != null) {
                    ProtocolBuildUi.showResult(owner, java.util.Collections.singletonList(exported.zipFile),
                            java.util.Collections.emptyList(), exported.publicDirectory);
                } else {
                    ProtocolBuildUi.showError(owner, "协议生成失败：" + (reason == null ? "等待授权密钥超时" : reason));
                }
            });
        }, "protocol-export-after-login").start();
    }
}
