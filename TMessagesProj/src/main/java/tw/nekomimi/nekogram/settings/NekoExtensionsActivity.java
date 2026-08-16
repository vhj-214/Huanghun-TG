package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.DatePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.HuanghunExtensionHelper;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Settings page for Huanghun account cleanup and non-contact message blocking. */
public class NekoExtensionsActivity extends BaseNekoSettingsActivity {

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

    private int blockHeaderRow;
    private int blockNonContactsRow;
    private int keywordsRow;
    private int blockNoticeRow;
    private int blockEndRow;

    @Override
    protected void updateRows() {
        super.updateRows();
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

        blockHeaderRow = addRow();
        blockNonContactsRow = addRow();
        keywordsRow = addRow();
        blockNoticeRow = addRow();
        blockEndRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.HuanghunExtensions);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == blockNonContactsRow) {
            boolean enabled = NekoConfig.huanghunBlockNonContacts.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
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
            executeCleanupDirectly(action);
            return;
        }

    }

    private void showCleanupConfirmation(HuanghunExtensionHelper.CleanupAction action) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);

        TextView description = new TextView(context);
        description.setText(getString(R.string.HuanghunCleanupConfirmMessage));
        description.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        description.setGravity(Gravity.START);
        container.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        input.setHint("清除");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField));
        input.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        container.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(48)));

        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle(getString(R.string.HuanghunCleanupConfirmTitle))
                .setView(container)
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.HuanghunCleanupConfirmAction), null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!"清除".contentEquals(input.getText().toString().trim())) {
                input.setError(getString(R.string.HuanghunCleanupConfirmInvalid));
                input.requestFocus();
                return;
            }
            dialog.dismiss();
            AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
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
        }));
        showDialog(dialog);
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
                String headerText = position == blockHeaderRow
                        ? getString(R.string.HuanghunBlockZone)
                        : getString(R.string.HuanghunCleanupZone);
                cell.setText(headerText);
            } else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                if (position == clearBotsRow) {
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
                } else if (position == keywordsRow) {
                    cell.setTextAndValue(getString(R.string.HuanghunBlockKeywords), LocaleController.formatString(R.string.HuanghunBlockKeywordsCount, HuanghunExtensionHelper.getKeywordCount()), false);
                }
            } else if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck(getString(R.string.HuanghunBlockNonContacts), NekoConfig.huanghunBlockNonContacts.Bool(), true);
            } else if (type == TYPE_INFO_PRIVACY) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(position == cleanupNoticeRow ? getString(R.string.HuanghunCleanupNotice) : getString(R.string.HuanghunBlockNotice));
                cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            } else if (type == TYPE_SHADOW) {
                holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == cleanupHeaderRow || position == blockHeaderRow) {
                return TYPE_HEADER;
            }
            if (position == blockNonContactsRow) {
                return TYPE_CHECK;
            }
            if (position == cleanupNoticeRow || position == blockNoticeRow) {
                return TYPE_INFO_PRIVACY;
            }
            if (position == cleanupEndRow || position == blockEndRow) {
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
            executeTimeCleanup(mode, startTime, endTime, scope);
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
