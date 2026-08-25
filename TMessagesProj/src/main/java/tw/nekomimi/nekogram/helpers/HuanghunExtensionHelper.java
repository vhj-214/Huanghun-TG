package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * Huanghun account-safety extensions. Operations are only dispatched after the settings UI
 * obtains explicit typed confirmation from the account holder.
 */
public final class HuanghunExtensionHelper {

    public enum CleanupAction {
        BOT_INTERACTIONS,
        GROUPS,
        CONTACTS,
        CHATS,
        PROFILE,
        DELETED_ACCOUNTS,
        ALL
    }

    public interface CleanupCallback {
        void onComplete(int scheduledActions);
    }

    public interface TimeBoundsCallback {
        void onBounds(long earliestMillis, long latestMillis);
    }

    private HuanghunExtensionHelper() {
    }

    public static List<String> getKeywords() {
        return new ArrayList<>(parseKeywords(NekoConfig.huanghunBlockedKeywords.String()));
    }

    public static int getKeywordCount() {
        return getKeywords().size();
    }

    public static void setKeywords(String rawKeywords) {
        LinkedHashSet<String> keywords = parseKeywords(rawKeywords);
        NekoConfig.huanghunBlockedKeywords.setConfigString(TextUtils.join("\n", keywords));
    }

    public static boolean shouldBlockIncomingMessage(int account, MessageObject messageObject) {
        if (!NekoConfig.huanghunBlockNonContacts.Bool() || messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        if (messageObject.isOutOwner() || messageObject.getDialogId() <= 0) {
            return false;
        }
        long userId = messageObject.getDialogId();
        if (userId == UserConfig.getInstance(account).getClientUserId()) {
            return false;
        }
        MessagesController messagesController = MessagesController.getInstance(account);
        TLRPC.User user = messagesController.getUser(userId);
        if (user == null || ContactsController.getInstance(account).isContact(userId)) {
            return false;
        }

        CharSequence messageText = messageObject.messageText;
        if (TextUtils.isEmpty(messageText)) {
            return false;
        }
        String normalizedMessage = messageText.toString().toLowerCase(Locale.ROOT);
        for (String keyword : getKeywords()) {
            if (TextUtils.isEmpty(keyword)) {
                continue;
            }
            if (normalizedMessage.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes keyword-matching private messages from notification processing and blocks the
     * non-contact sender. Message and dialog history must never be deleted automatically.
     */
    public static void filterIncomingMessages(int account, ArrayList<MessageObject> messageObjects) {
        if (messageObjects == null || messageObjects.isEmpty() || !NekoConfig.huanghunBlockNonContacts.Bool()) {
            return;
        }
        Set<Long> handledDialogs = new LinkedHashSet<>();
        for (int index = messageObjects.size() - 1; index >= 0; index--) {
            MessageObject messageObject = messageObjects.get(index);
            if (!shouldBlockIncomingMessage(account, messageObject)) {
                continue;
            }
            long dialogId = messageObject.getDialogId();
            messageObjects.remove(index);
            if (!handledDialogs.add(dialogId)) {
                continue;
            }
            MessagesController controller = MessagesController.getInstance(account);
            controller.blockPeer(dialogId);
        }
    }

    public static void runCleanup(int account, CleanupAction action, CleanupCallback callback) {
        AndroidUtilities.runOnUIThread(() -> {
            int scheduledActions = 0;
            switch (action) {
                case BOT_INTERACTIONS:
                    scheduledActions = clearBotInteractions(account);
                    break;
                case GROUPS:
                    scheduledActions = leaveGroups(account);
                    break;
                case CONTACTS:
                    scheduledActions = clearContacts(account);
                    break;
                case CHATS:
                    scheduledActions = clearChats(account);
                    break;
                case PROFILE:
                    scheduledActions = resetProfile(account);
                    break;
                case DELETED_ACCOUNTS:
                    scheduledActions = clearDeletedAccounts(account);
                    break;
                case ALL:
                    scheduledActions += clearBotInteractions(account);
                    scheduledActions += leaveGroups(account);
                    scheduledActions += clearContacts(account);
                    scheduledActions += clearChats(account);
                    scheduledActions += resetProfile(account);
                    scheduledActions += clearDeletedAccounts(account);
                    break;
            }
            if (callback != null) {
                callback.onComplete(scheduledActions);
            }
        });
    }

    private static int clearBotInteractions(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        int scheduled = 0;
        ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            dialogs.add(controller.dialogs_dict.valueAt(i));
        }
        for (TLRPC.Dialog dialog : dialogs) {
            if (dialog == null || dialog.id <= 0) {
                continue;
            }
            TLRPC.User user = controller.getUser(dialog.id);
            if (user != null && user.bot) {
                controller.deleteDialog(dialog.id, 0, true);
                scheduled++;
            }
        }
        return scheduled;
    }

    private static int leaveGroups(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        int scheduled = 0;
        ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            dialogs.add(controller.dialogs_dict.valueAt(i));
        }
        for (TLRPC.Dialog dialog : dialogs) {
            if (dialog == null || dialog.id >= 0) {
                continue;
            }
            long chatId = -dialog.id;
            TLRPC.Chat chat = controller.getChat(chatId);
            if (chat == null) {
                controller.deleteDialog(dialog.id, 0, true);
                scheduled++;
                continue;
            }
            if (ChatObject.isChannel(chat)) {
                TLRPC.TL_channels_leaveChannel req = new TLRPC.TL_channels_leaveChannel();
                req.channel = MessagesController.getInputChannel(chat);
                ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {});
            } else {
                controller.deleteParticipantFromChat(chatId, controller.getInputPeer(UserConfig.getInstance(account).getClientUserId()));
            }
            controller.deleteDialog(dialog.id, 0, true);
            scheduled++;
        }
        return scheduled;
    }

    private static int clearContacts(int account) {
        ContactsController contactsController = ContactsController.getInstance(account);
        int count = contactsController.contacts != null ? contactsController.contacts.size() : 0;
        contactsController.deleteAllContacts(null);
        return count;
    }

    private static int clearChats(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        long selfId = UserConfig.getInstance(account).getClientUserId();
        int scheduled = 0;
        ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            dialogs.add(controller.dialogs_dict.valueAt(i));
        }
        for (TLRPC.Dialog dialog : dialogs) {
            if (dialog == null || dialog.id == selfId || dialog.id == 0) {
                continue;
            }
            controller.deleteDialog(dialog.id, 0, true);
            scheduled++;
        }
        return scheduled;
    }

    private static int resetProfile(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        TL_account.updateProfile profileRequest = new TL_account.updateProfile();
        profileRequest.flags = 1 | 2 | 4;
        profileRequest.first_name = " "; // Telegram requires non-empty first name
        profileRequest.last_name = "";
        profileRequest.about = "";
        ConnectionsManager.getInstance(account).sendRequest(profileRequest, (response, error) -> {
            if (response instanceof TLRPC.Updates) {
                controller.processUpdates((TLRPC.Updates) response, false);
            }
        });

        TL_account.updateUsername usernameRequest = new TL_account.updateUsername();
        usernameRequest.username = "";
        ConnectionsManager.getInstance(account).sendRequest(usernameRequest, (response, error) -> {
            if (response instanceof TLRPC.User) {
                ArrayList<TLRPC.User> users = new ArrayList<>();
                users.add((TLRPC.User) response);
                MessagesStorage.getInstance(account).putUsersAndChats(users, null, false, true);
            }
        });
        controller.deleteUserPhoto(null);
        return 3;
    }

    private static LinkedHashSet<String> parseKeywords(String rawKeywords) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (rawKeywords == null) {
            return keywords;
        }
        for (String part : rawKeywords.split("[\\p{P}\\p{S}\\p{Z}\\s]+")) {
            String keyword = part.trim();
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    private static int clearDeletedAccounts(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        int scheduled = 0;
        ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            dialogs.add(controller.dialogs_dict.valueAt(i));
        }
        for (TLRPC.Dialog dialog : dialogs) {
            if (dialog == null || dialog.id <= 0) {
                continue;
            }
            TLRPC.User user = controller.getUser(dialog.id);
            if (user != null && user.deleted) {
                controller.deleteDialog(dialog.id, 0, true);
                scheduled++;
            }
        }
        return scheduled;
    }

    /**
     * Removes only messages whose local timestamps fall in the selected inclusive date interval.
     * scope: 0 = both, 1 = groups/channels, 2 = private chats and bots.
     * mode: 0 = before the selected end date, 1 = selected start-to-end date interval.
     */
    public static void clearMessagesByTime(int account, int mode, long startTime, long endTime, int scope, CleanupCallback callback) {
        final int minDate = mode == 1 ? (int) (startTime / 1000L) : 0;
        final int maxDate = (int) (endTime / 1000L);
        final MessagesStorage storage = MessagesStorage.getInstance(account);
        final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
        MessagesController controller = MessagesController.getInstance(account);
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            dialogs.add(controller.dialogs_dict.valueAt(i));
        }
        storage.getStorageQueue().postRunnable(() -> {
            int scheduled = 0;
            for (TLRPC.Dialog dialog : dialogs) {
                if (dialog == null || dialog.id == 0) {
                    continue;
                }
                boolean isGroupOrChannel = dialog.id < 0;
                boolean isPrivateOrBot = dialog.id > 0;
                if ((scope == 1 && !isGroupOrChannel) || (scope == 2 && !isPrivateOrBot)) {
                    continue;
                }
                ArrayList<Integer> messageIds = storage.getCachedMessagesInRange(dialog.id, minDate, maxDate);
                if (messageIds.isEmpty()) {
                    continue;
                }
                scheduled += messageIds.size();
                final ArrayList<Integer> ids = messageIds;
                final long dialogId = dialog.id;
                AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(account)
                        .deleteMessages(ids, null, null, dialogId, 0, true, 0));
            }
            final int result = scheduled;
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.onComplete(result);
                }
            });
        });
    }

    /** Reads the earliest and latest locally cached ordinary-message dates without blocking the UI. */
    public static void loadAccountMessageTimeBounds(int account, TimeBoundsCallback callback) {
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            long earliest = System.currentTimeMillis();
            long latest = earliest;
            SQLiteCursor cursor = null;
            try {
                cursor = storage.getDatabase().queryFinalized("SELECT MIN(date), MAX(date) FROM messages_v2 WHERE date > 0");
                if (cursor.next()) {
                    if (!cursor.isNull(0)) {
                        earliest = cursor.longValue(0) * 1000L;
                    }
                    if (!cursor.isNull(1)) {
                        latest = cursor.longValue(1) * 1000L;
                    }
                }
            } catch (Exception ignore) {
                // Safe fallback: keep today's date when the local database is empty or unavailable.
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            final long finalEarliest = earliest;
            final long finalLatest = latest;
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.onBounds(finalEarliest, finalLatest);
                }
            });
        });
    }
}
