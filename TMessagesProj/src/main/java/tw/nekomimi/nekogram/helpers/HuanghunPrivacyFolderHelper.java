package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 本机隐私聊天文件夹。
 *
 * 该功能只保存当前设备上需要保护的聊天标识和经过加盐哈希的访问密码；不会上传、
 * 同步或修改 Telegram 服务器中的聊天记录。其他客户端不受此本机保护设置影响。
 */
public final class HuanghunPrivacyFolderHelper {
    public static final int POLICY_DIGITS = 0;
    public static final int POLICY_LETTERS = 1;
    public static final int POLICY_MIXED = 2;

    public static final int VERIFY_OK = 0;
    public static final int VERIFY_INCORRECT = 1;
    public static final int VERIFY_LOCKED = 2;
    public static final int VERIFY_NOT_CREATED = 3;

    private static final String PREF_PREFIX = "huanghun_privacy_folder_";
    private static final String KEY_CREATED = "created";
    private static final String KEY_SALT = "salt";
    private static final String KEY_PASSWORD_HASH = "password_hash";
    private static final String KEY_POLICY = "policy";
    private static final String KEY_DIALOGS = "dialogs";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LOCKED_UNTIL = "locked_until";
    private static final long LOCK_DURATION_MS = 30L * 60L * 1000L;

    private HuanghunPrivacyFolderHelper() {
    }

    private static SharedPreferences prefs(Context context, int account) {
        return context.getSharedPreferences(PREF_PREFIX + account, Context.MODE_PRIVATE);
    }

    private static File folder(Context context, int account) {
        return new File(new File(context.getFilesDir(), "huanghun_private_chats"), String.valueOf(account));
    }

    public static boolean isCreated(Context context, int account) {
        return prefs(context, account).getBoolean(KEY_CREATED, false);
    }

    public static boolean create(Context context, int account, int policy, String password) {
        if (!isPasswordValid(policy, password)) {
            return false;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String hash = hash(salt, password);
        if (hash == null) {
            return false;
        }
        File localFolder = folder(context, account);
        if (!localFolder.exists() && !localFolder.mkdirs()) {
            return false;
        }
        prefs(context, account).edit()
                .putBoolean(KEY_CREATED, true)
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_PASSWORD_HASH, hash)
                .putInt(KEY_POLICY, policy)
                .putString(KEY_DIALOGS, "")
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL, 0L)
                .apply();
        return true;
    }

    public static boolean changePassword(Context context, int account, int policy, String newPassword) {
        if (!isCreated(context, account) || !isPasswordValid(policy, newPassword)) {
            return false;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String hash = hash(salt, newPassword);
        if (hash == null) {
            return false;
        }
        prefs(context, account).edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_PASSWORD_HASH, hash)
                .putInt(KEY_POLICY, policy)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL, 0L)
                .apply();
        return true;
    }

    public static int verifyPassword(Context context, int account, String password) {
        SharedPreferences preferences = prefs(context, account);
        if (!preferences.getBoolean(KEY_CREATED, false)) {
            return VERIFY_NOT_CREATED;
        }
        long now = System.currentTimeMillis();
        long lockedUntil = preferences.getLong(KEY_LOCKED_UNTIL, 0L);
        if (lockedUntil > now) {
            return VERIFY_LOCKED;
        }
        String saltValue = preferences.getString(KEY_SALT, "");
        String expectedHash = preferences.getString(KEY_PASSWORD_HASH, "");
        String actualHash;
        try {
            actualHash = hash(Base64.decode(saltValue, Base64.NO_WRAP), password == null ? "" : password);
        } catch (Throwable e) {
            FileLog.e(e);
            actualHash = null;
        }
        if (actualHash != null && secureEquals(expectedHash, actualHash)) {
            preferences.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply();
            return VERIFY_OK;
        }
        int attempts = preferences.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = preferences.edit();
        if (attempts >= 3) {
            editor.putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKED_UNTIL, now + LOCK_DURATION_MS).apply();
            return VERIFY_LOCKED;
        }
        editor.putInt(KEY_FAILED_ATTEMPTS, attempts).apply();
        return VERIFY_INCORRECT;
    }

    public static long getLockedUntil(Context context, int account) {
        return prefs(context, account).getLong(KEY_LOCKED_UNTIL, 0L);
    }

    public static String getLockMessage(Context context, int account) {
        long remaining = getLockedUntil(context, account) - System.currentTimeMillis();
        if (remaining <= 0L) {
            return "";
        }
        long seconds = (remaining + 999L) / 1000L;
        long minutes = seconds / 60L;
        long restSeconds = seconds % 60L;
        return String.format(Locale.CHINA, "已连续输错 3 次，隐私文件夹已锁定。请在 %02d:%02d 后重试。", minutes, restSeconds);
    }

    public static int getPolicy(Context context, int account) {
        return prefs(context, account).getInt(KEY_POLICY, POLICY_DIGITS);
    }

    public static boolean isPasswordValid(int policy, String password) {
        if (password == null || password.length() < 4 || password.length() > 12) {
            return false;
        }
        if (policy == POLICY_DIGITS) {
            return password.matches("[0-9]{4,12}");
        }
        if (policy == POLICY_LETTERS) {
            return password.matches("[A-Za-z]{4,12}");
        }
        return password.matches("(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{4,12}");
    }

    public static String policyHint(int policy) {
        if (policy == POLICY_LETTERS) {
            return "请输入 4–12 位英文密码。";
        }
        if (policy == POLICY_MIXED) {
            return "请输入 4–12 位英文和数字混合密码。";
        }
        return "请输入 4–12 位纯数字密码。";
    }

    public static Set<Long> getProtectedDialogs(Context context, int account) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        String raw = prefs(context, account).getString(KEY_DIALOGS, "");
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        for (String value : raw.split(",")) {
            try {
                result.add(Long.parseLong(value));
            } catch (Throwable ignore) {
            }
        }
        return result;
    }

    public static void saveProtectedDialogs(Context context, int account, Set<Long> dialogIds) {
        if (!isCreated(context, account)) {
            return;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (dialogIds != null) {
            for (Long dialogId : dialogIds) {
                if (dialogId != null && dialogId != 0L) {
                    values.add(String.valueOf(dialogId));
                }
            }
        }
        prefs(context, account).edit().putString(KEY_DIALOGS, TextUtils.join(",", values)).apply();
    }

    public static boolean isProtected(Context context, int account, long dialogId) {
        return isCreated(context, account) && getProtectedDialogs(context, account).contains(dialogId);
    }

    public static boolean delete(Context context, int account) {
        if (!isCreated(context, account)) {
            return false;
        }
        prefs(context, account).edit().clear().apply();
        deleteRecursively(folder(context, account));
        return true;
    }

    private static String hash(byte[] salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return Base64.encodeToString(digest.digest((password == null ? "" : password).getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static boolean secureEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
