package com.Huanghun.protocol.maker;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LoginActivity;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 外部账号的「协议提取」专用链路。
 *
 * <p>认证只用于取得 256 字节 auth_key：成功后立刻退出登录页并清理临时槽位，
 * 不保存正式账号、不切换账号、不写入联系人/配置，也不触发频道初始化。
 * 号码与扫码页面仍复用 Telegram 自己的安全输入控件。</p>
 */
public final class ExternalAccountLogin {

    /** 认证完成后最多等这么久让 native 交出 auth_key。 */
    private static final long KEY_WAIT_TIMEOUT_MS = 12_000L;
    private static final long KEY_RETRY_INTERVAL_MS = 250L;

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

    public static synchronized boolean isPendingFor(int accountNum) {
        return pendingAccount == accountNum;
    }

    private static synchronized boolean consumePending(int accountNum) {
        if (pendingAccount != accountNum) {
            return false;
        }
        pendingAccount = -1;
        return true;
    }

    /**
     * 由 HTML 界面调用：进入协议专用登录页（号码或扫码）。
     *
     * @return 是否成功进入；false 表示当前槽位已被占用，调用方不应关闭界面。
     */
    public static boolean startProtocolExtraction(LoginActivity activity, boolean qrLogin) {
        if (activity == null) {
            return false;
        }
        int accountNum = activity.getCurrentAccount();
        if (accountNum < 0 || accountNum >= UserConfig.MAX_ACCOUNT_COUNT) {
            ProtocolBuildUi.showError(activity.getParentActivity(), "当前登录页没有可用的账号槽位，请重新进入登录页。");
            return false;
        }
        if (UserConfig.getInstance(accountNum).isClientActivated()) {
            ProtocolBuildUi.showError(activity.getParentActivity(),
                    "当前槽位已经登录了其他账号。外部提取只使用空闲槽位，请改用「本机账号提取」，或先在账号列表中添加新账号。");
            return false;
        }
        markPending(accountNum);
        activity.startProtocolExternalLogin(qrLogin);
        return true;
    }

    /**
     * 从黄昏服务页启动同一条官方登录链路。服务页本身不是 LoginActivity，
     * 因此先打开一个空闲账号槽位的 LoginActivity，再由该页面承接手机号或扫码流程。
     */
    public static boolean startProtocolExtraction(BaseFragment source, boolean qrLogin) {
        if (source instanceof LoginActivity) {
            return startProtocolExtraction((LoginActivity) source, qrLogin);
        }
        Activity activity = source == null ? null : source.getParentActivity();
        if (activity == null) {
            return false;
        }
        int accountNum = findAvailableAccount();
        if (accountNum < 0) {
            ProtocolBuildUi.showError(activity, "没有可用的空闲账号槽位，请先退出一个账号后再进行外部提取。");
            return false;
        }
        LoginActivity loginActivity = new LoginActivity(accountNum);
        markPending(accountNum);
        try {
            if (!source.presentFragment(loginActivity)) {
                consumePending(accountNum);
                ProtocolBuildUi.showError(activity, "无法打开外部登录页面，请稍后重试。");
                return false;
            }
        } catch (Throwable error) {
            consumePending(accountNum);
            FileLog.e(error);
            ProtocolBuildUi.showError(activity, "无法打开外部登录页面，请稍后重试。");
            return false;
        }
        // LoginActivity needs one layout pass before setPage() can switch to the
        // protocol phone/QR screen. Keep the service page responsive meanwhile.
        AndroidUtilities.runOnUIThread(() -> {
            if (loginActivity.getParentActivity() != null) {
                loginActivity.startProtocolExternalLogin(qrLogin);
            } else {
                consumePending(accountNum);
            }
        }, 120L);
        return true;
    }

    private static int findAvailableAccount() {
        for (int account = UserConfig.MAX_ACCOUNT_COUNT - 1; account >= 0; account--) {
            if (!UserConfig.getInstance(account).isClientActivated()) {
                return account;
            }
        }
        return -1;
    }

    /**
     * 认证成功回调，必须早于官方账号落盘逻辑执行。
     *
     * <p>此时只把用户信息与授权密钥取到内存，随即退出登录页并清理临时槽位，
     * 随后在后台完成协议打包。</p>
     */
    public static void onExternalAuthSuccess(LoginActivity loginActivity, int accountNum, TLRPC.User user) {
        if (loginActivity == null || user == null || !consumePending(accountNum)) {
            return;
        }
        final Activity parent = loginActivity.getParentActivity();
        final long userId = user.id;
        final String phone = user.phone == null ? "" : user.phone;
        final boolean isBot = user.bot;
        if (parent == null || parent.isFinishing() || parent.isDestroyed()) {
            cleanupTemporarySlot(accountNum);
            return;
        }
        // 立即离开登录页：本次认证不会被保存，界面不应停留在验证码/密码步骤。
        try {
            loginActivity.finishFragment();
        } catch (Throwable error) {
            FileLog.e(error);
        }
        extractAndExport(parent, accountNum, userId, phone, isBot);
    }

    private static void extractAndExport(Activity parent, int accountNum, long userId,
                                         String phone, boolean isBot) {
        if (!exporting.compareAndSet(false, true)) {
            cleanupTemporarySlot(accountNum);
            return;
        }
        final Context context = parent.getApplicationContext();
        final WeakReference<Activity> activityRef = new WeakReference<>(parent);
        final Handler handler = new Handler(Looper.getMainLooper());
        Toast.makeText(parent, "正在提取协议，不会保存登录状态…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            ProtocolExporter.Result result = null;
            String failure = null;
            ProtocolAccount account = null;
            final long deadline = System.currentTimeMillis() + KEY_WAIT_TIMEOUT_MS;
            try {
                while (System.currentTimeMillis() < deadline) {
                    byte[] key = ConnectionsManager.getInstance(accountNum).getCurrentAuthKey();
                    int dcId = ConnectionsManager.getInstance(accountNum).getCurrentDatacenterId();
                    if (key != null && key.length == ProtocolAccount.KEY_SIZE && dcId >= 1 && dcId <= 5) {
                        account = new ProtocolAccount(key, dcId);
                        break;
                    }
                    if (key != null) {
                        java.util.Arrays.fill(key, (byte) 0);
                    }
                    try {
                        Thread.sleep(KEY_RETRY_INTERVAL_MS);
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (account == null) {
                    failure = "等待授权密钥超时，请检查网络后重试";
                } else {
                    account.userId = userId;
                    account.phone = phone;
                    account.country = CountryCodes.resolve(phone);
                    account.apiId = BuildVars.APP_ID;
                    account.apiHash = BuildVars.APP_HASH;
                    account.isBot = isBot;
                    account.source = "外部账号协议提取";
                    result = ProtocolExporter.exportAccount(context, account, ProtocolExporter.FORMAT_ALL);
                }
            } catch (Throwable error) {
                failure = ProtocolConverter.describe(error);
                FileLog.e(error);
            } finally {
                if (account != null && account.authKey != null) {
                    java.util.Arrays.fill(account.authKey, (byte) 0);
                }
                cleanupTemporarySlot(accountNum);
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
                    ProtocolBuildUi.showError(owner, "协议提取失败：" + (reason == null ? "未知错误" : reason));
                }
            });
        }, "protocol-extract-external").start();
    }

    /** 清空本次临时认证占用的槽位，确保不会留下任何可复用的登录状态。 */
    private static void cleanupTemporarySlot(int accountNum) {
        try {
            ConnectionsManager.getInstance(accountNum).cleanup(true);
        } catch (Throwable error) {
            FileLog.e(error);
        }
        try {
            UserConfig.getInstance(accountNum).clearConfig();
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }
}
