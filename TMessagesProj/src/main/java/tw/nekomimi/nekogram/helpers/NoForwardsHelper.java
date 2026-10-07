package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

import xyz.nextalone.nagram.NaConfig;

/**
 * 「该频道不允许拷贝和转发」(noforwards / protected content)相关的本地处理:
 * <p>
 * 1. {@link #isBypassEnabled()} —— 是否在本地解除该限制(仅影响本机 UI 与本地行为,不改协议层);
 * 2. {@link #ensureDownloaded} —— 重发(以副本转发)之前把图片/视频/文件自动下载到本地;
 * 3. {@link #filterForRepost} —— 按需要保留「有视频时只发视频」的过滤。
 * <p>
 * created for Huanghun-TG
 */
public class NoForwardsHelper {

    /** 自动下载等待上限(15 分钟),超时后放弃并回调失败 */
    public static final long DOWNLOAD_TIMEOUT = 15 * 60 * 1000L;

    /** 自动下载轮询间隔 */
    private static final long DOWNLOAD_POLL_INTERVAL = 500L;

    /** 每隔多久重新触发一次还没下完的文件的下载(防止偶发失败后卡死) */
    private static final long DOWNLOAD_RETRY_INTERVAL = 8000L;

    private NoForwardsHelper() {
    }

    /**
     * 本地解除「不允许拷贝和转发」限制。默认开启,可在 设置 -> 聊天气泡/聊天设置 里关闭。
     */
    public static boolean isBypassEnabled() {
        try {
            return NaConfig.INSTANCE.getEnableNoForwardsBypass().Bool();
        } catch (Throwable ignore) {
            return true;
        }
    }

    /**
     * 「有视频就只发视频」开关。默认关闭 = 原样重组(图片、视频、文字按原消息还原)。
     */
    public static boolean isVideoOnlyRepost() {
        try {
            return NaConfig.INSTANCE.getRepostVideoOnly().Bool();
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * 重发前的媒体过滤。
     * 开了「有视频只发视频」且这批消息里确实有视频时,只留下视频(原样重组时直接返回原列表)。
     */
    public static ArrayList<MessageObject> filterForRepost(ArrayList<MessageObject> in) {
        if (in == null || in.isEmpty() || !isVideoOnlyRepost()) {
            return in;
        }
        boolean hasVideo = false;
        for (int i = 0, n = in.size(); i < n; i++) {
            MessageObject m = in.get(i);
            if (m != null && (m.isVideo() || m.isRoundVideo())) {
                hasVideo = true;
                break;
            }
        }
        if (!hasVideo) {
            return in;
        }
        ArrayList<MessageObject> out = new ArrayList<>();
        for (int i = 0, n = in.size(); i < n; i++) {
            MessageObject m = in.get(i);
            if (m != null && (m.isVideo() || m.isRoundVideo())) {
                out.add(m);
            }
        }
        return out.isEmpty() ? in : out;
    }

    /** 这条消息重发之前是否还得先下载媒体文件 */
    public static boolean needsDownload(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        if (messageObject.isSticker() || messageObject.isAnimatedSticker() || messageObject.isAnimatedEmoji()) {
            return false;
        }
        if (!(messageObject.isPhoto() || messageObject.isVideo() || messageObject.isRoundVideo() || messageObject.getDocument() != null)) {
            return false;
        }
        return TextUtils.isEmpty(MessageHelper.getPathToMessage(messageObject));
    }

    /** 这批消息里还有几条没下完 */
    public static int countPending(ArrayList<MessageObject> messages) {
        if (messages == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0, n = messages.size(); i < n; i++) {
            if (needsDownload(messages.get(i))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 把这一批消息需要的图片 / 视频 / 文件下到本地,全部就绪后回调 onReady。
     * 期间每 {@link #DOWNLOAD_RETRY_INTERVAL} 毫秒会对还没下完的重新触发一次。
     * 超过 {@link #DOWNLOAD_TIMEOUT} 仍未完成则回调 onTimeout。
     */
    public static void ensureDownloaded(int account, ArrayList<MessageObject> messages, Runnable onReady, Runnable onTimeout) {
        if (messages == null || messages.isEmpty() || countPending(messages) == 0) {
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        new DownloadWaiter(account, messages, onReady, onTimeout).start();
    }

    /* ==================== 强制转发 ==================== */

    /**
     * 直接读服务端原始标记:这个会话是不是「不允许拷贝和转发」。
     * 注意这里刻意不看本地开关(用来决定要不要走"副本重发"这条兜底路)。
     */
    public static boolean isPeerProtectedRaw(int account, long dialogId) {
        if (dialogId == 0) {
            return false;
        }
        try {
            final MessagesController controller = MessagesController.getInstance(account);
            if (dialogId > 0) {
                TLRPC.UserFull userFull = controller.getUserFull(dialogId);
                return userFull != null && (userFull.noforwards_peer_enabled || userFull.noforwards_my_enabled);
            }
            TLRPC.Chat chat = controller.getChat(-dialogId);
            return chat != null && chat.noforwards;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** 这批消息里有没有真正受保护(不允许转发)的内容 */
    public static boolean containsProtected(int account, ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        for (int i = 0, n = messages.size(); i < n; i++) {
            MessageObject m = messages.get(i);
            if (m == null || m.messageOwner == null) {
                continue;
            }
            if (m.messageOwner.noforwards) {
                return true;
            }
            if (isPeerProtectedRaw(account, m.getDialogId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 以副本形式重发一批消息(媒体必须已经在本地)。整批发不出去时逐条兜底,尽量把内容送出去。
     *
     * @return 是否至少送出了一部分
     */
    public static boolean republishAsCopy(int account, ArrayList<MessageObject> messages, long targetDialogId) {
        if (messages == null || messages.isEmpty() || targetDialogId == 0) {
            return false;
        }
        final MessageHelper helper = MessageHelper.getInstance(account);
        boolean sent = false;
        try {
            sent = helper.sendMessagesAsCopy(messages, targetDialogId, null, null, null, true, 0, 0, null, 0, 0, 0, null);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (!sent) {
            // 整批发不出去(某一条不支持 / 会话限制),逐条硬发,能发几条是几条
            for (int i = 0, n = messages.size(); i < n; i++) {
                MessageObject m = messages.get(i);
                if (m == null) {
                    continue;
                }
                ArrayList<MessageObject> single = new ArrayList<>(1);
                single.add(m);
                try {
                    if (helper.sendMessagesAsCopy(single, targetDialogId, null, null, null, true, 0, 0, null, 0, 0, 0, null)) {
                        sent = true;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
        return sent;
    }

    /**
     * 「强制转发」统一入口。
     * <p>
     * 普通内容 → 原样真转发(保留来源标记);
     * 受保护内容 → 先把图片 / 视频 / 文件下到本地,再按原消息的样子(相册 / 视频 / 文字一起)以副本形式重发出去。
     * 这样不管对方发的时候带没带「不允许拷贝和转发」,内容都能照样发到目标会话。
     */
    public static void forwardOrRepost(int account, ArrayList<MessageObject> messages, long targetDialogId, boolean noQuote, Runnable onDone) {
        if (messages == null || messages.isEmpty() || targetDialogId == 0) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        final ArrayList<MessageObject> list = new ArrayList<>(messages);
        if (!isBypassEnabled() || !containsProtected(account, list)) {
            try {
                SendMessagesHelper.getInstance(account).sendMessage(list, targetDialogId, noQuote, false, true, 0, 0);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        final ArrayList<MessageObject> toSend = filterForRepost(list);
        ensureDownloaded(account, toSend, () -> {
            republishAsCopy(account, toSend, targetDialogId);
            if (onDone != null) {
                onDone.run();
            }
        }, () -> {
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    /** 触发一条消息的媒体下载 */
    private static void triggerDownload(int account, MessageObject messageObject) {
        try {
            final FileLoader fileLoader = FileLoader.getInstance(account);
            TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
            if (media == null) {
                return;
            }
            if (messageObject.isPhoto() && media.photo != null) {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, AndroidUtilities.getPhotoSize(true), false, null, true);
                if (photoSize != null) {
                    fileLoader.loadFile(ImageLocation.getForPhoto(photoSize, media.photo), messageObject, "jpg", FileLoader.PRIORITY_HIGH, 0);
                    return;
                }
            }
            TLRPC.Document document = messageObject.getDocument();
            if (document != null) {
                fileLoader.loadFile(document, messageObject, FileLoader.PRIORITY_HIGH, 0);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static class DownloadWaiter implements Runnable {

        private final int account;
        private final ArrayList<MessageObject> messages;
        private final Runnable onReady;
        private final Runnable onTimeout;

        private final long startTime = System.currentTimeMillis();
        private long lastRetryTime = 0;

        DownloadWaiter(int account, ArrayList<MessageObject> messages, Runnable onReady, Runnable onTimeout) {
            this.account = account;
            this.messages = messages;
            this.onReady = onReady;
            this.onTimeout = onTimeout;
        }

        void start() {
            retrigger();
            AndroidUtilities.runOnUIThread(this, DOWNLOAD_POLL_INTERVAL);
        }

        private void retrigger() {
            lastRetryTime = System.currentTimeMillis();
            for (int i = 0, n = messages.size(); i < n; i++) {
                MessageObject m = messages.get(i);
                if (needsDownload(m)) {
                    triggerDownload(account, m);
                }
            }
        }

        @Override
        public void run() {
            if (countPending(messages) == 0) {
                if (onReady != null) {
                    onReady.run();
                }
                return;
            }
            if (System.currentTimeMillis() - startTime > DOWNLOAD_TIMEOUT) {
                if (onTimeout != null) {
                    onTimeout.run();
                }
                return;
            }
            if (System.currentTimeMillis() - lastRetryTime > DOWNLOAD_RETRY_INTERVAL) {
                retrigger();
            }
            AndroidUtilities.runOnUIThread(this, DOWNLOAD_POLL_INTERVAL);
        }
    }
}
