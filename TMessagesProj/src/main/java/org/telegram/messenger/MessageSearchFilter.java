package org.telegram.messenger;

import java.util.HashSet;
import java.util.Objects;

/** Client-side message filtering. Create a fresh instance for each result rebuild. */
public final class MessageSearchFilter {
    private final boolean deduplicate;
    private final boolean exact;
    private final HashSet<SenderKey> senders = new HashSet<>();

    public MessageSearchFilter(boolean deduplicate, boolean exact) {
        this.deduplicate = deduplicate;
        this.exact = exact;
    }

    public boolean accept(long dialogId, long senderId, int messageId, String text, String query) {
        // Compare the original text/caption, not highlighted or formatted display text.
        // An empty query is used by date/chat filters and must not hide all messages.
        if (exact && query != null && !query.isEmpty() && !query.equals(text)) {
            return false;
        }
        // Keep private-chat search unchanged. Group/channel identity is scoped to
        // its dialog; the same person in a different group remains a separate hit.
        if (deduplicate && dialogId < 0) {
            // Anonymous/channel posts without from_id belong to the visible channel,
            // not an inferred human identity or the original author of a forward.
            return senders.add(new SenderKey(dialogId, senderId == 0 ? dialogId : senderId));
        }
        return true;
    }

    private static final class SenderKey {
        private final long dialogId;
        private final long senderId;

        private SenderKey(long dialogId, long senderId) {
            this.dialogId = dialogId;
            this.senderId = senderId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SenderKey)) return false;
            SenderKey key = (SenderKey) other;
            return dialogId == key.dialogId && senderId == key.senderId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dialogId, senderId);
        }
    }
}
