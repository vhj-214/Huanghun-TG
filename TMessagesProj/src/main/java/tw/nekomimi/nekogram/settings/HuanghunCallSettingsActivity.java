package tw.nekomimi.nekogram.settings;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.HuanghunVirtualCameraCapturer;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.HuanghunCallVideoLibraryHelper;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * 通话专区设置页。
 *
 * 通话虚拟摄像头的视频文件使用独立的账号隔离库，绝不会读取或修改视频录制专区的视频列表。
 */
public class HuanghunCallSettingsActivity extends BaseNekoSettingsActivity {

    private static final int REQUEST_HUANGHUN_CALL_VIDEOS = 10938;

    private int callHeaderRow;
    private int defaultCameraRow;
    private int callCameraNoticeRow;
    private int virtualCameraHeaderRow;
    private int virtualCameraEnabledRow;
    private int selectCallVideosRow;
    private int virtualVideoSoundRow;
    private int viewCallVideosRow;
    private int deleteCallVideosRow;
    private int virtualCameraNoticeRow;
    private int callEndRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        callHeaderRow = addRow();
        defaultCameraRow = addRow();
        callCameraNoticeRow = addRow();
        virtualCameraHeaderRow = addRow();
        virtualCameraEnabledRow = addRow();
        selectCallVideosRow = addRow();
        virtualVideoSoundRow = addRow();
        viewCallVideosRow = addRow();
        deleteCallVideosRow = addRow();
        virtualCameraNoticeRow = addRow();
        callEndRow = addRow();
    }

    @Override
    public void onResume() {
        super.onResume();
        notifyCallRows();
    }

    @Override
    protected String getActionBarTitle() {
        return "通话专区";
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == defaultCameraRow) {
            showDefaultCameraChooser();
            return;
        }
        if (position == virtualCameraEnabledRow) {
            boolean enabled = NekoConfig.huanghunCallVirtualCameraEnabled.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            if (enabled && HuanghunCallVideoLibraryHelper.getVideoCount(ApplicationLoader.applicationContext, currentAccount) == 0) {
                showInfo("请先选取视频", "已开启内置虚拟摄像头。请先点击“选取内置视频”导入至少一个视频；下次开启视频通话摄像头时才会播放本地视频。\n\n关闭此开关后，通话会完全恢复 Telegram 官方前置、后置摄像头或手机屏幕共享流程。");
            }
            return;
        }
        if (position == selectCallVideosRow) {
            chooseCallVideos();
            return;
        }
        if (position == virtualVideoSoundRow) {
            boolean enabled = NekoConfig.huanghunCallVirtualVideoSound.toggleConfigBool();
            HuanghunVirtualCameraCapturer.refreshSoundState();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            return;
        }
        if (position == viewCallVideosRow) {
            presentFragment(new HuanghunCallVideoLibraryActivity(false));
            return;
        }
        if (position == deleteCallVideosRow) {
            presentFragment(new HuanghunCallVideoLibraryActivity(true));
        }
    }

    private void showDefaultCameraChooser() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final String[] cameras = new String[]{"前置摄像头", "后置摄像头", "手机屏幕"};
        int selected = NekoConfig.huanghunCallDefaultCamera.Int();
        if (selected < 0 || selected >= cameras.length) {
            selected = 1;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle("通话默认摄像头");
        builder.setItems(cameras, (dialog, which) -> {
            if (which >= 0 && which < cameras.length) {
                NekoConfig.huanghunCallDefaultCamera.setConfigInt(which);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(defaultCameraRow);
                }
            }
        });
        showDialog(builder.create());
    }

    private void chooseCallVideos() {
        if (getParentActivity() == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_HUANGHUN_CALL_VIDEOS);
        } catch (Throwable e) {
            FileLog.e(e);
            showInfo("无法选择视频", "无法打开系统视频选择器，请稍后重试。");
        }
    }

    private void importCallVideos(ArrayList<Uri> sources) {
        if (sources == null || sources.isEmpty() || getParentActivity() == null) {
            showInfo("导入失败", "未读取到所选视频，请重新选择。");
            return;
        }
        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setMessage("正在保存并验证通话内置视频，请稍候。\n");
        progress.setCancelable(false);
        showDialog(progress);
        final int account = currentAccount;
        Utilities.globalQueue.postRunnable(() -> {
            HuanghunCallVideoLibraryHelper.ImportResult result;
            try {
                result = HuanghunCallVideoLibraryHelper.importVideos(ApplicationLoader.applicationContext, account, sources);
            } catch (Throwable e) {
                FileLog.e(e);
                ArrayList<String> errors = new ArrayList<>();
                String message = e.getMessage();
                errors.add(message == null || message.length() == 0 ? "视频导入失败，请稍后重试。" : message);
                result = new HuanghunCallVideoLibraryHelper.ImportResult(0, errors);
            }
            final HuanghunCallVideoLibraryHelper.ImportResult importResult = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (progress.isShowing()) {
                    progress.dismiss();
                }
                notifyCallRows();
                if (importResult.importedCount > 0) {
                    String message = "已导入 " + importResult.importedCount + " 个通话内置视频。";
                    if (importResult.hasErrors()) {
                        message += "部分文件未能导入。";
                    }
                    BulletinFactory.of(HuanghunCallSettingsActivity.this).createSimpleBulletin(R.raw.done, message).show();
                } else {
                    String message = importResult.errors.isEmpty() ? "所选视频无法导入，请更换视频后重试。" : importResult.errors.get(0);
                    showInfo("导入失败", message);
                }
            });
        });
    }

    private void showInfo(String title, String message) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(context, resourceProvider)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_HUANGHUN_CALL_VIDEOS && resultCode == Activity.RESULT_OK) {
            ArrayList<Uri> sources = new ArrayList<>();
            if (data != null) {
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (uri != null && !sources.contains(uri)) {
                            sources.add(uri);
                        }
                    }
                }
                Uri singleUri = data.getData();
                if (singleUri != null && !sources.contains(singleUri)) {
                    sources.add(singleUri);
                }
            }
            importCallVideos(sources);
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    private void notifyCallRows() {
        if (listAdapter == null) {
            return;
        }
        listAdapter.notifyItemChanged(defaultCameraRow);
        listAdapter.notifyItemChanged(virtualCameraEnabledRow);
        listAdapter.notifyItemChanged(selectCallVideosRow);
        listAdapter.notifyItemChanged(virtualVideoSoundRow);
        listAdapter.notifyItemChanged(viewCallVideosRow);
        listAdapter.notifyItemChanged(deleteCallVideosRow);
    }

    private String getDefaultCameraName() {
        switch (NekoConfig.huanghunCallDefaultCamera.Int()) {
            case 0:
                return "前置摄像头";
            case 2:
                return "手机屏幕";
            case 1:
            default:
                return "后置摄像头";
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        private ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_CHECK || type == TYPE_SETTINGS;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            int type = holder.getItemViewType();
            if (type == TYPE_HEADER) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                cell.setText(position == callHeaderRow ? "通话设置" : "虚拟摄像头配置");
            } else if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                if (position == virtualCameraEnabledRow) {
                    cell.setTextAndCheck("启动内置虚拟摄像头", NekoConfig.huanghunCallVirtualCameraEnabled.Bool(), true);
                } else if (position == virtualVideoSoundRow) {
                    cell.setTextAndCheck("视频声音", NekoConfig.huanghunCallVirtualVideoSound.Bool(), true);
                }
            } else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == defaultCameraRow) {
                    cell.setTextAndValue("通话默认摄像头", getDefaultCameraName(), false);
                } else if (position == selectCallVideosRow) {
                    int count = HuanghunCallVideoLibraryHelper.getVideoCount(mContext, currentAccount);
                    cell.setTextAndValue("选取内置视频", count == 0 ? "未选取" : "已选取 " + count + " 个", true);
                } else if (position == viewCallVideosRow) {
                    cell.setText("查看内置视频", true);
                } else if (position == deleteCallVideosRow) {
                    cell.setText("删除视频", false);
                }
            } else if (type == TYPE_INFO_PRIVACY) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(position == callCameraNoticeRow
                        ? "默认摄像头用于关闭虚拟摄像头时的新视频通话。选择“手机屏幕”时，开启视频会进入 Telegram 官方屏幕共享授权流程。"
                        : "通话内置视频仅保存在当前设备和当前账号中，与视频录制专区完全隔离。开启虚拟摄像头并在视频通话中开启摄像头后，将循环播放所选视频；通话界面会显示上一个、下一个、暂停和播放控制按钮。视频会保持原始宽高比例，自适应通话画面。关闭“视频声音”后，不会向通话上行混入内置视频原声。");
                cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            } else if (type == TYPE_SHADOW) {
                holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == callHeaderRow || position == virtualCameraHeaderRow) {
                return TYPE_HEADER;
            }
            if (position == virtualCameraEnabledRow || position == virtualVideoSoundRow) {
                return TYPE_CHECK;
            }
            if (position == callCameraNoticeRow || position == virtualCameraNoticeRow) {
                return TYPE_INFO_PRIVACY;
            }
            if (position == callEndRow) {
                return TYPE_SHADOW;
            }
            return TYPE_SETTINGS;
        }
    }
}
