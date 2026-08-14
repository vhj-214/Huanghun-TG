package com.Huanghun;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.util.HashSet;
import java.util.Set;

/** Keeps one local slot per Telegram user without revoking the user's Telegram devices. */
public final class HuanghunAccountDeduplicator {
    private HuanghunAccountDeduplicator() {
    }

    /**
     * Retains the currently selected account when duplicates exist, then clears only
     * redundant local slots for the same Telegram user ID.
     */
    public static void collapseDuplicateLocalAccounts(int preferredAccount) {
        Set<Long> retainedUserIds = new HashSet<>();
        if (preferredAccount >= 0 && preferredAccount < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(preferredAccount).isClientActivated()) {
            long userId = UserConfig.getInstance(preferredAccount).getClientUserId();
            if (userId != 0) {
                retainedUserIds.add(userId);
            }
        }
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (account == preferredAccount || !UserConfig.getInstance(account).isClientActivated()) {
                continue;
            }
            long userId = UserConfig.getInstance(account).getClientUserId();
            if (userId == 0) {
                continue;
            }
            if (retainedUserIds.add(userId)) {
                continue;
            }
            try {
                ConnectionsManager.getInstance(account).cleanup(true);
                MessagesController.getInstance(account).cleanup();
                UserConfig.getInstance(account).clearConfig();
            } catch (Throwable ignore) {
                // A duplicate slot is best-effort cleanup; never block app startup.
            }
        }
    }
}
