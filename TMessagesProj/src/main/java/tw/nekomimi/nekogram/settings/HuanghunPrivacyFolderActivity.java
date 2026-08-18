package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Set;

import tw.nekomimi.nekogram.helpers.HuanghunPrivacyFolderHelper;

/** 本机隐私文件夹的只读入口；只有完成密码验证后才显示受保护的聊天。 */
public class HuanghunPrivacyFolderActivity extends BaseFragment {
    private final int account;
    private final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    private FolderAdapter adapter;
    private boolean unlocked;

    public HuanghunPrivacyFolderActivity(int account) {
        this.account = account;
        setCurrentAccount(account);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("隐私文件夹");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new FolderAdapter(context));
        listView.setOnItemClickListener((view, position) -> openDialog(position));
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;

        if (!HuanghunPrivacyFolderHelper.isCreated(context, account)) {
            showInfo(context, "隐私文件夹尚未创建，请先前往“黄昏插件功能设置”的隐私专区创建。", true);
        } else {
            showUnlockDialog(context);
        }
        return root;
    }

    private int privacyBlend(int color1, int color2, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        return Color.rgb(
                (int) (Color.red(color1) * r + Color.red(color2) * (1f - r)),
                (int) (Color.green(color1) * r + Color.green(color2) * (1f - r)),
                (int) (Color.blue(color1) * r + Color.blue(color2) * (1f - r))
        );
    }

    private GradientDrawable privacyShape(int color, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(AndroidUtilities.dp(radiusDp));
        drawable.setStroke(Math.max(1, AndroidUtilities.dp(1)), stroke);
        return drawable;
    }

    private void showUnlockDialog(Context context) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(18), AndroidUtilities.dp(6));
        container.setBackground(privacyShape(getThemedColor(Theme.key_windowBackgroundWhite),
                getThemedColor(Theme.key_windowBackgroundWhiteGrayText2), 22));

        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13), AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        hero.setBackground(privacyShape(getThemedColor(Theme.key_windowBackgroundWhite),
                getThemedColor(Theme.key_windowBackgroundWhiteGrayText2), 17));
        TextView tag = new TextView(context);
        tag.setText("本机安全保护");
        tag.setTextSize(12);
        tag.setTypeface(AndroidUtilities.bold());
        tag.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        hero.addView(tag, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView heading = new TextView(context);
        heading.setText("验证后查看隐私聊天");
        heading.setTextSize(19);
        heading.setTypeface(AndroidUtilities.bold());
        heading.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        hero.addView(heading, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 4));
        TextView description = new TextView(context);
        description.setText("此文件夹仅保存在本机。输入正确的访问密码后才会显示已保护的聊天。");
        description.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        description.setTextSize(13);
        description.setLineSpacing(AndroidUtilities.dp(2), 1f);
        hero.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(hero, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        int policy = HuanghunPrivacyFolderHelper.getPolicy(context, account);
        TextView rule = new TextView(context);
        rule.setText("验证规则：" + HuanghunPrivacyFolderHelper.policyHint(policy) + " 连续输错 3 次将锁定 30 分钟。");
        rule.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        rule.setTextSize(13);
        rule.setLineSpacing(AndroidUtilities.dp(2), 1f);
        container.addView(rule, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 15, 0, 9));
        EditTextBoldCursor password = new EditTextBoldCursor(context);
        password.setHint("输入访问密码后继续");
        password.setSingleLine(true);
        password.setTextSize(16);
        password.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        password.setHintTextColor(getThemedColor(Theme.key_dialogTextGray));
        password.setPadding(AndroidUtilities.dp(15), 0, AndroidUtilities.dp(15), 0);
        password.setBackground(privacyShape(getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteGrayText2), 14));
        password.setInputType(policy == HuanghunPrivacyFolderHelper.POLICY_DIGITS
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        container.addView(password, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
        TextView status = new TextView(context);
        status.setText("密码只在当前设备验证，不会上传或同步。");
        status.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        status.setTextSize(13);
        container.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 9, 0, 0));

        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle("安全验证")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("验证并查看", null)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            if (!unlocked && !isFinishing()) {
                finishFragment();
            }
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int result = HuanghunPrivacyFolderHelper.verifyPassword(context, account, password.getText().toString());
            if (result == HuanghunPrivacyFolderHelper.VERIFY_OK) {
                unlocked = true;
                dialog.setOnDismissListener(null);
                dialog.dismiss();
                reloadDialogs(context);
                return;
            }
            if (result == HuanghunPrivacyFolderHelper.VERIFY_LOCKED) {
                status.setText(HuanghunPrivacyFolderHelper.getLockMessage(context, account));
            } else if (result == HuanghunPrivacyFolderHelper.VERIFY_NOT_CREATED) {
                status.setText("隐私文件夹已不存在，无法继续验证。");
            } else {
                status.setText("密码错误。连续输错 3 次将锁定 30 分钟。");
            }
            status.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            password.setText("");
            password.requestFocus();
        }));
        showDialog(dialog);
    }

    private void showInfo(Context context, String message, boolean exitWhenClosed) {
        AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle("隐私文件夹")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .create();
        if (exitWhenClosed) {
            dialog.setOnDismissListener(ignored -> finishFragment());
        }
        showDialog(dialog);
    }

    private void reloadDialogs(Context context) {
        dialogs.clear();
        Set<Long> protectedIds = HuanghunPrivacyFolderHelper.getProtectedDialogs(context, account);
        MessagesController controller = MessagesController.getInstance(account);
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            TLRPC.Dialog dialog = controller.dialogs_dict.valueAt(i);
            if (dialog != null && protectedIds.contains(dialog.id)) {
                dialogs.add(dialog);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void openDialog(int position) {
        if (!unlocked || position < 0 || position >= dialogs.size()) {
            return;
        }
        TLRPC.Dialog dialog = dialogs.get(position);
        Bundle args = new Bundle();
        if (dialog.id > 0L) {
            args.putLong("user_id", dialog.id);
        } else {
            args.putLong("chat_id", -dialog.id);
        }
        // 列表入口已经完成文件夹验证，此标记仅用于本次新建聊天页。
        args.putBoolean("huanghun_privacy_verified", true);
        presentFragment(new ChatActivity(args));
    }

    private String dialogTitle(TLRPC.Dialog dialog) {
        MessagesController controller = MessagesController.getInstance(account);
        if (dialog.id > 0L) {
            TLRPC.User user = controller.getUser(dialog.id);
            return user == null ? "未知聊天" : UserObject.getUserName(user);
        }
        TLRPC.Chat chat = controller.getChat(-dialog.id);
        return chat == null ? "未知聊天" : chat.title;
    }

    private String dialogType(TLRPC.Dialog dialog) {
        MessagesController controller = MessagesController.getInstance(account);
        if (dialog.id > 0L) {
            TLRPC.User user = controller.getUser(dialog.id);
            return user != null && user.bot ? "机器人" : "私聊";
        }
        TLRPC.Chat chat = controller.getChat(-dialog.id);
        return chat != null && chat.broadcast ? "频道" : "群聊";
    }

    private final class FolderAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        FolderAdapter(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new TextCheckCell(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            TLRPC.Dialog dialog = dialogs.get(position);
            cell.setTextAndValueAndCheck(dialogTitle(dialog), dialogType(dialog), false, false, position != dialogs.size() - 1, false);
        }

        @Override
        public int getItemCount() {
            return dialogs.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }
}
