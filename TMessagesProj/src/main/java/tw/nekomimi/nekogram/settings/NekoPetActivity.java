package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
    @Override public void onResume() {
        super.onResume();
        // The file picker returns asynchronously and the fragment can be resumed
        // without being recreated. Always re-read installed packages here so the
        // empty-state row cannot remain after an import.
        if (listAdapter != null) reloadPets();
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
                    AndroidUtilities.runOnUIThread(() -> {
                        reloadPets();
                        String message = containsPet(id)
                                ? "桌宠资源已保存。请在桌宠列表中点击它并选择“启用”。"
                                : "资源包已处理，但列表尚未读到该桌宠。请重新打开桌宠页面。";
                        showInfo(containsPet(id) ? "导入完成" : "列表刷新失败", message);
                    });
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
        if (listAdapter != null) {
            // The item count changes when the empty-state row is replaced by
            // imported pets. Re-attaching the adapter is intentional here:
            // notifyDataSetChanged() alone can leave the old empty row cached
            // while the file-picker fragment is being dismissed.
            if (listView != null) {
                listView.setAdapter(null);
                listView.setAdapter(listAdapter);
            } else {
                listAdapter.notifyDataSetChanged();
            }
        }
    }
    private boolean containsPet(String id) {
        for (HuanghunPetHelper.PetInfo pet : pets) {
            if (pet.id.equals(id)) return true;
        }
        return false;
    }
    private void showTutorial() {
        if (getParentActivity() == null) return;
        LinearLayout content = new LinearLayout(getParentActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(18));

        addTutorialText(content, "一、复制完整生成口令", true);
        addTutorialText(content, "点击下面的“复制生成口令”按钮，完整口令会写入手机剪贴板，并提示复制成功。然后打开豆包，将口令粘贴到输入框。", false);
        Button copyButton = new Button(getParentActivity());
        copyButton.setText("复制生成口令");
        copyButton.setAllCaps(false);
        copyButton.setOnClickListener(v -> copyPrompt(copyButton));
        content.addView(copyButton, new LinearLayout.LayoutParams(-1, dp(48)));

        addTutorialText(content, "二、在豆包选择工作任务 Turbo", true);
        addTutorialText(content, "打开豆包后点击左下角的快速按钮，在列表中选择“工作任务 Turbo”。下图中橙色圈出的位置就是正确选项。", false);
        addTutorialImage(content, R.drawable.huanghun_pet_tutorial_step1);

        addTutorialText(content, "三、确认已选择 Turbo 后查看示例图", true);
        addTutorialText(content, "选择完成后，使用“查看示例图”按钮查看示意图，再返回输入框。选择一张清晰、主体完整的角色图片，并与上面的生成口令一起发送给豆包。半身或头像也可以生成，但客户端会自动禁用行走、跳跃、转圈、蹲下和跺脚等全身动作。", false);
        addTutorialImage(content, R.drawable.huanghun_pet_tutorial_step2);

        addTutorialText(content, "四、等待并下载生成的压缩包", true);
        addTutorialText(content, "等待豆包完成“黄昏客户端桌宠素材包”的生成。生成过程中会显示下载提示，请根据压缩包大小和网络环境耐心等待，不要频繁重复请求。生成完成后，先点击压缩包卡片或下载按钮，按页面提示点击“立即下载”，等待文件保存完成。", false);
        addTutorialImage(content, R.drawable.huanghun_pet_tutorial_step4_wait);
        addTutorialText(content, "如果点击后出现“保存压缩包文件”窗口，请点击“立即下载”。看到下载进度或“正在下载”提示，表示任务已经开始。", false);
        addTutorialImage(content, R.drawable.huanghun_pet_tutorial_step4_download);
        addTutorialText(content, "如果当前页面无法直接下载，也可以长按压缩包消息，选择“分享”，再复制链接到浏览器打开，在浏览器中完成下载。下载完成后回到黄昏客户端导入 ZIP 文件。", false);
        addTutorialImage(content, R.drawable.huanghun_pet_tutorial_step4_share);

        addTutorialText(content, "五、回到黄昏客户端导入并启用", true);
        addTutorialText(content, "点击教程页面右下角的“关闭”，再点击“导入宠物压缩包（ZIP）”，选择下载好的 ZIP。导入成功后点击宠物名称，可以选择启用、停用、预览、设置或删除。客户端只读取图片、音频、JSON 和说明文档，不会执行压缩包内的程序或脚本。", false);

        ScrollView scrollView = new ScrollView(getParentActivity());
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle("创建宠物教程")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .create();
        showDialog(dialog);
    }

    private void addTutorialText(LinearLayout parent, String text, boolean heading) {
        TextView view = new TextView(getParentActivity());
        view.setText(text);
        view.setTextSize(heading ? 17 : 15);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setTypeface(null, heading ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        view.setPadding(0, dp(heading ? 14 : 6), 0, dp(heading ? 5 : 10));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addTutorialImage(LinearLayout parent, int resource) {
        ImageView image = new ImageView(getParentActivity());
        image.setImageResource(resource);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setContentDescription("创建宠物教程示意图");
        parent.addView(image, new LinearLayout.LayoutParams(-1, -2));
    }

    private void copyPrompt(Button button) {
        try {
            java.io.InputStream input = getParentActivity().getResources().openRawResource(R.raw.huanghun_pet_prompt);
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            input.close();
            ClipboardManager clipboard = (ClipboardManager) getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("黄昏桌宠生成口令", output.toString("UTF-8")));
            button.setText("已复制，可粘贴到豆包");
            showInfo("复制成功", "完整生成口令已复制到剪贴板，请打开豆包粘贴使用。");
        } catch (Exception e) {
            showInfo("复制失败", "无法读取生成口令，请稍后重试。");
        }
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
                else if (position == ROW_EMPTY && pets.isEmpty()) cell.setTextAndIcon("暂无已导入宠物", R.drawable.msg_emoji_cat_solar, false);
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
