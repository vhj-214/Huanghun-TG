package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

import tw.nekomimi.nekogram.helpers.HuanghunPrivacyFolderHelper;

/**
 * 本机隐私聊天选择器。勾选只暂存在页面内，用户点击右上角“保存”后一次性写入。
 * 这样在快速连续点击、搜索或滚动回收时不会直接修改正在绑定的列表数据。
 */
public class HuanghunPrivacyChatsActivity extends BaseFragment {
    private static final int MENU_SAVE = 1;
    private static final int MENU_SEARCH = 2;

    private final int account;
    private final ArrayList<TLRPC.Dialog> allDialogs = new ArrayList<>();
    private final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    private final LinkedHashSet<Long> selectedDialogs = new LinkedHashSet<>();

    private RecyclerListView listView;
    private PrivacyAdapter adapter;
    private TextView selectionStatus;
    private String currentQuery = "";

    public HuanghunPrivacyChatsActivity(int account) {
        this.account = account;
        setCurrentAccount(account);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("管理隐私聊天");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SAVE) {
                    saveSelection(context);
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(MENU_SAVE, R.drawable.ic_ab_done, AndroidUtilities.dp(56), "保存");
        menu.addItem(MENU_SEARCH, R.drawable.outline_header_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        updateSelectionStatus();
                    }

                    @Override
                    public void onSearchCollapse() {
                        filterDialogs("");
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        filterDialogs(editText == null ? "" : editText.getText().toString());
                    }
                }).setSearchFieldHint("搜索群聊、频道、联系人或机器人");

        reloadDialogs(context);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        selectionStatus = new TextView(context);
        selectionStatus.setTextSize(13);
        selectionStatus.setGravity(Gravity.CENTER_VERTICAL);
        selectionStatus.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        selectionStatus.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        selectionStatus.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        root.addView(selectionStatus, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new PrivacyAdapter(context));
        listView.setOnItemClickListener((view, position) -> toggleSelection(position));
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 48, 0, 0));

        updateSelectionStatus();
        return fragmentView = root;
    }

    private void reloadDialogs(Context context) {
        selectedDialogs.clear();
        selectedDialogs.addAll(HuanghunPrivacyFolderHelper.getProtectedDialogs(context, account));
        allDialogs.clear();
        MessagesController controller = MessagesController.getInstance(account);
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            TLRPC.Dialog dialog = controller.dialogs_dict.valueAt(i);
            if (dialog != null && dialog.id != 0L && dialog.id != getUserConfig().getClientUserId()) {
                allDialogs.add(dialog);
            }
        }
        filterDialogs(currentQuery);
    }

    private void filterDialogs(String query) {
        currentQuery = query == null ? "" : query.trim();
        String keyword = currentQuery.toLowerCase(Locale.ROOT);
        dialogs.clear();
        for (int i = 0; i < allDialogs.size(); i++) {
            TLRPC.Dialog dialog = allDialogs.get(i);
            if (keyword.isEmpty()
                    || dialogTitle(dialog).toLowerCase(Locale.ROOT).contains(keyword)
                    || dialogType(dialog).toLowerCase(Locale.ROOT).contains(keyword)) {
                dialogs.add(dialog);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateSelectionStatus();
    }

    private void toggleSelection(int position) {
        if (position < 0 || position >= dialogs.size()) {
            return;
        }
        TLRPC.Dialog dialog = dialogs.get(position);
        if (dialog == null || dialog.id == 0L) {
            return;
        }
        if (selectedDialogs.contains(dialog.id)) {
            selectedDialogs.remove(dialog.id);
        } else {
            selectedDialogs.add(dialog.id);
        }
        if (adapter != null && position < adapter.getItemCount()) {
            adapter.notifyItemChanged(position);
        }
        updateSelectionStatus();
    }

    private void saveSelection(Context context) {
        if (!HuanghunPrivacyFolderHelper.isCreated(context, account)) {
            Toast.makeText(context, "请先在隐私专区创建隐私文件夹。", Toast.LENGTH_SHORT).show();
            return;
        }
        HuanghunPrivacyFolderHelper.saveProtectedDialogs(context, account, selectedDialogs);
        Toast.makeText(context, "已保存 " + selectedDialogs.size() + " 个隐私聊天。", Toast.LENGTH_SHORT).show();
        updateSelectionStatus();
    }

    private void updateSelectionStatus() {
        if (selectionStatus == null) {
            return;
        }
        selectionStatus.setText("已选择 " + selectedDialogs.size() + " 个聊天；可继续多选，点击右上角“保存”后生效。");
    }

    private String dialogTitle(TLRPC.Dialog dialog) {
        MessagesController controller = MessagesController.getInstance(account);
        if (dialog.id > 0L) {
            TLRPC.User user = controller.getUser(dialog.id);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (dialog.id < 0L) {
            TLRPC.Chat chat = controller.getChat(-dialog.id);
            if (chat != null) {
                return chat.title;
            }
        }
        return "未知聊天";
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

    private final class PrivacyAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        PrivacyAdapter(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new TextCheckCell(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= dialogs.size()) {
                return;
            }
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            TLRPC.Dialog dialog = dialogs.get(position);
            cell.setTextAndValueAndCheck(dialogTitle(dialog), dialogType(dialog), selectedDialogs.contains(dialog.id), false, position != dialogs.size() - 1, true);
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
