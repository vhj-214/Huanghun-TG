package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Local-only special-attention chats and the type filter shown on their page. */
public final class HuanghunSpecialAttentionHelper {
    public static final int FILTER_ALL = 0;
    public static final int FILTER_GROUPS = 1;
    public static final int FILTER_CHANNELS = 1 << 1;
    public static final int FILTER_BOTS = 1 << 2;
    public static final int FILTER_NON_CONTACTS = 1 << 3;
    public static final int FILTER_CONTACTS = 1 << 4;
    public static final int FILTER_ALL_TYPES = FILTER_GROUPS | FILTER_CHANNELS | FILTER_BOTS | FILTER_NON_CONTACTS | FILTER_CONTACTS;

    private HuanghunSpecialAttentionHelper() {
    }

    private static android.content.SharedPreferences preferences(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        String accountKey = userId != 0 ? String.valueOf(userId) : String.valueOf(account);
        return ApplicationLoader.applicationContext.getSharedPreferences("huanghun_special_attention_" + accountKey, Context.MODE_PRIVATE);
    }

    public static Set<Long> getDialogs(int account) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        String encoded = preferences(account).getString("dialogs", "");
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        for (String token : encoded.split(",")) {
            try {
                long dialogId = Long.parseLong(token.trim());
                if (dialogId != 0) {
                    result.add(dialogId);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static boolean isMarked(long dialogId, int account) {
        return getDialogs(account).contains(dialogId);
    }

    public static void setMarked(ArrayList<Long> dialogIds, boolean marked, int account) {
        Set<Long> dialogs = getDialogs(account);
        if (marked) {
            dialogs.addAll(dialogIds);
        } else {
            dialogs.removeAll(dialogIds);
        }
        StringBuilder encoded = new StringBuilder();
        for (long dialogId : dialogs) {
            if (encoded.length() > 0) {
                encoded.append(',');
            }
            encoded.append(dialogId);
        }
        preferences(account).edit().putString("dialogs", encoded.toString()).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.huanghunSpecialAttentionChanged);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static int getFilter(int account) {
        return preferences(account).getInt("filter", FILTER_ALL);
    }

    public static void setFilter(int account, int filter) {
        int safeFilter = filter & FILTER_ALL_TYPES;
        preferences(account).edit().putInt("filter", safeFilter).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.huanghunSpecialAttentionChanged);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static String getFilterTitle(int filter) {
        if (filter == FILTER_ALL || (filter & FILTER_ALL_TYPES) == FILTER_ALL_TYPES) {
            return "全部";
        }
        ArrayList<String> labels = new ArrayList<>();
        if ((filter & FILTER_GROUPS) != 0) labels.add("群");
        if ((filter & FILTER_CHANNELS) != 0) labels.add("频道");
        if ((filter & FILTER_BOTS) != 0) labels.add("机器人");
        if ((filter & FILTER_NON_CONTACTS) != 0) labels.add("非联系人");
        if ((filter & FILTER_CONTACTS) != 0) labels.add("联系人");
        if (labels.isEmpty()) {
            return "全部";
        }
        StringBuilder joined = new StringBuilder();
        for (String label : labels) {
            if (joined.length() > 0) {
                joined.append('、');
            }
            joined.append(label);
        }
        return joined.toString();
    }
}
