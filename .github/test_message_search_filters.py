#!/usr/bin/env python3
"""Run real message-search Java logic with minimal Android/network stubs.

Requires Python 3 and a JDK (javac/java). The helper is compiled unchanged;
selected adapter methods and state declarations are extracted unchanged from
production. Fake RPCs are delivered explicitly, exercising the real request,
response, rebuild, toggle and pagination code without Android or a Telegram
server. These are targeted regression tests, not an APK build or device UI test.
"""

from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/MessageSearchFilter.java"
ADAPTER = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java"


def java_block(source: str, marker: str) -> str:
    """Extract an actual Java declaration, ignoring braces in strings/comments."""
    start = source.index(marker)
    opening = source.index("{", start)
    depth = 0
    state = "code"
    index = opening
    while index < len(source):
        char = source[index]
        following = source[index:index + 2]
        if state == "line":
            if char == "\n":
                state = "code"
        elif state == "comment":
            if following == "*/":
                state = "code"
                index += 1
        elif state in ("string", "char"):
            if char == "\\":
                index += 1
            elif char == ('"' if state == "string" else "'"):
                state = "code"
        elif following == "//":
            state = "line"
            index += 1
        elif following == "/*":
            state = "comment"
            index += 1
        elif char == '"':
            state = "string"
        elif char == "'":
            state = "char"
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
        index += 1
    raise AssertionError(f"Unclosed Java declaration: {marker}")


def java_field(source: str, name: str) -> str:
    pattern = rf"^\s*private (?:final )?[^\n;]+\b{re.escape(name)}\b[^\n;]*;"
    matches = re.findall(pattern, source, flags=re.MULTILINE)
    if len(matches) != 1:
        raise AssertionError(f"Expected one adapter state declaration for {name}, got {len(matches)}")
    return matches[0].strip()


METHODS = (
    "public boolean isDeduplicateSearchEnabled()",
    "public boolean isExactSearchEnabled()",
    "public void setMessageSearchOptions(boolean deduplicate, boolean exact)",
    "private void clearGlobalMessageResults()",
    "private void clearForumMessageResults()",
    "private void rebuildMessageSearchResults()",
    "private void maybeLoadMoreFilteredMessages()",
    "public boolean isMessagesSearchEndReached()",
    "public void loadMoreSearchMessages()",
    "private void searchForumMessagesInternal(final String query, int searchId)",
    "private void searchMessagesInternal(final String query, int searchId)",
)
FIELDS = (
    "searchForumResultMessages", "searchResultMessages",
    "rawForumResultMessages", "rawResultMessages", "lastRawGlobalMessage",
    "rawForumOffset", "deduplicateSearchEnabled", "exactSearchEnabled",
    "messageSearchPageTarget", "currentMessagesQuery", "nextSearchRate",
)


HARNESS = r"""
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.telegram.messenger.MessageSearchFilter;

public class MessageSearchRegression {
    static final class TextUtils {
        static boolean isEmpty(CharSequence value) { return value == null || value.length() == 0; }
    }
    static class LongSparseArray<T> {
        final Map<Long,T> values = new HashMap<>();
        void put(long id, T value) { values.put(id, value); }
    }
    static final class LongIntArray {
        final Map<Long,Integer> values = new HashMap<>();
        int get(long id) { return values.getOrDefault(id, 0); }
        void put(long id, int value) { values.put(id, value); }
    }
    static final class Pair<A,B> {
        final A first; final B second;
        Pair(A first, B second) { this.first = first; this.second = second; }
    }
    static final class TLRPC {
        static class Peer { long user_id, chat_id, channel_id; }
        static class TL_inputPeerEmpty extends Peer {}
        static class TL_inputMessagesFilterEmpty {}
        static class Message {
            int id; Peer peer_id, from_id; String message;
            boolean out, unread;
        }
        static class Chat { long id; }
        static class User { long id; }
        static class messages_Messages {
            final ArrayList<Message> messages = new ArrayList<>();
            final ArrayList<Chat> chats = new ArrayList<>();
            final ArrayList<User> users = new ArrayList<>();
            int next_rate;
        }
        static class TL_messages_search {
            int limit, add_offset; String q; Object filter; Peer peer;
        }
        static class TL_messages_searchGlobal {
            boolean broadcasts_only, groups_only, users_only;
            int limit, flags, folder_id, offset_id, offset_rate;
            String q; Object filter; Peer offset_peer;
        }
    }
    static TLRPC.Peer peer(long id) {
        if (id == 0) return null;
        TLRPC.Peer peer = new TLRPC.Peer();
        if (id > 0) peer.user_id = id; else peer.channel_id = -id;
        return peer;
    }
    static final class MessageObject {
        final TLRPC.Message messageOwner;
        CharSequence messageText = "highlighted display text must not be used";
        MessageObject(TLRPC.Message message) { messageOwner = message; }
        MessageObject(int account, TLRPC.Message message, LongSparseArray<TLRPC.User> users,
                      LongSparseArray<TLRPC.Chat> chats, boolean first, boolean second) {
            this(message);
        }
        long getDialogId() { return getDialogId(messageOwner); }
        int getId() { return messageOwner.id; }
        static long getDialogId(TLRPC.Message message) { return getPeerId(message.peer_id); }
        static long getPeerId(TLRPC.Peer peer) {
            if (peer == null) return 0;
            if (peer.user_id != 0) return peer.user_id;
            if (peer.channel_id != 0) return -peer.channel_id;
            return -peer.chat_id;
        }
        void setQuery(String query) {}
    }
    static final class MessagesController {
        static final MessagesController INSTANCE = new MessagesController();
        final LongIntArray deletedHistory = new LongIntArray();
        final ConcurrentHashMap<Long,Integer> dialogs_read_outbox_max = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long,Integer> dialogs_read_inbox_max = new ConcurrentHashMap<>();
        static MessagesController getInstance(int account) { return INSTANCE; }
        TLRPC.Peer getInputPeer(long id) { return peer(id); }
        void putUsers(ArrayList<TLRPC.User> users, boolean cache) {}
        void putChats(ArrayList<TLRPC.Chat> chats, boolean cache) {}
    }
    static final class MessagesStorage {
        static final MessagesStorage INSTANCE = new MessagesStorage();
        static MessagesStorage getInstance(int account) { return INSTANCE; }
        void putUsersAndChats(ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats,
                              boolean first, boolean second) {}
        MessagesStorage getStorageQueue() { return this; }
        void postRunnable(Runnable runnable) { runnable.run(); }
        int getDialogReadMaxSync(boolean out, long did) { return 0; }
    }
    static final class AndroidUtilities {
        static void runOnUIThread(Runnable runnable) { runnable.run(); }
    }
    interface Callback { void run(Object response, Object error); }
    static final class Pending {
        final int id; final Object request; final Callback callback;
        Pending(int id, Object request, Callback callback) {
            this.id = id; this.request = request; this.callback = callback;
        }
    }
    static final class ConnectionsManager {
        static final int RequestFlagFailOnServerErrors = 1;
        static final ConnectionsManager INSTANCE = new ConnectionsManager();
        final ArrayList<Pending> pending = new ArrayList<>();
        final ArrayList<Object> sent = new ArrayList<>();
        int serial;
        static ConnectionsManager getInstance(int account) { return INSTANCE; }
        int sendRequest(Object request, Callback callback, int flags) {
            int id = ++serial;
            sent.add(request);
            pending.add(new Pending(id, request, callback));
            return id;
        }
        void cancelRequest(int id, boolean notify) { pending.removeIf(p -> p.id == id); }
        Object nextRequest() {
            check(pending.size() == 1, "expected exactly one pending RPC, got " + pending.size());
            return pending.get(0).request;
        }
        void deliver(TLRPC.messages_Messages response) {
            check(pending.size() == 1, "delivery requires exactly one pending RPC");
            pending.remove(0).callback.run(response, null);
        }
        void fail() { pending.remove(0).callback.run(null, new Object()); }
        void reset() { pending.clear(); sent.clear(); serial = 0; }
    }
    static final class DialogsActivity { static final int DIALOGS_TYPE_BOT_REQUEST_PEER = 99; }
    static final class SearchAdapterHelper {
        void clear() {}
        void mergeResults(ArrayList<Object> results, ArrayList<Object> recent) {}
    }
    static final class Delegate {
        long forumId; int stateNotifications;
        long getSearchForumDialogId() { return forumId; }
        void searchStateChanged(boolean searching, boolean animated) { stateNotifications++; }
        void runResultsEnterAnimation() {}
    }
    static final class EmptyLayout { void setQuery(String query) {} }
    static final class Adapter {
        __FIELDS__
        int currentAccount, reqId, reqForumId, lastReqId, lastForumReqId;
        int needMessagesSearch = 1, lastMessagesSearchId = 7, lastSearchId = 7;
        int lastLocalSearchId = 7, lastGlobalSearchId = 7;
        int lastMessagesSearchFilterFlags, dialogsType, folderId, waitingResponseCount;
        String lastMessagesSearchString = "你好", filteredRecentQuery;
        boolean messagesSearchEndReached = true, localMessagesSearchEndReached = true;
        boolean searchWas, forceLoadingMessages, globalSearchCollapsed, phoneCollapsed;
        final Delegate delegate = new Delegate();
        final SearchAdapterHelper searchAdapterHelper = new SearchAdapterHelper();
        final ArrayList<Object> searchResult = new ArrayList<>();
        final ArrayList<Object> filtered2RecentSearchObjects = new ArrayList<>();
        static final class Filter { int flags; }
        final Filter currentMessagesFilter = new Filter();
        EmptyLayout messagesEmptyLayout;
        int notifications;
        void notifyDataSetChanged() { notifications++; }
        void filterRecent(String query) {}
        __METHODS__
    }
    static Adapter adapter() {
        ConnectionsManager.INSTANCE.reset();
        MessagesController.INSTANCE.deletedHistory.values.clear();
        MessagesController.INSTANCE.dialogs_read_inbox_max.clear();
        MessagesController.INSTANCE.dialogs_read_outbox_max.clear();
        Adapter adapter = new Adapter();
        adapter.currentMessagesQuery = "你好";
        return adapter;
    }
    static MessageObject message(long dialog, long sender, int id, String text) {
        TLRPC.Message message = new TLRPC.Message();
        message.peer_id = peer(dialog); message.from_id = peer(sender);
        message.id = id; message.message = text;
        return new MessageObject(message);
    }
    static TLRPC.messages_Messages page(long dialog, long sender, int start, int count,
                                       String text, int rate) {
        TLRPC.messages_Messages page = new TLRPC.messages_Messages();
        page.next_rate = rate;
        for (int i = 0; i < count; i++) page.messages.add(message(dialog, sender, start + i, text).messageOwner);
        return page;
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void ids(List<MessageObject> actual, int... expected) {
        check(actual.size() == expected.length, "wrong visible count: " + actual.size() + ", expected " + expected.length);
        for (int i = 0; i < expected.length; i++)
            check(actual.get(i).getId() == expected[i], "wrong order/message at " + i);
    }
    static void exactChinese() {
        MessageSearchFilter filter = new MessageSearchFilter(false, true);
        check(filter.accept(-10, 1, 1, "你好", "你好"), "literal Chinese match rejected");
        for (String text : new String[]{"你好啊", "啊你好", "你好\n", "\n你好", "你\n好",
                "你好\r\n", "你好 ", " 你好", "你 好", "你好！", "你好!", "你好。", "你好\t", "你好\u00a0"})
            check(!filter.accept(-10, 1, 2, text, "你好"), "non-exact Chinese text accepted: [" + text + "]");
    }
    static void exactCase() {
        MessageSearchFilter filter = new MessageSearchFilter(false, true);
        check(filter.accept(-10, 1, 1, "Hello", "Hello"), "literal Latin match rejected");
        for (String text : new String[]{"hello", "HELLO", "Hello!", " Hello", "Hello\n"})
            check(!filter.accept(-10, 1, 2, text, "Hello"), "case/format normalization accepted");
    }
    static void nullText() {
        check(!new MessageSearchFilter(false, true).accept(-10, 1, 1, null, "你好"), "null text passed exact filter");
        check(new MessageSearchFilter(false, false).accept(-10, 1, 1, null, "你好"), "null text rejected with exact off");
    }
    static void emptyQuery() {
        for (String query : new String[]{null, ""}) {
            MessageSearchFilter filter = new MessageSearchFilter(false, true);
            check(filter.accept(-10, 1, 1, "unrelated", query), "empty query must bypass exact matching");
            check(filter.accept(-10, 1, 2, null, query), "empty query must allow null text");
            MessageSearchFilter dedup = new MessageSearchFilter(true, true);
            check(dedup.accept(-10, 1, 1, "a", query), "empty query first sender rejected");
            check(!dedup.accept(-10, 1, 2, "b", query), "empty query must not disable deduplication");
        }
        check(!new MessageSearchFilter(false, true).accept(-10, 1, 1, "你好", " "), "whitespace query must not be trimmed to empty");
    }
    static void senderScope() {
        MessageSearchFilter filter = new MessageSearchFilter(true, false);
        check(filter.accept(-10, 1, 100, "a", "你好"), "first sender rejected");
        check(!filter.accept(-10, 1, 99, "b", "你好"), "same group/sender retained");
        check(filter.accept(-20, 1, 98, "c", "你好"), "same sender in different group rejected");
        check(filter.accept(-10, 2, 97, "d", "你好"), "different sender rejected");
        check(!filter.accept(-20, 1, 96, "e", "你好"), "second hit in other group retained");
    }
    static void privateChats() {
        MessageSearchFilter filter = new MessageSearchFilter(true, false);
        for (int id = 0; id < 4; id++)
            check(filter.accept(10, 1, id, "text", "你好"), "private chat was deduplicated");
        check(filter.accept(20, 1, 5, "text", "你好"), "other private dialog rejected");
        check(filter.accept(-10, 1, 6, "text", "你好"), "private hit consumed group sender");
    }
    static void anonymousChannel() {
        MessageSearchFilter filter = new MessageSearchFilter(true, false);
        check(filter.accept(-10, 0, 1, "post", "你好"), "first anonymous post rejected");
        check(!filter.accept(-10, 0, 2, "post", "你好"), "same channel anonymous sender retained");
        check(filter.accept(-20, 0, 3, "post", "你好"), "anonymous sender leaked across dialogs");
        check(!filter.accept(-10, -10, 4, "post", "你好"), "sender zero must fall back to dialog identity");
        check(filter.accept(-10, 1, 5, "post", "你好"), "actual human sender collapsed with anonymous post");
        Adapter adapter = adapter();
        adapter.rawResultMessages.add(message(-10, 0, 1, "你好"));
        adapter.rawResultMessages.add(message(-10, 0, 2, "你好"));
        adapter.rawResultMessages.add(message(-20, 0, 3, "你好"));
        adapter.rebuildMessageSearchResults();
        ids(adapter.searchResultMessages, 1, 3); // null from_id exercises the real adapter call.
    }
    static void exactBeforeDedup() {
        MessageSearchFilter filter = new MessageSearchFilter(true, true);
        check(!filter.accept(-10, 1, 1, "你好啊", "你好"), "fuzzy hit passed exact filter");
        check(filter.accept(-10, 1, 2, "你好", "你好"), "fuzzy hit consumed sender identity");
        check(!filter.accept(-10, 1, 3, "你好", "你好"), "second exact hit retained");
    }
    static void bothOff() {
        MessageSearchFilter filter = new MessageSearchFilter(false, false);
        for (String text : new String[]{"你好", "你好啊", "prefix你好", null, ""})
            check(filter.accept(-10, 1, 1, text, "你好"), "disabled switches changed results");
    }
    static void rebuildAndToggles() {
        Adapter adapter = adapter();
        adapter.rawForumResultMessages.add(message(-10, 1, 1, "你好啊"));
        adapter.rawForumResultMessages.add(message(-10, 1, 2, "你好"));
        adapter.rawResultMessages.add(message(-10, 1, 3, "你好"));
        adapter.rawResultMessages.add(message(-20, 1, 4, "你好"));
        adapter.rawResultMessages.add(message(-10, 2, 5, "你好"));
        adapter.rawResultMessages.add(message(10, 1, 6, "你好啊"));
        adapter.rawResultMessages.add(message(10, 1, 7, "你好"));
        adapter.rebuildMessageSearchResults();
        ids(adapter.searchForumResultMessages, 1);
        ids(adapter.searchResultMessages, 4, 5, 6, 7);
        adapter.setMessageSearchOptions(true, true);
        ids(adapter.searchForumResultMessages, 2); // fuzzy 1 must not consume sender.
        ids(adapter.searchResultMessages, 4, 5, 7);
        adapter.setMessageSearchOptions(false, true);
        ids(adapter.searchForumResultMessages, 2);
        ids(adapter.searchResultMessages, 3, 4, 5, 7);
        adapter.setMessageSearchOptions(false, false);
        ids(adapter.searchForumResultMessages, 1, 2);
        ids(adapter.searchResultMessages, 3, 4, 5, 6, 7);
        adapter.setMessageSearchOptions(true, false);
        ids(adapter.searchForumResultMessages, 1);
        ids(adapter.searchResultMessages, 4, 5, 6, 7);
        check(adapter.rawForumResultMessages.size() == 2 && adapter.rawResultMessages.size() == 5,
                "toggle destroyed loaded raw results");
        check(ConnectionsManager.INSTANCE.sent.isEmpty(), "completed loaded results unnecessarily fetched again");
        int notifications = adapter.notifications;
        adapter.setMessageSearchOptions(true, false);
        check(adapter.notifications == notifications, "unchanged options should be a no-op");
    }
    static void clearState() {
        Adapter adapter = adapter();
        MessageObject global = message(-10, 1, 1, "你好");
        MessageObject forum = message(-20, 1, 2, "你好");
        adapter.rawResultMessages.add(global); adapter.rawForumResultMessages.add(forum);
        adapter.lastRawGlobalMessage = global.messageOwner; adapter.nextSearchRate = 99; adapter.rawForumOffset = 40;
        adapter.rebuildMessageSearchResults(); adapter.clearGlobalMessageResults();
        ids(adapter.searchResultMessages); ids(adapter.searchForumResultMessages, 2);
        check(adapter.rawResultMessages.isEmpty() && adapter.lastRawGlobalMessage == null && adapter.nextSearchRate == 0,
                "global clear did not clear raw results and cursor");
        check(adapter.rawForumOffset == 40 && adapter.rawForumResultMessages.size() == 1, "global clear modified forum state");
        adapter.rawResultMessages.add(global); adapter.lastRawGlobalMessage = global.messageOwner; adapter.nextSearchRate = 99;
        adapter.rebuildMessageSearchResults(); adapter.clearForumMessageResults();
        ids(adapter.searchForumResultMessages); ids(adapter.searchResultMessages, 1);
        check(adapter.rawForumResultMessages.isEmpty() && adapter.rawForumOffset == 0, "forum clear left raw cursor/results");
        check(adapter.lastRawGlobalMessage == global.messageOwner && adapter.nextSearchRate == 99, "forum clear modified global cursor");
    }
    static void globalExactPagination() {
        Adapter adapter = adapter(); adapter.exactSearchEnabled = true; adapter.messagesSearchEndReached = false;
        ConnectionsManager network = ConnectionsManager.INSTANCE;
        adapter.loadMoreSearchMessages();
        TLRPC.TL_messages_searchGlobal first = (TLRPC.TL_messages_searchGlobal) network.nextRequest();
        check(first.offset_id == 0 && first.offset_rate == 0 && first.limit == 20, "wrong initial global request");
        TLRPC.messages_Messages fuzzy = page(-10, 1, 100, 20, "你好啊", 77);
        TLRPC.Message deletedTail = message(-20, 1, 119, "你好啊").messageOwner;
        fuzzy.messages.set(19, deletedTail);
        MessagesController.INSTANCE.deletedHistory.put(-20, 119);
        network.deliver(fuzzy);
        ids(adapter.searchResultMessages);
        check(adapter.rawResultMessages.size() == 19, "deleted raw item should not become loaded result");
        check(adapter.lastRawGlobalMessage == deletedTail, "cursor must include raw tail even when deleted/filtered");
        check(!adapter.messagesSearchEndReached, "full filtered page was treated as end");
        TLRPC.TL_messages_searchGlobal next = (TLRPC.TL_messages_searchGlobal) network.nextRequest();
        check(next.offset_id == 119 && next.offset_rate == 77 && MessageObject.getPeerId(next.offset_peer) == -20,
                "pagination used visible result instead of raw server cursor");
        TLRPC.messages_Messages exact = page(-10, 1, 200, 1, "你好", 88);
        exact.messages.add(message(-10, 2, 201, "你好").messageOwner);
        network.deliver(exact);
        ids(adapter.searchResultMessages, 200, 201);
        check(adapter.messagesSearchEndReached && network.pending.isEmpty() && adapter.waitingResponseCount == 0,
                "short page did not terminate autoload cleanly");
        adapter.setMessageSearchOptions(false, false);
        check(adapter.searchResultMessages.size() == 21, "switches off did not restore all loaded, undeleted hits");
    }
    static void globalDedupPagination() {
        Adapter adapter = adapter(); adapter.messagesSearchEndReached = false;
        ConnectionsManager network = ConnectionsManager.INSTANCE;
        adapter.loadMoreSearchMessages(); network.deliver(page(-10, 1, 100, 20, "你好", 10));
        ids(adapter.searchResultMessages, 100);
        network.deliver(page(-10, 1, 200, 20, "你好", 20));
        ids(adapter.searchResultMessages, 100);
        TLRPC.TL_messages_searchGlobal third = (TLRPC.TL_messages_searchGlobal) network.nextRequest();
        check(third.offset_id == 219 && third.offset_rate == 20, "entirely deduplicated page did not advance raw cursor");
        network.deliver(page(-10, 2, 300, 1, "你好", 30));
        ids(adapter.searchResultMessages, 100, 300);
        check(adapter.rawResultMessages.size() == 41 && network.sent.size() == 3 && network.pending.isEmpty(),
                "dedup autoload lost raw pages or failed to stop");
    }
    static void forumPagination() {
        Adapter adapter = adapter(); adapter.delegate.forumId = -10;
        adapter.exactSearchEnabled = true; adapter.localMessagesSearchEndReached = false;
        ConnectionsManager network = ConnectionsManager.INSTANCE;
        adapter.loadMoreSearchMessages();
        TLRPC.TL_messages_search first = (TLRPC.TL_messages_search) network.nextRequest();
        check(first.add_offset == 0 && first.limit == 20, "wrong initial forum request");
        TLRPC.messages_Messages fuzzy = page(-10, 1, 100, 20, "你好啊", 0);
        fuzzy.messages.set(19, message(-10, 1, 50, "你好啊").messageOwner);
        MessagesController.INSTANCE.deletedHistory.put(-10, 50);
        network.deliver(fuzzy);
        ids(adapter.searchForumResultMessages);
        check(adapter.rawForumResultMessages.size() == 19 && adapter.rawForumOffset == 20,
                "forum offset must count all server messages, not visible/undeleted items");
        check(!adapter.localMessagesSearchEndReached, "empty visible forum page was treated as end");
        TLRPC.TL_messages_search next = (TLRPC.TL_messages_search) network.nextRequest();
        check(next.add_offset == 20, "filtered forum page did not autoload using raw offset");
        network.deliver(page(-10, 1, 200, 1, "你好", 0));
        ids(adapter.searchForumResultMessages, 200);
        check(adapter.rawForumOffset == 21 && adapter.localMessagesSearchEndReached && network.pending.isEmpty(),
                "forum short page did not advance and stop");
        adapter.setMessageSearchOptions(false, false);
        check(adapter.searchForumResultMessages.size() == 20, "forum toggle did not restore loaded fuzzy hits");
    }
    static void disabledPaginationAndTarget() {
        Adapter adapter = adapter(); adapter.deduplicateSearchEnabled = false; adapter.messagesSearchEndReached = false;
        ConnectionsManager network = ConnectionsManager.INSTANCE;
        adapter.loadMoreSearchMessages(); network.deliver(page(-10, 1, 100, 20, "你好啊", 10));
        check(adapter.searchResultMessages.size() == 20 && network.pending.isEmpty(), "switches off filtered or autoloaded a full page");
        adapter.loadMoreSearchMessages();
        check(adapter.messageSearchPageTarget == 40, "explicit load-more should increase a reached visible target by 20");
        TLRPC.TL_messages_searchGlobal next = (TLRPC.TL_messages_searchGlobal) network.nextRequest();
        check(next.offset_id == 119, "manual pagination lost raw cursor");
        network.deliver(page(-10, 1, 200, 1, "你好啊", 20));
        check(adapter.searchResultMessages.size() == 21, "switches off must retain same-sender pages");
    }
    static void toggleAutoload() {
        Adapter adapter = adapter(); adapter.deduplicateSearchEnabled = false; adapter.messagesSearchEndReached = false;
        ConnectionsManager network = ConnectionsManager.INSTANCE;
        adapter.loadMoreSearchMessages(); network.deliver(page(-10, 1, 100, 20, "你好", 10));
        check(network.pending.isEmpty(), "unexpected initial autoload");
        adapter.messageSearchPageTarget = 80;
        adapter.setMessageSearchOptions(true, false);
        ids(adapter.searchResultMessages, 100);
        check(adapter.messageSearchPageTarget == 20 && network.pending.size() == 1, "toggle must rebuild, reset target and fill filtered page");
        TLRPC.TL_messages_searchGlobal next = (TLRPC.TL_messages_searchGlobal) network.nextRequest();
        check(next.offset_id == 119 && next.offset_rate == 10, "toggle restarted instead of continuing raw pages");
        network.deliver(page(-10, 2, 200, 1, "你好", 20));
        ids(adapter.searchResultMessages, 100, 200);
    }
    static void autoloadGates() {
        for (int gate = 0; gate < 8; gate++) {
            Adapter adapter = adapter(); adapter.messagesSearchEndReached = false;
            switch (gate) {
                case 0: adapter.needMessagesSearch = 0; break;
                case 1: adapter.deduplicateSearchEnabled = false; adapter.exactSearchEnabled = false; break;
                case 2: adapter.lastMessagesSearchString = ""; break;
                case 3: adapter.lastMessagesSearchString = null; break;
                case 4: adapter.lastMessagesSearchId--; break;
                case 5: adapter.messagesSearchEndReached = true; break;
                case 6: adapter.reqId = 123; break;
                case 7:
                    for (int i = 0; i < 10; i++) {
                        adapter.searchForumResultMessages.add(message(-10, i + 1, i, "你好"));
                        adapter.searchResultMessages.add(message(-20, i + 1, i + 10, "你好"));
                    }
                    break;
            }
            adapter.maybeLoadMoreFilteredMessages();
            check(ConnectionsManager.INSTANCE.sent.isEmpty(), "autoload gate failed: " + gate);
        }
        Adapter adapter = adapter(); adapter.messagesSearchEndReached = false; adapter.reqForumId = 456;
        adapter.loadMoreSearchMessages();
        check(ConnectionsManager.INSTANCE.sent.isEmpty(), "in-flight forum request did not block load-more");
        adapter = adapter(); adapter.messagesSearchEndReached = false;
        adapter.maybeLoadMoreFilteredMessages();
        check(ConnectionsManager.INSTANCE.pending.size() == 1, "valid filtered empty result failed to autoload");
    }
    static void repeatedCursorStops() {
        Adapter adapter = adapter(); adapter.exactSearchEnabled = true; adapter.messagesSearchEndReached = false;
        ConnectionsManager network = ConnectionsManager.INSTANCE;
        adapter.loadMoreSearchMessages(); network.deliver(page(-10, 1, 100, 20, "你好啊", 10));
        network.deliver(page(-10, 1, 100, 20, "你好啊", 10));
        check(adapter.messagesSearchEndReached && network.pending.isEmpty() && network.sent.size() == 2,
                "unchanged raw cursor/rate must stop rather than loop on filtered pages");
    }
    static void rpcErrorsStop() {
        for (boolean forum : new boolean[]{false, true}) {
            Adapter adapter = adapter(); adapter.messagesSearchEndReached = false;
            if (forum) { adapter.delegate.forumId = -10; adapter.localMessagesSearchEndReached = false; }
            adapter.loadMoreSearchMessages(); ConnectionsManager.INSTANCE.fail();
            check(adapter.reqId == 0 && adapter.reqForumId == 0 && adapter.waitingResponseCount == 0,
                    "RPC error did not release request/loading state");
            check(ConnectionsManager.INSTANCE.sent.size() == 1 && ConnectionsManager.INSTANCE.pending.isEmpty(),
                    "RPC error incorrectly triggered filtered-page retry");
        }
    }
    public static void main(String[] args) {
        switch (args[0]) {
            case "exact_chinese": exactChinese(); break;
            case "exact_case": exactCase(); break;
            case "null_text": nullText(); break;
            case "empty_query": emptyQuery(); break;
            case "sender_scope": senderScope(); break;
            case "private_chats": privateChats(); break;
            case "anonymous_channel": anonymousChannel(); break;
            case "exact_before_dedup": exactBeforeDedup(); break;
            case "both_off": bothOff(); break;
            case "rebuild_toggles": rebuildAndToggles(); break;
            case "clear_state": clearState(); break;
            case "global_exact_pagination": globalExactPagination(); break;
            case "global_dedup_pagination": globalDedupPagination(); break;
            case "forum_pagination": forumPagination(); break;
            case "disabled_pagination_target": disabledPaginationAndTarget(); break;
            case "toggle_autoload": toggleAutoload(); break;
            case "autoload_gates": autoloadGates(); break;
            case "repeated_cursor_stops": repeatedCursorStops(); break;
            case "rpc_errors_stop": rpcErrorsStop(); break;
            default: throw new AssertionError("Unknown scenario: " + args[0]);
        }
        System.out.println("PASS: " + args[0]);
    }
}
"""


class MessageSearchFilterTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        for command in ("javac", "java"):
            if shutil.which(command) is None:
                raise RuntimeError(f"{command} is required; install a JDK to run the Java regression tests")
        cls.source = ADAPTER.read_text(encoding="utf-8")
        directory = tempfile.TemporaryDirectory(prefix="message-search-test-")
        cls.addClassCleanup(directory.cleanup)
        cls.directory = Path(directory.name)
        helper = cls.directory / "org/telegram/messenger/MessageSearchFilter.java"
        helper.parent.mkdir(parents=True)
        shutil.copyfile(HELPER, helper)  # Compile the actual helper without rewriting it.
        fields = "\n".join(java_field(cls.source, name) for name in FIELDS)
        methods = "\n".join(java_block(cls.source, marker) for marker in METHODS)
        harness = cls.directory / "MessageSearchRegression.java"
        harness.write_text(HARNESS.replace("__FIELDS__", fields).replace("__METHODS__", methods), encoding="utf-8")
        result = subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", str(cls.directory), str(helper), str(harness)],
            capture_output=True, text=True, timeout=60,
        )
        if result.returncode:
            raise AssertionError(f"Production Java extraction failed to compile:\n{result.stdout}\n{result.stderr}")

    def scenario(self, name):
        result = subprocess.run(
            ["java", "-cp", str(self.directory), "MessageSearchRegression", name],
            capture_output=True, text=True, timeout=20,
        )
        self.assertEqual(result.returncode, 0, f"Java regression {name} failed:\n{result.stdout}\n{result.stderr}")
        self.assertIn(f"PASS: {name}", result.stdout)

    def test_defaults_are_deduplicate_on_exact_off(self):
        self.assertRegex(self.source, r"private boolean deduplicateSearchEnabled\s*=\s*true\s*;")
        # An omitted boolean initializer is Java's false default.
        self.assertRegex(self.source, r"private boolean exactSearchEnabled\s*(?:=\s*false\s*)?;")

    def test_exact_chinese_rejects_prefix_suffix_whitespace_newlines_punctuation(self):
        self.scenario("exact_chinese")

    def test_exact_is_case_sensitive_without_normalization(self):
        self.scenario("exact_case")

    def test_null_text(self):
        self.scenario("null_text")

    def test_null_or_empty_query_bypasses_exact_but_not_dedup(self):
        self.scenario("empty_query")

    def test_dedup_is_actual_sender_scoped_to_group_and_first_hit(self):
        self.scenario("sender_scope")

    def test_private_chats_are_not_deduplicated(self):
        self.scenario("private_chats")

    def test_anonymous_channel_sender_zero_falls_back_to_dialog(self):
        self.scenario("anonymous_channel")

    def test_exact_runs_before_sender_is_consumed(self):
        self.scenario("exact_before_dedup")

    def test_both_switches_off_retains_every_hit(self):
        self.scenario("both_off")

    def test_actual_adapter_rebuild_and_toggles_restore_loaded_raw_results(self):
        self.scenario("rebuild_toggles")

    def test_actual_clear_methods_reset_only_their_own_raw_cursor_and_results(self):
        self.scenario("clear_state")

    def test_global_exact_filtered_page_autoloads_from_even_deleted_raw_tail(self):
        self.scenario("global_exact_pagination")

    def test_global_dedup_entirely_hidden_page_advances_cursor_and_autoloads(self):
        self.scenario("global_dedup_pagination")

    def test_forum_filtered_page_autoloads_from_server_count_not_visible_count(self):
        self.scenario("forum_pagination")

    def test_disabled_filters_do_not_autoload_and_manual_load_increases_target(self):
        self.scenario("disabled_pagination_target")

    def test_toggle_rebuild_resets_target_and_autoloads_from_existing_raw_cursor(self):
        self.scenario("toggle_autoload")

    def test_autoload_and_load_more_respect_query_generation_loading_and_end_gates(self):
        self.scenario("autoload_gates")

    def test_repeated_raw_cursor_and_rate_stop_filtered_page_loop(self):
        self.scenario("repeated_cursor_stops")

    def test_rpc_errors_release_loading_without_auto_retry(self):
        self.scenario("rpc_errors_stop")


if __name__ == "__main__":
    unittest.main(verbosity=2)
