package com.Huanghun.protocol.maker;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannedString;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Shares only native maker results. Never expose a path-taking JavaScript bridge. */
public final class ProtocolShareHelper {
    private ProtocolShareHelper() {
    }

    /** Returns whether a native recipient picker was presented, not whether messages were sent. */
    public static boolean share(BaseFragment fragment, List<File> files) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        BaseFragment host = resolveHost(fragment, activity);
        return open(host, fragment == null ? host : fragment, files);
    }

    /** Context must belong to the same live Activity as the native navigation host. */
    public static boolean share(Context context, List<File> files) {
        Activity activity = AndroidUtilities.findActivity(context);
        if (!live(activity)) {
            return false;
        }
        BaseFragment host = resolveHost(null, activity);
        return open(host, host, files);
    }

    private static boolean live(Activity activity) {
        return activity != null && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    private static boolean live(BaseFragment fragment, Activity activity) {
        return fragment != null && !fragment.isFinished && fragment.getParentLayout() != null
                && live(fragment.getParentActivity())
                && (activity == null || fragment.getParentActivity() == activity);
    }

    private static BaseFragment resolveHost(BaseFragment preferred, Activity activity) {
        if (live(preferred, activity)) {
            return preferred;
        }
        BaseFragment host = LaunchActivity.getLastFragmentIncludeMainTabs();
        if (live(host, activity)) {
            return host;
        }
        host = LaunchActivity.getLastFragment();
        return live(host, activity) ? host : null;
    }

    private static boolean activated(int account) {
        return account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(account).isClientActivated();
    }

    private static int account(BaseFragment preferred) {
        if (preferred != null && activated(preferred.getCurrentAccount())) {
            return preferred.getCurrentAccount();
        }
        if (activated(UserConfig.selectedAccount)) {
            return UserConfig.selectedAccount;
        }
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (activated(i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean open(BaseFragment host, BaseFragment preferred, List<File> files) {
        if (!live(host, null)) {
            return false;
        }
        int account = account(preferred);
        if (account < 0) {
            alert(host, "请先登录", "请先在客户端登录 Telegram 账号，再选择用户、群组或频道分享协议压缩包。");
            return false;
        }
        final ArrayList<Archive> archives = new ArrayList<>();
        try {
            if (files == null || files.isEmpty()) {
                throw new IOException("没有可分享的生成结果");
            }
            HashSet<String> paths = new HashSet<>();
            // Copy the list and canonical files now; never retain a caller-owned mutable list.
            for (File file : new ArrayList<>(files)) {
                Archive archive = new Archive(file);
                if (!paths.add(archive.file.getPath())) {
                    throw new IOException("生成结果包含重复文件");
                }
                archives.add(archive);
            }
        } catch (Exception e) {
            FileLog.e(e);
            alert(host, "无法分享", "生成的 ZIP 文件不存在、不可读取或为空。请重新生成后再分享。");
            return false;
        }
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("checkCanWrite", true);
        args.putBoolean("allowGlobalSearch", true);
        args.putBoolean("allowSwitchAccount", true);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setCurrentAccount(account);
        Session session = new Session(picker, host.getParentActivity(), archives);
        picker.setDelegate((selectedPicker, dids, message, param, notify, scheduleDate,
                            scheduleRepeatPeriod, topicsFragment) -> {
            session.select(selectedPicker, dids, message, notify, scheduleDate, scheduleRepeatPeriod);
            // Returning true may clear the delegate. Cancel must leave this picker usable.
            return false;
        });
        return host.presentFragment(picker);
    }

    private static void alert(BaseFragment fragment, String title, String message) {
        if (live(fragment, null)) {
            fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(title).setMessage(message).setPositiveButton("确定", null).create());
        }
    }

    private static final class Archive {
        final File file;
        final long size;
        final long modified;

        Archive(File source) throws IOException {
            if (source == null) {
                throw new IOException("Missing generated ZIP");
            }
            file = source.getCanonicalFile();
            size = file.length();
            modified = file.lastModified();
            validate();
        }

        void validate() throws IOException {
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".zip") || !file.isFile()
                    || !file.canRead() || size <= 0 || file.length() != size
                    || file.lastModified() != modified || !file.equals(file.getCanonicalFile())) {
                throw new IOException("Generated ZIP is missing, unreadable or changed: " + file.getName());
            }
        }
    }

    private static final class Request {
        final int account;
        final ArrayList<MessagesStorage.TopicKey> peers;
        final CharSequence caption;
        final boolean notify;
        final int scheduleDate;
        final int repeat;
        final long[] stars;
        boolean confirmed;
        boolean queueStarted;

        Request(int account, ArrayList<MessagesStorage.TopicKey> peers, CharSequence caption,
                boolean notify, int scheduleDate, int repeat) {
            this.account = account;
            this.peers = peers;
            this.caption = caption == null ? null : new SpannedString(caption);
            this.notify = notify;
            this.scheduleDate = scheduleDate;
            this.repeat = repeat;
            stars = new long[peers.size()];
        }
    }

    private static final class Session {
        DialogsActivity picker;
        final Activity activity;
        final ArrayList<Archive> archives;
        Request pending;
        boolean busy;
        boolean queued;

        Session(DialogsActivity picker, Activity activity, ArrayList<Archive> archives) {
            this.picker = picker;
            this.activity = activity;
            this.archives = archives;
        }

        boolean usable(Request request) {
            return pending == request && !queued && live(picker, activity)
                    && picker.getCurrentAccount() == request.account && activated(request.account);
        }

        void select(DialogsActivity selectedPicker, ArrayList<MessagesStorage.TopicKey> dids,
                    CharSequence message, boolean notify, int scheduleDate, int repeat) {
            if (queued || !live(selectedPicker, activity)) {
                return;
            }
            // DialogsActivity.openAccountSelector creates a NEW picker carrying this
            // delegate. Bind to that live, same-Activity picker, never the old account.
            if (selectedPicker != picker) {
                pending = null;
                busy = false;
                picker = selectedPicker;
            }
            if (busy) {
                return;
            }
            // A fresh selection invalidates any cancelled native Stars-confirmation callback.
            pending = null;
            if (dids == null || dids.isEmpty()) {
                return;
            }
            int account = picker.getCurrentAccount();
            if (!activated(account)) {
                alert(picker, "请先登录", "当前选择的账号已退出登录，请重新登录后分享。");
                return;
            }
            ArrayList<MessagesStorage.TopicKey> peers = new ArrayList<>();
            HashSet<MessagesStorage.TopicKey> seen = new HashSet<>();
            for (MessagesStorage.TopicKey key : new ArrayList<>(dids)) {
                if (key == null || key.dialogId == 0 || key.topicId < 0) {
                    alert(picker, "无法分享", "接收方无效，请重新选择。");
                    return;
                }
                MessagesStorage.TopicKey copy = MessagesStorage.TopicKey.of(key.dialogId, key.topicId);
                if (seen.add(copy)) {
                    peers.add(copy);
                }
            }
            Request request = new Request(account, peers, message, notify, scheduleDate, repeat);
            pending = request;
            try {
                validate(request);
                StringBuilder warning = new StringBuilder("协议 / Passkey 压缩包包含登录授权信息、认证密钥或私钥。接收方可能使用这些文件登录并控制账号。请仅分享给完全信任的接收方。\n\n文件：\n");
                for (Archive archive : archives) {
                    warning.append(archive.file.getName()).append('\n');
                }
                warning.append("\n接收方（").append(peers.size()).append("）：\n");
                AccountInstance instance = AccountInstance.getInstance(account);
                for (MessagesStorage.TopicKey peer : peers) {
                    String name = DialogObject.getDialogTitle(instance.getMessagesController().getUserOrChat(peer.dialogId));
                    warning.append(name == null || name.isEmpty() ? "聊天 " + peer.dialogId : name);
                    if (peer.topicId != 0) {
                        warning.append(" / ").append(topic(instance, peer).title);
                    }
                    warning.append('\n');
                }
                warning.append("\n确认后才会发送。如接收方要求付费，将按客户端原有 Stars 确认规则处理。");
                busy = true;
                AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("确认分享敏感协议文件")
                        .setMessage(warning.toString())
                        .setNegativeButton("取消", (d, which) -> cancel(request))
                        .setPositiveButton("确认分享", (d, which) -> {
                            if (!usable(request) || request.confirmed) {
                                return;
                            }
                            request.confirmed = true;
                            // Dismiss our warning first; native payment UI may open another dialog.
                            d.dismiss();
                            approve(request, 0);
                        }).create();
                if (picker.showDialog(dialog, d -> {
                    if (!request.confirmed) {
                        cancel(request);
                    }
                }) == null) {
                    cancel(request);
                }
            } catch (Exception e) {
                fail(request, e);
            }
        }

        void cancel(Request request) {
            if (pending == request && !request.confirmed) {
                pending = null;
                busy = false;
            }
        }

        void validate(Request request) throws IOException {
            if (!usable(request)) {
                throw new IOException("分享页面或账号已改变，请重新选择接收方。");
            }
            for (Archive archive : archives) {
                archive.validate();
            }
            AccountInstance instance = AccountInstance.getInstance(request.account);
            for (MessagesStorage.TopicKey peer : request.peers) {
                topic(instance, peer);
            }
        }

        TLRPC.TL_forumTopic topic(AccountInstance instance, MessagesStorage.TopicKey peer) throws IOException {
            if (peer.topicId == 0) {
                return null;
            }
            TLRPC.TL_forumTopic topic = instance.getMessagesController().getTopicsController()
                    .findTopic(-peer.dialogId, peer.topicId);
            if (topic == null || topic.topicStartMessage == null) {
                throw new IOException("所选话题不可用，请重新选择。不会改发到群组主聊天。");
            }
            return topic;
        }

        void approve(Request request, int index) {
            if (!usable(request) || !request.confirmed || request.queueStarted) {
                busy = false;
                return;
            }
            try {
                validate(request);
                if (index == request.peers.size()) {
                    enqueue(request);
                    return;
                }
                MessagesStorage.TopicKey peer = request.peers.get(index);
                // Ask once for all archives going to this dialog (possibly multiple topics).
                int first = -1;
                int copies = 0;
                for (int i = 0; i < request.peers.size(); i++) {
                    if (request.peers.get(i).dialogId == peer.dialogId) {
                        if (first < 0) first = i;
                        copies++;
                    }
                }
                if (first < index) {
                    request.stars[index] = request.stars[first];
                    approve(request, index + 1);
                    return;
                }
                boolean waiting = AlertsCreator.ensurePaidMessageConfirmation(request.account,
                        peer.dialogId, archives.size() * copies, price -> {
                            if (usable(request) && !request.queueStarted) {
                                busy = true;
                                request.stars[index] = price;
                                approve(request, index + 1);
                            }
                        });
                // Native payment cancellation has no callback. Permit a fresh selection on
                // dismissal; its new Request invalidates all old callbacks, preventing retries
                // from sending the old selection or double-queueing the same request.
                if (waiting && usable(request) && !request.queueStarted) {
                    busy = false;
                }
            } catch (Exception e) {
                fail(request, e);
            }
        }

        void enqueue(Request request) throws IOException {
            // All peers, topics, files and payment approvals pass before ANY document is queued.
            validate(request);
            ArrayList<MessageObject> replies = new ArrayList<>();
            AccountInstance instance = AccountInstance.getInstance(picker.getCurrentAccount());
            for (MessagesStorage.TopicKey peer : request.peers) {
                TLRPC.TL_forumTopic topic = topic(instance, peer);
                MessageObject reply = topic == null ? null : new MessageObject(request.account,
                        topic.topicStartMessage, false, false);
                if (reply != null) reply.isTopicMainMessage = true;
                replies.add(reply);
            }
            CharSequence[] caption = {request.caption};
            ArrayList<TLRPC.MessageEntity> entities = request.caption == null ? null
                    : MediaDataController.getInstance(request.account).getEntities(caption, false);
            String text = caption[0] == null ? null : caption[0].toString();
            request.queueStarted = true;
            busy = true;
            int submitted = 0;
            try {
                for (int i = 0; i < request.peers.size(); i++) {
                    ArrayList<String> paths = new ArrayList<>();
                    for (Archive archive : archives) paths.add(archive.file.getPath());
                    MessageObject reply = replies.get(i);
                    // Use the full schedule-repeat overload in this checkout (24 parameters).
                    SendMessagesHelper.prepareSendingDocuments(instance, paths, new ArrayList<>(paths),
                            null, text, entities == null ? null : new ArrayList<>(entities), "application/zip",
                            request.peers.get(i).dialogId, reply, reply, null, null, null,
                            request.notify, request.scheduleDate, request.repeat, null, null, 0,
                            0, false, request.stars[i], 0, null);
                    submitted++;
                }
                queued = true;
                picker.finishFragment();
            } catch (Exception e) {
                // The sender has no transaction/queue-result API. Never allow a retry to
                // duplicate already queued recipients if a later synchronous call fails.
                queued = submitted > 0;
                busy = queued;
                pending = null;
                FileLog.e(e);
                alert(picker, "分享未完成", submitted > 0
                        ? "部分接收方已进入客户端发送队列。为避免重复发送，请返回并检查聊天记录。"
                        : "无法加入客户端发送队列，请重新选择后重试。");
            }
        }

        void fail(Request request, Exception error) {
            FileLog.e(error);
            if (pending == request) {
                pending = null;
                busy = false;
                alert(picker, "无法分享", "生成文件、话题或账号已不可用，未发送。请重新生成或选择接收方。\n" + error.getMessage());
            }
        }
    }
}
