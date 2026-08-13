package com.Huanghun;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

import java.util.HashMap;

/**
 * Shows a clear, user-controlled invitation to 黄昏's public channels. No channel
 * is joined until the account holder explicitly presses the one-click button.
 */
public final class HuanghunChannelPrompt {
    private static final String PREFS = "huanghun_channel_prompt";
    private static final String NOTIFICATION_CHANNEL = "hqsh_dbtz";
    private static final String FEEDBACK_CHANNEL = "hqsh_db";
    private static final long PROMPT_COOLDOWN_MS = 5_000L;
    private static final HashMap<Long, Long> lastPromptAt = new HashMap<>();

    private HuanghunChannelPrompt() {
    }

    public static void showIfNeeded(LaunchActivity activity, int accountNum) {
        if (activity == null || activity.isFinishing() || !UserConfig.getInstance(accountNum).isClientActivated()) {
            return;
        }
        long userId = UserConfig.getInstance(accountNum).getClientUserId();
        if (userId == 0 || isCompleted(userId) || recentlyShown(userId)) {
            return;
        }
        detectJoinedChannels(activity, accountNum, userId);
    }

    private static boolean isCompleted(long userId) {
        return preferences().getBoolean("joined_" + userId, false);
    }

    private static void markCompleted(long userId) {
        preferences().edit().putBoolean("joined_" + userId, true).apply();
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean recentlyShown(long userId) {
        synchronized (lastPromptAt) {
            long now = System.currentTimeMillis();
            Long previous = lastPromptAt.get(userId);
            if (previous != null && now - previous < PROMPT_COOLDOWN_MS) {
                return true;
            }
            lastPromptAt.put(userId, now);
            return false;
        }
    }

    private static void detectJoinedChannels(LaunchActivity activity, int accountNum, long userId) {
        isJoined(accountNum, NOTIFICATION_CHANNEL, notificationJoined ->
                isJoined(accountNum, FEEDBACK_CHANNEL, feedbackJoined -> {
                    if (notificationJoined && feedbackJoined) {
                        markCompleted(userId);
                    } else if (!activity.isFinishing()) {
                        showPrompt(activity, accountNum, userId);
                    }
                }));
    }

    private static void showPrompt(LaunchActivity activity, int accountNum, long userId) {
        new AlertDialog.Builder(activity)
                .setTitle("黄昏频道")
                .setMessage("加入黄昏通知频道和黄昏反馈频道，可获取通知与反馈入口。")
                .setPositiveButton("一键添加", (dialog, which) -> joinBoth(activity, accountNum, userId, 0, true))
                .setNegativeButton("暂不添加", null)
                .show();
    }

    private static void joinBoth(LaunchActivity activity, int accountNum, long userId, int index, boolean allSucceeded) {
        if (index >= 2) {
            if (allSucceeded) {
                markCompleted(userId);
                Toast.makeText(activity, "已加入黄昏频道", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "部分频道未能加入，可稍后在关于页重试", Toast.LENGTH_LONG).show();
            }
            return;
        }
        String username = index == 0 ? NOTIFICATION_CHANNEL : FEEDBACK_CHANNEL;
        MessagesController controller = MessagesController.getInstance(accountNum);
        controller.getUserNameResolver().resolve(username, peerId -> {
            if (peerId == null || peerId >= 0) {
                joinBoth(activity, accountNum, userId, index + 1, false);
                return;
            }
            long chatId = -peerId;
            TLRPC.Chat chat = controller.getChat(chatId);
            if (chat == null) {
                joinBoth(activity, accountNum, userId, index + 1, false);
                return;
            }
            if (!chat.left && !chat.kicked) {
                joinBoth(activity, accountNum, userId, index + 1, allSucceeded);
                return;
            }
            controller.addUserToChat(chatId, UserConfig.getInstance(accountNum).getCurrentUser(), 0,
                    null, null, true,
                    () -> joinBoth(activity, accountNum, userId, index + 1, allSucceeded),
                    error -> {
                        joinBoth(activity, accountNum, userId, index + 1, false);
                        return false;
                    });
        });
    }

    private interface BooleanCallback {
        void run(boolean value);
    }

    private static void isJoined(int accountNum, String username, BooleanCallback callback) {
        MessagesController controller = MessagesController.getInstance(accountNum);
        controller.getUserNameResolver().resolve(username, peerId -> {
            if (peerId == null || peerId >= 0) {
                callback.run(false);
                return;
            }
            TLRPC.Chat chat = controller.getChat(-peerId);
            callback.run(chat != null && !chat.left && !chat.kicked);
        });
    }
}
