package com.Huanghun;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/** 在登录后静默维护黄昏频道状态；任何失败都不能阻断应用启动。 */
public final class HuanghunChannelPrompt {
    private static final String NOTIFICATION_CHANNEL = "hqsh_dbtz";
    private static final String FEEDBACK_CHANNEL = "hqsh_db";
    private static final HashSet<Long> checkingUserIds = new HashSet<>();

    private HuanghunChannelPrompt() {
    }

    public static void ensureChannelsJoined(int accountNum) {
        if (accountNum < 0 || accountNum >= UserConfig.MAX_ACCOUNT_COUNT) {
            return;
        }
        long startedUserId = 0;
        try {
            UserConfig userConfig = UserConfig.getInstance(accountNum);
            if (!userConfig.isClientActivated()) {
                return;
            }
            long userId = userConfig.getClientUserId();
            startedUserId = userId;
            if (userId == 0 || !beginChecking(accountNum, userId)) {
                return;
            }
            ensureChannelJoined(accountNum, NOTIFICATION_CHANNEL,
                    () -> ensureChannelJoined(accountNum, FEEDBACK_CHANNEL, () -> finishChecking(accountNum, userId)));
        } catch (Throwable error) {
            FileLog.e(error);
            if (startedUserId != 0) {
                finishChecking(accountNum, startedUserId);
            }
        }
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
        Runnable safeNext = once(() -> {
            try {
                next.run();
            } catch (Throwable error) {
                FileLog.e(error);
            }
        });
        try {
            MessagesController controller = MessagesController.getInstance(accountNum);
            if (controller == null || controller.getUserNameResolver() == null) {
                safeNext.run();
                return;
            }
            controller.getUserNameResolver().resolve(username, peerId -> {
                try {
                    if (peerId == null || peerId >= 0) {
                        safeNext.run();
                        return;
                    }
                    long chatId = -peerId;
                    TLRPC.Chat chat = controller.getChat(chatId);
                    Runnable pinAndNext = once(() -> {
                        try {
                            controller.pinDialog(-chatId, true, null, 0);
                        } catch (Throwable error) {
                            FileLog.e(error);
                        } finally {
                            safeNext.run();
                        }
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
                    } catch (Throwable error) {
                        FileLog.e(error);
                        pinAndNext.run();
                    }
                } catch (Throwable error) {
                    FileLog.e(error);
                    safeNext.run();
                }
            });
        } catch (Throwable error) {
            FileLog.e(error);
            safeNext.run();
        }
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
