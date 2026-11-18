package com.Huanghun.protocol.maker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LoginActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 「协议制作」入口的全部弹窗与流程编排。
 *
 * <p>设置入口可导出当前账号；登录页入口仅提供转换和外部账号登录。</p>
 */
public final class ProtocolBuildUi {

    public static final int REQUEST_PICK_CONVERT = 5004;

    private ProtocolBuildUi() {
    }

    // ------------------------------------------------------------------ 主入口

    public static void showMain(LoginActivity fragment) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        String[] options = {"1  协议转换（session / tdata / json）", "2  外部其他账号提取"};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("协议制作")
                .setMessage("生成的协议会保存到 黄昏/协议制作/；若系统不允许写入公共目录，会退到应用私有目录。\n\n"
                        + "重要：协议文件含有可直接登录账号的授权密钥，等同于账号密码。仅导出本人账号，勿上传或转发。")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        showConvertPicker(fragment);
                    } else {
                        showExternalAccounts(fragment);
                    }
                })
                .create();
        styleDialog(dialog);
        dialog.show();
    }

    /** 黄昏服务设置页：本机提取严格限定为该页面所属的当前账号。 */
    public static void showFromSettings(BaseFragment fragment) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) return;
        String[] options = {"提取当前账号协议", "协议转换（session / tdata / json）"};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("协议制作")
                .setMessage("本机提取只会导出当前选中的账号；协议文件包含可直接登录账号的授权密钥，请妥善保管。")
                .setItems(options, (d, which) -> {
                    if (which == 0) confirmAndExportCurrentAccount(fragment);
                    else showConvertPicker(fragment);
                })
                .setNegativeButton("取消", null)
                .create();
        styleDialog(dialog);
        dialog.show();
    }

    private static void confirmAndExportCurrentAccount(BaseFragment fragment) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        int account = fragment.getCurrentAccount();
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT
                || !UserConfig.getInstance(account).isClientActivated()
                || UserConfig.getInstance(account).getCurrentUser() == null) {
            showError(activity, "当前选中的账号未登录，无法提取协议。请先切换到已登录账号。");
            return;
        }
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        String phone = TextUtils.isEmpty(user.phone) ? "未知号码" : user.phone;
        confirmSensitiveAction(activity,
                "即将导出当前账号（" + phone + "，账号" + (account + 1) + "）的登录授权密钥。该文件可直接登录账号，请确认这是你本人的账号并妥善保存。",
                () -> exportLocalAccount(fragment, account));
    }

    /** Android 11+ 写公共目录需要「所有文件访问」，先引导用户去打开。 */
    private static void requestStorageAccess(Activity activity) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("需要存储权限")
                .setMessage("协议压缩包要保存到 黄昏/协议制作/ 目录，需要「所有文件访问」权限。\n\n"
                        + "点「去设置」→ 找到本应用 → 打开「允许访问所有文件」，回来再试一次。")
                .setPositiveButton("去设置", (d, which) -> {
                    try {
                        Intent intent = new Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    } catch (Throwable error) {
                        try {
                            activity.startActivity(new Intent(
                                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        } catch (Throwable ignored) {
                            showError(activity, "打不开系统设置，请手动到应用权限里打开「所有文件访问」");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        styleDialog(dialog);
        dialog.show();
    }

    // -------------------------------------------------------------- 功能一：本号

    private static void exportLocalAccount(BaseFragment fragment, int accountNum) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        Context context = activity.getApplicationContext();
        java.lang.ref.WeakReference<Activity> activityRef = new java.lang.ref.WeakReference<>(activity);
        Toast.makeText(activity, "正在读取账号并生成协议…", Toast.LENGTH_SHORT).show();
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            ProtocolExporter.Result result = null;
            String failure = null;
            try {
                result = ProtocolExporter.export(context, accountNum, ProtocolExporter.FORMAT_ALL);
            } catch (Throwable error) {
                failure = ProtocolConverter.describe(error);
                FileLog.e(error);
            }
            final ProtocolExporter.Result exported = result;
            final String reason = failure;
            handler.post(() -> {
                Activity owner = activityRef.get();
                if (owner == null || owner.isFinishing() || owner.isDestroyed()) {
                    return;
                }
                if (exported != null) {
                    showResult(owner, java.util.Collections.singletonList(exported.zipFile),
                            java.util.Collections.emptyList(), exported.publicDirectory);
                } else {
                    showError(owner, "导出失败：" + reason);
                }
            });
        }, "protocol-export-local").start();
    }

    // -------------------------------------------------------------- 功能二：转换

    private static void showConvertPicker(BaseFragment fragment) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("协议转换")
                .setMessage("选择压缩包、.session 或 .json 文件；tdata 请用文件夹选择器选择整个目录。\n"
                        + "支持 tdata→session、session→tdata、json→session 全部方向，转换后统一打包。")
                .setPositiveButton("选择文件", (d, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/zip", "application/x-zip-compressed",
                            "application/octet-stream", "application/json", "*/*"
                    });
                    try {
                        fragment.startActivityForResult(intent, REQUEST_PICK_CONVERT);
                    } catch (Throwable error) {
                        showError(activity, "打不开文件选择器：" + ProtocolConverter.describe(error));
                    }
                })
                .setNeutralButton("选择 tdata 文件夹", (d, which) -> {
                    try {
                        fragment.startActivityForResult(
                                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_PICK_CONVERT + 1);
                    } catch (Throwable error) {
                        showError(activity, "打不开文件夹选择器：" + ProtocolConverter.describe(error));
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        styleDialog(dialog);
        dialog.show();
    }

    /** 由发起文件选择的 Fragment 的 onActivityResultFragment 回调。 */
    public static void onConvertFilePicked(BaseFragment fragment, Uri uri) {
        Activity activity = fragment.getParentActivity();
        if (activity == null || uri == null) {
            return;
        }
        confirmSensitiveAction(activity,
                "转换会重新生成包含授权密钥的登录协议文件。该文件可直接登录对应账号，请确保你有权处理此账号。",
                () -> convertSelectedFile(activity, uri));
    }

    private static void convertSelectedFile(Activity activity, Uri uri) {
        Context context = activity.getApplicationContext();
        java.lang.ref.WeakReference<Activity> activityRef = new java.lang.ref.WeakReference<>(activity);
        Toast.makeText(activity, "正在读取协议…", Toast.LENGTH_SHORT).show();
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            File temp = null;
            ProtocolConverter.Result result = null;
            String failure = null;
            try {
                if (DocumentsContract.isTreeUri(uri)) {
                    temp = ProtocolPaths.createTemporaryDirectory(context, "picked_tdata_");
                    String documentId = DocumentsContract.getTreeDocumentId(uri);
                    copyDocumentTree(context, uri, documentId, temp, 0, new TreeCopyState());
                } else {
                    temp = ProtocolPaths.createTemporaryFile(context, "picked_file_", selectedExtension(context, uri));
                    copyUriToFile(context, uri, temp);
                }
                result = ProtocolConverter.convert(context, temp, ProtocolExporter.FORMAT_ALL);
            } catch (Throwable error) {
                failure = ProtocolConverter.describe(error);
                FileLog.e(error);
            } finally {
                if (temp != null) {
                    ProtocolPaths.deleteRecursive(temp);
                }
            }
            final ProtocolConverter.Result converted = result;
            final String reason = failure;
            handler.post(() -> {
                Activity owner = activityRef.get();
                if (owner == null || owner.isFinishing() || owner.isDestroyed()) {
                    return;
                }
                if (converted == null) {
                    showError(owner, "转换失败：" + reason);
                    return;
                }
                if (converted.zipFiles.isEmpty()) {
                    String detail = converted.failures.isEmpty()
                            ? "输入里没有识别到可用的协议"
                            : TextUtils.join("\n", converted.failures);
                    showError(owner, "没有转换出任何协议：\n" + detail);
                    return;
                }
                showResult(owner, converted.zipFiles, converted.failures, converted.publicDirectory);
            });
        }, "protocol-convert").start();
    }

    private static void copyUriToFile(Context context, Uri uri, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) {
                throw new Exception("读不到这个文件");
            }
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 256L * 1024L * 1024L) {
                    throw new Exception("文件太大（超过 256MB）");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static final class TreeCopyState {
        int entries;
        long bytes;
    }

    private static void copyDocumentTree(Context context, Uri treeUri, String parentId,
                                         File targetDirectory, int depth, TreeCopyState state) throws Exception {
        if (depth > 16) {
            throw new java.io.IOException("tdata 目录层级过深");
        }
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                throw new java.io.IOException("无法读取所选目录");
            }
            while (cursor.moveToNext()) {
                if (++state.entries > 4096) {
                    throw new java.io.IOException("tdata 目录文件数量超过 4096 项");
                }
                String childId = cursor.getString(0);
                String name = safeDocumentName(cursor.getString(1));
                String mimeType = cursor.getString(2);
                File child = new File(targetDirectory, name);
                String root = targetDirectory.getCanonicalPath() + File.separator;
                if (!child.getCanonicalPath().startsWith(root)) {
                    throw new java.io.IOException("文件名越界：" + name);
                }
                if (child.exists()) {
                    throw new java.io.IOException("目录中存在重复文件名：" + name);
                }
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    if (!child.mkdir()) {
                        throw new java.io.IOException("无法创建目录：" + name);
                    }
                    copyDocumentTree(context, treeUri, childId, child, depth + 1, state);
                } else {
                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                    try (InputStream input = context.getContentResolver().openInputStream(childUri);
                         FileOutputStream output = new FileOutputStream(child)) {
                        if (input == null) {
                            throw new java.io.IOException("读不到文件：" + name);
                        }
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            state.bytes += read;
                            if (state.bytes > 256L * 1024L * 1024L) {
                                throw new java.io.IOException("tdata 目录超过 256 MiB");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
            }
        }
    }

    private static String safeDocumentName(String name) throws java.io.IOException {
        if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
                || name.length() > 255) {
            throw new java.io.IOException("目录内含有不安全的文件名");
        }
        return name;
    }

    private static String selectedExtension(Context context, Uri uri) {
        String displayName = null;
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(0);
            }
        } catch (Throwable ignore) {
        }
        if (displayName == null) {
            return ".bin";
        }
        String lower = displayName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".session")) {
            return ".session";
        }
        if (lower.endsWith(".json")) {
            return ".json";
        }
        if (lower.endsWith(".zip")) {
            return ".zip";
        }
        return ".bin";
    }

    // -------------------------------------------------------------- 功能三：外部

    private static void showExternalAccounts(LoginActivity fragment) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        String message = "登录成功后会自动生成协议包，保存位置与设置页的本机账号提取相同。\n\n"
                + "该协议包含有可直接登录账号的授权密钥，仅可用于本人账号；不要上传或转发。\n\n"
                + "如果账号被轮询，或者收不到验证码，请用扫码登录。\n"
                + "二维码有效期 30 秒，请尽快扫描。";
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("外部账号提取")
                .setMessage(message)
                .setPositiveButton("确认本人账号 · 号码登录", (d, which) -> {
                    ExternalAccountLogin.markPending(fragment.getCurrentAccount());
                    fragment.startProtocolExternalLogin(false);
                })
                .setNeutralButton("确认本人账号 · 扫码登录", (d, which) -> {
                    ExternalAccountLogin.markPending(fragment.getCurrentAccount());
                    fragment.startProtocolExternalLogin(true);
                })
                .setNegativeButton("取消", null)
                .create();
        styleDialog(dialog);
        dialog.show();
    }

    // -------------------------------------------------------------- 结果与错误

    public static void showResult(Context context, List<File> files, List<String> failures,
                                  boolean publicDirectory) {
        if (context instanceof Activity && (((Activity) context).isFinishing()
                || ((Activity) context).isDestroyed())) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("已生成 ").append(files.size()).append(" 个协议压缩包：\n");
        for (File file : files) {
            message.append("· ").append(file.getName()).append("（")
                    .append(file.length() / 1024).append(" KB）\n");
        }
        message.append("\n保存位置：\n");
        if (!files.isEmpty()) {
            File parent = files.get(0).getParentFile();
            message.append(parent == null ? "未知" : parent.getAbsolutePath()).append("\n");
        }
        if (!publicDirectory) {
            message.append("\n注意：公共存储没有写入权限，文件存在应用私有目录，")
                    .append("可以从文件管理器的 Android/data 下找到。\n");
        }
        if (failures != null && !failures.isEmpty()) {
            message.append("\n未成功 ").append(failures.size()).append(" 个：\n");
            int limit = Math.min(5, failures.size());
            for (int i = 0; i < limit; i++) {
                message.append("· ").append(failures.get(i)).append("\n");
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("协议制作完成")
                .setMessage(message.toString().trim())
                .setPositiveButton("好", null)
                .create();
        styleDialog(dialog);
        dialog.show();
    }

    public static void showError(Context context, String text) {
        if (context instanceof Activity && (((Activity) context).isFinishing()
                || ((Activity) context).isDestroyed())) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("协议制作")
                .setMessage(text)
                .setPositiveButton("好", null)
                .create();
        styleDialog(dialog);
        dialog.show();
    }

    private static void confirmSensitiveAction(Activity activity, String message, Runnable action) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("确认处理登录凭据")
                .setMessage(message)
                .setPositiveButton("继续", (d, which) -> action.run())
                .setNegativeButton("取消", null)
                .create();
        styleDialog(dialog);
        dialog.show();
    }

    // ------------------------------------------------------------------ 样式

    private static void styleDialog(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() == null) {
                return;
            }
            GradientDrawable frame = new GradientDrawable();
            frame.setColor(0xF2FFFFFF);
            frame.setCornerRadius(AndroidUtilities.dp(20));
            frame.setStroke(AndroidUtilities.dp(1), 0x55FFFFFF);
            dialog.getWindow().setBackgroundDrawable(frame);
            dialog.getWindow().getDecorView().setBackgroundColor(0x00000000);
        });
    }

    static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6),
                AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }
}
