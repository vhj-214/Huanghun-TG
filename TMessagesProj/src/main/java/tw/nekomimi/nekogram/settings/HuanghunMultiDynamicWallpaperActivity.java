package tw.nekomimi.nekogram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import tw.nekomimi.nekogram.helpers.MultiDynamicVideoWallpaperHelper;

/** 本地多视频壁纸的选择、预览和删除页面。 */
public class HuanghunMultiDynamicWallpaperActivity extends BaseFragment {
    private static final int REQUEST_PICK = 7701;
    private final boolean deleteOnly;
    private LinearLayout gallery;
    private TextView summary;
    private TextView selectAllButton;
    private TextView deleteButton;
    private TextView emptyView;
    private final Set<String> selected = new HashSet<>();
    private final ArrayList<VideoView> videoPlayers = new ArrayList<>();
    private VideoView playingVideo;
    private TextView playingHint;
    private MediaController mediaController;

    public HuanghunMultiDynamicWallpaperActivity() {
        this(false);
    }

    public HuanghunMultiDynamicWallpaperActivity(boolean deleteOnly) {
        this.deleteOnly = deleteOnly;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(deleteOnly ? "查看/删除当前动态视频" : "多轮循环动态壁纸");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        summary = new TextView(context);
        summary.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        summary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        summary.setLineSpacing(AndroidUtilities.dp(2), 1f);
        root.addView(summary, lp(-1, -2, 0, 0, 0, 8));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        actions.setBackground(glassBackground(0x20FFFFFF, 0x55FFFFFF, 14));

        selectAllButton = actionButton(context, "全选");
        selectAllButton.setOnClickListener(v -> toggleSelectAll());
        actions.addView(selectAllButton, lp(0, 42, 0, 0, 6, 0, 1));

        deleteButton = actionButton(context, "删除已选");
        deleteButton.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        deleteButton.setOnClickListener(v -> confirmDeleteSelected(context));
        actions.addView(deleteButton, lp(0, 42, 0, 0, 6, 0, 1));

        if (!deleteOnly) {
            TextView addButton = actionButton(context, "添加视频");
            addButton.setOnClickListener(v -> pickVideos());
            actions.addView(addButton, lp(0, 42, 0, 0, 0, 0, 1));
        }
        root.addView(actions, lp(-1, 54, 0, 0, 0, 8));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(8));
        gallery = new LinearLayout(context);
        gallery.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(gallery, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, lp(-1, 0, 0, 0, 0, 0, 1));

        fragmentView = root;
        refresh();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onFragmentDestroy() {
        stopAllVideoPlayers();
        super.onFragmentDestroy();
    }

    private LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(AndroidUtilities.dp(left), AndroidUtilities.dp(top), AndroidUtilities.dp(right), AndroidUtilities.dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom, int weight) {
        LinearLayout.LayoutParams params = lp(width, height, left, top, right, bottom);
        params.weight = weight;
        return params;
    }

    private TextView actionButton(Context context, String text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        button.setBackground(glassBackground(0x24FFFFFF, 0x5CFFFFFF, 12));
        button.setMinHeight(AndroidUtilities.dp(42));
        return button;
    }

    private GradientDrawable glassBackground(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(AndroidUtilities.dp(radiusDp));
        drawable.setStroke(Math.max(1, AndroidUtilities.dp(1)), strokeColor);
        return drawable;
    }

    private void pickVideos() {
        if (getParentActivity() == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK);
        } catch (Throwable e) {
            showMessage("打开失败", "无法打开视频选择器，请稍后重试。\n\n" + e.getMessage());
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) {
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                MultiDynamicVideoWallpaperHelper.FetchResult result = MultiDynamicVideoWallpaperHelper.importLocalVideos(ApplicationLoader.applicationContext, currentAccount, uris);
                AndroidUtilities.runOnUIThread(() -> {
                    refresh();
                    showResult(result);
                });
            });
        } else {
            super.onActivityResultFragment(requestCode, resultCode, data);
        }
    }

    private ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> currentItems() {
        if (getParentActivity() == null) {
            return new ArrayList<>();
        }
        return MultiDynamicVideoWallpaperHelper.getVideos(getParentActivity(), currentAccount);
    }

    private void refresh() {
        if (gallery == null || getParentActivity() == null) {
            return;
        }
        stopAllVideoPlayers();
        gallery.removeAllViews();

        ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> items = currentItems();
        selected.retainAll(pathsOf(items));
        boolean allSelected = !items.isEmpty() && selected.size() == items.size();
        selectAllButton.setText(allSelected ? "取消全选" : "全选");
        summary.setText("当前视频 " + items.size() + " 个\n当前播放模式：" + modeText(getParentActivity())
                + (MultiDynamicVideoWallpaperHelper.isEnabled(getParentActivity(), currentAccount) ? "\n状态：已启用" : "\n状态：未启用")
                + "\n点击视频预览，再次点击可暂停；勾选后可批量删除");

        if (items.isEmpty()) {
            emptyView = new TextView(getParentActivity());
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setText("暂无多视频动态壁纸\n请点击上方“添加视频”导入一个或多个竖屏视频。");
            emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            emptyView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            emptyView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(40), AndroidUtilities.dp(24), AndroidUtilities.dp(40));
            gallery.addView(emptyView, lp(-1, -2, 0, 12, 0, 0));
        } else {
            for (int i = 0; i < items.size(); i++) {
                LinearLayout row = new LinearLayout(getParentActivity());
                row.setOrientation(LinearLayout.VERTICAL);
                row.setGravity(Gravity.TOP);
                gallery.addView(row, lp(-1, -2, 0, 0, 0, 8));

                row.addView(createVideoCard(getParentActivity(), items.get(i), i), lp(-1, -2, 0, 0, 0, 0));
            }
        }
        updateControls(items.size());
    }

    private String modeText(Context context) {
        return MultiDynamicVideoWallpaperHelper.getMode(context, currentAccount) == MultiDynamicVideoWallpaperHelper.MODE_RANDOM ? "随机播放" : "顺序播放";
    }

    private Set<String> pathsOf(ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> items) {
        Set<String> paths = new HashSet<>();
        for (MultiDynamicVideoWallpaperHelper.VideoItem item : items) {
            paths.add(item.path);
        }
        return paths;
    }

    private View createVideoCard(Context context, MultiDynamicVideoWallpaperHelper.VideoItem item, int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        card.setBackground(glassBackground(0x22FFFFFF, 0x55FFFFFF, 16));

        FrameLayout preview = new FrameLayout(context);
        preview.setBackground(glassBackground(0x30000000, 0x46FFFFFF, 12));
        VideoView videoView = new VideoView(context);
        videoView.setVideoPath(item.path);
        videoView.setKeepScreenOn(false);
        videoView.setOnPreparedListener(mediaPlayer -> {
            try {
                mediaPlayer.setLooping(true);
                videoView.seekTo(1);
            } catch (Throwable ignore) {
            }
        });
        TextView hint = new TextView(context);
        hint.setText("点击播放");
        hint.setGravity(Gravity.CENTER);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(0xffffffff);
        hint.setBackgroundColor(0x55000000);
        hint.setClickable(false);
        View.OnClickListener playListener = v -> toggleVideo(videoView, hint);
        videoView.setOnClickListener(playListener);
        preview.setOnClickListener(playListener);
        int previewHeight = AndroidUtilities.dp(300);
        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(-1, previewHeight);
        videoParams.gravity = Gravity.CENTER;
        preview.addView(videoView, videoParams);
        preview.addView(hint, new FrameLayout.LayoutParams(-1, AndroidUtilities.dp(52), Gravity.BOTTOM));
        videoPlayers.add(videoView);
        card.addView(preview, lp(-1, 300, 0, 0, 0, 8));

        TextView title = new TextView(context);
        title.setText("视频 " + (index + 1) + "  ·  " + new File(item.path).getName());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        card.addView(title, lp(-1, -2, 0, 0, 0, 2));

        TextView info = new TextView(context);
        info.setText(formatDuration(item.durationMs) + "  ·  " + formatSize(item.size));
        info.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        card.addView(info, lp(-1, -2, 0, 0, 0, 3));

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView check = actionButton(context, selected.contains(item.path) ? "已选择" : "选择");
        check.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        check.setOnClickListener(v -> {
            if (selected.contains(item.path)) {
                selected.remove(item.path);
                check.setText("选择");
            } else {
                selected.add(item.path);
                check.setText("已选择");
            }
            updateControls(currentItems().size());
        });
        controls.addView(check, lp(0, 48, 0, 0, 4, 0, 1));

        TextView delete = actionButton(context, "删除");
        delete.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        delete.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        delete.setOnClickListener(v -> confirmDeleteSingle(context, item.path));
        controls.addView(delete, lp(0, 48, 4, 0, 0, 0, 1));
        card.addView(controls, lp(-1, 48, 0, 4, 0, 0));
        return card;
    }

    private void toggleVideo(VideoView videoView, TextView hint) {
        if (playingVideo == videoView) {
            try {
                videoView.pause();
            } catch (Throwable ignore) {
            }
            hint.setText("继续播放");
            playingVideo = null;
            playingHint = null;
            return;
        }
        if (playingVideo != null) {
            try {
                playingVideo.pause();
            } catch (Throwable ignore) {
            }
            if (playingHint != null) {
                playingHint.setText("继续播放");
            }
        }
        playingVideo = videoView;
        playingHint = hint;
        if (mediaController == null && getParentActivity() != null) {
            mediaController = new MediaController(getParentActivity());
        }
        if (mediaController != null) {
            videoView.setMediaController(mediaController);
        }
        try {
            videoView.start();
            hint.setText("暂停");
            if (mediaController != null) {
                mediaController.show(4000);
            }
        } catch (Throwable e) {
            playingVideo = null;
            playingHint = null;
            hint.setText("点击播放");
            showMessage("播放失败", "该视频无法在当前设备上播放，请删除后重新选择。\n\n" + e.getMessage());
        }
    }

    private void stopAllVideoPlayers() {
        if (playingVideo != null) {
            try {
                playingVideo.pause();
            } catch (Throwable ignore) {
            }
        }
        playingVideo = null;
        playingHint = null;
        for (VideoView videoView : videoPlayers) {
            try {
                videoView.stopPlayback();
            } catch (Throwable ignore) {
            }
        }
        videoPlayers.clear();
    }

    private void toggleSelectAll() {
        ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> items = currentItems();
        if (items.isEmpty()) {
            return;
        }
        if (selected.size() == items.size()) {
            selected.clear();
        } else {
            selected.clear();
            for (MultiDynamicVideoWallpaperHelper.VideoItem item : items) {
                selected.add(item.path);
            }
        }
        refresh();
    }

    private void updateControls(int itemCount) {
        if (selectAllButton == null || deleteButton == null) {
            return;
        }
        boolean hasItems = itemCount > 0;
        selectAllButton.setEnabled(hasItems);
        deleteButton.setEnabled(!selected.isEmpty());
        selectAllButton.setAlpha(hasItems ? 1f : .45f);
        deleteButton.setAlpha(selected.isEmpty() ? .45f : 1f);
        selectAllButton.setText(hasItems && selected.size() == itemCount ? "取消全选" : "全选");
        deleteButton.setText(selected.isEmpty() ? "删除已选" : "删除已选（" + selected.size() + "）");
    }

    private void confirmDeleteSingle(Context context, String path) {
        new AlertDialog.Builder(context, getResourceProvider())
                .setTitle("删除视频")
                .setMessage("确定删除这个视频吗？删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    stopAllVideoPlayers();
                    MultiDynamicVideoWallpaperHelper.deleteVideos(context, currentAccount, java.util.Collections.singletonList(path));
                    selected.remove(path);
                    refresh();
                }).show();
    }

    private void confirmDeleteSelected(Context context) {
        if (selected.isEmpty()) {
            showMessage("提示", "请先勾选要删除的视频。");
            return;
        }
        new AlertDialog.Builder(context, getResourceProvider())
                .setTitle("批量删除视频")
                .setMessage("确定删除已选择的 " + selected.size() + " 个视频吗？删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    stopAllVideoPlayers();
                    MultiDynamicVideoWallpaperHelper.deleteVideos(context, currentAccount, new ArrayList<>(selected));
                    selected.clear();
                    refresh();
                }).show();
    }

    private void showMessage(String title, String message) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void showResult(MultiDynamicVideoWallpaperHelper.FetchResult result) {
        String text = "已新增 " + result.imported + " 个视频。";
        if (result.skippedLandscape > 0) {
            text += " 已跳过 " + result.skippedLandscape + " 个横屏视频。";
        }
        if (!result.errors.isEmpty()) {
            text += "\n有 " + result.errors.size() + " 个视频无法读取或播放。";
        }
        showMessage("处理完成", text);
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
