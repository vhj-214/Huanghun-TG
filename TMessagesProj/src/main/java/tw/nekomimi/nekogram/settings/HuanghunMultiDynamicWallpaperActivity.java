package tw.nekomimi.nekogram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.MediaController;
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
import java.util.Set;

import tw.nekomimi.nekogram.helpers.MultiDynamicVideoWallpaperHelper;

/** 本地多视频壁纸的选择、预览和删除页面。 */
public class HuanghunMultiDynamicWallpaperActivity extends BaseFragment {
    private static final int REQUEST_PICK = 7701;
    private final boolean deleteOnly;
    private LinearLayout gallery;
    private TextView summary;
    private CheckBox selectAll;
    private final Set<String> selected = new HashSet<>();
    private final ArrayList<VideoView> videoPlayers = new ArrayList<>();
    private VideoView playingVideo;
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
        summary.setTextSize(15);
        root.addView(summary, lp(-1, -2, 0, 0, 0, 8));

        if (!deleteOnly) {
            Button enable = button(context, "选择视频并开启多轮循环动态壁纸");
            enable.setOnClickListener(v -> pickVideos());
            root.addView(enable, lp(-1, 46, 0, 0, 0, 6));

            Button mode = button(context, "切换播放模式：" + modeText(context));
            mode.setOnClickListener(v -> {
                MultiDynamicVideoWallpaperHelper.setMode(context, currentAccount,
                        MultiDynamicVideoWallpaperHelper.getMode(context, currentAccount) == MultiDynamicVideoWallpaperHelper.MODE_ORDER
                                ? MultiDynamicVideoWallpaperHelper.MODE_RANDOM
                                : MultiDynamicVideoWallpaperHelper.MODE_ORDER);
                ((Button) v).setText("切换播放模式：" + modeText(context));
            });
            root.addView(mode, lp(-1, 46, 0, 0, 0, 6));
        }

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        selectAll = new CheckBox(context);
        selectAll.setText("全选");
        selectAll.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        selectAll.setOnClickListener(v -> {
            ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> items = currentItems();
            if (selectAll.isChecked()) {
                for (MultiDynamicVideoWallpaperHelper.VideoItem item : items) {
                    selected.add(item.path);
                }
            } else {
                selected.clear();
            }
            refresh();
        });
        actions.addView(selectAll, lp(0, 46, 0, 0, 8, 0, 1));

        Button delete = button(context, "删除选中");
        delete.setOnClickListener(v -> confirmDeleteSelected(context));
        actions.addView(delete, lp(0, 42, 0, 0, 8, 0, 1));

        Button add = button(context, "继续添加视频");
        add.setOnClickListener(v -> pickVideos());
        actions.addView(add, lp(0, 42, 0, 0, 0, 0, 1));
        root.addView(actions, lp(-1, 48, 0, 0, 0, 4));

        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(context);
        horizontalScrollView.setFillViewport(false);
        horizontalScrollView.setClipToPadding(false);
        horizontalScrollView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        gallery = new LinearLayout(context);
        gallery.setOrientation(LinearLayout.HORIZONTAL);
        horizontalScrollView.addView(gallery, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(horizontalScrollView, lp(-1, 0, 0, 0, 0, 0, 1));

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
        stopPlayingVideo();
        for (VideoView videoView : videoPlayers) {
            try {
                videoView.stopPlayback();
            } catch (Throwable ignore) {
            }
        }
        videoPlayers.clear();
        super.onFragmentDestroy();
    }

    private Button button(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        return button;
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

    private String modeText(Context context) {
        return MultiDynamicVideoWallpaperHelper.getMode(context, currentAccount) == MultiDynamicVideoWallpaperHelper.MODE_RANDOM ? "随机播放" : "顺序播放";
    }

    private void pickVideos() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_PICK);
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
        stopPlayingVideo();
        for (VideoView videoView : videoPlayers) {
            try {
                videoView.stopPlayback();
            } catch (Throwable ignore) {
            }
        }
        videoPlayers.clear();
        gallery.removeAllViews();

        ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> items = currentItems();
        selected.retainAll(pathsOf(items));
        selectAll.setChecked(!items.isEmpty() && selected.size() == items.size());
        summary.setText("当前视频 " + items.size() + " 个\n当前播放模式：" + modeText(getParentActivity())
                + (MultiDynamicVideoWallpaperHelper.isEnabled(getParentActivity(), currentAccount) ? "\n状态：已启用" : "\n状态：未启用")
                + "\n左右滑动浏览，点击视频播放");

        for (MultiDynamicVideoWallpaperHelper.VideoItem item : items) {
            gallery.addView(createVideoCard(getParentActivity(), item), lp(220, -1, 0, 0, 8, 0));
        }
    }

    private Set<String> pathsOf(ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> items) {
        Set<String> paths = new HashSet<>();
        for (MultiDynamicVideoWallpaperHelper.VideoItem item : items) {
            paths.add(item.path);
        }
        return paths;
    }

    private View createVideoCard(Context context, MultiDynamicVideoWallpaperHelper.VideoItem item) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        card.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        VideoView videoView = new VideoView(context);
        videoView.setVideoPath(item.path);
        videoView.setKeepScreenOn(false);
        videoView.setOnClickListener(v -> toggleVideo(videoView));
        videoPlayers.add(videoView);
        card.addView(videoView, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(300)));

        TextView name = new TextView(context);
        name.setText(new File(item.path).getName());
        name.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setTextSize(13);
        name.setMaxLines(2);
        card.addView(name, lp(-1, -2, 0, 4, 0, 0));

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox checkBox = new CheckBox(context);
        checkBox.setChecked(selected.contains(item.path));
        checkBox.setText("选择");
        checkBox.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        checkBox.setOnClickListener(v -> {
            if (checkBox.isChecked()) {
                selected.add(item.path);
            } else {
                selected.remove(item.path);
            }
            selectAll.setChecked(!currentItems().isEmpty() && selected.size() == currentItems().size());
        });
        controls.addView(checkBox, lp(0, 44, 0, 0, 0, 0, 1));

        Button delete = button(context, "删除");
        delete.setOnClickListener(v -> confirmDeleteSingle(context, item.path));
        controls.addView(delete, lp(76, 40, 0, 0, 0, 0));
        card.addView(controls, lp(-1, 44, 0, 2, 0, 0));
        return card;
    }

    private void toggleVideo(VideoView videoView) {
        if (playingVideo != null && playingVideo != videoView) {
            try {
                playingVideo.pause();
            } catch (Throwable ignore) {
            }
        }
        playingVideo = videoView;
        if (mediaController == null) {
            mediaController = new MediaController(getParentActivity());
        }
        videoView.setMediaController(mediaController);
        try {
            if (videoView.isPlaying()) {
                videoView.pause();
            } else {
                videoView.start();
            }
        } catch (Throwable e) {
            showMessage("播放失败", "该视频无法在当前设备上播放，请删除后重新选择。" + "\n\n" + e.getMessage());
        }
    }

    private void stopPlayingVideo() {
        if (playingVideo != null) {
            try {
                playingVideo.pause();
            } catch (Throwable ignore) {
            }
            playingVideo = null;
        }
    }

    private void confirmDeleteSingle(Context context, String path) {
        new AlertDialog.Builder(context, getResourceProvider())
                .setTitle("删除视频")
                .setMessage("确定删除这个视频吗？删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
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
}
