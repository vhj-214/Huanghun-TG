package xyz.nextalone.nagram.helper;

import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import xyz.nextalone.nagram.NaConfig;

/**
 * Local-only message reaction overlay. The stored reaction is never sent to Telegram.
 */
public final class LocalMessageReactionHelper {
    private static final String REACTION_PREFIX = "localMessageReaction_";
    private static final String CUSTOM_PREFIX = "custom:";

    private LocalMessageReactionHelper() {
    }

    private static String key(int account, long dialogId, int messageId) {
        return REACTION_PREFIX + account + "_" + dialogId + "_" + messageId;
    }

    private static String fallbackKey(int account, long dialogId, int messageId) {
        return key(account, dialogId, messageId) + "_fallback";
    }

    public static void set(int account, long dialogId, int messageId, String reaction) {
        set(account, dialogId, messageId, reaction, null);
    }

    /** Stores the local reaction and, for custom reactions, the server compatibility emoji. */
    public static void set(int account, long dialogId, int messageId, String reaction, String fallbackEmoji) {
        if (messageId == 0 || TextUtils.isEmpty(reaction)) {
            return;
        }
        android.content.SharedPreferences.Editor editor = NaConfig.getPreferences().edit()
                .putString(key(account, dialogId, messageId), reaction);
        if (isCustomEmoji(reaction) && !TextUtils.isEmpty(fallbackEmoji)) {
            editor.putString(fallbackKey(account, dialogId, messageId), fallbackEmoji);
        } else {
            editor.remove(fallbackKey(account, dialogId, messageId));
        }
        editor.apply();
    }

    public static String encodeEmoji(String emoji) {
        return emoji;
    }

    public static boolean isCustomEmoji(String reaction) {
        return reaction != null && reaction.startsWith(CUSTOM_PREFIX);
    }

    public static String encodeCustomEmoji(long documentId) {
        return CUSTOM_PREFIX + documentId;
    }

    public static void apply(TLRPC.Message message, int account) {
        if (message == null || message.id == 0) {
            return;
        }
        final long dialogId;
        try {
            dialogId = MessageObject.getDialogId(message);
        } catch (Exception ignored) {
            return;
        }
        if (dialogId == 0) {
            return;
        }
        String stored = NaConfig.getPreferences().getString(key(account, dialogId, message.id), null);
        if (TextUtils.isEmpty(stored)) {
            return;
        }

        TLRPC.Reaction localReaction = decode(stored);
        if (localReaction == null) {
            return;
        }
        if (message.reactions == null) {
            message.reactions = new TLRPC.TL_messageReactions();
            message.reactions.can_see_list = false;
        }
        if (message.reactions.results == null) {
            message.reactions.results = new java.util.ArrayList<>();
        }

        TLRPC.ReactionCount count = find(message.reactions, localReaction);
        if (localReaction instanceof TLRPC.TL_reactionCustomEmoji) {
            final long documentId = ((TLRPC.TL_reactionCustomEmoji) localReaction).document_id;
            String fallbackEmoji = NaConfig.getPreferences().getString(
                    fallbackKey(account, dialogId, message.id), null);
            if (TextUtils.isEmpty(fallbackEmoji)) {
                try {
                    final TLRPC.Document document = AnimatedEmojiDrawable.findDocument(account, documentId);
                    fallbackEmoji = MessageObject.findAnimatedEmojiEmoticon(document, null, account);
                } catch (Exception ignored) {
                    // A missing document must not crash message rendering.
                }
            }
            // Older local records did not persist the compatibility emoji. The sender
            // used 👍 as its final fallback, so clean that legacy duplicate as well.
            if (TextUtils.isEmpty(fallbackEmoji)) {
                fallbackEmoji = "👍";
            }
            TLRPC.ReactionCount fallbackCount = find(message.reactions, decode(fallbackEmoji));
            if (fallbackCount != null && count == null) {
                // Keep the server count while rendering the selected custom emoji locally.
                fallbackCount.reaction = localReaction;
                count = fallbackCount;
            } else if (fallbackCount != null && fallbackCount != count) {
                // The local custom overlay already exists; remove only the duplicate
                // compatibility reaction from this client's rendered list.
                message.reactions.results.remove(fallbackCount);
            }
        }
        if (count == null) {
            count = new TLRPC.TL_reactionCount();
            count.reaction = localReaction;
            count.count = 1;
            message.reactions.results.add(count);
        }
        if (!count.chosen) {
            count.chosen = true;
            count.chosen_order = nextChosenOrder(message.reactions);
            if (count.count <= 0) {
                count.count = 1;
            }
        }
        message.flags |= 1048576;
    }

    private static TLRPC.Reaction decode(String stored) {
        if (stored.startsWith(CUSTOM_PREFIX)) {
            try {
                long documentId = Long.parseLong(stored.substring(CUSTOM_PREFIX.length()));
                if (documentId <= 0) {
                    return null;
                }
                TLRPC.TL_reactionCustomEmoji reaction = new TLRPC.TL_reactionCustomEmoji();
                reaction.document_id = documentId;
                return reaction;
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        TLRPC.TL_reactionEmoji reaction = new TLRPC.TL_reactionEmoji();
        reaction.emoticon = stored;
        return reaction;
    }

    private static TLRPC.ReactionCount find(TLRPC.TL_messageReactions reactions, TLRPC.Reaction target) {
        for (TLRPC.ReactionCount count : reactions.results) {
            if (count == null || count.reaction == null) {
                continue;
            }
            if (target instanceof TLRPC.TL_reactionEmoji && count.reaction instanceof TLRPC.TL_reactionEmoji
                    && TextUtils.equals(((TLRPC.TL_reactionEmoji) target).emoticon, ((TLRPC.TL_reactionEmoji) count.reaction).emoticon)) {
                return count;
            }
            if (target instanceof TLRPC.TL_reactionCustomEmoji && count.reaction instanceof TLRPC.TL_reactionCustomEmoji
                    && ((TLRPC.TL_reactionCustomEmoji) target).document_id == ((TLRPC.TL_reactionCustomEmoji) count.reaction).document_id) {
                return count;
            }
        }
        return null;
    }

    private static int nextChosenOrder(TLRPC.TL_messageReactions reactions) {
        int order = 0;
        for (TLRPC.ReactionCount count : reactions.results) {
            if (count != null && count.chosen) {
                order = Math.max(order, count.chosen_order);
            }
        }
        return order + 1;
    }
}
