package tw.nekomimi.nekogram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
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
    private final HashMap<String, Long> durationCache = new HashMap<>();
    private final ArrayList<PreviewPlayer> previewPlayers = new ArrayList<>();
    private PreviewPlayer playingPreview;
    private int refreshGeneration;

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
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setWeightSum(deleteOnly ? 2f : 3f);
        actions.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        actions.setBackground(glassBackground(0xFFF7F9FC, 0xFFDCE3EC, 14));

        selectAllButton = actionButton(context, "全选");
        selectAllButton.setOnClickListener(v -> toggleSelectAll());
        actions.addView(selectAllButton, lp(0, 46, 0, 0, 4, 0, 1));

        deleteButton = actionButton(context, "删除已选");
        deleteButton.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        deleteButton.setOnClickListener(v -> confirmDeleteSelected(context));
        actions.addView(deleteButton, lp(0, 46, 0, 0, 4, 0, 1));

        if (!deleteOnly) {
            TextView addButton = actionButton(context, "添加视频");
            addButton.setOnClickListener(v -> pickVideos());
            actions.addView(addButton, lp(0, 46, 0, 0, 0, 0, 1));
        }
        root.addView(actions, lp(-1, -2, 0, 0, 0, 8));

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
        refreshGeneration++;
        stopAllVideoPlayers();
        super.onFragmentDestroy();
    }

    private LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom) {
        int resolvedHeight = height > 0 ? AndroidUtilities.dp(height) : height;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, resolvedHeight);
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
        button.setIncludeFontPadding(false);
        button.setMaxLines(1);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        button.setBackground(glassBackground(0xFFFFFFFF, 0xFFD8E0EA, 12));
        button.setMinHeight(AndroidUtilities.dp(42));
        button.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
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
        ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> result = new ArrayList<>();
        for (String path : MultiDynamicVideoWallpaperHelper.getVideoPaths(getParentActivity(), currentAccount)) {
            Long duration = durationCache.get(path);
            result.add(new MultiDynamicVideoWallpaperHelper.VideoItem(path, duration == null ? 0L : duration, new File(path).length()));
        }
        return result;
    }

    private void refresh() {
        if (gallery == null || getParentActivity() == null) {
            return;
        }
        final int generation = ++refreshGeneration;
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
        loadMissingDurations(items, generation);
    }

    /** Never block the settings screen on media parser/native codec work. */
    private void loadMissingDurations(ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> items, int generation) {
        ArrayList<String> pending = new ArrayList<>();
        for (MultiDynamicVideoWallpaperHelper.VideoItem item : items) {
            if (!durationCache.containsKey(item.path)) {
                pending.add(item.path);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            HashMap<String, Long> loaded = new HashMap<>();
            for (String path : pending) {
                loaded.put(path, MultiDynamicVideoWallpaperHelper.getVideoDuration(path));
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (fragmentView == null || generation != refreshGeneration || getParentActivity() == null) {
                    return;
                }
                durationCache.putAll(loaded);
                refresh();
            });
        });
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
        card.setBackground(glassBackground(0xFFFFFFFF, 0xFFE0E6EE, 16));

        FrameLayout preview = new FrameLayout(context);
        preview.setBackground(glassBackground(Color.BLACK, 0xFFCBD3DE, 12));
        preview.setClipToOutline(true);
        TextureView textureView = new TextureView(context);
        textureView.setOpaque(false);
        textureView.setBackgroundColor(Color.BLACK);
        TextView hint = new TextView(context);
        hint.setText("点击播放");
        hint.setGravity(Gravity.CENTER);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(0xffffffff);
        hint.setClickable(false);
        hint.setIncludeFontPadding(false);
        hint.setTypeface(AndroidUtilities.bold());
        hint.setBackgroundColor(0x99000000);
        View.OnClickListener playListener = v -> toggleVideo(textureView, hint);
        textureView.setOnClickListener(playListener);
        preview.setOnClickListener(playListener);
        int previewHeight = AndroidUtilities.dp(280);
        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(-1, previewHeight);
        videoParams.gravity = Gravity.CENTER;
        preview.addView(textureView, videoParams);
        preview.addView(hint, new FrameLayout.LayoutParams(-1, AndroidUtilities.dp(52), Gravity.BOTTOM));
        PreviewPlayer player = new PreviewPlayer(textureView, hint, item.path);
        previewPlayers.add(player);
        card.addView(preview, lp(-1, 280, 0, 0, 0, 8));

        TextView title = new TextView(context);
        title.setText("视频 " + (index + 1) + "  ·  " + new File(item.path).getName());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        card.addView(title, lp(-1, -2, 0, 0, 0, 2));

        TextView info = new TextView(context);
        info.setText(formatDuration(item.durationMs) + "  ·  " + formatSize(item.size));
        info.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        card.addView(info, lp(-1, -2, 0, 0, 0, 3));

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setWeightSum(2f);
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
        controls.addView(check, lp(0, 46, 0, 0, 4, 0, 1));

        TextView delete = actionButton(context, "删除");
        delete.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        delete.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        delete.setOnClickListener(v -> confirmDeleteSingle(context, item.path));
        controls.addView(delete, lp(0, 46, 4, 0, 0, 0, 1));
        card.addView(controls, lp(-1, 48, 0, 4, 0, 0));
        return card;
    }

    private void toggleVideo(TextureView textureView, TextView hint) {
        PreviewPlayer target = null;
        for (PreviewPlayer player : previewPlayers) {
            if (player.textureView == textureView) {
                target = player;
                break;
            }
        }
        if (target == null) {
            hint.setText("无法播放");
            return;
        }
        if (playingPreview == target) {
            target.pause();
            playingPreview = null;
            return;
        }
        if (playingPreview != null) {
            playingPreview.pause();
        }
        playingPreview = target;
        target.play();
    }

    private void stopAllVideoPlayers() {
        for (PreviewPlayer player : previewPlayers) {
            try {
                player.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        previewPlayers.clear();
        playingPreview = null;
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
        if (durationMs <= 0) {
            return "时长未知";
        }
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

    /** 管理页预览播放器：使用项目动态壁纸同款 Surface 管线，避免黑帧和生命周期竞态。 */
    private final class PreviewPlayer implements TextureView.SurfaceTextureListener {
        private final TextureView textureView;
        private final TextView hint;
        private final String path;
        private MediaPlayer mediaPlayer;
        private Surface surface;
        private SurfaceTexture surfaceTexture;
        private boolean released;
        private boolean playWhenPrepared;
        private int videoWidth;
        private int videoHeight;

        PreviewPlayer(TextureView textureView, TextView hint, String path) {
            this.textureView = textureView;
            this.hint = hint;
            this.path = path;
            textureView.setSurfaceTextureListener(this);
        }

        void play() {
            if (released) {
                return;
            }
            playWhenPrepared = true;
            hint.setText("加载中…");
            if (mediaPlayer == null) {
                if (textureView.isAvailable()) {
                    prepare(textureView.getSurfaceTexture());
                }
                return;
            }
            try {
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
                hint.setText("暂停");
            } catch (Throwable e) {
                FileLog.e(e);
                showPlaybackError();
            }
        }

        void pause() {
            playWhenPrepared = false;
            try {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            hint.setText("继续播放");
        }

        private void prepare(SurfaceTexture texture) {
            if (released || texture == null || path == null || !new File(path).isFile()) {
                showPlaybackError();
                return;
            }
            releaseMediaPlayer();
            try {
                surfaceTexture = texture;
                surface = new Surface(texture);
                MediaPlayer player = new MediaPlayer();
                mediaPlayer = player;
                player.setDataSource(path);
                player.setSurface(surface);
                player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                player.setLooping(true);
                player.setOnVideoSizeChangedListener((mp, width, height) -> {
                    videoWidth = width;
                    videoHeight = height;
                    configureVideoBuffer();
                    applyFitCenter();
                });
                player.setOnPreparedListener(mp -> {
                    if (released || mp != mediaPlayer || !textureView.isAvailable()) {
                        return;
                    }
                    videoWidth = mp.getVideoWidth();
                    videoHeight = mp.getVideoHeight();
                    configureVideoBuffer();
                    applyFitCenter();
                    textureView.setAlpha(1f);
                    if (playWhenPrepared) {
                        try {
                            mp.start();
                            hint.setText("暂停");
                        } catch (Throwable e) {
                            FileLog.e(e);
                            showPlaybackError();
                        }
                    } else {
                        try {
                            mp.seekTo(1);
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                        hint.setText("点击播放");
                    }
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    FileLog.e("Multi wallpaper preview playback failed: " + what + "/" + extra + ": " + path);
                    showPlaybackError();
                    return true;
                });
                player.prepareAsync();
            } catch (Throwable e) {
                FileLog.e(e);
                showPlaybackError();
            }
        }

        private void showPlaybackError() {
            playWhenPrepared = false;
            hint.setText("无法播放");
            textureView.setAlpha(1f);
            releaseMediaPlayer();
            if (playingPreview == this) {
                playingPreview = null;
            }
        }

        private void configureVideoBuffer() {
            if (surfaceTexture == null || videoWidth <= 0 || videoHeight <= 0) {
                return;
            }
            try {
                surfaceTexture.setDefaultBufferSize(videoWidth, videoHeight);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        private void applyFitCenter() {
            if (released || videoWidth <= 0 || videoHeight <= 0 || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) {
                return;
            }
            float scale = Math.min(textureView.getWidth() / (float) videoWidth, textureView.getHeight() / (float) videoHeight);
            float scaledWidth = videoWidth * scale;
            float scaledHeight = videoHeight * scale;
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate((textureView.getWidth() - scaledWidth) / 2f, (textureView.getHeight() - scaledHeight) / 2f);
            textureView.setTransform(matrix);
        }

        void release() {
            released = true;
            playWhenPrepared = false;
            textureView.setSurfaceTextureListener(null);
            releaseMediaPlayer();
        }

        private void releaseMediaPlayer() {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.reset();
                    mediaPlayer.release();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                mediaPlayer = null;
            }
            if (surface != null) {
                try {
                    surface.release();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                surface = null;
            }
            surfaceTexture = null;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            prepare(surface);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            applyFitCenter();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            releaseMediaPlayer();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    }
}
