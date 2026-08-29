package com.Huanghun;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Silently maintains membership in Huanghun's two public channels for an active account.
 * The check runs after an account switch or a successful app login. Membership is tested
 * before every join attempt, so an already joined account is never added again; if the
 * account later leaves a channel, the next account activation checks and joins it again.
 */
public final class HuanghunChannelPrompt {
    private static final String NOTIFICATION_CHANNEL = "hqsh_dbtz";
    private static final String FEEDBACK_CHANNEL = "hqsh_db";
    private static final HashSet<Long> checkingUserIds = new HashSet<>();

    private HuanghunChannelPrompt() {
    }

    /**
     * Starts one silent membership check for the currently activated account. Concurrent
     * lifecycle callbacks for the same Telegram user are coalesced to prevent duplicate
     * join requests while a previous check is still in progress.
     */
    public static void ensureChannelsJoined(int accountNum) {
        UserConfig userConfig = UserConfig.getInstance(accountNum);
        if (!userConfig.isClientActivated()) {
            return;
        }
        long userId = userConfig.getClientUserId();
        if (userId == 0 || !beginChecking(accountNum, userId)) {
            return;
        }
        ensureChannelJoined(accountNum, NOTIFICATION_CHANNEL,
                () -> ensureChannelJoined(accountNum, FEEDBACK_CHANNEL, () -> finishChecking(accountNum, userId)));
    }

    private static boolean beginChecking(int accountNum, long userId) {
        synchronized (checkingUserIds) {
            return checkingUserIds.add((((long) accountNum) << 56) ^ userId);
        }
    }

    private static void finishChecking(int accountNum, long userId) {
        synchronized (checkingUserIds) {
            checkingUserIds.remove((((long) accountNum) << 56) ^ userId);
        }
    }

    private static void ensureChannelJoined(int accountNum, String username, Runnable next) {
        MessagesController controller = MessagesController.getInstance(accountNum);
        controller.getUserNameResolver().resolve(username, peerId -> {
            if (peerId == null || peerId >= 0) {
                next.run();
                return;
            }
            long chatId = -peerId;
            TLRPC.Chat chat = controller.getChat(chatId);
            Runnable pinAndNext = once(() -> {
                // dialogId 对频道和群聊均为负数；taskId=0 允许 MessagesController 同步发送置顶请求。
                controller.pinDialog(-chatId, true, null, 0);
                next.run();
            });
            if (chat == null || (!chat.left && !chat.kicked)) {
                pinAndNext.run();
                return;
            }
            try {
                controller.addUserToChat(chatId, UserConfig.getInstance(accountNum).getCurrentUser(), 0,
                        null, null, true,
                        pinAndNext,
                        error -> {
                            pinAndNext.run();
                            return false;
                        });
            } catch (Throwable ignore) {
                pinAndNext.run();
            }
        });
    }

    private static Runnable once(Runnable action) {
        AtomicBoolean called = new AtomicBoolean(false);
        return () -> {
            if (called.compareAndSet(false, true)) {
                action.run();
            }
        };
    }
}
