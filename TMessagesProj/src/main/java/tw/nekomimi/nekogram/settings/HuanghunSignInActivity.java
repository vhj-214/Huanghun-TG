package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.messenger.DialogObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tw.nekomimi.nekogram.helpers.HuanghunSignInHelper;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Daily recurring message automation page. Times are always interpreted in Asia/Shanghai time. */
public class HuanghunSignInActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {
    private static final int HEADER = 0;
    private static final int ACTIONS = 1;
    private static final int ENABLED = 2;
    private static final int INFO = 3;
    private static final int TASKS = 4;
    private static final int TYPE_ACTIONS = 1001;
    private static final int TYPE_TASK = 1002;
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d{1,2}):([0-5]\\d)$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,}$");

    private ArrayList<HuanghunSignInHelper.Task> tasks = new ArrayList<>();
    private long selectedDialogId;
    private String selectedDialogName = "";
    private boolean restoreFormAfterPicker;
    private HuanghunSignInHelper.Task pendingEditTask;
    private String pendingTarget = "";
    private String pendingContent = "";
    private String pendingTime = "00:01";

    @Override
    protected String getActionBarTitle() {
        return "自动签到函数";
    }

    @Override
    protected String getKey() {
        return "sign_in";
    }

    @Override
    protected void updateRows() {
        tasks = HuanghunSignInHelper.getTasks(currentAccount);
        rowCount = TASKS + tasks.size();
    }

    @Override
    public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messageSendError);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messageSendError);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if ((id == NotificationCenter.messageReceivedByServer || id == NotificationCenter.messageSendError) && account == currentAccount) {
            AndroidUtilities.runOnUIThread(() -> {
                tasks = HuanghunSignInHelper.getTasks(currentAccount);
                if (listAdapter != null) listAdapter.notifyDataSetChanged();
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Opening this page is also a retry point for tasks that missed their
        // time while the device had no network connection.
        HuanghunSignInHelper.executeDueTasks(currentAccount);
        tasks = HuanghunSignInHelper.getTasks(currentAccount);
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (restoreFormAfterPicker && getParentActivity() != null) {
            restoreFormAfterPicker = false;
            showTaskDialog(pendingEditTask, pendingContent, pendingTime, true, pendingTarget);
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == ENABLED) {
            HuanghunSignInHelper.setEnabled(currentAccount, !HuanghunSignInHelper.isEnabled(currentAccount));
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(ENABLED);
            }
        } else if (position >= TASKS && position - TASKS < tasks.size()) {
            showTaskDialog(tasks.get(position - TASKS));
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= TASKS && position - TASKS < tasks.size()) {
            confirmDeleteTask(tasks.get(position - TASKS));
            return true;
        }
        return false;
    }

    private void confirmDeleteTask(HuanghunSignInHelper.Task task) {
        Context context = getParentActivity();
        if (context == null) return;
        new AlertDialog.Builder(context, resourceProvider)
                .setTitle("删除签到任务？")
                .setMessage(task.target + "\n" + task.time + "  ·  " + task.content)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    tasks.remove(task);
                    HuanghunSignInHelper.saveTasks(currentAccount, tasks);
                    updateRows();
                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                })
                .show();
    }

    private void runAllNow() {
        if (tasks.isEmpty()) {
            Context context = getParentActivity();
            if (context != null) Toast.makeText(context, "请先添加至少一个签到任务", Toast.LENGTH_SHORT).show();
            return;
        }
        for (HuanghunSignInHelper.Task task : tasks) {
            try {
                SendMessagesHelper helper = org.telegram.messenger.AccountInstance.getInstance(currentAccount).getSendMessagesHelper();
                int previousMessageId = helper.getSendingMessageId(task.dialogId);
                helper.sendMessage(SendMessagesHelper.SendMessageParams.of(task.content, task.dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false));
                int messageId = helper.getSendingMessageId(task.dialogId);
                task.pendingMessageId = messageId != 0 && messageId != previousMessageId ? messageId : 0;
                task.status = task.pendingMessageId == 0 ? "已提交发送" : "发送中";
                if (task.pendingMessageId == 0) {
                    task.status = "已提交发送，等待确认";
                }
            } catch (Throwable e) {
                task.pendingMessageId = 0;
                task.status = "失败【目标永远存在禁言无法发送】";
                FileLog.e(e);
            }
        }
        HuanghunSignInHelper.saveTasks(currentAccount, tasks);
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private void resendTaskNow(HuanghunSignInHelper.Task task) {
        if (task == null) return;
        Context context = getParentActivity();
        boolean accepted = HuanghunSignInHelper.resendTaskNow(currentAccount, task.id);
        if (!accepted && context != null) {
            Toast.makeText(context, "此任务正在发送，请稍候", Toast.LENGTH_SHORT).show();
        }
        tasks = HuanghunSignInHelper.getTasks(currentAccount);
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private void showTaskDialog(HuanghunSignInHelper.Task editing) {
        showTaskDialog(editing, editing == null ? "" : editing.content, editing == null ? "00:01" : editing.time);
    }

    private void showTaskDialog(HuanghunSignInHelper.Task editing, String initialContent, String initialTime) {
        showTaskDialog(editing, initialContent, initialTime, false);
    }

    private void showTaskDialog(HuanghunSignInHelper.Task editing, String initialContent, String initialTime, boolean keepSelectedTarget) {
        showTaskDialog(editing, initialContent, initialTime, keepSelectedTarget, selectedDialogName);
    }

    private void showTaskDialog(HuanghunSignInHelper.Task editing, String initialContent, String initialTime, boolean keepSelectedTarget, String initialTarget) {
        Context context = getParentActivity();
        if (context == null) return;

        if (!keepSelectedTarget) {
            selectedDialogId = editing == null ? 0 : editing.dialogId;
            selectedDialogName = editing == null ? "" : editing.target;
        }

        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4), AndroidUtilities.dp(20), AndroidUtilities.dp(4));

        EditTextBoldCursor target = createInput(context, "用户名、ID 或 t.me 链接", false);
        target.setText(initialTarget);
        TextView choose = addTargetField(context, form, target);

        EditTextBoldCursor content = createInput(context, "请输入要发送的内容", true);
        content.setText(initialContent);
        addField(context, form, "设定内容", content, 88, false);

        EditTextBoldCursor time = createInput(context, "例如 9:58 或 10:20（北京时间）", false);
        time.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        time.setText(initialTime);
        addField(context, form, "设定时间", time, 52, false);

        TextView error = new TextView(context);
        error.setTextColor(0xffd94343);
        error.setTextSize(13);
        error.setVisibility(View.GONE);
        form.addView(error, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.addView(form, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle(editing == null ? "目标设定" : "编辑签到任务")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        choose.setOnClickListener(v -> openChatPicker(dialog, editing, target, content, time));
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String targetText = target.getText() == null ? "" : target.getText().toString().trim();
                String messageText = content.getText() == null ? "" : content.getText().toString().trim();
                String normalizedTime = HuanghunSignInHelper.normalizeTime(time.getText() == null ? "" : time.getText().toString());
                target.setError(null);
                content.setError(null);
                time.setError(null);
                error.setVisibility(View.GONE);

                if (targetText.isEmpty()) {
                    target.setError("请填写目标或使用“选择聊天”");
                    showFormError(error, "还没有设置目标。请选择聊天，或填写可识别的用户名、ID / 链接。");
                    return;
                }
                if (messageText.isEmpty()) {
                    content.setError("请输入要发送的内容");
                    showFormError(error, "发送内容不能为空。");
                    return;
                }
                if (!TIME_PATTERN.matcher(normalizedTime).matches()) {
                    time.setError("时间格式应为 9:58 或 10:20");
                    showFormError(error, "时间无效，请输入 00:00–23:59，例如 9:58 或 10:20。");
                    return;
                }
                Matcher matcher = TIME_PATTERN.matcher(normalizedTime);
                if (!matcher.matches()) return;
                int hour = Integer.parseInt(matcher.group(1));
                if (hour > 23) {
                    time.setError("小时应为 0–23");
                    showFormError(error, "小时应为 0–23，分钟应为 00–59。");
                    return;
                }
                normalizedTime = String.format(Locale.US, "%02d:%s", hour, matcher.group(2));

                long chosenId = selectedDialogId;
                if (chosenId != 0 && (targetText.equals(selectedDialogName) || targetText.equals(String.valueOf(chosenId)))) {
                    saveTask(dialog, editing, chosenId, selectedDialogName, messageText, normalizedTime);
                    return;
                }
                resolveTypedTarget(targetText, target, error, dialog, editing, messageText, normalizedTime);
            });
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
        showDialog(dialog);
    }

    private void addField(Context context, LinearLayout form, String label, EditTextBoldCursor input, int inputHeight, boolean addChooseGap) {
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextSize(14);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        form.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 26, 0, 0, 0, 3));

        LinearLayout inputWrap = new LinearLayout(context);
        inputWrap.setOrientation(LinearLayout.VERTICAL);
        inputWrap.setGravity(Gravity.CENTER_VERTICAL);
        inputWrap.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        inputWrap.setBackground(roundedBackground(getThemedColor(Theme.key_windowBackgroundGray), AndroidUtilities.dp(12)));
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        inputWrap.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        form.addView(inputWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, inputHeight, 0, 0, 0, addChooseGap ? 5 : 12));
    }

    private TextView addTargetField(Context context, LinearLayout form, EditTextBoldCursor target) {
        TextView title = new TextView(context);
        title.setText("目标设定");
        title.setTextSize(14);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        form.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 26, 0, 0, 0, 3));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout inputWrap = new LinearLayout(context);
        inputWrap.setOrientation(LinearLayout.VERTICAL);
        inputWrap.setGravity(Gravity.CENTER_VERTICAL);
        inputWrap.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        inputWrap.setBackground(roundedBackground(getThemedColor(Theme.key_windowBackgroundGray), AndroidUtilities.dp(12)));
        target.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        inputWrap.addView(target, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        row.addView(inputWrap, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(50), 1f));

        TextView choose = createTextButton(context, "选择", true);
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(88), AndroidUtilities.dp(48));
        chooseParams.leftMargin = AndroidUtilities.dp(12);
        row.addView(choose, chooseParams);
        form.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 0, 0, 12));
        return choose;
    }

    private EditTextBoldCursor createInput(Context context, String hint, boolean multiline) {
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(16);
        input.setSingleLine(!multiline);
        if (multiline) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(6));
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            input.setPadding(0, 0, 0, 0);
        }
        input.setHint(hint);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        input.setSelectAllOnFocus(false);
        return input;
    }

    private void openChatPicker(AlertDialog dialog, HuanghunSignInHelper.Task editing, EditTextBoldCursor target, EditTextBoldCursor content, EditTextBoldCursor time) {
        pendingEditTask = editing;
        pendingTarget = target.getText() == null ? "" : target.getText().toString();
        pendingContent = content.getText() == null ? "" : content.getText().toString();
        pendingTime = time.getText() == null ? "00:01" : time.getText().toString();
        restoreFormAfterPicker = true;
        dialog.dismiss();

        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("checkCanWrite", false);
        args.putBoolean("allowGlobalSearch", true);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty() || dids.get(0).dialogId == 0) return false;
            selectedDialogId = dids.get(0).dialogId;
            selectedDialogName = resolveName(selectedDialogId);
            pendingTarget = selectedDialogName;
            picker.finishFragment();
            return true;
        });
        presentFragment(picker);
    }

    private void resolveTypedTarget(String rawTarget, EditTextBoldCursor target, TextView error, AlertDialog dialog,
                                    HuanghunSignInHelper.Task editing, String message, String time) {
        String candidate = rawTarget.trim();
        if (candidate.matches("-?\\d+")) {
            try {
                long dialogId = Long.parseLong(candidate);
                if (dialogId != 0 && MessagesController.getInstance(currentAccount).getUserOrChat(dialogId) != null) {
                    String name = resolveName(dialogId);
                    selectedDialogId = dialogId;
                    selectedDialogName = name;
                    target.setText(name);
                    saveTask(dialog, editing, dialogId, name, message, time);
                    return;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            target.setError("找不到此 ID 对应的聊天");
            showFormError(error, "无法识别这个 ID。建议点击“选择聊天”以确保目标正确。");
            return;
        }

        String username = extractUsername(candidate);
        if (TextUtils.isEmpty(username) || !USERNAME_PATTERN.matcher(username).matches()) {
            target.setError("请输入有效的用户名或选择聊天");
            showFormError(error, "支持公开用户名、数字 ID 或 t.me/用户名链接；邀请链接请先加入聊天后再从列表选择。");
            return;
        }

        TLObject cached = MessagesController.getInstance(currentAccount).getUserOrChat(username);
        if (cached instanceof TLRPC.User || cached instanceof TLRPC.Chat) {
            long dialogId = cached instanceof TLRPC.User ? ((TLRPC.User) cached).id : -((TLRPC.Chat) cached).id;
            selectedDialogId = dialogId;
            selectedDialogName = resolveName(dialogId);
            target.setText(selectedDialogName);
            saveTask(dialog, editing, dialogId, selectedDialogName, message, time);
            return;
        }

        View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) positive.setEnabled(false);
        showFormError(error, "正在查找 @" + username + "…");
        TLRPC.TL_contacts_resolveUsername request = new TLRPC.TL_contacts_resolveUsername();
        request.username = username;
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, requestError) -> AndroidUtilities.runOnUIThread(() -> {
            if (!dialog.isShowing()) return;
            if (positive != null) positive.setEnabled(true);
            if (requestError != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                target.setError("没有找到此公开用户名");
                showFormError(error, "找不到 @" + username + "。请检查拼写、网络，或直接点击“选择聊天”。");
                return;
            }
            TLRPC.TL_contacts_resolvedPeer result = (TLRPC.TL_contacts_resolvedPeer) response;
            MessagesController.getInstance(currentAccount).putUsers(result.users, false);
            MessagesController.getInstance(currentAccount).putChats(result.chats, false);
            org.telegram.messenger.AccountInstance.getInstance(currentAccount).getMessagesStorage().putUsersAndChats(result.users, result.chats, false, true);
            if (result.peer == null) {
                target.setError("无法识别该聊天");
                showFormError(error, "Telegram 返回了无法识别的目标，请从聊天列表中选择。");
                return;
            }
            long dialogId = DialogObject.getPeerDialogId(result.peer);
            if (dialogId == 0) {
                target.setError("无法识别该聊天");
                showFormError(error, "Telegram 返回了无法识别的目标，请从聊天列表中选择。");
                return;
            }
            selectedDialogId = dialogId;
            selectedDialogName = resolveName(dialogId);
            target.setText(selectedDialogName);
            saveTask(dialog, editing, dialogId, selectedDialogName, message, time);
        }));
    }

    private String extractUsername(String value) {
        String username = value.trim();
        username = username.replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "");
        if (username.regionMatches(true, 0, "t.me/", 0, 5)) {
            username = username.substring(5);
        } else if (username.regionMatches(true, 0, "telegram.me/", 0, 12)) {
            username = username.substring(12);
        }
        if (username.startsWith("@")) username = username.substring(1);
        int slash = username.indexOf('/');
        if (slash >= 0) username = username.substring(0, slash);
        int query = username.indexOf('?');
        if (query >= 0) username = username.substring(0, query);
        if (username.startsWith("+") || username.startsWith("joinchat")) return "";
        return username;
    }

    private void saveTask(AlertDialog dialog, HuanghunSignInHelper.Task editing, long dialogId, String name, String content, String time) {
        if (dialogId == 0) return;
        HuanghunSignInHelper.Task task = editing == null ? new HuanghunSignInHelper.Task() : editing;
        task.id = editing == null ? System.currentTimeMillis() : editing.id;
        task.dialogId = dialogId;
        task.target = TextUtils.isEmpty(name) ? String.valueOf(dialogId) : name;
        task.content = content;
        task.time = time;
        if (editing == null) {
            task.status = "等待执行";
            tasks.add(task);
        }
        HuanghunSignInHelper.saveTasks(currentAccount, tasks);
        updateRows();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
        dialog.dismiss();
    }

    private void showFormError(TextView view, String text) {
        view.setText(text);
        view.setVisibility(View.VISIBLE);
    }

    private String resolveName(long dialogId) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        if (dialogId > 0) {
            TLRPC.User user = controller.getUser(dialogId);
            if (user != null) {
                if (!TextUtils.isEmpty(user.username)) return "@" + user.username;
                return (user.first_name + " " + user.last_name).trim();
            }
        } else {
            TLRPC.Chat chat = controller.getChat(-dialogId);
            if (chat != null) return chat.title;
        }
        return String.valueOf(dialogId);
    }

    private String shortError(Throwable error) {
        String message = error.getMessage();
        if (TextUtils.isEmpty(message)) return "请检查网络和目标权限";
        return message.length() > 36 ? message.substring(0, 36) + "…" : message;
    }

    private TextView createTextButton(Context context, String label, boolean primary) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(AndroidUtilities.dp(48));
        button.setMinWidth(AndroidUtilities.dp(48));
        button.setMaxLines(1);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setTextColor(primary ? Color.WHITE : 0xff2584c7);
        button.setBackground(roundedBackground(primary ? 0xff2584c7 : 0xffeaf3fa, AndroidUtilities.dp(12)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private GradientDrawable roundedBackground(int color, float radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new Adapter(context);
    }

    private class Adapter extends BaseListAdapter {
        Adapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_ACTIONS) {
                return new RecyclerView.ViewHolder(new ActionRow(mContext)) {};
            }
            if (viewType == TYPE_TASK) {
                TaskRow row = new TaskRow(mContext);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.leftMargin = AndroidUtilities.dp(16);
                params.rightMargin = AndroidUtilities.dp(16);
                params.bottomMargin = AndroidUtilities.dp(12);
                row.setLayoutParams(params);
                return new RecyclerView.ViewHolder(row) {};
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_TASK || super.isEnabled(holder);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = holder.getItemViewType();
            if (type == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText("自动签到任务");
            } else if (type == TYPE_CHECK) {
                ((TextCheckCell) holder.itemView).setTextAndCheck("开启自动签到", HuanghunSignInHelper.isEnabled(currentAccount), true);
            } else if (type == TYPE_INFO_PRIVACY) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(tasks.isEmpty()
                        ? "还没有签到任务。先添加目标、内容和北京时间，任务就会显示在这里。每天到点自动发送；需要网络，请提前配置好内置代理。\n"
                        : "任务每天按北京时间执行。发送结果会更新状态；自动发送需要网络，请提前配置好内置代理。长按任务可删除，点击任务可编辑。\n");
            } else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setText("", false);
            } else if (type == TYPE_TASK) {
                ((TaskRow) holder.itemView).bind(tasks.get(position - TASKS));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == HEADER) return TYPE_HEADER;
            if (position == ACTIONS) return TYPE_ACTIONS;
            if (position == ENABLED) return TYPE_CHECK;
            if (position == INFO) return TYPE_INFO_PRIVACY;
            return TYPE_TASK;
        }
    }

    private class ActionRow extends LinearLayout {
        ActionRow(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

            TextView add = createTextButton(context, "＋  添加目标", true);
            TextView run = createTextButton(context, "▶  执行全部任务", false);
            LayoutParams left = new LayoutParams(0, AndroidUtilities.dp(52), 1f);
            left.rightMargin = AndroidUtilities.dp(6);
            LayoutParams right = new LayoutParams(0, AndroidUtilities.dp(52), 1f);
            right.leftMargin = AndroidUtilities.dp(6);
            addView(add, left);
            addView(run, right);
            add.setOnClickListener(v -> showTaskDialog((HuanghunSignInHelper.Task) null));
            run.setOnClickListener(v -> runAllNow());
        }
    }

    private class TaskRow extends LinearLayout {
        private final TextView target;
        private final TextView details;
        private final TextView edit;
        private final TextView delete;
        private final TextView resend;
        private HuanghunSignInHelper.Task boundTask;

        TaskRow(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
            setBackground(roundedBackground(getThemedColor(Theme.key_windowBackgroundWhite), AndroidUtilities.dp(14)));
            setElevation(AndroidUtilities.dp(1));
            setClickable(true);
            setFocusable(true);

            LinearLayout top = new LinearLayout(context);
            top.setGravity(Gravity.CENTER_VERTICAL);
            target = new TextView(context);
            target.setTextSize(16);
            target.setTypeface(null, android.graphics.Typeface.BOLD);
            target.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            target.setMaxLines(1);
            target.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(target, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            edit = new TextView(context);
            edit.setText("编辑");
            edit.setTextSize(14);
            edit.setTextColor(0xff2584c7);
            edit.setGravity(Gravity.CENTER);
            edit.setMinWidth(AndroidUtilities.dp(48));
            edit.setMinHeight(AndroidUtilities.dp(48));
            delete = new TextView(context);
            delete.setText("删除");
            delete.setTextSize(14);
            delete.setTextColor(0xffd94343);
            delete.setGravity(Gravity.CENTER);
            delete.setMinWidth(AndroidUtilities.dp(48));
            delete.setMinHeight(AndroidUtilities.dp(48));
            addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            details = new TextView(context);
            details.setTextSize(14);
            details.setLineSpacing(AndroidUtilities.dp(4), 1f);
            details.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            LayoutParams detailParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = AndroidUtilities.dp(8);
            addView(details, detailParams);

            LinearLayout actionRow = new LinearLayout(context);
            actionRow.setGravity(Gravity.CENTER_VERTICAL);
            actionRow.setPadding(0, AndroidUtilities.dp(6), 0, 0);
            resend = new TextView(context);
            resend.setText("重发");
            resend.setTextSize(14);
            resend.setTextColor(0xff2584c7);
            resend.setGravity(Gravity.CENTER);
            resend.setMinWidth(AndroidUtilities.dp(48));
            resend.setMinHeight(AndroidUtilities.dp(48));
            resend.setContentDescription("重新发送此签到任务");
            actionRow.addView(edit, new LayoutParams(0, AndroidUtilities.dp(48), 1f));
            actionRow.addView(delete, new LayoutParams(0, AndroidUtilities.dp(48), 1f));
            actionRow.addView(resend, new LayoutParams(0, AndroidUtilities.dp(48), 1f));
            addView(actionRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            setOnClickListener(v -> {
                if (boundTask != null) showTaskDialog(boundTask);
            });
            edit.setOnClickListener(v -> {
                if (boundTask != null) showTaskDialog(boundTask);
            });
            delete.setOnClickListener(v -> {
                if (boundTask != null) confirmDeleteTask(boundTask);
            });
            resend.setOnClickListener(v -> {
                if (boundTask != null) resendTaskNow(boundTask);
            });
        }

        void bind(HuanghunSignInHelper.Task task) {
            boundTask = task;
            target.setText(task.target);
            details.setText("内容  ·  " + task.content + "\n时间  ·  " + task.time + "（北京时间）\n状态  ·  " + task.status);
            details.setTextColor(task.status.startsWith("失败")
                    ? 0xffd94343
                    : getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        }
    }
}
