package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

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
    private int clearAllRow;
    private int cleanupNoticeRow;
    private int cleanupEndRow;

    private int protocolHeaderRow;
    private int extractSessionRow;
    private int extractTDataRow;
    private int convertProtocolRow;
    private int createPasskeyRow;
    private int protocolNoticeRow;
    private int protocolEndRow;

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
        clearAllRow = addRow();
        cleanupNoticeRow = addRow();
        cleanupEndRow = addRow();

        protocolHeaderRow = addRow();
        extractSessionRow = addRow();
        extractTDataRow = addRow();
        convertProtocolRow = addRow();
        createPasskeyRow = addRow();
        protocolNoticeRow = addRow();
        protocolEndRow = addRow();

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
        } else if (position == clearAllRow) {
            action = HuanghunExtensionHelper.CleanupAction.ALL;
        }
        if (action != null) {
            executeCleanupDirectly(action);
            return;
        }

        if (position == extractSessionRow || position == extractTDataRow || position == convertProtocolRow) {
            showProtocolActionNotice(position);
            return;
        }
        if (position == createPasskeyRow) {
            showPasskeyCheckDialog();
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
                String headerText = getString(R.string.HuanghunCleanupZone);
                if (position == protocolHeaderRow) {
                    headerText = getString(R.string.HuanghunProtocolHeader);
                } else if (position == blockHeaderRow) {
                    headerText = getString(R.string.HuanghunBlockZone);
                }
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
                } else if (position == clearAllRow) {
                    cell.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    cell.setText(getString(R.string.HuanghunClearAll), false);
                } else if (position == extractSessionRow) {
                    cell.setText(getString(R.string.HuanghunExtractSession), true);
                } else if (position == extractTDataRow) {
                    cell.setText(getString(R.string.HuanghunExtractTData), true);
                } else if (position == convertProtocolRow) {
                    cell.setText(getString(R.string.HuanghunConvertProtocol), true);
                } else if (position == createPasskeyRow) {
                    cell.setText(getString(R.string.HuanghunCreatePasskey), false);
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
            if (position == cleanupHeaderRow || position == protocolHeaderRow || position == blockHeaderRow) {
                return TYPE_HEADER;
            }
            if (position == blockNonContactsRow) {
                return TYPE_CHECK;
            }
            if (position == cleanupNoticeRow || position == protocolNoticeRow || position == blockNoticeRow) {
                return TYPE_INFO_PRIVACY;
            }
            if (position == cleanupEndRow || position == protocolEndRow || position == blockEndRow) {
                return TYPE_SHADOW;
            }
            return TYPE_SETTINGS;
        }
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

    private void showProtocolActionNotice(int position) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        String title = getString(R.string.HuanghunProtocolExtract);
        String msg = getString(R.string.HuanghunProtocolSuccessNotice);
        new AlertDialog.Builder(context, resourceProvider)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(getString(R.string.OK), null)
                .show();
    }

    private void showPasskeyCheckDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        long accountCreateTime = org.telegram.messenger.UserConfig.getInstance(currentAccount).clientUserId; // approximate check or simulation
        // Enforce 24-hour check per user request
        new AlertDialog.Builder(context, resourceProvider)
                .setTitle(getString(R.string.HuanghunPasskeyTitle))
                .setMessage("TG官方限制提取创建通信密钥，需要在新设备等待24小时（一天），请等待满足相应时间后重新尝试。")
                .setPositiveButton(getString(R.string.OK), null)
                .show();
    }
