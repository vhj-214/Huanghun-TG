package tw.nekomimi.nekogram;

import static tw.nekomimi.nekogram.config.ConfigItem.configTypeBool;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeFloat;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeInt;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeLong;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeMapIntInt;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeSetInt;
import static tw.nekomimi.nekogram.config.ConfigItem.configTypeString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Pair;

import com.radolyn.ayugram.utils.AyuGhostUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.helpers.CloudSettingsHelper;

@SuppressLint("ApplySharedPref")
@SuppressWarnings("unused")
public class NekoConfig {

    public static final int TABLET_AUTO = 0;
    public static final int TABLET_ENABLE = 1;

    public static final int DIALOG_FILTER_EXCLUDE_NONE = 0;
    public static final int DIALOG_FILTER_EXCLUDE_MUTED = 1;
    public static final int DIALOG_FILTER_EXCLUDE_ALL = 2;

    public static final int MARKDOWN_PARSER_TELEGRAM = 0;
    public static final int MARKDOWN_PARSER_NEKO = 1;

    public static final int DRAWER_BACKGROUND_DEFAULT = 0;
    public static final int DRAWER_BACKGROUND_AVATAR = 1;
    public static final int DRAWER_BACKGROUND_BIG_AVATAR = 2;
    public static final int DRAWER_BACKGROUND_WALLPAPER = 3;

    public static final int DNS_TYPE_DEFAULT = 0;
    public static final int DNS_TYPE_NAX = 1;
    public static final int DNS_TYPE_SYSTEM = 2;
    public static final int DNS_TYPE_CUSTOM_DOH = 3;

    public static final int ID_TYPE_HIDDEN = 0;
    public static final int ID_TYPE_API = 1;
    public static final int ID_TYPE_BOT_API = 2;

    private static SharedPreferences preferences;

    public static SharedPreferences getPreferences() {
        if (preferences == null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("nkmrcfg", Context.MODE_PRIVATE);
        }
        return preferences;
    }

    public static final Object sync = new Object();

    private static boolean configLoaded = false;
    private static final ArrayList<ConfigItem> configs = new ArrayList<>();
    public static final ArrayList<DatacenterInfo> datacenterInfos = new ArrayList<>(5);

    // Configs
    public static ConfigItem unreadBadgeOnBackButton = addConfig("unreadBadgeOnBackButton", configTypeBool, false);
    public static ConfigItem useCustomEmoji = addConfig("useCustomEmoji", configTypeBool, false);
    public static ConfigItem repeatConfirm = addConfig("repeatConfirm", configTypeBool, true);
    public static ConfigItem disableInstantCamera = addConfig("DisableInstantCamera", configTypeBool, true);
    public static ConfigItem showSeconds = addConfig("showSeconds", configTypeBool, false);

    // From NekoConfig
    public static ConfigItem useIPv6 = addConfig("IPv6", configTypeBool, false);
    public static ConfigItem hidePhone = addConfig("HidePhone", configTypeBool, true);
    public static ConfigItem ignoreBlocked = addConfig("IgnoreBlocked", configTypeBool, false);
    public static ConfigItem tabletMode = addConfig("TabletMode", configTypeInt, 0);

    public static ConfigItem typeface = addConfig("TypefaceUseDefault", configTypeBool, false);
    // 0 keeps the original font; positive values map to the bundled Huanghun fonts.
    public static ConfigItem huanghunCustomTypeface = addConfig("HuanghunCustomTypeface", configTypeInt, 0);
    // Version marker for inserting a new user-provided font at index 1 without changing an
    // existing installation's current selection.
    private static ConfigItem huanghunTypefaceIndexShiftV2 = addConfig("HuanghunTypefaceIndexShiftV2", configTypeBool, false);
    public static ConfigItem nameOrder = addConfig("NameOrder", configTypeInt, 1);
    public static ConfigItem mapPreviewProvider = addConfig("MapPreviewProvider", configTypeInt, 0);
    public static ConfigItem showAddToSavedMessages = addConfig("showAddToSavedMessages", configTypeBool, true);
    public static ConfigItem showReport = addConfig("showReport", configTypeBool, false);
    public static ConfigItem showViewHistory = addConfig("showViewHistory", configTypeBool, true);
    public static ConfigItem showAdminActions = addConfig("showAdminActions", configTypeBool, true);
    public static ConfigItem showChangePermissions = addConfig("showChangePermissions", configTypeBool, true);
    public static ConfigItem showDeleteDownloadedFile = addConfig("showDeleteDownloadedFile", configTypeBool, true);
    public static ConfigItem showMessageDetails = addConfig("showMessageDetails", configTypeBool, true);
    public static ConfigItem showTranslate = addConfig("showTranslate", configTypeBool, true);
    public static ConfigItem showRepeat = addConfig("showRepeat", configTypeBool, true);
    public static ConfigItem showShareMessages = addConfig("showShareMessages", configTypeBool, false);
    public static ConfigItem showMessageHide = addConfig("showMessageHide", configTypeBool, false);

    public static ConfigItem actionBarDecoration = addConfig("ActionBarDecoration", configTypeInt, 0);
    public static ConfigItem stickerSize = addConfig("stickerSize", configTypeFloat, 14.0f);
    public static ConfigItem unlimitedFavedStickers = addConfig("UnlimitedFavoredStickers", configTypeBool, false);
    public static ConfigItem unlimitedPinnedDialogs = addConfig("UnlimitedPinnedDialogs", configTypeBool, false);
    // Huanghun enables this by default to remove the client's own folder-count gate.
    // Telegram may still reject unsupported counts server-side, which remains authoritative.
    public static ConfigItem unlimitedDialogFilters = addConfig("UnlimitedDialogFilters", configTypeBool, true);
    public static ConfigItem openArchiveOnPull = addConfig("OpenArchiveOnPull", configTypeBool, false);
    public static ConfigItem hideKeyboardOnChatScroll = addConfig("HideKeyboardOnChatScroll", configTypeBool, false);
    public static ConfigItem useSystemEmoji = addConfig("EmojiUseDefault", configTypeBool, false);
    public static ConfigItem rearVideoMessages = addConfig("RearVideoMessages", configTypeBool, false);
    public static ConfigItem hideAllTab = addConfig("HideAllTab", configTypeBool, false);

    public static ConfigItem sortByUnread = addConfig("sort_by_unread", configTypeBool, false);
    public static ConfigItem sortByUnmuted = addConfig("sort_by_unmuted", configTypeBool, true);
    public static ConfigItem sortByUser = addConfig("sort_by_user", configTypeBool, true);
    public static ConfigItem sortByContacts = addConfig("sort_by_contacts", configTypeBool, true);

    public static ConfigItem disableSystemAccount = addConfig("DisableSystemAccount", configTypeBool, false);
    public static ConfigItem skipOpenLinkConfirm = addConfig("SkipOpenLinkConfirm", configTypeBool, false);

    public static ConfigItem showIdAndDc = addConfig("ShowIdAndDc", configTypeBool, true);

    public static ConfigItem cachePath = addConfig("cache_path", configTypeString, "");
    // Default attachment folder under Downloads. Users can still change or clear it in General settings.
    public static ConfigItem customSavePath = addConfig("customSavePath", configTypeString, "黄昏");

    // 新安装默认使用 Google 翻译；升级用户由黄昏一次性迁移同步。
    public static ConfigItem translationProvider = addConfig("translationProvider", configTypeInt, 1);
    public static ConfigItem translateToLang = addConfig("TransToLang", configTypeString, ""); // "" -> translate to current language (MessageTrans.kt & Translator.kt)
    public static ConfigItem translateInputLang = addConfig("TransInputToLang", configTypeString, "en");
    public static ConfigItem googleCloudTranslateKey = addConfig("GoogleCloudTransKey", configTypeString, "");

    // Non-contact keyword filtering is opt-in. It may hide notification content and block a sender,
    // but it must never remove chat history automatically.
    public static ConfigItem huanghunBlockNonContacts = addConfig("HuanghunBlockNonContacts", configTypeBool, false);
    // 仅控制本机共同群聊天中已拉黑用户消息的显示，不删除或修改任何消息数据。
    public static ConfigItem huanghunBlockMutualGroupMessages = addConfig("HuanghunBlockMutualGroupMessages", configTypeBool, false);
    public static ConfigItem huanghunBlockedKeywords = addConfig("HuanghunBlockedKeywords", configTypeString, "海外bc\n安全验证\n兼职刷单\n加微信\n高收益\nUSDT\n免费领取\n投资理财\n裸聊交友\n色情服务\n代办签证\n买粉买赞\n刷单返利\n博彩网站\n线上赌场\n同城约炮\n兼职招聘\n高薪诚聘\n数字货币\n币圈投资\n杀猪盘\n贷款代办\n办证刻章\n发票代开\n买赞买粉\n流量变现\n私密直播\n福利姬\n原声视频\n引流加V\nQQ群\n飞机群\nTG群\n加Q\n联系客服\n专业导师\n带单盈利");
    // 内置视频录制默认关闭；开启后仅替代圆形视频录制流程，普通官方相机不受影响。
    public static ConfigItem huanghunBuiltinCameraEnabled = addConfig("HuanghunBuiltinCameraEnabled", configTypeBool, false);
    // 默认保留所选视频的原声，关闭后发送的圆形视频将由官方发送管线标记为静音。
    public static ConfigItem huanghunBuiltinVideoSound = addConfig("HuanghunBuiltinVideoSound", configTypeBool, true);
    // 内置视频默认按 Telegram 官方圆形视频消息发送；方形模式默认关闭。
    // 设置页保证两者不会同时开启，二者同时关闭时内置视频录制入口会回退到官方真实相机。
    public static ConfigItem huanghunBuiltinRoundVideo = addConfig("HuanghunBuiltinRoundVideo", configTypeBool, true);
    public static ConfigItem huanghunBuiltinSquareVideo = addConfig("HuanghunBuiltinSquareVideo", configTypeBool, false);

    // 通话专区：0=前置，1=后置，2=手机屏幕；默认后置，保持官方默认通话体验。
    public static ConfigItem huanghunCallDefaultCamera = addConfig("HuanghunCallDefaultCamera", configTypeInt, 1);
    // 关闭时通话始终走 Telegram 官方前后摄像头或屏幕共享采集流程。
    public static ConfigItem huanghunCallVirtualCameraEnabled = addConfig("HuanghunCallVirtualCameraEnabled", configTypeBool, false);
    // 开启虚拟摄像头时默认保留内置视频原声，用户可在通话专区关闭。
    public static ConfigItem huanghunCallVirtualVideoSound = addConfig("HuanghunCallVirtualVideoSound", configTypeBool, true);

    public static ConfigItem disableNotificationBubbles = addConfig("disableNotificationBubbles", configTypeBool, false);

    public static ConfigItem tabsTitleType = addConfig("TabTitleType", configTypeInt, NekoXConfig.TITLE_TYPE_TEXT);
    public static ConfigItem confirmAVMessage = addConfig("ConfirmAVMessage", configTypeBool, false);
    public static ConfigItem askBeforeCall = addConfig("AskBeforeCalling", configTypeBool, true);
    public static ConfigItem disableNumberRounding = addConfig("DisableNumberRounding", configTypeBool, false);

    public static ConfigItem dnsType = addConfig("DnsType", configTypeInt, DNS_TYPE_DEFAULT);
    public static ConfigItem customDoH = addConfig("CustomDoH", configTypeString, "");

    public static ConfigItem mediaPreview = addConfig("MediaPreview", configTypeBool, true);

    public static ConfigItem disableVibration = addConfig("DisableVibration", configTypeBool, false);
    public static ConfigItem autoPauseVideo = addConfig("AutoPauseVideo", configTypeBool, false);
    public static ConfigItem disableProximityEvents = addConfig("DisableProximityEvents", configTypeBool, false);

    public static ConfigItem ignoreContentRestrictions = addConfig("ignoreContentRestrictions", configTypeBool, true);
    public static ConfigItem useChatAttachMediaMenu = addConfig("UseChatAttachEnterMenu", configTypeBool, true);
    public static ConfigItem disableLinkPreviewByDefault = addConfig("DisableLinkPreviewByDefault", configTypeBool, false);
    public static ConfigItem sendCommentAfterForward = addConfig("SendCommentAfterForward", configTypeBool, true);
    public static ConfigItem disableTrending = addConfig("DisableTrending", configTypeBool, true);
    public static ConfigItem dontSendGreetingSticker = addConfig("DontSendGreetingSticker", configTypeBool, true);
    public static ConfigItem hideTimeForSticker = addConfig("HideTimeForSticker", configTypeBool, false);
    public static ConfigItem takeGIFasVideo = addConfig("TakeGIFasVideo", configTypeBool, false);
    public static ConfigItem maxRecentStickerCount = addConfig("maxRecentStickerCount", configTypeInt, 20);
    public static ConfigItem disableSwipeToNext = addConfig("disableSwipeToNextChannel", configTypeBool, false);
    public static ConfigItem disableSwipeToNextTopic = addConfig("disableSwipeToNextTopic", configTypeBool, false);
    public static ConfigItem disableChoosingSticker = addConfig("disableChoosingSticker", configTypeBool, false);
    public static ConfigItem hideGroupSticker = addConfig("hideGroupSticker", configTypeBool, false);
    public static ConfigItem rememberAllBackMessages = addConfig("rememberAllBackMessages", configTypeBool, false);
    public static ConfigItem hideSendAsChannel = addConfig("hideSendAsChannel", configTypeBool, false);
    public static ConfigItem showSpoilersDirectly = addConfig("showSpoilersDirectly", configTypeBool, false);

    public static ConfigItem disableAutoDownloadingWin32Executable = addConfig("Win32ExecutableFiles", configTypeBool, true);
    public static ConfigItem disableAutoDownloadingArchive = addConfig("ArchiveFiles", configTypeBool, true);

    public static ConfigItem customAudioBitrate = addConfig("customAudioBitrate", configTypeInt, 32);
    public static ConfigItem enhancedFileLoader = addConfig("enhancedFileLoader", configTypeBool, false);
    public static ConfigItem uploadBoost = addConfig("uploadBoost", configTypeBool, false);
    public static ConfigItem useOSMDroidMap = addConfig("useOSMDroidMap", configTypeBool, false);
    public static ConfigItem mapDriftingFixForGoogleMaps = addConfig("mapDriftingFixForGoogleMaps", configTypeBool, true);

    public static ConfigItem localPremium = addConfig("localPremium", configTypeBool, false);

    public static ConfigItem usePersianCalendar = addConfig("UsePersianCalendar", configTypeBool, false);
    public static ConfigItem displayPersianCalendarByLatin = addConfig("DisplayPersianCalendarByLatin", configTypeBool, false);

    public static ConfigItem minimizedStickerCreator = addConfig("minimizedStickerCreator", configTypeBool, false);

    // --- Ghost Mode ---
    public static ConfigItem sendReadMessagePackets = addConfig("sendReadMessagePackets", configTypeBool, true);
    public static ConfigItem sendReadStoriesPackets = addConfig("sendReadStoriesPackets", configTypeBool, true);
    public static ConfigItem sendOnlinePackets = addConfig("sendOnlinePackets", configTypeBool, true);
    public static ConfigItem sendUploadProgress = addConfig("sendUploadProgress", configTypeBool, true);
    public static ConfigItem sendOfflinePacketAfterOnline = addConfig("sendOfflinePacketAfterOnline", configTypeBool, false);
    public static ConfigItem markReadAfterSend = addConfig("markReadAfterSend", configTypeBool, true);
    public static ConfigItem showGhostInDrawer = addConfig("showGhostInDrawer", configTypeBool, false);
    public static ConfigItem showGhostModeStatus = addConfig("showGhostModeStatus", configTypeBool, false);

    // --- Locked Status ---
    public static ConfigItem sendReadMessagePacketsLocked = addConfig("sendReadMessagePacketsLocked", configTypeBool, false);
    public static ConfigItem sendReadStoriesPacketsLocked = addConfig("sendReadStoriesPacketsLocked", configTypeBool, false);
    public static ConfigItem sendOnlinePacketsLocked = addConfig("sendOnlinePacketsLocked", configTypeBool, false);
    public static ConfigItem sendUploadProgressLocked = addConfig("sendUploadProgressLocked", configTypeBool, false);
    public static ConfigItem sendOfflinePacketAfterOnlineLocked = addConfig("sendOfflinePacketAfterOnlineLocked", configTypeBool, false);
    // --- Ghost Mode ---

    static {
        init();
    }

    public static void init() {
        loadConfig(false);
    }

    public static ConfigItem addConfig(String k, int t, Object d) {
        ConfigItem a = new ConfigItem(k, t, d);
        configs.add(a);
        return a;
    }

    public static void loadConfig(boolean force) {
        synchronized (sync) {
            if (configLoaded && !force) {
                return;
            }
            if (ApplicationLoader.applicationContext == null) {
                return;
            }
            for (int i = 0; i < configs.size(); i++) {
                ConfigItem o = configs.get(i);

                try {
                    if (o.type == configTypeBool) {
                        o.value = getPreferences().getBoolean(o.key, (boolean) o.defaultValue);
                    }
                    if (o.type == configTypeInt) {
                        o.value = getPreferences().getInt(o.key, (int) o.defaultValue);
                    }
                    if (o.type == configTypeLong) {
                        o.value = getPreferences().getLong(o.key, (Long) o.defaultValue);
                    }
                    if (o.type == configTypeFloat) {
                        o.value = getPreferences().getFloat(o.key, (Float) o.defaultValue);
                    }
                    if (o.type == configTypeString) {
                        o.value = getPreferences().getString(o.key, (String) o.defaultValue);
                    }
                    if (o.type == configTypeSetInt) {
                        Set<String> ss = getPreferences().getStringSet(o.key, new HashSet<>());
                        HashSet<Integer> si = new HashSet<>();
                        for (String s : ss) {
                            si.add(Integer.parseInt(s));
                        }
                        o.value = si;
                    }
                    if (o.type == configTypeMapIntInt) {
                        String cv = getPreferences().getString(o.key, "");
                        if (cv.isEmpty()) {
                            o.value = new HashMap<Integer, Integer>();
                        } else {
                            try {
                                byte[] data = Base64.decode(cv, Base64.DEFAULT);
                                ObjectInputStream ois = new ObjectInputStream(
                                        new ByteArrayInputStream(data));
                                o.value = ois.readObject();
                                if (o.value == null) {
                                    o.value = new HashMap<Integer, Integer>();
                                }
                                ois.close();
                            } catch (Exception e) {
                                o.value = new HashMap<Integer, Integer>();
                            }
                        }
                    }
                } catch (ClassCastException | NumberFormatException e) {
                    FileLog.e("Invalid config value for " + o.key, e);
                    o.value = o.defaultValue;
                    getPreferences().edit().remove(o.key).apply();
                }
            }
            // The original shipped selector began at index 1. Keep existing user selections
            // mapped to the same font after the user-provided font is inserted at index 1.
            if (!huanghunTypefaceIndexShiftV2.Bool()) {
                int selectedTypeface = huanghunCustomTypeface.Int();
                if (selectedTypeface > 0) {
                    huanghunCustomTypeface.setConfigInt(selectedTypeface + 1);
                }
                huanghunTypefaceIndexShiftV2.setConfigBool(true);
            }
            if (!configLoaded)
                getPreferences().registerOnSharedPreferenceChangeListener(CloudSettingsHelper.listener);
            for (int a = 1; a <= 5; a++) {
                datacenterInfos.add(new DatacenterInfo(a));
            }
            configLoaded = true;
        }
    }

    public static class DatacenterInfo {

        public int id;

        public long pingId;
        public long ping;
        public boolean checking;
        public boolean available;
        public long availableCheckTime;

        public DatacenterInfo(int i) {
            id = i;
        }
    }

    public static boolean fixDriftingForGoogleMaps() {
        return !useOSMDroidMap.Bool() && mapDriftingFixForGoogleMaps.Bool();
    }

    // --- Ghost Mode ---
    public static boolean isGhostModeActive() {
        for (Pair<ConfigItem, ConfigItem> pair : ghostToggleItems) {
            ConfigItem item = pair.first;
            ConfigItem lockedItem = pair.second;
            if (!lockedItem.Bool()) {
                boolean currentValue = item.Bool();
                boolean isGhostState = (item == sendOfflinePacketAfterOnline) == currentValue;

                if (!isGhostState) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void setGhostMode(boolean enabled) {
        for (Pair<ConfigItem, ConfigItem> pair : ghostToggleItems) {
            ConfigItem item = pair.first;
            ConfigItem lockedItem = pair.second;
            if (!lockedItem.Bool()) {
                boolean targetValue = (item == sendOfflinePacketAfterOnline) == enabled;
                item.setConfigBool(targetValue);
            }
        }
    }

    public static void toggleGhostMode() {
        boolean newState = !isGhostModeActive();
        setGhostMode(newState);

        boolean sendOnlineNow = !newState && !sendOfflinePacketAfterOnlineLocked.Bool() && sendOfflinePacketAfterOnline.Bool();
        AyuGhostUtils.performStatusRequest(sendOnlineNow);
    }

    private static final List<Pair<ConfigItem, ConfigItem>> ghostToggleItems = Arrays.asList(
            new Pair<>(sendReadMessagePackets, sendReadMessagePacketsLocked),
            new Pair<>(sendReadStoriesPackets, sendReadStoriesPacketsLocked),
            new Pair<>(sendOnlinePackets, sendOnlinePacketsLocked),
            new Pair<>(sendUploadProgress, sendUploadProgressLocked),
            new Pair<>(sendOfflinePacketAfterOnline, sendOfflinePacketAfterOnlineLocked)
    );
    // --- Ghost Mode ---

    public static Map<String, Integer> getConfigTypes() {
        synchronized (sync) {
            Map<String, Integer> types = new HashMap<>();
            for (ConfigItem o : configs) {
                types.put(o.getKey(), o.type);
            }
            return types;
        }
    }
}
