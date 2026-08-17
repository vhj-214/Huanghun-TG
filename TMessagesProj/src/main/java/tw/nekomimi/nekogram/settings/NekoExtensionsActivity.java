package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.DatePickerDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import tw.nekomimi.nekogram.helpers.HuanghunPrivacyFolderHelper;
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

    private int privacyHeaderRow;
    private int createPrivacyFolderRow;
    private int managePrivacyChatsRow;
    private int changePrivacyPasswordRow;
    private int deletePrivacyFolderRow;
    private int privacyNoticeRow;
    private int privacyEndRow;

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

        privacyHeaderRow = addRow();
        createPrivacyFolderRow = addRow();
        managePrivacyChatsRow = addRow();
        changePrivacyPasswordRow = addRow();
        deletePrivacyFolderRow = addRow();
        privacyNoticeRow = addRow();
        privacyEndRow = addRow();

        blockHeaderRow = addRow();
        blockNonContactsRow = addRow();
        keywordsRow = addRow();
        blockNoticeRow = addRow();
        blockEndRow = addRow();
    }

    @Override
    public void onResume() {
        super.onResume();
        notifyPrivacyRows();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.HuanghunExtensions);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
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
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(6), AndroidUtilities.dp(24), 0);

        TextView description = new TextView(context);
        description.setText(changing ? "请选择新密码类型，并连续输入两次相同的新密码。密码仅在本机保存。" : "首次创建需要设置访问密码。请选择密码类型，并连续输入两次相同密码。密码仅在本机保存。");
        description.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description.setLineSpacing(AndroidUtilities.dp(3), 1f);
        container.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        RadioGroup policyGroup = new RadioGroup(context);
        policyGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton digits = privacyPolicyRadio(context, "纯数字密码", HuanghunPrivacyFolderHelper.POLICY_DIGITS);
        RadioButton letters = privacyPolicyRadio(context, "纯英文密码", HuanghunPrivacyFolderHelper.POLICY_LETTERS);
        RadioButton mixed = privacyPolicyRadio(context, "英文和数字混合密码", HuanghunPrivacyFolderHelper.POLICY_MIXED);
        policyGroup.addView(digits);
        policyGroup.addView(letters);
        policyGroup.addView(mixed);
        policyGroup.check(changing ? policyButtonId(HuanghunPrivacyFolderHelper.getPolicy(context, currentAccount), digits, letters, mixed) : digits.getId());
        container.addView(policyGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        TextView hint = new TextView(context);
        hint.setTextColor(getThemedColor(Theme.key_dialogTextHint));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        container.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        EditTextBoldCursor first = privacyPasswordInput(context, "请输入密码");
        EditTextBoldCursor confirm = privacyPasswordInput(context, "请再次输入确认密码");
        container.addView(first, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(48), 0, 0, 0, 8));
        container.addView(confirm, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(48)));

        int initialPolicy = changing ? HuanghunPrivacyFolderHelper.getPolicy(context, currentAccount) : HuanghunPrivacyFolderHelper.POLICY_DIGITS;
        applyPrivacyPasswordPolicy(first, confirm, hint, initialPolicy);
        policyGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int policy = checkedId == letters.getId() ? HuanghunPrivacyFolderHelper.POLICY_LETTERS : (checkedId == mixed.getId() ? HuanghunPrivacyFolderHelper.POLICY_MIXED : HuanghunPrivacyFolderHelper.POLICY_DIGITS);
            first.setText("");
            confirm.setText("");
            applyPrivacyPasswordPolicy(first, confirm, hint, policy);
        });

        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle(changing ? "设置隐私文件夹访问密码" : "创建隐私文件夹")
                .setView(container)
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(changing ? "保存新密码" : "创建", null)
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
                confirm.setError("两次输入的密码不一致，请重新确认。");
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

    private RadioButton privacyPolicyRadio(Context context, String text, int policy) {
        RadioButton button = new RadioButton(context);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTag(policy);
        button.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
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
        input.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField));
        input.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
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
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(6), AndroidUtilities.dp(24), 0);
        TextView description = new TextView(context);
        description.setText(descriptionText);
        description.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description.setLineSpacing(AndroidUtilities.dp(3), 1f);
        container.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        EditTextBoldCursor password = privacyPasswordInput(context, "请输入访问密码");
        int policy = HuanghunPrivacyFolderHelper.getPolicy(context, currentAccount);
        applyPrivacyPasswordPolicy(password, password, description, policy);
        description.setText(descriptionText + "\n" + HuanghunPrivacyFolderHelper.policyHint(policy));
        container.addView(password, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(48)));

        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle(title)
                .setView(container)
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton("解锁", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int result = HuanghunPrivacyFolderHelper.verifyPassword(context, currentAccount, password.getText().toString());
            if (result == HuanghunPrivacyFolderHelper.VERIFY_OK) {
                dialog.dismiss();
                verified.run();
                return;
            }
            if (result == HuanghunPrivacyFolderHelper.VERIFY_LOCKED) {
                password.setError(HuanghunPrivacyFolderHelper.getLockMessage(context, currentAccount));
                return;
            }
            if (result == HuanghunPrivacyFolderHelper.VERIFY_NOT_CREATED) {
                password.setError("需要先创建隐私文件夹。");
                return;
            }
            password.setError("密码错误。连续输错 3 次将锁定 30 分钟。");
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
                String headerText = position == privacyHeaderRow
                        ? "隐私专区"
                        : (position == blockHeaderRow ? getString(R.string.HuanghunBlockZone) : getString(R.string.HuanghunCleanupZone));
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
                } else if (position == createPrivacyFolderRow) {
                    boolean created = HuanghunPrivacyFolderHelper.isCreated(mContext, currentAccount);
                    cell.setTextAndValue("创建隐私文件夹", created ? "已创建" : "未创建", true);
                } else if (position == managePrivacyChatsRow) {
                    int count = HuanghunPrivacyFolderHelper.getProtectedDialogs(mContext, currentAccount).size();
                    cell.setTextAndValue("管理隐私聊天", HuanghunPrivacyFolderHelper.isCreated(mContext, currentAccount) ? "已保护 " + count + " 个聊天" : "请先创建", true);
                } else if (position == changePrivacyPasswordRow) {
                    cell.setText("设置隐私文件夹访问密码", true);
                } else if (position == deletePrivacyFolderRow) {
                    cell.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    cell.setText("删除当前隐私文件夹", false);
                } else if (position == keywordsRow) {
                    cell.setTextAndValue(getString(R.string.HuanghunBlockKeywords), LocaleController.formatString(R.string.HuanghunBlockKeywordsCount, HuanghunExtensionHelper.getKeywordCount()), false);
                }
            } else if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck(getString(R.string.HuanghunBlockNonContacts), NekoConfig.huanghunBlockNonContacts.Bool(), true);
            } else if (type == TYPE_INFO_PRIVACY) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(position == cleanupNoticeRow ? getString(R.string.HuanghunCleanupNotice) : (position == privacyNoticeRow ? "隐私文件夹仅保存在本机。已加入的群组、频道、机器人或私聊会在本客户端的任意入口先要求密码验证；连续输错 3 次将锁定 30 分钟。" : getString(R.string.HuanghunBlockNotice)));
                cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            } else if (type == TYPE_SHADOW) {
                holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == cleanupHeaderRow || position == privacyHeaderRow || position == blockHeaderRow) {
                return TYPE_HEADER;
            }
            if (position == blockNonContactsRow) {
                return TYPE_CHECK;
            }
            if (position == cleanupNoticeRow || position == privacyNoticeRow || position == blockNoticeRow) {
                return TYPE_INFO_PRIVACY;
            }
            if (position == cleanupEndRow || position == privacyEndRow || position == blockEndRow) {
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
