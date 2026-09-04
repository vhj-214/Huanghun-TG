package tw.nekomimi.nekogram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import tw.nekomimi.nekogram.helpers.DynamicVideoWallpaperHelper;
import tw.nekomimi.nekogram.helpers.MultiDynamicVideoWallpaperHelper;

public class HuanghunMultiDynamicWallpaperActivity extends BaseFragment {
    private static final int REQUEST_PICK = 7701;
    private final boolean deleteOnly;
    private LinearLayout list;
    private TextView summary;
    private final Set<String> selected = new HashSet<>();

    public HuanghunMultiDynamicWallpaperActivity() { this(false); }
    public HuanghunMultiDynamicWallpaperActivity(boolean deleteOnly) { this.deleteOnly = deleteOnly; }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(deleteOnly ? "查看/删除当前动态视频" : "多轮循环动态壁纸");
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
        LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12)); root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        summary = new TextView(context); summary.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText)); summary.setTextSize(15); root.addView(summary, lp( -1, -2, 0, 0, 0, 10));
        if (!deleteOnly) {
            Button enable = button(context, "开启多轮循环动态壁纸"); enable.setOnClickListener(v -> { MultiDynamicVideoWallpaperHelper.setEnabled(context, currentAccount, true); DynamicVideoWallpaperHelper.clearVideo(context, currentAccount, 0L); refresh(); }); root.addView(enable, lp(-1, 46, 0, 0, 0, 8));
            Button pick = button(context, "从手机选择竖屏视频"); pick.setOnClickListener(v -> pickVideos()); root.addView(pick, lp(-1, 46, 0, 0, 0, 8));
            Button api = button(context, "通过接口解析视频"); api.setOnClickListener(v -> showApiDialog(context)); root.addView(api, lp(-1, 46, 0, 0, 0, 8));
            Button mode = button(context, "切换播放模式：" + modeText(context)); mode.setOnClickListener(v -> { MultiDynamicVideoWallpaperHelper.setMode(context, currentAccount, MultiDynamicVideoWallpaperHelper.getMode(context, currentAccount) == MultiDynamicVideoWallpaperHelper.MODE_ORDER ? MultiDynamicVideoWallpaperHelper.MODE_RANDOM : MultiDynamicVideoWallpaperHelper.MODE_ORDER); ((Button)v).setText("切换播放模式：" + modeText(context)); }); root.addView(mode, lp(-1, 46, 0, 0, 0, 8));
        }
        Button delete = button(context, "删除已勾选视频"); delete.setOnClickListener(v -> deleteSelected(context)); root.addView(delete, lp(-1, 46, 0, 0, 0, 8));
        list = new LinearLayout(context); list.setOrientation(LinearLayout.VERTICAL); root.addView(list, lp(-1, -1, 0, 8, 0, 0)); fragmentView = root; refresh(); return root;
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    private Button button(Context c, String text) { Button b = new Button(c); b.setText(text); b.setAllCaps(false); return b; }
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(AndroidUtilities.dp(l),AndroidUtilities.dp(t),AndroidUtilities.dp(r),AndroidUtilities.dp(b));return p; }
    private String modeText(Context c) { return MultiDynamicVideoWallpaperHelper.getMode(c,currentAccount)==MultiDynamicVideoWallpaperHelper.MODE_RANDOM?"随机播放":"顺序播放"; }
    private void pickVideos() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("video/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,REQUEST_PICK); }
    @Override public void onActivityResultFragment(int requestCode,int resultCode,Intent data){ if(requestCode==REQUEST_PICK&&resultCode==Activity.RESULT_OK&&data!=null){ ArrayList<Uri> uris=new ArrayList<>(); if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri()); else if(data.getData()!=null)uris.add(data.getData()); Utilities.globalQueue.postRunnable(()->{ MultiDynamicVideoWallpaperHelper.FetchResult r=MultiDynamicVideoWallpaperHelper.importLocalVideos(ApplicationLoader.applicationContext,currentAccount,uris); AndroidUtilities.runOnUIThread(()->{ refresh(); showResult(r); }); }); } else super.onActivityResultFragment(requestCode,resultCode,data); }
    private void showApiDialog(Context c){ final android.widget.EditText input=new android.widget.EditText(c); input.setSingleLine(false); input.setMinLines(4); input.setText(MultiDynamicVideoWallpaperHelper.getApiText(c)); input.setHint("每行一个接口路径"); new AlertDialog.Builder(c,getResourceProvider()).setTitle("视频接口").setMessage("内置接口已填入；接口返回的横屏视频会自动跳过。支持添加多个接口，每行一个。\n\n注意：接口需返回可直接下载的视频地址或 JSON 中包含视频地址。") .setView(input).setNegativeButton("取消",null).setNeutralButton("保存",(d,w)->MultiDynamicVideoWallpaperHelper.setApiText(c,input.getText().toString())).setPositiveButton("获取并启用",(d,w)->{ MultiDynamicVideoWallpaperHelper.setApiText(c,input.getText().toString()); Utilities.globalQueue.postRunnable(()->{MultiDynamicVideoWallpaperHelper.FetchResult r=MultiDynamicVideoWallpaperHelper.fetchFromApis(ApplicationLoader.applicationContext,currentAccount,input.getText().toString());AndroidUtilities.runOnUIThread(()->{refresh();showResult(r);});}); }).show(); }
    private void showResult(MultiDynamicVideoWallpaperHelper.FetchResult r){ String text="已新增 " + r.imported + " 个视频。"; if(r.skippedLandscape>0)text+=" 已跳过 " + r.skippedLandscape + " 个横屏视频。"; if(!r.errors.isEmpty())text+="\n有 " + r.errors.size() + " 个地址获取失败或不是可播放视频。"; new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle("处理完成").setMessage(text).setPositiveButton("确定",null).show(); }
    private void refresh(){ if(list==null||getParentActivity()==null)return; list.removeAllViews(); ArrayList<MultiDynamicVideoWallpaperHelper.VideoItem> items=MultiDynamicVideoWallpaperHelper.getVideos(getParentActivity(),currentAccount); summary.setText("当前已选视频 " + items.size() + " 个\n当前播放模式：" + modeText(getParentActivity()) + (MultiDynamicVideoWallpaperHelper.isEnabled(getParentActivity(),currentAccount)?"\n状态：已启用":"\n状态：未启用")); for(MultiDynamicVideoWallpaperHelper.VideoItem item:items){ CheckBox c=new CheckBox(getParentActivity()); c.setText(new java.io.File(item.path).getName()); c.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText)); c.setChecked(selected.contains(item.path)); c.setOnClickListener(v->{if(c.isChecked())selected.add(item.path);else selected.remove(item.path);}); list.addView(c); } }
    private void deleteSelected(Context c){ if(selected.isEmpty())return; MultiDynamicVideoWallpaperHelper.deleteVideos(c,currentAccount,new ArrayList<>(selected)); selected.clear(); refresh(); }
}
