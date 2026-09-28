package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.regex.Pattern;

import tw.nekomimi.nekogram.helpers.HuanghunSignInHelper;
import tw.nekomimi.nekogram.helpers.HuanghunSignInScheduler;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Daily recurring message automation page. Times are always interpreted in Asia/Shanghai time. */
public class HuanghunSignInActivity extends BaseNekoSettingsActivity {
    private static final int HEADER = 0, ENABLED = 1, ADD = 2, RUN = 3, INFO = 4, TASKS = 5;
    private ArrayList<HuanghunSignInHelper.Task> tasks = new ArrayList<>();
    private long selectedDialogId;
    private String selectedDialogName = "";

    @Override protected String getActionBarTitle() { return "自动签到函数"; }
    @Override protected String getKey() { return "sign_in"; }
    @Override protected void updateRows() {
        rowCount = 5;
        tasks = HuanghunSignInHelper.getTasks(currentAccount);
        for (int i = 0; i < tasks.size(); i++) rowCount++;
    }
    @Override public void onResume() { super.onResume(); tasks = HuanghunSignInHelper.getTasks(currentAccount); if (listAdapter != null) listAdapter.notifyDataSetChanged(); }

    @Override protected void onItemClick(View view, int position, float x, float y) {
        if (position == ENABLED) {
            HuanghunSignInHelper.setEnabled(currentAccount, !HuanghunSignInHelper.isEnabled(currentAccount));
            if (listAdapter != null) listAdapter.notifyItemChanged(ENABLED);
        } else if (position == ADD) {
            showTaskDialog(null);
        } else if (position == RUN) {
            runAllNow();
        } else if (position >= TASKS && position - TASKS < tasks.size()) {
            TaskAtPosition(position);
        }
    }

    private void TaskAtPosition(int position) {
        HuanghunSignInHelper.Task task = tasks.get(position - TASKS);
        new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle("删除签到目标")
                .setMessage(task.target + "\n" + task.time + "\n" + task.content)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> { tasks.remove(task); HuanghunSignInHelper.saveTasks(currentAccount, tasks); updateRows(); listAdapter.notifyDataSetChanged(); }).show();
    }

    private void runAllNow() {
        for (HuanghunSignInHelper.Task task : tasks) {
            try {
                org.telegram.messenger.AccountInstance.getInstance(currentAccount).getSendMessagesHelper().sendMessage(
                        org.telegram.messenger.SendMessagesHelper.SendMessageParams.of(task.content, task.dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false));
                task.status = "成功（立即执行）";
            } catch (Throwable e) { task.status = "失败（立即执行）"; FileLog.e(e); }
        }
        HuanghunSignInHelper.saveTasks(currentAccount, tasks);
        listAdapter.notifyDataSetChanged();
    }

    private void showTaskDialog(HuanghunSignInHelper.Task editing) {
        Context context = getParentActivity(); if (context == null) return;
        selectedDialogId = editing == null ? 0 : editing.dialogId;
        selectedDialogName = editing == null ? "" : editing.target;
        LinearLayout form = new LinearLayout(context); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        EditTextBoldCursor target = input(context, "请选择你要设定的目标（用户名 / ID / 链接）"); target.setText(selectedDialogName);
        Button choose = new Button(context); choose.setText("选择"); choose.setOnClickListener(v -> chooseChat(target));
        LinearLayout targetLine = new LinearLayout(context); targetLine.setGravity(Gravity.CENTER_VERTICAL); targetLine.addView(target, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(52), 1)); targetLine.addView(choose, new LinearLayout.LayoutParams(AndroidUtilities.dp(80), AndroidUtilities.dp(52))); form.addView(targetLine);
        EditTextBoldCursor content = input(context, "需要发送的内容"); content.setSingleLine(false); content.setMinLines(2); if (editing != null) content.setText(editing.content); form.addView(content, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(82)));
        EditTextBoldCursor time = input(context, "设定时间（格式：00:01，北京时间）"); time.setInputType(InputType.TYPE_CLASS_DATETIME); time.setText(editing == null ? "00:01" : editing.time); form.addView(time, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(52)));
        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider).setTitle("目标设定").setView(form).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = content.getText().toString().trim(); String tm = HuanghunSignInHelper.normalizeTime(time.getText().toString());
            if (selectedDialogId == 0 || text.isEmpty() || !Pattern.matches("(?:[01]\\d|2[0-3]):[0-5]\\d", tm)) { time.setError("请填写目标、内容和正确的时间格式 00:01"); return; }
            HuanghunSignInHelper.Task task = editing == null ? new HuanghunSignInHelper.Task() : editing;
            task.id = editing == null ? System.currentTimeMillis() : editing.id; task.dialogId = selectedDialogId; task.target = selectedDialogName; task.content = text; task.time = tm; if (editing == null) task.status = "未执行";
            if (editing == null) tasks.add(task); HuanghunSignInHelper.saveTasks(currentAccount, tasks); updateRows(); listAdapter.notifyDataSetChanged(); dialog.dismiss();
        }));
        showDialog(dialog);
    }

    private EditTextBoldCursor input(Context context, String hint) { EditTextBoldCursor e = new EditTextBoldCursor(context); e.setHint(hint); e.setTextSize(16); e.setSingleLine(true); e.setPadding(0, 0, 0, 0); e.setBackground(null); e.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText)); e.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText)); return e; }

    private void chooseChat(EditTextBoldCursor target) {
        Bundle args = new Bundle(); args.putBoolean("onlySelect", true); args.putBoolean("checkCanWrite", false); args.putBoolean("allowGlobalSearch", true);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty()) return;
            selectedDialogId = dids.get(0).dialogId; selectedDialogName = resolveName(selectedDialogId); target.setText(selectedDialogName); picker.finishFragment();
        });
        presentFragment(picker);
    }
    private String resolveName(long dialogId) {
        MessagesController c = MessagesController.getInstance(currentAccount);
        if (dialogId > 0) { TLRPC.User u = c.getUser(dialogId); if (u != null) return u.username == null || u.username.isEmpty() ? (u.first_name + " " + u.last_name).trim() : "@" + u.username; }
        TLRPC.Chat chat = c.getChat(-dialogId); return chat == null ? String.valueOf(dialogId) : chat.title;
    }

    @Override protected BaseListAdapter createAdapter(Context context) { return new Adapter(context); }
    private class Adapter extends BaseListAdapter {
        Adapter(Context context) { super(context); }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = holder.getItemViewType();
            if (type == TYPE_HEADER) ((HeaderCell) holder.itemView).setText("自动签到");
            else if (type == TYPE_CHECK) ((TextCheckCell) holder.itemView).setTextAndCheck("开启自动签到", HuanghunSignInHelper.isEnabled(currentAccount), true);
            else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == ADD) cell.setTextAndValue("添加目标", "设置群、频道、用户或机器人", true);
                else if (position == RUN) cell.setText("执行全部任务", false);
                else { HuanghunSignInHelper.Task task = tasks.get(position - TASKS); cell.setTextAndValue(task.target, task.content + "  ·  " + task.time + "  ·  " + task.status, false); }
            } else if (type == TYPE_INFO_PRIVACY) { TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView; cell.setText("设定目标    设定内容    设定时间    发送状态\n每天按北京时间执行。此功能依赖网络，请提前配置好内置代理。点击已添加任务可删除。\n"); }
        }
        @Override public int getItemViewType(int position) { if (position == HEADER) return TYPE_HEADER; if (position == ENABLED) return TYPE_CHECK; if (position == INFO) return TYPE_INFO_PRIVACY; return TYPE_SETTINGS; }
    }
}
