package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
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
        ALL
    }

    public interface CleanupCallback {
        void onComplete(int scheduledActions);
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
     * Removes keyword-matching private messages from notification processing, then blocks the
     * non-contact sender and deletes the matching private dialog for the account owner.
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
            controller.deleteDialog(dialogId, 0, true);
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
                case ALL:
                    scheduledActions += clearBotInteractions(account);
                    scheduledActions += leaveGroups(account);
                    scheduledActions += clearContacts(account);
                    scheduledActions += clearChats(account);
                    scheduledActions += resetProfile(account);
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
                controller.getMessagesStorage().putUsersAndChats(users, null, false, true);
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
}
