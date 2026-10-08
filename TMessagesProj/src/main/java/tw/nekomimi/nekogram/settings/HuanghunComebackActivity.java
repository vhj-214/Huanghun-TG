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

/** Settings for the opt-in exact-trigger private-message reply feature. */
public class HuanghunComebackActivity extends BaseNekoSettingsActivity {
    private static final int REQUEST_CORPUS = 10941;
    private int headerRow, enabledRow, triggerRow, importRow, selectAllRow, deleteAllRow, noticeRow, corpusHeaderRow, defaultCorpusRow, activeCorpusRow, endRow;
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
        importRow = addRow();
        selectAllRow = addRow();
        deleteAllRow = addRow();
        noticeRow = addRow();
        corpusHeaderRow = addRow();
        defaultCorpusRow = addRow();
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
            showTriggerDialog();
        } else if (position == importRow) {
            chooseCorpora();
        } else if (position == selectAllRow) {
            helper.setAllCorporaEnabled(true);
            refreshRows();
        } else if (position == deleteAllRow) {
            showDeleteAllDialog();
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

    private void showTriggerDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("请输入你要自定义的快捷触发词语");
        input.setText(HuanghunComebackHelper.getInstance(currentAccount).getTrigger());
        input.setSelection(input.length());
        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle("自定义快捷触发词语")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String trigger = input.getText().toString();
            if (!HuanghunComebackHelper.getInstance(currentAccount).setTrigger(trigger)) {
                input.setError("触发词不能为空，且不能超过 64 个字符");
                return;
            }
            dialog.dismiss();
            refreshRows();
        }));
        showDialog(dialog);
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
            for (Uri uri : uris) {
                try {
                    String name = getDisplayName(uri);
                    if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) continue;
                    StringBuilder text = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                        String line;
                        int lines = 0;
                        while ((line = reader.readLine()) != null && lines++ < 500) {
                            if (text.length() + line.length() > 200000) break;
                            text.append(line).append('\n');
                        }
                    }
                    if (text.toString().trim().isEmpty()) continue;
                    HuanghunComebackHelper.getInstance(account).addCorpus(name, text.toString());
                    imported++;
                } catch (Throwable error) { FileLog.e(error); }
            }
            final int count = imported;
            AndroidUtilities.runOnUIThread(() -> {
                rebuildRows();
                showMessage(count == 0 ? "没有导入有效的 .txt 词库。" : "已导入 " + count + " 个词库；每一行会作为一条消息。 ");
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
        showMessage(names.isEmpty() ? "当前没有启用词库。" : "将按列表顺序发送：\n" + android.text.TextUtils.join("\n", names));
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
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) { return new Adapter(context); }

    private class Adapter extends BaseListAdapter {
        Adapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) {
            if (position == headerRow || position == corpusHeaderRow) return TYPE_HEADER;
            if (position == enabledRow || position == defaultCorpusRow || useRows.contains(position)) return TYPE_CHECK;
            if (position == noticeRow) return TYPE_INFO_PRIVACY;
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
                    else {
                        int index = useRows.indexOf(position);
                        HuanghunComebackHelper.Corpus corpus = corpora.get(index);
                        check.setTextAndCheck(corpus.name + "（使用）", corpus.enabled, true);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell info = (TextInfoPrivacyCell) holder.itemView;
                    info.setText("仅当当前账号本人在私聊中发送完整触发词时才会触发；不会识别他人消息、群组或频道内容。每行发送一条，间隔 1–3 秒。建议仅在你确认的对话中使用。关闭总开关会停止尚未发送的内容。");
                    break;
                case TYPE_SHADOW:
                    break;
                default:
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == triggerRow) cell.setTextAndValue("当前快捷触发词语", HuanghunComebackHelper.getInstance(currentAccount).getTrigger() + "　自定义", true);
                    else if (position == importRow) cell.setTextAndValue("自定义词库", "点我选择 .txt 文件", true);
                    else if (position == selectAllRow) cell.setText("全选使用", true);
                    else if (position == deleteAllRow) {
                        cell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        cell.setText("全选删除自定义词库", true);
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
