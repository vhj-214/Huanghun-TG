package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.DatePickerDialog;
import android.content.Context;
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
