package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.HuanghunPetOverlay;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import tw.nekomimi.nekogram.helpers.HuanghunPetHelper;

/** Huanghun resource-only desktop pet manager. */
public class NekoPetActivity extends BaseNekoSettingsActivity {
    private static final int REQUEST_IMPORT_PET = 18831;
    private static final int ROW_TUTORIAL = 0;
    private static final int ROW_IMPORT = 1;
    private static final int ROW_EMPTY = 2;
    private final ArrayList<HuanghunPetHelper.PetInfo> pets = new ArrayList<>();
    private int rowCountStart;

    @Override protected void updateRows() {
        super.updateRows();
        addRow();
        addRow();
        rowCountStart = pets.isEmpty() ? 3 : 2;
        if (pets.isEmpty()) addRow();
        for (int i = 0; i < pets.size(); i++) addRow();
    }
    @Override public boolean onFragmentCreate() {
        pets.clear();
        pets.addAll(HuanghunPetHelper.list(ApplicationLoader.applicationContext));
        return super.onFragmentCreate();
    }
    @Override protected String getActionBarTitle() { return getString(R.string.HuanghunPet); }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        if (position == ROW_TUTORIAL) { showTutorial(); return; }
        if (position == ROW_IMPORT) { openImporter(); return; }
        if (position >= rowCountStart && position < rowCountStart + pets.size()) { showPetActions(pets.get(position - rowCountStart)); }
    }
    private void openImporter() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, REQUEST_IMPORT_PET);
    }
    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMPORT_PET && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            new Thread(() -> {
                try {
                    String id = HuanghunPetHelper.importZip(ApplicationLoader.applicationContext, uri);
                    AndroidUtilities.runOnUIThread(() -> { reloadPets(); showInfo("导入完成", "桌宠资源已保存。请在桌宠列表中点击它并选择“启用”。"); });
                } catch (Exception e) {
                    AndroidUtilities.runOnUIThread(() -> showInfo("导入失败", e.getMessage() == null ? "不是有效的桌宠包" : e.getMessage()));
                }
            }).start();
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }
    private void reloadPets() {
        pets.clear(); pets.addAll(HuanghunPetHelper.list(ApplicationLoader.applicationContext));
        updateRows();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }
    private void showTutorial() {
        showInfo("创建宠物教程", "使用豆包生成资源包后，准备一个符合 V1.1 规范的 ZIP 文件，包含 manifest.json、preview.png、images 或 frames、animations/animations.json 和 dialogs/dialogs.json。\n\n客户端支持 28 套动作、fps、loop、interruptible、weight、trigger_time、整点对白、午夜彩蛋和半身角色过滤。客户端只读取图片、音频和 JSON 等资源，不会运行 ZIP 内的程序或脚本。\n\n生成口令：请生成符合黄昏客户端 huanghun_pet_pack V1.1 规范的纯资源桌宠 ZIP，提供完整 manifest.json、animations/animations.json、dialogs/dialogs.json 和透明 PNG 动画帧，不要包含任何可执行文件、脚本或网络配置。\n\n导入后点击宠物名称，可进行启用、停用、预览、设置和删除。");
    }
    private void showPetActions(HuanghunPetHelper.PetInfo pet) {
        String active = HuanghunPetHelper.activeId(ApplicationLoader.applicationContext);
        String state = pet.id.equals(active) ? "（已启用）" : "";
        new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(pet.name + " " + state)
                .setItems(new CharSequence[]{"启用", "停用", "预览", "设置", "删除"}, (dialog, which) -> {
                    if (which == 0) { HuanghunPetHelper.setActive(ApplicationLoader.applicationContext, pet.id); HuanghunPetOverlay.reloadActive(); reloadPets(); showInfo("已启用", "已将“" + pet.name + "”设为当前桌宠。\n它会在黄昏客户端中持续活动，点击后会给出回复。"); }
                    else if (which == 1) { if (pet.id.equals(HuanghunPetHelper.activeId(ApplicationLoader.applicationContext))) { HuanghunPetHelper.setActive(ApplicationLoader.applicationContext, ""); HuanghunPetOverlay.reloadActive(); reloadPets(); } }
                    else if (which == 2) showPreview(pet);
                    else if (which == 3) showPetSettings();
                    else new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle("删除桌宠").setMessage("确定删除“" + pet.name + "”吗？此操作不可恢复。").setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> { HuanghunPetHelper.delete(ApplicationLoader.applicationContext, pet.id); HuanghunPetOverlay.reloadActive(); reloadPets(); }).show();
                }).setNegativeButton("取消", null).show();
    }
    private void showPetSettings() {
        android.content.SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("huanghun_pets", Context.MODE_PRIVATE);
        String[] items = {"开启对白气泡", "关闭对白气泡", "开启整点报时", "关闭整点报时", "小尺寸", "标准尺寸", "大尺寸"};
        new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle("桌宠设置")
                .setItems(items, (dialog, which) -> {
                    android.content.SharedPreferences.Editor editor = preferences.edit();
                    if (which == 0) editor.putBoolean("show_dialog", true);
                    else if (which == 1) editor.putBoolean("show_dialog", false);
                    else if (which == 2) editor.putBoolean("hourly_chime", true);
                    else if (which == 3) editor.putBoolean("hourly_chime", false);
                    else if (which == 4) editor.putInt("pet_size", 88);
                    else if (which == 5) editor.putInt("pet_size", 116);
                    else if (which == 6) editor.putInt("pet_size", 160);
                    editor.apply();
                    HuanghunPetOverlay.reloadActive();
                }).setNegativeButton("关闭", null).show();
    }
    private void showPreview(HuanghunPetHelper.PetInfo pet) {
        ImageView image = new ImageView(getParentActivity());
        Drawable animation = createIdleAnimation(pet);
        image.setImageDrawable(animation != null ? animation : Drawable.createFromPath(pet.preview().getAbsolutePath()));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(24), dp(12), dp(24), dp(12));
        image.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (animation instanceof AnimationDrawable) ((AnimationDrawable) animation).start();
        showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(pet.name + " · " + pet.version).setView(image, dp(320)).setPositiveButton("关闭", null).create());
    }
    private Drawable createIdleAnimation(HuanghunPetHelper.PetInfo pet) {
        File idle = new File(pet.directory, "images/idle");
        if (!idle.isDirectory()) idle = new File(pet.directory, "frames/idle");
        if (!idle.isDirectory()) idle = new File(pet.directory, "images/idle_breath");
        File[] frames = idle.listFiles((dir, name) -> name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".webp"));
        if (frames == null || frames.length == 0) return null;
        Arrays.sort(frames, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        AnimationDrawable result = new AnimationDrawable();
        for (File frame : frames) {
            Drawable drawable = Drawable.createFromPath(frame.getAbsolutePath());
            if (drawable != null) result.addFrame(drawable, 180);
        }
        result.setOneShot(false);
        return result.getNumberOfFrames() == 0 ? null : result;
    }
    private void showInfo(String title, String message) { if (getParentActivity() != null) showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider).setTitle(title).setMessage(message).setPositiveButton("确定", null).create()); }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }
    private class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (holder.itemView instanceof TextSettingsCell) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == ROW_TUTORIAL) cell.setTextAndIcon("创建宠物教程（含口令生成）", R.drawable.msg_info, true);
                else if (position == ROW_IMPORT) cell.setTextAndIcon("导入宠物压缩包（ZIP）", R.drawable.import_solar, true);
                else if (position == ROW_EMPTY) cell.setTextAndIcon("暂无已导入宠物", R.drawable.msg_emoji_cat_solar, false);
                else if (position >= rowCountStart && position - rowCountStart < pets.size()) {
                    HuanghunPetHelper.PetInfo p = pets.get(position - rowCountStart);
                    String value = p.version + (p.id.equals(HuanghunPetHelper.activeId(ApplicationLoader.applicationContext)) ? " · 已启用" : "");
                    cell.setTextAndValue(p.name, value, true);
                }
            }
        }
        @Override public int getItemViewType(int position) { return TYPE_SETTINGS; }
    }
}
