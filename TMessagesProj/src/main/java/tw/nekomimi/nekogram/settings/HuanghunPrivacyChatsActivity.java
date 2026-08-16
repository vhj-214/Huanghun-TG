package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import tw.nekomimi.nekogram.helpers.HuanghunPrivacyFolderHelper;

/** 本机隐私文件夹中的聊天选择器。勾选变更立即保存到当前账号的本地私密集合。 */
public class HuanghunPrivacyChatsActivity extends BaseFragment {
    private final int account;
    private final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    private final Set<Long> protectedDialogs = new LinkedHashSet<>();
    private RecyclerListView listView;
    private PrivacyAdapter adapter;

    public HuanghunPrivacyChatsActivity(int account) {
        this.account = account;
        setCurrentAccount(account);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("隐私文件夹聊天");
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        reloadDialogs(context);
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new PrivacyAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= dialogs.size()) {
                return;
            }
            long dialogId = dialogs.get(position).id;
            if (protectedDialogs.contains(dialogId)) {
                protectedDialogs.remove(dialogId);
            } else {
                protectedDialogs.add(dialogId);
            }
            HuanghunPrivacyFolderHelper.saveProtectedDialogs(context, account, protectedDialogs);
            adapter.notifyItemChanged(position);
        });
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView = root;
    }

    private void reloadDialogs(Context context) {
        protectedDialogs.clear();
        protectedDialogs.addAll(HuanghunPrivacyFolderHelper.getProtectedDialogs(context, account));
        dialogs.clear();
        MessagesController controller = MessagesController.getInstance(account);
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            TLRPC.Dialog dialog = controller.dialogs_dict.valueAt(i);
            if (dialog != null && dialog.id != 0L && dialog.id != getUserConfig().getClientUserId()) {
                dialogs.add(dialog);
            }
        }
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
            if (user != null && user.bot) {
                return "机器人";
            }
            return "私聊";
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
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            TLRPC.Dialog dialog = dialogs.get(position);
            cell.setTextAndValueAndCheck(dialogTitle(dialog), dialogType(dialog), protectedDialogs.contains(dialog.id), false, position != dialogs.size() - 1, true);
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
