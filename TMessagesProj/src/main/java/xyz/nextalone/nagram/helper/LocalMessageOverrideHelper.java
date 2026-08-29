package xyz.nextalone.nagram.helper;

import android.content.SharedPreferences;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import xyz.nextalone.nagram.NaConfig;

/** Local-only message text/date overrides. Never sends an edit request to Telegram. */
public final class LocalMessageOverrideHelper {
    private static final String TEXT_PREFIX = "localMessageText_";
    private static final String DATE_PREFIX = "localMessageDate_";

    private LocalMessageOverrideHelper() { }

    private static String key(String prefix, int account, long dialogId, int messageId) {
        return prefix + account + "_" + dialogId + "_" + messageId;
    }

    public static String getText(int account, long dialogId, int messageId) {
        return NaConfig.getPreferences().getString(key(TEXT_PREFIX, account, dialogId, messageId), null);
    }

    public static int getDate(int account, long dialogId, int messageId, int fallback) {
        return NaConfig.getPreferences().getInt(key(DATE_PREFIX, account, dialogId, messageId), fallback);
    }

    public static void setText(int account, long dialogId, int messageId, String text) {
        SharedPreferences.Editor editor = NaConfig.getPreferences().edit();
        String key = key(TEXT_PREFIX, account, dialogId, messageId);
        if (text == null) editor.remove(key); else editor.putString(key, text);
        editor.apply();
    }

    public static void setDate(int account, long dialogId, int messageId, int date) {
        NaConfig.getPreferences().edit().putInt(key(DATE_PREFIX, account, dialogId, messageId), date).apply();
    }

    public static void apply(TLRPC.Message message, int account) {
        if (message == null || message.id == 0) return;
        long dialogId = MessageObject.getDialogId(message);
        String text = getText(account, dialogId, message.id);
        if (text != null) message.message = text;
        message.date = getDate(account, dialogId, message.id, message.date);
    }
}
