package tw.nekomimi.nekogram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import tw.nekomimi.nekogram.helpers.HuanghunComebackHelper;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Settings for the opt-in exact-trigger reply feature and optional group/channel mode. */
public class HuanghunComebackActivity extends BaseNekoSettingsActivity {
    private static final int REQUEST_CORPUS = 10941;
    private int headerRow, enabledRow, triggerRow, stopTriggerRow, importRow, selectAllRow, deleteAllRow, noticeRow, corpusHeaderRow, defaultCorpusRow, sendModeRow, speedRow, speedNoticeRow, groupBombRow, groupBombNoticeRow, activeCorpusRow, endRow;
    private final ArrayList<Integer> useRows = new ArrayList<>();
    private final ArrayList<Integer> deleteRows = new ArrayList<>();
    private List<HuanghunComebackHelper.Corpus> corpora = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        boolean created = super.onFragmentCreate();
        HuanghunComebackHelper.getInstance(currentAccount);
        return created;
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        corpora = HuanghunComebackHelper.getInstance(currentAccount).getCorpora();
        headerRow = addRow();
        enabledRow = addRow();
        triggerRow = addRow();
        stopTriggerRow = addRow();
        importRow = addRow();
        selectAllRow = addRow();
        // Both bulk actions share one row: left half enables all, right half deletes all.
        deleteAllRow = selectAllRow;
        noticeRow = addRow();
        corpusHeaderRow = addRow();
        defaultCorpusRow = addRow();
        sendModeRow = addRow();
        speedRow = addRow();
        speedNoticeRow = addRow();
        groupBombRow = addRow();
        groupBombNoticeRow = addRow();
        activeCorpusRow = addRow();
        useRows.clear();
        deleteRows.clear();
        for (int i = 0; i < corpora.size(); i++) {
            useRows.add(addRow());
            deleteRows.add(addRow());
        }
        endRow = addRow();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshRows();
    }

    @Override
    protected String getActionBarTitle() { return "回怼系统"; }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        HuanghunComebackHelper helper = HuanghunComebackHelper.getInstance(currentAccount);
        if (position == enabledRow) {
            boolean enabled = !helper.isEnabled();
            helper.setEnabled(enabled);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(enabled);
        } else if (position == triggerRow) {
            showTriggerDialog(false);
        } else if (position == stopTriggerRow) {
            showTriggerDialog(true);
        } else if (position == sendModeRow) {
            showSendModeDialog();
        } else if (position == speedRow) {
            showSpeedDialog();
        } else if (position == groupBombRow) {
            boolean enabled = !helper.isGroupBombEnabled();
            helper.setGroupBombEnabled(enabled);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(enabled);
            refreshRows();
        } else if (position == importRow) {
            chooseCorpora();
        } else if (position == selectAllRow) {
            if (x >= view.getWidth() / 2f) {
                showDeleteAllDialog();
            } else {
                helper.setAllCorporaEnabled(true);
                refreshRows();
            }
        } else if (position == defaultCorpusRow) {
            helper.setDefaultEnabled(!helper.isDefaultEnabled());
            refreshRows();
        } else if (position == activeCorpusRow) {
            showActiveSummary();
        } else {
            int index = useRows.indexOf(position);
            if (index >= 0 && index < corpora.size()) {
                HuanghunComebackHelper.Corpus corpus = corpora.get(index);
                helper.setCorpusEnabled(corpus.id, !corpus.enabled);
                refreshRows();
                return;
            }
            index = deleteRows.indexOf(position);
            if (index >= 0 && index < corpora.size()) {
                HuanghunComebackHelper.Corpus corpus = corpora.get(index);
                new AlertDialog.Builder(getParentActivity(), resourceProvider)
                        .setTitle("删除词库")
                        .setMessage("确定删除“" + corpus.name + "”吗？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (dialog, which) -> {
                            helper.deleteCorpus(corpus.id);
                            rebuildRows();
                        }).show();
            }
        }
    }

    private void showTriggerDialog(boolean stopTrigger) {
        Context context = getParentActivity();
        if (context == null) return;
        HuanghunComebackHelper helper = HuanghunComebackHelper.getInstance(currentAccount);
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(stopTrigger ? "请输入快捷关闭词语" : "请输入你要自定义的快捷触发词语");
        input.setText(stopTrigger ? helper.getStopTrigger() : helper.getTrigger());
        input.setSelection(input.length());
        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle(stopTrigger ? "自定义快捷关闭词语" : "自定义快捷触发词语")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString();
            boolean saved = stopTrigger ? helper.setStopTrigger(value) : helper.setTrigger(value);
            if (!saved) {
                input.setError("词语不能为空、不能超过 64 个字符，且不能与另一触发词相同");
                return;
            }
            dialog.dismiss();
            refreshRows();
        }));
        showDialog(dialog);
    }
    private void showSendModeDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        HuanghunComebackHelper helper = HuanghunComebackHelper.getInstance(currentAccount);
        final int current = helper.getSendMode();
        final String[] modes = {
                "顺序发送" + (current == HuanghunComebackHelper.SEND_MODE_SEQUENTIAL ? "（当前）" : ""),
                "随机发送" + (current == HuanghunComebackHelper.SEND_MODE_RANDOM ? "（当前）" : "")
        };
        showDialog(new AlertDialog.Builder(context, resourceProvider)
                .setTitle("当前发送模式")
                .setItems(modes, (dialog, which) -> {
                    helper.setSendMode(which);
                    dialog.dismiss();
                    refreshRows();
                })
                .setNegativeButton("取消", null)
                .create());
    }
    private void showSpeedDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        HuanghunComebackHelper helper = HuanghunComebackHelper.getInstance(currentAccount);
        final int current = helper.getSendSpeed();
        final String[] items = new String[HuanghunComebackHelper.MAX_SEND_SPEED];
        for (int i = 0; i < items.length; i++) {
            int multiplier = i + 1;
            items[i] = multiplier + "x" + (multiplier == current ? "（当前）" : "");
        }
        showDialog(new AlertDialog.Builder(context, resourceProvider)
                .setTitle("当前发送进程")
                .setMessage("1x 为每个发送周期发送 1 条；Nx 会在一个发送周期内最多连续发送 N 条。速度越快越容易受到 Telegram 限制，请谨慎设置。")
                .setItems(items, (dialog, which) -> {
                    helper.setSendSpeed(which + 1);
                    dialog.dismiss();
                    refreshRows();
                })
                .setNegativeButton("取消", null)
                .create());
    }

    private void chooseCorpora() {
        if (getParentActivity() == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CORPUS);
        } catch (Throwable error) {
            FileLog.e(error);
            showMessage("无法打开文件选择器，请稍后重试。");
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CORPUS && resultCode == Activity.RESULT_OK) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data != null && data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (uri != null && !uris.contains(uri)) uris.add(uri);
                }
            }
            if (data != null && data.getData() != null && !uris.contains(data.getData())) uris.add(data.getData());
            importFiles(uris);
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    private void importFiles(ArrayList<Uri> uris) {
        if (uris.isEmpty()) return;
        final int account = currentAccount;
        Utilities.globalQueue.postRunnable(() -> {
            int imported = 0;
            int skipped = 0;
            int failed = 0;
            int truncated = 0;
            for (Uri uri : uris) {
                try {
                    String name = getDisplayName(uri);
                    String mimeType = ApplicationLoader.applicationContext.getContentResolver().getType(uri);
                    boolean hasTxtExtension = name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".txt");
                    boolean isPlainText = mimeType != null && mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("text/plain");
                    if (!hasTxtExtension && !isPlainText) {
                        skipped++;
                        continue;
                    }
                    StringBuilder text = new StringBuilder();
                    java.io.InputStream stream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                    if (stream == null) {
                        failed++;
                        continue;
                    }
                    boolean wasTruncated = false;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        int lines = 0;
                        while ((line = reader.readLine()) != null) {
                            if (lines >= 500 || text.length() + line.length() + 1 > 200000) {
                                wasTruncated = true;
                                break;
                            }
                            text.append(line).append('\n');
                            lines++;
                        }
                    }
                    if (text.toString().trim().isEmpty()) {
                        skipped++;
                        continue;
                    }
                    HuanghunComebackHelper.getInstance(account).addCorpus(name, text.toString());
                    imported++;
                    if (wasTruncated) truncated++;
                } catch (Throwable error) {
                    failed++;
                    FileLog.e(error);
                }
            }
            final int count = imported;
            final int skippedCount = skipped;
            final int failedCount = failed;
            final int truncatedCount = truncated;
            AndroidUtilities.runOnUIThread(() -> {
                Activity activity = getParentActivity();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                rebuildRows();
                String message = count == 0 ? "没有导入有效的文本词库。" : "已导入 " + count + " 个词库；每一行会作为一条消息。";
                if (skippedCount > 0) message += " 跳过 " + skippedCount + " 个不支持或为空的文件。";
                if (failedCount > 0) message += " 有 " + failedCount + " 个文件读取失败。";
                if (truncatedCount > 0) message += " 有 " + truncatedCount + " 个文件达到每个词库 500 行或 20 万字符的导入上限。";
                showMessage(message);
            });
        });
    }

    private String getDisplayName(Uri uri) {
        try (android.database.Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Throwable error) { FileLog.e(error); }
        String segment = uri.getLastPathSegment();
        return segment == null ? "词库.txt" : segment;
    }

    private void showDeleteAllDialog() {
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle("删除全部自定义词库")
                .setMessage("确定删除所有导入的词库吗？系统默认词库不会删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("全部删除", (dialog, which) -> {
                    HuanghunComebackHelper.getInstance(currentAccount).deleteAllCorpora();
                    rebuildRows();
                }).show();
    }

    private void showActiveSummary() {
        ArrayList<String> names = new ArrayList<>();
        HuanghunComebackHelper helper = HuanghunComebackHelper.getInstance(currentAccount);
        if (helper.isDefaultEnabled()) names.add("系统默认词库");
        for (HuanghunComebackHelper.Corpus corpus : helper.getCorpora()) if (corpus.enabled) names.add(corpus.name);
        String mode = helper.getSendMode() == HuanghunComebackHelper.SEND_MODE_RANDOM ? "随机发送" : "顺序发送";
        if (names.isEmpty()) showMessage("当前没有启用词库。");
        else if (helper.getSendMode() == HuanghunComebackHelper.SEND_MODE_RANDOM) showMessage("当前模式：" + mode + "\n每个发送周期都会随机选择一个已启用词库，并随机发送其中一条；仅有一个词库时就在该词库内随机抽取。会持续发送，直到发送快捷关闭词语或关闭回怼系统。\n\n已启用词库：\n" + android.text.TextUtils.join("\n", names));
        else showMessage("当前模式：" + mode + "\n将按此顺序发送词库中的内容：\n" + android.text.TextUtils.join("\n", names));
    }

    private void showMessage(String text) {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity(), resourceProvider).setMessage(text).setPositiveButton("确定", null).show();
    }

    private void rebuildRows() {
        if (listView == null) return;
        updateRows();
        listAdapter.notifyDataSetChanged();
    }

    private void refreshRows() {
        // Reload persisted state before rebinding; otherwise an imported corpus toggle
        // is saved but the adapter keeps rendering the old in-memory snapshot.
        corpora = HuanghunComebackHelper.getInstance(currentAccount).getCorpora();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) { return new Adapter(context); }

    private class Adapter extends BaseListAdapter {
        Adapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) {
            if (position == headerRow || position == corpusHeaderRow) return TYPE_HEADER;
            if (position == enabledRow || position == defaultCorpusRow || position == groupBombRow || useRows.contains(position)) return TYPE_CHECK;
            if (position == noticeRow || position == speedNoticeRow || position == groupBombNoticeRow) return TYPE_INFO_PRIVACY;
            if (position == endRow) return TYPE_SHADOW;
            return TYPE_SETTINGS;
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (getItemViewType(position)) {
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(position == headerRow ? "触发与发送设置" : "当前使用词库");
                    break;
                case TYPE_CHECK:
                    TextCheckCell check = (TextCheckCell) holder.itemView;
                    if (position == enabledRow) check.setTextAndCheck("开启回怼系统", HuanghunComebackHelper.getInstance(currentAccount).isEnabled(), true);
                    else if (position == defaultCorpusRow) check.setTextAndCheck("系统默认词库", HuanghunComebackHelper.getInstance(currentAccount).isDefaultEnabled(), corpora.isEmpty());
                    else if (position == groupBombRow) check.setTextAndCheck("群/频道轰炸", HuanghunComebackHelper.getInstance(currentAccount).isGroupBombEnabled(), true);
                    else {
                        int index = useRows.indexOf(position);
                        HuanghunComebackHelper.Corpus corpus = corpora.get(index);
                        check.setTextAndCheck(corpus.name + "（使用）", corpus.enabled, true);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell info = (TextInfoPrivacyCell) holder.itemView;
                    if (position == speedNoticeRow) {
                        info.setText("发送进程速度越快，越容易受到 Telegram 限制；建议从 1x 开始，仅在确认对话中谨慎使用。");
                    } else if (position == groupBombNoticeRow) {
                        info.setText("开启后，在群组或频道中发送完整触发词也会触发发送（默认关闭）。\n\n注：在群中轰炸极易违规，请注意使用。");
                    } else {
                        info.setText(HuanghunComebackHelper.getInstance(currentAccount).isGroupBombEnabled()
                                ? "仅当当前账号本人发送完整触发词时才会触发；默认只在私聊生效，群/频道轰炸已开启时群组与频道也会生效。关闭词语会立即清空尚未发送的队列。顺序模式按词库及文本行顺序发送一轮；随机模式持续按发送周期从已启用词库中随机抽取语句发送，直到检测到快捷关闭词语。发送周期为 0.32–1.7 秒。关闭总开关也会立即停止发送。"
                                : "仅当当前账号本人在私聊中发送完整触发词时才会触发；不会识别他人消息、群组或频道内容。关闭词语会立即清空尚未发送的队列。顺序模式按词库及文本行顺序发送一轮；随机模式持续按发送周期从已启用词库中随机抽取语句发送，直到检测到快捷关闭词语。发送周期为 0.32–1.7 秒。关闭总开关也会立即停止发送。");
                    }
                    break;
                case TYPE_SHADOW:
                    break;
                default:
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    HuanghunComebackHelper helper = HuanghunComebackHelper.getInstance(currentAccount);
                    if (position == triggerRow) cell.setTextAndValue("当前快捷触发词语", helper.getTrigger() + "　自定义", true);
                    else if (position == stopTriggerRow) cell.setTextAndValue("当前快捷关闭词语", helper.getStopTrigger() + "　自定义", true);
                    else if (position == sendModeRow) cell.setTextAndValue("当前发送模式", helper.getSendMode() == HuanghunComebackHelper.SEND_MODE_RANDOM ? "随机发送" : "顺序发送", true);
                    else if (position == speedRow) cell.setTextAndValue("当前发送进程", helper.getSendSpeed() + "x", true);
                    else if (position == importRow) cell.setTextAndValue("自定义词库", "点我选择 .txt 文件", true);
                    else if (position == selectAllRow) {
                        cell.setTextAndValue("全选使用", "全选删除", false);
                    } else if (position == activeCorpusRow) {
                        cell.setTextAndValue("当前使用词库", "查看启用顺序", false);
                    } else {
                        int index = deleteRows.indexOf(position);
                        cell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        cell.setText("删除词库：" + corpora.get(index).name, false);
                    }
            }
        }
    }
}
