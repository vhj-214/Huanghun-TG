package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.text.ParseException;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.HuanghunActiveZoneHelper;
import tw.nekomimi.nekogram.helpers.HuanghunExtensionHelper;
import tw.nekomimi.nekogram.helpers.HuanghunPrivacyFolderHelper;
import tw.nekomimi.nekogram.helpers.HuanghunVideoLibraryHelper;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Settings page for Huanghun account cleanup and non-contact message blocking. */
public class NekoExtensionsActivity extends BaseNekoSettingsActivity {

    private static final int REQUEST_HUANGHUN_BUILTIN_VIDEOS = 10937;

    private int activeHeaderRow;
    private int activeEnabledRow;
    private int activeDirectionRow;
    private int activeTargetRow;
    private int activeEmojiRow;
    private int activeNoticeRow;
    private int activeEndRow;

    private int videoHeaderRow;
    private int builtinCameraRow;
    private int selectBuiltinVideosRow;
    // 位于启动内置相机和视频声音之间，直接打开独立的通话专区。
    private int callCameraFeatureRow;
    private int builtinVideoSoundRow;
    private int builtinRoundVideoRow;
    private int builtinSquareVideoRow;
    private int viewBuiltinVideosRow;
    private int deleteBuiltinVideosRow;
    private int videoToGifRow;
    private int videoNoticeRow;
    private int videoEndRow;

    private int cleanupHeaderRow;
    private int clearBotsRow;
    private int leaveGroupsRow;
    private int clearContactsRow;
    private int clearChatsRow;
    private int resetProfileRow;
    private int clearDeletedAccountsRow;
    private int clearMessagesByTimeRow;
    private int clearAllRow;
    private int cleanupNoticeRow;
    private int cleanupEndRow;

    private int privacyHeaderRow;
    private int createPrivacyFolderRow;
    private int managePrivacyChatsRow;
    private int changePrivacyPasswordRow;
    private int forgotPrivacyPasswordRow;
    private int deletePrivacyFolderRow;
    private int privacyNoticeRow;
    private int privacyEndRow;

    private int blockHeaderRow;
    private int blockNonContactsRow;
    private int blockMutualGroupMessagesRow;
    private int keywordsRow;
    private int blockNoticeRow;
    private int blockEndRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        activeHeaderRow = addRow();
        activeEnabledRow = addRow();
        activeDirectionRow = addRow();
        activeTargetRow = addRow();
        activeEmojiRow = addRow();
        activeNoticeRow = addRow();
        activeEndRow = addRow();

        videoHeaderRow = addRow();
        builtinCameraRow = addRow();
        selectBuiltinVideosRow = addRow();
        callCameraFeatureRow = addRow();
        builtinVideoSoundRow = addRow();
        builtinRoundVideoRow = addRow();
        builtinSquareVideoRow = addRow();
        viewBuiltinVideosRow = addRow();
        deleteBuiltinVideosRow = addRow();
        videoToGifRow = addRow();
        videoNoticeRow = addRow();
        videoEndRow = addRow();

        cleanupHeaderRow = addRow();
        clearBotsRow = addRow();
        leaveGroupsRow = addRow();
        clearContactsRow = addRow();
        clearChatsRow = addRow();
        resetProfileRow = addRow();
        clearDeletedAccountsRow = addRow();
        clearMessagesByTimeRow = addRow();
        clearAllRow = addRow();
        cleanupNoticeRow = addRow();
        cleanupEndRow = addRow();

        privacyHeaderRow = addRow();
        createPrivacyFolderRow = addRow();
        managePrivacyChatsRow = addRow();
        changePrivacyPasswordRow = addRow();
        forgotPrivacyPasswordRow = addRow();
        deletePrivacyFolderRow = addRow();
        privacyNoticeRow = addRow();
        privacyEndRow = addRow();

        blockHeaderRow = addRow();
        blockNonContactsRow = addRow();
        blockMutualGroupMessagesRow = addRow();
        keywordsRow = addRow();
        blockNoticeRow = addRow();
        blockEndRow = addRow();
    }

    @Override
    public void onResume() {
        super.onResume();
        notifyActiveRows();
        notifyVideoRows();
        notifyPrivacyRows();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.HuanghunExtensions);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == activeEnabledRow) {
            boolean enabled = NekoConfig.huanghunActiveZoneEnabled.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            return;
        }
        if (position == activeDirectionRow) {
            showActiveDirectionDialog();
            return;
        }
        if (position == activeTargetRow) {
            showActiveTargetDialog();
            return;
        }
        if (position == activeEmojiRow) {
            showActiveEmojiDialog();
            return;
        }
        if (position == builtinCameraRow) {
            boolean enabled = NekoConfig.huanghunBuiltinCameraEnabled.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            if (enabled && HuanghunVideoLibraryHelper.getVideoCount(ApplicationLoader.applicationContext, currentAccount) == 0) {
                showVideoInfo("请先选取视频", "已开启内置相机。请先点击“选取内置视频”导入至少一个视频，录制视频时才会使用预置视频。\n\n关闭此开关后，录制视频会完全恢复 Telegram 官方真实录制。\n");
            }
            return;
        }
        if (position == selectBuiltinVideosRow) {
            chooseBuiltinVideos();
            return;
        }
        if (position == callCameraFeatureRow) {
            presentFragment(new HuanghunCallSettingsActivity());
            return;
        }
        if (position == builtinVideoSoundRow) {
            boolean enabled = NekoConfig.huanghunBuiltinVideoSound.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            return;
        }
        if (position == builtinRoundVideoRow) {
            boolean enabled = !NekoConfig.huanghunBuiltinRoundVideo.Bool();
            NekoConfig.huanghunBuiltinRoundVideo.setConfigBool(enabled);
            // 圆形与方形模式可以同时关闭，但一旦开启其中一个必须关闭另一个。
            if (enabled) {
                NekoConfig.huanghunBuiltinSquareVideo.setConfigBool(false);
            }
            notifyVideoRows();
            return;
        }
        if (position == builtinSquareVideoRow) {
            boolean enabled = !NekoConfig.huanghunBuiltinSquareVideo.Bool();
            NekoConfig.huanghunBuiltinSquareVideo.setConfigBool(enabled);
            // 方形模式开启时自动关闭圆形模式；关闭方形时不强制开启圆形。
            if (enabled) {
                NekoConfig.huanghunBuiltinRoundVideo.setConfigBool(false);
            }
            notifyVideoRows();
            return;
        }
        if (position == viewBuiltinVideosRow) {
            presentFragment(new HuanghunVideoLibraryActivity(false));
            return;
        }
        if (position == deleteBuiltinVideosRow) {
            presentFragment(new HuanghunVideoLibraryActivity(true));
            return;
        }
        if (position == videoToGifRow) {
            boolean enabled = NekoConfig.huanghunVideoToGif.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            return;
        }
        if (position == createPrivacyFolderRow) {
            showCreatePrivacyFolderDialog();
            return;
        }
        if (position == managePrivacyChatsRow) {
            openPrivacyChats();
            return;
        }
        if (position == changePrivacyPasswordRow) {
            showChangePrivacyPassword();
            return;
        }
        if (position == forgotPrivacyPasswordRow) {
            showForgotPrivacyPassword();
            return;
        }
        if (position == deletePrivacyFolderRow) {
            showDeletePrivacyFolder();
            return;
        }
        if (position == blockNonContactsRow) {
            boolean enabled = NekoConfig.huanghunBlockNonContacts.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            return;
        }
        if (position == blockMutualGroupMessagesRow) {
            boolean enabled = NekoConfig.huanghunBlockMutualGroupMessages.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.huanghunMutualGroupMessageBlockChanged);
            return;
        }
        if (position == keywordsRow) {
            showKeywordsDialog();
            return;
        }

        HuanghunExtensionHelper.CleanupAction action = null;
        if (position == clearBotsRow) {
            action = HuanghunExtensionHelper.CleanupAction.BOT_INTERACTIONS;
        } else if (position == leaveGroupsRow) {
            action = HuanghunExtensionHelper.CleanupAction.GROUPS;
        } else if (position == clearContactsRow) {
            action = HuanghunExtensionHelper.CleanupAction.CONTACTS;
        } else if (position == clearChatsRow) {
            action = HuanghunExtensionHelper.CleanupAction.CHATS;
        } else if (position == resetProfileRow) {
            action = HuanghunExtensionHelper.CleanupAction.PROFILE;
        } else if (position == clearDeletedAccountsRow) {
            action = HuanghunExtensionHelper.CleanupAction.DELETED_ACCOUNTS;
        } else if (position == clearMessagesByTimeRow) {
            showTimeRangeCleanupDialog();
            return;
        } else if (position == clearAllRow) {
            action = HuanghunExtensionHelper.CleanupAction.ALL;
        }
        if (action != null) {
            showCleanupConfirmation(action);
            return;
        }

    }

    private void chooseBuiltinVideos() {
        if (getParentActivity() == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_HUANGHUN_BUILTIN_VIDEOS);
        } catch (Throwable e) {
            FileLog.e(e);
            showVideoInfo("无法选择视频", "无法打开系统视频选择器，请稍后重试。\n");
        }
    }

    private void importBuiltinVideos(ArrayList<Uri> sources) {
        if (sources == null || sources.isEmpty() || getParentActivity() == null) {
            showVideoInfo("导入失败", "未读取到所选视频，请重新选择。\n");
            return;
        }
        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setMessage("正在保存并验证所选视频，请稍候。\n");
        progress.setCancelable(false);
        showDialog(progress);
        final int account = currentAccount;
        Utilities.globalQueue.postRunnable(() -> {
            HuanghunVideoLibraryHelper.ImportResult result;
            try {
                result = HuanghunVideoLibraryHelper.importVideos(ApplicationLoader.applicationContext, account, sources);
            } catch (Throwable e) {
                FileLog.e(e);
                ArrayList<String> errors = new ArrayList<>();
                String message = e.getMessage();
                errors.add(message == null || message.length() == 0 ? "视频导入失败，请稍后重试。" : message);
                result = new HuanghunVideoLibraryHelper.ImportResult(0, errors);
            }
            final HuanghunVideoLibraryHelper.ImportResult importResult = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (progress.isShowing()) {
                    progress.dismiss();
                }
                notifyVideoRows();
                if (importResult.importedCount > 0) {
                    String message = "已导入 " + importResult.importedCount + " 个内置视频。";
                    if (importResult.hasErrors()) {
                        message += "部分文件未能导入。";
                    }
                    BulletinFactory.of(NekoExtensionsActivity.this).createSimpleBulletin(R.raw.done, message).show();
                } else {
                    String message = importResult.errors.isEmpty() ? "所选视频无法导入，请更换视频后重试。" : importResult.errors.get(0);
                    showVideoInfo("导入失败", message);
                }
            });
        });
    }

    private void showVideoInfo(String title, String message) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(context, resourceProvider)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.OK), null)
                .create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_HUANGHUN_BUILTIN_VIDEOS && resultCode == Activity.RESULT_OK) {
            ArrayList<Uri> sources = new ArrayList<>();
            if (data != null) {
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (uri != null && !sources.contains(uri)) {
                            sources.add(uri);
                        }
                    }
                }
                Uri singleUri = data.getData();
                if (singleUri != null && !sources.contains(singleUri)) {
                    sources.add(singleUri);
                }
            }
            importBuiltinVideos(sources);
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    private void notifyVideoRows() {
        if (listAdapter == null) {
            return;
        }
        listAdapter.notifyItemChanged(builtinCameraRow);
        listAdapter.notifyItemChanged(selectBuiltinVideosRow);
        listAdapter.notifyItemChanged(callCameraFeatureRow);
        listAdapter.notifyItemChanged(builtinVideoSoundRow);
        listAdapter.notifyItemChanged(builtinRoundVideoRow);
        listAdapter.notifyItemChanged(builtinSquareVideoRow);
        listAdapter.notifyItemChanged(viewBuiltinVideosRow);
        listAdapter.notifyItemChanged(deleteBuiltinVideosRow);
    }

    private void showCleanupConfirmation(HuanghunExtensionHelper.CleanupAction action) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle("确认清理")
                .setMessage(getCleanupConfirmationMessage(action))
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清理", (d, which) -> executeCleanupDirectly(action))
                .create();
        showDialog(dialog);
    }

    private String getCleanupConfirmationMessage(HuanghunExtensionHelper.CleanupAction action) {
        String target;
        switch (action) {
            case BOT_INTERACTIONS:
                target = "清除当前账号与机器人的全部交互数据";
                break;
            case GROUPS:
                target = "删除群聊和频道的聊天记录，并尝试退出相关群组或频道";
                break;
            case CONTACTS:
                target = "删除当前账号的所有联系人";
                break;
            case CHATS:
                target = "删除当前账号的所有聊天记录";
                break;
            case PROFILE:
                target = "清空当前账号的用户名、个人资料和动态信息";
                break;
            case DELETED_ACCOUNTS:
                target = "删除已注销账号的聊天记录与会话";
                break;
            case ALL:
            default:
                target = "执行清理专区列出的全部清理操作";
                break;
        }
        return "确认要" + target + "吗？\n\n点击“确认清理”后将立即开始执行；点击“取消”则不会进行任何清理。";
    }

    private void showActiveDirectionDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        String[] items = {"对方消息", "自己消息", "对方和自己"};
        int checked = Math.max(0, Math.min(2, NekoConfig.huanghunActiveZoneDirection.Int()));
        showDialog(new AlertDialog.Builder(context, resourceProvider)
                .setTitle("点赞消息方向")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    NekoConfig.huanghunActiveZoneDirection.setConfigInt(which);
                    dialog.dismiss();
                    notifyActiveRows();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create());
    }

    private void showActiveTargetDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        String[] items = {"全部用户（默认）", "选择指定用户", "输入用户 ID 或用户名"};
        int checked = NekoConfig.huanghunActiveZoneTargetMode.Int() == HuanghunActiveZoneHelper.TARGET_SELECTED ? 1 : 0;
        showDialog(new AlertDialog.Builder(context, resourceProvider)
                .setTitle("点赞对象")
                .setMessage("默认对所有用户生效；可以从会话和全局搜索选择用户，也可以直接输入用户 ID 或用户名。")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    if (which == 0) {
                        NekoConfig.huanghunActiveZoneTargetMode.setConfigInt(HuanghunActiveZoneHelper.TARGET_ALL);
                        dialog.dismiss();
                        notifyActiveRows();
                    } else if (which == 1) {
                        dialog.dismiss();
                        selectActiveUser();
                    } else {
                        dialog.dismiss();
                        showActiveUserInputDialog();
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create());
    }

    private void selectActiveUser() {
        Bundle args = new Bundle();
        args.putBoolean("onlyUsers", true);
        args.putBoolean("destroyAfterSelect", true);
        args.putBoolean("returnAsResult", true);
        args.putBoolean("allowBots", true);
        args.putBoolean("allowSelf", true);
        ContactsActivity contactsActivity = new ContactsActivity(args);
        contactsActivity.setDelegate((user, param, activity) -> {
            String current = NekoConfig.huanghunActiveZoneTargetUsers.String();
            String id = String.valueOf(user.id);
            ArrayList<String> users = new ArrayList<>();
            if (current != null && !current.trim().isEmpty()) {
                for (String value : current.split(",")) {
                    value = value.trim();
                    if (!value.isEmpty() && !users.contains(value)) users.add(value);
                }
            }
            if (!users.contains(id)) users.add(id);
            NekoConfig.huanghunActiveZoneTargetUsers.setConfigString(android.text.TextUtils.join(",", users));
            NekoConfig.huanghunActiveZoneTargetMode.setConfigInt(HuanghunActiveZoneHelper.TARGET_SELECTED);
            activity.removeSelfFromStack();
            notifyActiveRows();
        });
        presentFragment(contactsActivity);
    }

    private void showActiveUserInputDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        input.setHint("例如：123456789 或 @username");
        input.setText(NekoConfig.huanghunActiveZoneTargetUsers.String());
        input.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        showDialog(new AlertDialog.Builder(context, resourceProvider)
                .setTitle("输入用户 ID 或用户名")
                .setView(input)
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    NekoConfig.huanghunActiveZoneTargetUsers.setConfigString(value);
                    NekoConfig.huanghunActiveZoneTargetMode.setConfigInt(value.isEmpty() ? HuanghunActiveZoneHelper.TARGET_ALL : HuanghunActiveZoneHelper.TARGET_SELECTED);
                    notifyActiveRows();
                })
                .create());
    }

    private void showActiveEmojiDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setText(NekoConfig.huanghunActiveZoneEmoji.String());
        input.setGravity(Gravity.CENTER);
        showDialog(new AlertDialog.Builder(context, resourceProvider)
                .setTitle("点赞表情")
                .setMessage("请输入一个 Telegram 支持的普通表情，例如 👍。")
                .setView(input)
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    NekoConfig.huanghunActiveZoneEmoji.setConfigString(value.isEmpty() ? "👍" : value);
                    notifyActiveRows();
                })
                .create());
    }

    private String getActiveDirectionSummary() {
        switch (NekoConfig.huanghunActiveZoneDirection.Int()) {
            case HuanghunActiveZoneHelper.DIRECTION_SELF:
                return "自己消息";
            case HuanghunActiveZoneHelper.DIRECTION_BOTH:
                return "对方和自己";
            case HuanghunActiveZoneHelper.DIRECTION_OTHER:
            default:
                return "对方消息";
        }
    }

    private void notifyActiveRows() {
        if (listAdapter == null) return;
        listAdapter.notifyItemChanged(activeEnabledRow);
        listAdapter.notifyItemChanged(activeDirectionRow);
        listAdapter.notifyItemChanged(activeTargetRow);
        listAdapter.notifyItemChanged(activeEmojiRow);
    }

    private void showKeywordsDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);

        TextView description = new TextView(context);
        description.setText(getString(R.string.HuanghunBlockKeywordsHint));
        description.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        container.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setSingleLine(false);
        input.setMinLines(6);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(NekoConfig.huanghunBlockedKeywords.String());
        input.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField));
        input.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        container.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(180)));

        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle(getString(R.string.HuanghunBlockKeywords))
                .setView(container)
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.HuanghunSaveKeywords), (d, which) -> {
                    HuanghunExtensionHelper.setKeywords(input.getText().toString());
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(keywordsRow);
                    }
                })
                .create();
        showDialog(dialog);
    }

    private void showCreatePrivacyFolderDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        if (HuanghunPrivacyFolderHelper.isCreated(context, currentAccount)) {
            showPrivacyInfo("隐私文件夹已创建", "当前账号已经创建隐私文件夹。你可以进入“管理隐私聊天”添加需要保护的群组、频道、机器人或私聊。");
            return;
        }
        showPrivacyPasswordSetup(false);
    }

    private void showChangePrivacyPassword() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        if (!HuanghunPrivacyFolderHelper.isCreated(context, currentAccount)) {
            showPrivacyInfo("无法设置", "需要先创建隐私文件夹，才能设置访问密码。");
            return;
        }
        showPrivacyPasswordVerification("验证当前密码", "请输入原密码后，才能设置新的隐私文件夹访问密码。", () -> showPrivacyPasswordSetup(true));
    }

    private void openPrivacyChats() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        if (!HuanghunPrivacyFolderHelper.isCreated(context, currentAccount)) {
            showPrivacyInfo("无法管理", "需要先创建隐私文件夹，才能添加受保护聊天。");
            return;
        }
        showPrivacyPasswordVerification("解锁隐私文件夹", "请输入访问密码后管理受保护聊天。", () -> presentFragment(new HuanghunPrivacyChatsActivity(currentAccount)));
    }

    private void showForgotPrivacyPassword() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        if (!HuanghunPrivacyFolderHelper.isCreated(context, currentAccount)) {
            showPrivacyInfo("无法重置", "当前账号尚未创建隐私文件夹，无需执行密码重置。\n");
            return;
        }
        long remaining = HuanghunPrivacyFolderHelper.getPasswordResetRemaining(context, currentAccount);
        if (remaining > 0L) {
            final AlertDialog[] dialogHolder = new AlertDialog[1];
            final Runnable[] countdownUpdater = new Runnable[1];
            countdownUpdater[0] = () -> {
                AlertDialog activeDialog = dialogHolder[0];
                if (activeDialog == null || !activeDialog.isShowing()) {
                    return;
                }
                long latestRemaining = HuanghunPrivacyFolderHelper.getPasswordResetRemaining(context, currentAccount);
                if (latestRemaining <= 0L) {
                    activeDialog.dismiss();
                    notifyPrivacyRows();
                    return;
                }
                activeDialog.setMessage(buildPrivacyResetCountdownMessage(latestRemaining));
                notifyPrivacyRows();
                AndroidUtilities.runOnUIThread(countdownUpdater[0], 1000L);
            };
            AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                    .setTitle("密码重置倒计时")
                    .setMessage(buildPrivacyResetCountdownMessage(remaining))
                    .setNegativeButton("保留重置", null)
                    .setPositiveButton("取消密码重置", (d, which) -> {
                        HuanghunPrivacyFolderHelper.cancelPasswordReset(context, currentAccount);
                        notifyPrivacyRows();
                        BulletinFactory.of(NekoExtensionsActivity.this).createSimpleBulletin(R.raw.done, "已取消密码重置，隐私文件夹继续保留。\n").show();
                    })
                    .create();
            dialogHolder[0] = dialog;
            dialog.setOnDismissListener(d -> AndroidUtilities.cancelRunOnUIThread(countdownUpdater[0]));
            showDialog(dialog);
            countdownUpdater[0].run();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle("忘记隐私文件夹密码")
                .setMessage("为保护你的本机隐私聊天，密码不会立即清除。点击开始后将进入 24 小时安全等待期；到期后系统自动清除访问密码、受保护聊天列表和本机隐私文件夹。\n\n在倒计时结束前，你可以随时返回这里取消重置。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始 24 小时倒计时", (d, which) -> {
                    if (HuanghunPrivacyFolderHelper.startPasswordReset(context, currentAccount)) {
                        notifyPrivacyRows();
                        BulletinFactory.of(NekoExtensionsActivity.this).createSimpleBulletin(R.raw.done, "密码重置倒计时已开始。24 小时内可随时取消。\n").show();
                    }
                })
                .create();
        showDialog(dialog);
    }

    private String buildPrivacyResetCountdownMessage(long remaining) {
        return "已开始安全等待期。剩余 " + formatPrivacyResetRemaining(remaining) + " 后，系统会自动清除访问密码、受保护聊天列表和本机隐私文件夹。\n\n如果这不是你的操作，请立即取消重置。";
    }

    private String formatPrivacyResetRemaining(long remaining) {
        long totalSeconds = Math.max(0L, (remaining + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.CHINA, "%02d 小时 %02d 分 %02d 秒", hours, minutes, seconds);
    }

    private void showDeletePrivacyFolder() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        if (!HuanghunPrivacyFolderHelper.isCreated(context, currentAccount)) {
            showPrivacyInfo("无法删除", "当前账号尚未创建隐私文件夹。");
            return;
        }
        showPrivacyPasswordVerification("验证访问密码", "请输入隐私文件夹访问密码，验证成功后立即删除当前本机隐私文件夹。", () -> {
            HuanghunPrivacyFolderHelper.delete(context, currentAccount);
            notifyPrivacyRows();
            BulletinFactory.of(NekoExtensionsActivity.this).createSimpleBulletin(R.raw.done, "当前账号的本机隐私文件夹已删除。").show();
        });
    }

    private void showPrivacyPasswordSetup(boolean changing) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout container = createPrivacyCard(context);
        container.addView(createPrivacyHero(context, changing ? "更新本机访问密码" : "创建本机隐私文件夹",
                changing ? "新密码只保存在这台设备上，不会同步到其他客户端。" : "设置完成后，加入文件夹的聊天会在任意入口要求验证。"));

        TextView section = privacyLabel(context, "选择密码规则");
        container.addView(section, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 5));
        RadioGroup policyGroup = new RadioGroup(context);
        policyGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton digits = privacyPolicyRadio(context, "纯数字密码  ·  4–12 位", HuanghunPrivacyFolderHelper.POLICY_DIGITS);
        RadioButton letters = privacyPolicyRadio(context, "纯英文密码  ·  4–12 位", HuanghunPrivacyFolderHelper.POLICY_LETTERS);
        RadioButton mixed = privacyPolicyRadio(context, "英文 + 数字  ·  4–12 位", HuanghunPrivacyFolderHelper.POLICY_MIXED);
        policyGroup.addView(digits);
        policyGroup.addView(letters);
        policyGroup.addView(mixed);
        policyGroup.check(changing ? policyButtonId(HuanghunPrivacyFolderHelper.getPolicy(context, currentAccount), digits, letters, mixed) : digits.getId());
        container.addView(policyGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView hint = privacyHint(context);
        container.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 9));
        EditTextBoldCursor first = privacyPasswordInput(context, "设置访问密码");
        EditTextBoldCursor confirm = privacyPasswordInput(context, "再次输入，确认密码");
        container.addView(first, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(52), 0, 0, 0, 9));
        container.addView(confirm, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(52)));

        TextView matchingState = privacyHint(context);
        container.addView(matchingState, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 9, 0, 0));
        int initialPolicy = changing ? HuanghunPrivacyFolderHelper.getPolicy(context, currentAccount) : HuanghunPrivacyFolderHelper.POLICY_DIGITS;
        applyPrivacyPasswordPolicy(first, confirm, hint, initialPolicy);
        updatePasswordMatchingState(matchingState, first, confirm);
        TextWatcher confirmationWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { updatePasswordMatchingState(matchingState, first, confirm); }
        };
        first.addTextChangedListener(confirmationWatcher);
        confirm.addTextChangedListener(confirmationWatcher);
        policyGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int policy = checkedId == letters.getId() ? HuanghunPrivacyFolderHelper.POLICY_LETTERS : (checkedId == mixed.getId() ? HuanghunPrivacyFolderHelper.POLICY_MIXED : HuanghunPrivacyFolderHelper.POLICY_DIGITS);
            first.setText("");
            confirm.setText("");
            applyPrivacyPasswordPolicy(first, confirm, hint, policy);
            updatePasswordMatchingState(matchingState, first, confirm);
        });

        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle(changing ? "安全设置" : "隐私文件夹")
                .setView(container)
                .setNegativeButton("暂不设置", null)
                .setPositiveButton(changing ? "保存新密码" : "完成创建", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checkedId = policyGroup.getCheckedRadioButtonId();
            int policy = checkedId == letters.getId() ? HuanghunPrivacyFolderHelper.POLICY_LETTERS : (checkedId == mixed.getId() ? HuanghunPrivacyFolderHelper.POLICY_MIXED : HuanghunPrivacyFolderHelper.POLICY_DIGITS);
            String password = first.getText().toString();
            String repeated = confirm.getText().toString();
            if (!HuanghunPrivacyFolderHelper.isPasswordValid(policy, password)) {
                first.setError(HuanghunPrivacyFolderHelper.policyHint(policy));
                first.requestFocus();
                return;
            }
            if (!password.equals(repeated)) {
                matchingState.setText("两次输入不一致，请重新确认。");
                matchingState.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                confirm.setError("请确认两次密码一致");
                confirm.requestFocus();
                return;
            }
            boolean success = changing
                    ? HuanghunPrivacyFolderHelper.changePassword(context, currentAccount, policy, password)
                    : HuanghunPrivacyFolderHelper.create(context, currentAccount, policy, password);
            if (!success) {
                first.setError("保存失败，请稍后重试。");
                return;
            }
            dialog.dismiss();
            notifyPrivacyRows();
            BulletinFactory.of(NekoExtensionsActivity.this).createSimpleBulletin(R.raw.done, changing ? "隐私文件夹访问密码已更新。" : "隐私文件夹已创建，请添加需要保护的聊天。").show();
        }));
        showDialog(dialog);
    }

    private LinearLayout createPrivacyCard(Context context) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(18), AndroidUtilities.dp(4));
        container.setBackground(privacyRoundedDrawable(getThemedColor(Theme.key_windowBackgroundWhite),
                privacyBlend(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), Color.WHITE, .42f), 22, 1));
        return container;
    }

    private LinearLayout createPrivacyHero(Context context, String title, String subtitle) {
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        hero.setBackground(privacyRoundedDrawable(privacyBlend(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), getThemedColor(Theme.key_windowBackgroundWhite), .16f),
                privacyBlend(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), Color.WHITE, .70f), 17, 1));
        TextView eyebrow = new TextView(context);
        eyebrow.setText("本机安全保护");
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        eyebrow.setTypeface(AndroidUtilities.bold());
        eyebrow.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        hero.addView(eyebrow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        hero.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 4));
        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        subtitleView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        hero.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return hero;
    }

    private TextView privacyLabel(Context context, String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        return label;
    }

    private TextView privacyHint(Context context) {
        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setLineSpacing(AndroidUtilities.dp(2), 1f);
        hint.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        return hint;
    }

    private GradientDrawable privacyRoundedDrawable(int color, int strokeColor, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(AndroidUtilities.dp(radiusDp));
        drawable.setStroke(Math.max(1, AndroidUtilities.dp(strokeDp)), strokeColor);
        return drawable;
    }

    private int privacyBlend(int color1, int color2, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                (int) (Color.red(color1) * a + Color.red(color2) * (1f - a)),
                (int) (Color.green(color1) * a + Color.green(color2) * (1f - a)),
                (int) (Color.blue(color1) * a + Color.blue(color2) * (1f - a))
        );
    }

    private void updatePasswordMatchingState(TextView state, EditTextBoldCursor first, EditTextBoldCursor confirm) {
        String password = first.getText().toString();
        String repeated = confirm.getText().toString();
        if (password.isEmpty() && repeated.isEmpty()) {
            state.setText("输入两次相同的密码后即可完成设置。");
            state.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        } else if (repeated.isEmpty()) {
            state.setText("请再次输入密码以完成确认。");
            state.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        } else if (password.equals(repeated)) {
            state.setText("两次密码一致，可以安全保存。");
            state.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        } else {
            state.setText("两次密码暂不一致，请检查后重新输入。");
            state.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        }
    }

    private RadioButton privacyPolicyRadio(Context context, String text, int policy) {
        RadioButton button = new RadioButton(context);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTag(policy);
        button.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(8), AndroidUtilities.dp(2));
        button.setBackground(privacyRoundedDrawable(privacyBlend(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), .05f),
                privacyBlend(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), Color.WHITE, .62f), 12, 1));
        return button;
    }

    private int policyButtonId(int policy, RadioButton digits, RadioButton letters, RadioButton mixed) {
        if (policy == HuanghunPrivacyFolderHelper.POLICY_LETTERS) {
            return letters.getId();
        }
        if (policy == HuanghunPrivacyFolderHelper.POLICY_MIXED) {
            return mixed.getId();
        }
        return digits.getId();
    }

    private EditTextBoldCursor privacyPasswordInput(Context context, String hint) {
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        input.setHint(hint);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        input.setBackground(privacyRoundedDrawable(privacyBlend(getThemedColor(Theme.key_windowBackgroundWhiteInputField), Color.WHITE, .18f),
                privacyBlend(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), Color.WHITE, .56f), 14, 1));
        input.setPadding(AndroidUtilities.dp(15), 0, AndroidUtilities.dp(15), 0);
        return input;
    }

    private void applyPrivacyPasswordPolicy(EditTextBoldCursor first, EditTextBoldCursor confirm, TextView hint, int policy) {
        int type = policy == HuanghunPrivacyFolderHelper.POLICY_DIGITS
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        first.setInputType(type);
        confirm.setInputType(type);
        hint.setText(HuanghunPrivacyFolderHelper.policyHint(policy));
    }

    private void showPrivacyPasswordVerification(String title, String descriptionText, Runnable verified) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout container = createPrivacyCard(context);
        container.addView(createPrivacyHero(context, title, descriptionText));
        TextView rule = privacyHint(context);
        int policy = HuanghunPrivacyFolderHelper.getPolicy(context, currentAccount);
        rule.setText("验证规则：" + HuanghunPrivacyFolderHelper.policyHint(policy) + " 连续输错 3 次将锁定 30 分钟。");
        container.addView(rule, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 9));
        EditTextBoldCursor password = privacyPasswordInput(context, "输入访问密码后继续");
        applyPrivacyPasswordPolicy(password, password, rule, policy);
        rule.setText("验证规则：" + HuanghunPrivacyFolderHelper.policyHint(policy) + " 连续输错 3 次将锁定 30 分钟。");
        container.addView(password, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(52)));
        TextView status = privacyHint(context);
        status.setText("密码仅在当前设备验证，不会上传或同步。");
        container.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 9, 0, 0));

        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle("安全验证")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("验证并继续", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int result = HuanghunPrivacyFolderHelper.verifyPassword(context, currentAccount, password.getText().toString());
            if (result == HuanghunPrivacyFolderHelper.VERIFY_OK) {
                status.setText("验证成功，正在继续…");
                status.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
                dialog.dismiss();
                verified.run();
                return;
            }
            if (result == HuanghunPrivacyFolderHelper.VERIFY_LOCKED) {
                status.setText(HuanghunPrivacyFolderHelper.getLockMessage(context, currentAccount));
            } else if (result == HuanghunPrivacyFolderHelper.VERIFY_NOT_CREATED) {
                status.setText("需要先创建隐私文件夹。\n");
            } else {
                status.setText("密码错误。连续输错 3 次将锁定 30 分钟。");
            }
            status.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            password.setText("");
            password.requestFocus();
        }));
        showDialog(dialog);
    }

    private void showPrivacyInfo(String title, String message) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(context, resourceProvider)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.OK), null)
                .create());
    }

    private void notifyPrivacyRows() {
        if (listAdapter == null) {
            return;
        }
        listAdapter.notifyItemChanged(createPrivacyFolderRow);
        listAdapter.notifyItemChanged(managePrivacyChatsRow);
        listAdapter.notifyItemChanged(changePrivacyPasswordRow);
        listAdapter.notifyItemChanged(deletePrivacyFolderRow);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = holder.getItemViewType();
            if (type == TYPE_HEADER) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                String headerText = position == activeHeaderRow
                        ? "活跃专区"
                        : (position == videoHeaderRow
                        ? "视频专区"
                        : (position == privacyHeaderRow
                        ? "隐私专区"
                        : (position == blockHeaderRow ? getString(R.string.HuanghunBlockZone) : getString(R.string.HuanghunCleanupZone))));
                cell.setText(headerText);
            } else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                if (position == activeDirectionRow) {
                    cell.setTextAndValue("点赞消息方向", getActiveDirectionSummary(), true);
                } else if (position == activeTargetRow) {
                    cell.setTextAndValue("点赞对象", HuanghunActiveZoneHelper.getTargetSummary(), true);
                } else if (position == activeEmojiRow) {
                    cell.setTextAndValue("点赞表情", NekoConfig.huanghunActiveZoneEmoji.String(), false);
                } else if (position == selectBuiltinVideosRow) {
                    int count = HuanghunVideoLibraryHelper.getVideoCount(mContext, currentAccount);
                    cell.setTextAndValue("选取内置视频", "已选 " + count + " 个", true);
                } else if (position == callCameraFeatureRow) {
                    cell.setTextAndValue("摄像头功能", "通话专区", true);
                } else if (position == viewBuiltinVideosRow) {
                    cell.setTextAndValue("查看内置视频", "浏览已选视频", true);
                } else if (position == deleteBuiltinVideosRow) {
                    cell.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    cell.setText("删除视频", false);
                } else if (position == clearBotsRow) {
                    cell.setText(getString(R.string.HuanghunClearBotData), true);
                } else if (position == leaveGroupsRow) {
                    cell.setText(getString(R.string.HuanghunLeaveAllGroups), true);
                } else if (position == clearContactsRow) {
                    cell.setText(getString(R.string.HuanghunClearContacts), true);
                } else if (position == clearChatsRow) {
                    cell.setText(getString(R.string.HuanghunClearChats), true);
                } else if (position == resetProfileRow) {
                    cell.setText(getString(R.string.HuanghunResetProfile), true);
                } else if (position == clearDeletedAccountsRow) {
                    cell.setText(getString(R.string.HuanghunClearDeletedAccounts), true);
                } else if (position == clearMessagesByTimeRow) {
                    cell.setText(getString(R.string.HuanghunClearMessagesByTime), true);
                } else if (position == clearAllRow) {
                    cell.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    cell.setText(getString(R.string.HuanghunClearAll), false);
                } else if (position == createPrivacyFolderRow) {
                    boolean created = HuanghunPrivacyFolderHelper.isCreated(mContext, currentAccount);
                    cell.setTextAndValue("创建隐私文件夹", created ? "已创建" : "未创建", true);
                } else if (position == managePrivacyChatsRow) {
                    int count = HuanghunPrivacyFolderHelper.getProtectedDialogs(mContext, currentAccount).size();
                    cell.setTextAndValue("管理隐私聊天", HuanghunPrivacyFolderHelper.isCreated(mContext, currentAccount) ? "已保护 " + count + " 个聊天" : "请先创建", true);
                } else if (position == changePrivacyPasswordRow) {
                    cell.setText("设置隐私文件夹访问密码", true);
                } else if (position == forgotPrivacyPasswordRow) {
                    long remaining = HuanghunPrivacyFolderHelper.getPasswordResetRemaining(mContext, currentAccount);
                    cell.setTextAndValue("忘记隐私文件夹密码", remaining > 0L ? "重置倒计时 " + formatPrivacyResetRemaining(remaining) : "24 小时安全重置", true);
                } else if (position == deletePrivacyFolderRow) {
                    cell.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    cell.setText("删除当前隐私文件夹", false);
                } else if (position == keywordsRow) {
                    cell.setTextAndValue(getString(R.string.HuanghunBlockKeywords), LocaleController.formatString(R.string.HuanghunBlockKeywordsCount, HuanghunExtensionHelper.getKeywordCount()), false);
                }
            } else if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                if (position == activeEnabledRow) {
                    cell.setTextAndCheck("自动表情点赞", NekoConfig.huanghunActiveZoneEnabled.Bool(), true);
                } else if (position == builtinCameraRow) {
                    cell.setTextAndCheck("启动内置相机", NekoConfig.huanghunBuiltinCameraEnabled.Bool(), true);
                } else if (position == builtinVideoSoundRow) {
                    cell.setTextAndCheck("视频声音", NekoConfig.huanghunBuiltinVideoSound.Bool(), true);
                } else if (position == builtinRoundVideoRow) {
                    cell.setTextAndCheck("圆形视频", NekoConfig.huanghunBuiltinRoundVideo.Bool(), true);
                } else if (position == builtinSquareVideoRow) {
                    cell.setTextAndCheck("方形视频", NekoConfig.huanghunBuiltinSquareVideo.Bool(), true);
                } else if (position == videoToGifRow) {
                    cell.setTextAndCheck("视频转 GIF（无声）", NekoConfig.huanghunVideoToGif.Bool(), true);
                } else if (position == blockNonContactsRow) {
                    cell.setTextAndCheck(getString(R.string.HuanghunBlockNonContacts), NekoConfig.huanghunBlockNonContacts.Bool(), true);
                } else {
                    cell.setTextAndCheck("屏蔽共同群所有消息", NekoConfig.huanghunBlockMutualGroupMessages.Bool(), true);
                }
            } else if (type == TYPE_INFO_PRIVACY) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(position == activeNoticeRow ? "新消息到达后自动添加点赞表情。可以选择对方消息、自己消息或双方消息，也可以限定为全部用户或指定用户。默认开启、默认点赞对象为全部用户、默认表情为 👍。" : (position == videoNoticeRow ? "内置视频仅保存在当前设备和当前账号中。圆形视频默认开启，方形视频默认关闭；两者可同时关闭，但不能同时开启。开启内置相机后，录制会循环预览所选视频，并按当前模式发送。关闭两种模式或关闭内置相机开关即可恢复 Telegram 官方真实摄像头录制。" : (position == cleanupNoticeRow ? getString(R.string.HuanghunCleanupNotice) : (position == privacyNoticeRow ? "隐私文件夹仅保存在本机。已加入的群组、频道、机器人或私聊会在本客户端的任意入口先要求密码验证；连续输错 3 次将锁定 30 分钟。忘记密码后可启动 24 小时安全重置，期间可随时取消。" : getString(R.string.HuanghunBlockNotice)))));
                cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            } else if (type == TYPE_SHADOW) {
                holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == activeHeaderRow || position == videoHeaderRow || position == cleanupHeaderRow || position == privacyHeaderRow || position == blockHeaderRow) {
                return TYPE_HEADER;
            }
            if (position == activeEnabledRow || position == builtinCameraRow || position == builtinVideoSoundRow || position == builtinRoundVideoRow || position == builtinSquareVideoRow || position == videoToGifRow || position == blockNonContactsRow || position == blockMutualGroupMessagesRow) {
                return TYPE_CHECK;
            }
            if (position == activeNoticeRow || position == videoNoticeRow || position == cleanupNoticeRow || position == privacyNoticeRow || position == blockNoticeRow) {
                return TYPE_INFO_PRIVACY;
            }
            if (position == activeEndRow || position == videoEndRow || position == cleanupEndRow || position == privacyEndRow || position == blockEndRow) {
                return TYPE_SHADOW;
            }
            return TYPE_SETTINGS;
        }
    }

    private void executeCleanupDirectly(HuanghunExtensionHelper.CleanupAction action) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog progress = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        HuanghunExtensionHelper.runCleanup(currentAccount, action, scheduled -> AndroidUtilities.runOnUIThread(() -> {
            if (progress.isShowing()) {
                progress.dismiss();
            }
            BulletinFactory.of(NekoExtensionsActivity.this)
                    .createSimpleBulletin(R.raw.done, LocaleController.formatString(R.string.HuanghunCleanupScheduled, scheduled))
                    .show();
        }));
    }

    private void showTimeRangeCleanupDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.HuanghunClearMessagesByTime));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), 0);

        TextView modeTitle = new TextView(context);
        modeTitle.setText("请选择删除模式：");
        modeTitle.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        container.addView(modeTitle);

        final android.widget.RadioGroup modeGroup = new android.widget.RadioGroup(context);
        modeGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        android.widget.RadioButton rb1 = new android.widget.RadioButton(context);
        rb1.setId(View.generateViewId());
        rb1.setText("删除指定时间之前");
        modeGroup.addView(rb1);
        android.widget.RadioButton rb2 = new android.widget.RadioButton(context);
        rb2.setId(View.generateViewId());
        rb2.setText("指定时间段（例如：2025-08-16 至 2026-03-17）");
        modeGroup.addView(rb2);
        modeGroup.check(rb1.getId());
        container.addView(modeGroup);

        TextView dateTitle = new TextView(context);
        dateTitle.setText("\n请输入日期（格式 YYYY-MM-DD）：");
        dateTitle.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        container.addView(dateTitle);

        LinearLayout dateLayout = new LinearLayout(context);
        dateLayout.setOrientation(LinearLayout.HORIZONTAL);

        EditTextBoldCursor inputStart = new EditTextBoldCursor(context);
        inputStart.setHint("开始日期");
        inputStart.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        inputStart.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        inputStart.setInputType(InputType.TYPE_CLASS_DATETIME);
        inputStart.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField));
        dateLayout.addView(inputStart, LayoutHelper.createLinear(0, 48, 1.0f, 0, 0, 4, 0));

        EditTextBoldCursor inputEnd = new EditTextBoldCursor(context);
        inputEnd.setHint("结束日期");
        inputEnd.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        inputEnd.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        inputEnd.setInputType(InputType.TYPE_CLASS_DATETIME);
        inputEnd.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField));
        dateLayout.addView(inputEnd, LayoutHelper.createLinear(0, 48, 1.0f, 4, 0, 0, 0));

        inputStart.setOnClickListener(v -> showDatePicker(inputStart));
        inputEnd.setOnClickListener(v -> showDatePicker(inputEnd));
        HuanghunExtensionHelper.loadAccountMessageTimeBounds(currentAccount, (earliestMillis, latestMillis) -> {
            inputStart.setText(formatDate(earliestMillis));
            inputEnd.setText(formatDate(latestMillis));
        });
        container.addView(dateLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));

        TextView scopeTitle = new TextView(context);
        scopeTitle.setText("\n请选择清理范围：");
        scopeTitle.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        container.addView(scopeTitle);

        final android.widget.RadioGroup scopeGroup = new android.widget.RadioGroup(context);
        scopeGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        android.widget.RadioButton sb1 = new android.widget.RadioButton(context);
        sb1.setId(View.generateViewId());
        sb1.setText("群或频道");
        scopeGroup.addView(sb1);
        android.widget.RadioButton sb2 = new android.widget.RadioButton(context);
        sb2.setId(View.generateViewId());
        sb2.setText("用户发送的消息（包含机器人）");
        scopeGroup.addView(sb2);
        android.widget.RadioButton sb3 = new android.widget.RadioButton(context);
        sb3.setId(View.generateViewId());
        sb3.setText("两者都选");
        scopeGroup.addView(sb3);
        scopeGroup.check(sb1.getId());
        container.addView(scopeGroup);

        builder.setView(container);
        builder.setPositiveButton(getString(R.string.OK), null);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean rangeMode = rb2.isChecked();
            long endTime = parseDateAtDayBoundary(inputEnd.getText().toString(), true);
            long startTime = rangeMode ? parseDateAtDayBoundary(inputStart.getText().toString(), false) : 0L;
            if (endTime <= 0L) {
                inputEnd.setError("请选择有效的结束日期");
                return;
            }
            if (rangeMode && startTime <= 0L) {
                inputStart.setError("请选择有效的开始日期");
                return;
            }
            if (rangeMode && startTime > endTime) {
                inputStart.setError("开始日期不能晚于结束日期");
                return;
            }
            int mode = rangeMode ? 1 : 0;
            int scope = sb3.isChecked() ? 0 : (sb2.isChecked() ? 2 : 1);
            dialog.dismiss();
            showTimeCleanupConfirmation(mode, startTime, endTime, scope);
        }));
        showDialog(dialog);
    }

    private void showDatePicker(EditTextBoldCursor target) {
        Calendar calendar = Calendar.getInstance();
        long selected = parseDateAtDayBoundary(target.getText().toString(), false);
        if (selected > 0L) {
            calendar.setTimeInMillis(selected);
        }
        DatePickerDialog picker = new DatePickerDialog(getParentActivity(), (view, year, month, dayOfMonth) -> {
            target.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

    private String formatDate(long value) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(value));
    }

    private long parseDateAtDayBoundary(String text, boolean endOfDay) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setLenient(false);
            Date date = format.parse(text.trim());
            if (date == null) {
                return 0L;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.set(Calendar.HOUR_OF_DAY, endOfDay ? 23 : 0);
            calendar.set(Calendar.MINUTE, endOfDay ? 59 : 0);
            calendar.set(Calendar.SECOND, endOfDay ? 59 : 0);
            calendar.set(Calendar.MILLISECOND, endOfDay ? 999 : 0);
            return calendar.getTimeInMillis();
        } catch (ParseException | NullPointerException ignored) {
            return 0L;
        }
    }

    private void showTimeCleanupConfirmation(int mode, long startTime, long endTime, int scope) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        String timeRange = mode == 0 ? "结束日期之前的记录" : formatDate(startTime) + " 至 " + formatDate(endTime) + "（包含当天）";
        String scopeText = scope == 0 ? "群或频道和用户消息" : (scope == 1 ? "群或频道" : "用户消息（包含机器人）");
        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle("确认清理指定时间记录")
                .setMessage("确认要清理 " + timeRange + " 的 " + scopeText + " 吗？\n\n点击“确认清理”后将立即开始执行；点击“取消”则不会进行任何清理。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清理", (d, which) -> executeTimeCleanup(mode, startTime, endTime, scope))
                .create();
        showDialog(dialog);
    }

    private void executeTimeCleanup(int mode, long startTime, long endTime, int scope) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog progress = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        HuanghunExtensionHelper.clearMessagesByTime(currentAccount, mode, startTime, endTime, scope, scheduled -> AndroidUtilities.runOnUIThread(() -> {
            if (progress.isShowing()) {
                progress.dismiss();
            }
            BulletinFactory.of(NekoExtensionsActivity.this)
                    .createSimpleBulletin(R.raw.done, LocaleController.formatString(R.string.HuanghunCleanupScheduled, scheduled))
                    .show();
        }));
    }
}
