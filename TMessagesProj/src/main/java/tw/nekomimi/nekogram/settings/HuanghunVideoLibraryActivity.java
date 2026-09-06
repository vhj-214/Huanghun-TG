package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import tw.nekomimi.nekogram.helpers.HuanghunVideoLibraryHelper;
import tw.nekomimi.nekogram.helpers.MultiDynamicVideoWallpaperHelper;

/**
 * 内置视频库浏览与删除页。
 *
 * 浏览模式只展示本账号已导入的视频；管理模式支持逐项勾选、全选并在二次确认后删除本机副本。
 */
public class HuanghunVideoLibraryActivity extends BaseFragment {

    private final boolean deleteMode;
    private final boolean multiDynamicMode;
    private RecyclerListView listView;
    private VideoAdapter adapter;
    private TextView selectionButton;
    private TextView deleteButton;
    private TextView emptyView;
    private static final class VideoItem {
        final String path;
        final String fileName;
        final long durationMs;
        final long size;
        VideoItem(String path, String fileName, long durationMs, long size) {
            this.path = path;
            this.fileName = fileName;
            this.durationMs = durationMs;
            this.size = size;
        }
    }
    private final ArrayList<VideoItem> items = new ArrayList<>();
    private final Set<String> selectedPaths = new HashSet<>();

    public HuanghunVideoLibraryActivity(boolean deleteMode) {
        this(deleteMode, false);
    }

    /**
     * @param multiDynamicMode true 时仅浏览多循环动态壁纸库；删除模式仍只允许原视频专区入口使用。
     */
    public HuanghunVideoLibraryActivity(boolean deleteMode, boolean multiDynamicMode) {
        this.deleteMode = deleteMode;
        this.multiDynamicMode = multiDynamicMode;
    }

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(deleteMode ? "删除内置视频" : "查看内置视频");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        int topOffset = 0;
        if (deleteMode) {
            LinearLayout actions = new LinearLayout(context);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            actions.setBackground(createGlassDrawable(0x26FFFFFF, 0x55FFFFFF, 16));

            selectionButton = createActionText(context, "全选");
            selectionButton.setOnClickListener(v -> toggleSelectAll());
            actions.addView(selectionButton, LayoutHelper.createLinear(0, 40, 1f));

            deleteButton = createActionText(context, "删除已选");
            deleteButton.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            deleteButton.setOnClickListener(v -> confirmDeleteSelected());
            actions.addView(deleteButton, LayoutHelper.createLinear(0, 40, 1f, 8, 0, 0, 0));

            root.addView(actions, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.TOP));
            topOffset = 58;
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new VideoAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(18));
        listView.setOnItemClickListener((view, position) -> {
            if (deleteMode && position >= 0 && position < items.size()) {
                toggleSelected(items.get(position).path);
            }
        });
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, topOffset, 0, 0));

        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyView.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24));
        emptyView.setText(deleteMode ? "暂无可删除的内置视频\n请先在“选取内置视频”中导入视频。" : (multiDynamicMode ? "暂无多循环动态壁纸视频\n请先在动态视频设置中选择视频。" : "暂无内置视频\n请返回视频专区选取一个或多个视频。 "));
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 24, topOffset, 24, 0));

        reloadItems();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadItems();
    }

    private void reloadItems() {
        if (getParentActivity() == null) {
            return;
        }
        items.clear();
        if (multiDynamicMode) {
            for (String path : MultiDynamicVideoWallpaperHelper.getVideoPaths(getParentActivity(), currentAccount)) {
                File file = new File(path);
                long duration = MultiDynamicVideoWallpaperHelper.getVideoDuration(path);
                if (duration > 0L && file.isFile() && file.length() > 0L) {
                    items.add(new VideoItem(path, file.getName(), duration, file.length()));
                }
            }
        } else {
            for (HuanghunVideoLibraryHelper.VideoItem item : HuanghunVideoLibraryHelper.getVideoItems(getParentActivity(), currentAccount)) {
                items.add(new VideoItem(item.path, item.fileName, item.durationMs, item.size));
            }
        }
        Set<String> validPaths = new HashSet<>();
        for (VideoItem item : items) {
            validPaths.add(item.path);
        }
        selectedPaths.retainAll(validPaths);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateControls();
    }

    private void toggleSelected(String path) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path);
        } else {
            selectedPaths.add(path);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateControls();
    }

    private void toggleSelectAll() {
        if (items.isEmpty()) {
            return;
        }
        if (selectedPaths.size() == items.size()) {
            selectedPaths.clear();
        } else {
            selectedPaths.clear();
            for (VideoItem item : items) {
                selectedPaths.add(item.path);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateControls();
    }

    private void updateControls() {
        if (emptyView != null) {
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (!deleteMode || selectionButton == null || deleteButton == null) {
            return;
        }
        boolean hasItems = !items.isEmpty();
        selectionButton.setEnabled(hasItems);
        deleteButton.setEnabled(!selectedPaths.isEmpty());
        selectionButton.setAlpha(hasItems ? 1f : .45f);
        deleteButton.setAlpha(selectedPaths.isEmpty() ? .45f : 1f);
        selectionButton.setText(hasItems && selectedPaths.size() == items.size() ? "取消全选" : "全选");
        deleteButton.setText(selectedPaths.isEmpty() ? "删除已选" : "删除已选（" + selectedPaths.size() + "）");
    }

    private void confirmDeleteSelected() {
        if (multiDynamicMode || selectedPaths.isEmpty() || getParentActivity() == null) {
            return;
        }
        int count = selectedPaths.size();
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle("确认删除")
                .setMessage("确定删除已选的 " + count + " 个内置视频吗？删除后无法恢复，但不会影响手机相册中的原始文件。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    HuanghunVideoLibraryHelper.deleteVideos(getParentActivity(), currentAccount, new ArrayList<>(selectedPaths));
                    selectedPaths.clear();
                    reloadItems();
                    BulletinFactory.of(HuanghunVideoLibraryActivity.this).createSimpleBulletin(R.raw.done, "已删除 " + count + " 个内置视频。").show();
                })
                .create());
    }

    private TextView createActionText(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTypeface(AndroidUtilities.bold());
        view.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        view.setBackground(createGlassDrawable(0x24FFFFFF, 0x5CFFFFFF, 13));
        return view;
    }

    private GradientDrawable createGlassDrawable(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(AndroidUtilities.dp(radiusDp));
        drawable.setStroke(Math.max(1, AndroidUtilities.dp(1)), strokeColor);
        return drawable;
    }

    private final class VideoAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        private VideoAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return deleteMode;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new VideoCell(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            VideoItem item = items.get(position);
            ((VideoCell) holder.itemView).bind(item, deleteMode, selectedPaths.contains(item.path), position != items.size() - 1);
        }
    }

    private final class VideoCell extends FrameLayout {
        private final ImageView thumbnail;
        private final TextView title;
        private final TextView subtitle;
        private final CheckBox2 checkBox;
        private String boundPath;

        private VideoCell(Context context) {
            super(context);
            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(7), AndroidUtilities.dp(14), AndroidUtilities.dp(7));

            FrameLayout card = new FrameLayout(context);
            card.setBackground(createGlassDrawable(0x22FFFFFF, 0x5CFFFFFF, 18));
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 82, Gravity.TOP));

            thumbnail = new ImageView(context);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackground(createGlassDrawable(0x29000000, 0x46FFFFFF, 12));
            card.addView(thumbnail, LayoutHelper.createFrame(100, 64, Gravity.LEFT | Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(textContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 122, 0, deleteMode ? 48 : 14, 0));

            title = new TextView(context);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            textContainer.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitle = new TextView(context);
            subtitle.setSingleLine(true);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            textContainer.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));

            checkBox = new CheckBox2(context, 21, getResourceProvider());
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            card.addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
        }

        private void bind(VideoItem item, boolean selectable, boolean checked, boolean divider) {
            boundPath = item.path;
            thumbnail.setImageDrawable(null);
            title.setText(item.fileName);
            subtitle.setText(formatDuration(item.durationMs) + "  ·  " + formatSize(item.size));
            checkBox.setVisibility(selectable ? View.VISIBLE : View.GONE);
            checkBox.setChecked(checked, false);
            loadThumbnail(item.path);
        }

        private void loadThumbnail(String path) {
            Utilities.globalQueue.postRunnable(() -> {
                Bitmap bitmap = null;
                try {
                    bitmap = SendMessagesHelper.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                final Bitmap result = bitmap;
                AndroidUtilities.runOnUIThread(() -> {
                    if (TextUtils.equals(boundPath, path)) {
                        thumbnail.setImageBitmap(result);
                    }
                });
            });
        }
    }

    private static String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            return Math.max(1L, bytes / 1024L) + " KB";
        }
        return String.format(Locale.CHINA, "%.1f MB", bytes / (1024f * 1024f));
    }
}
