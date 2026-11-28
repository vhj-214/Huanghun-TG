package com.Huanghun.protocol.maker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
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
 * 「协议制作」流程编排。
 *
 * <p>界面由 {@link ProtocolWebUi} 使用 HTML 渲染；本类只保留业务逻辑：
 * 读取本机授权、选择输入、调用转换器、导出压缩包与结果提示。</p>
 */
public final class ProtocolBuildUi {

    public static final int REQUEST_PICK_CONVERT = 5004;
    public static final int REQUEST_PICK_CONVERT_TDATA = REQUEST_PICK_CONVERT + 1;
    public static final int REQUEST_PICK_CONVERT_JSON = REQUEST_PICK_CONVERT + 2;
    public static final int REQUEST_PICK_PASSKEY_SESSION = 5010;
    public static final int REQUEST_PICK_PASSKEY_JSON = 5011;
    public static final int REQUEST_PICK_PASSKEY_TDATA = 5012;

    private ProtocolBuildUi() {
    }

    // ------------------------------------------------------------------ 入口

    public static void showMain(LoginActivity fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        ProtocolWebUi.show(fragment, false);
    }

    /** 黄昏服务设置页：本机提取严格限定为该页面所属的当前账号。 */
    public static void showFromSettings(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        ProtocolWebUi.show(fragment, true);
    }

    // ------------------------------------------------- HTML 界面回调的三个动作

    /** HTML「本机账号提取」。 */
    public static void exportCurrentAccount(BaseFragment fragment) {
        confirmAndExportCurrentAccount(fragment);
    }

    /** HTML「协议转换」，kind 取 session / json / tdata。 */
    public static void convertFromUi(BaseFragment fragment, String kind) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30 && !ProtocolPaths.hasAllFilesAccess()) {
            requestStorageAccess(activity);
            return;
        }
        boolean jsonOnly = "json".equalsIgnoreCase(kind);
        boolean tdataArchive = "tdata".equalsIgnoreCase(kind);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, jsonOnly
                ? new String[]{"application/json", "text/json", "application/zip",
                "application/x-zip-compressed", "application/octet-stream"}
                : tdataArchive
                ? new String[]{"application/zip", "application/x-zip-compressed",
                "application/octet-stream"}
                : new String[]{"application/zip", "application/x-zip-compressed",
                "application/octet-stream", "application/x-sqlite3"});
        try {
            fragment.startActivityForResult(intent,
                    jsonOnly ? REQUEST_PICK_CONVERT_JSON
                            : tdataArchive ? REQUEST_PICK_CONVERT_TDATA : REQUEST_PICK_CONVERT);
        } catch (Throwable error) {
            showError(activity, "打不开文件选择器：" + ProtocolConverter.describe(error));
        }
    }

    /** HTML「外部账号提取」，只走协议专用链路，不保存正式账号。 */
    public static void externalExtractFromUi(BaseFragment fragment, boolean qrLogin) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30 && !ProtocolPaths.hasAllFilesAccess()) {
            requestStorageAccess(activity);
            return;
        }
        if (ExternalAccountLogin.startProtocolExtraction(fragment, qrLogin)) {
            ProtocolWebUi.dismissCurrent();
        }
    }

    /** HTML「外部【通行密钥】 Passkey 制作」格式选择。 */
    public static void externalPasskeyFromUi(BaseFragment fragment, String kind) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30 && !ProtocolPaths.hasAllFilesAccess()) {
            requestStorageAccess(activity);
            return;
        }
        boolean json = "json".equalsIgnoreCase(kind);
        boolean tdataArchive = "tdata".equalsIgnoreCase(kind);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, json
                ? new String[]{"application/json", "text/json", "application/zip",
                "application/x-zip-compressed", "application/octet-stream"}
                : tdataArchive
                ? new String[]{"application/zip", "application/x-zip-compressed",
                "application/octet-stream"}
                : new String[]{"application/x-sqlite3", "application/octet-stream",
                "application/zip", "application/x-zip-compressed"});
        try {
            fragment.startActivityForResult(intent, json
                    ? REQUEST_PICK_PASSKEY_JSON
                    : tdataArchive ? REQUEST_PICK_PASSKEY_TDATA : REQUEST_PICK_PASSKEY_SESSION);
        } catch (Throwable error) {
            showError(activity, "打不开文件选择器：" + ProtocolConverter.describe(error));
        }
    }

    /** Creates a Passkey from the service page's currently selected local account. */
    public static void localPasskeyFromUi(BaseFragment fragment) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (!(fragment instanceof com.Huanghun.HuanghunServiceActivity)) {
            showError(activity, "本机密钥制作仅能从黄昏服务页面使用。");
            return;
        }
        int account = fragment.getCurrentAccount();
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT
                || !UserConfig.getInstance(account).isClientActivated()
                || UserConfig.getInstance(account).getCurrentUser() == null) {
            showError(activity, "当前选中的账号未登录，无法制作本机密钥。请先切换到已登录账号。");
            return;
        }
        if (Build.VERSION.SDK_INT >= 30 && !ProtocolPaths.hasAllFilesAccess()) {
            requestStorageAccess(activity);
            return;
        }
        ProtocolWebUi.showBusy("正在制作本机密钥", "正在读取本机当前账号授权信息并向 Telegram 注册 Passkey…");
        Context context = activity.getApplicationContext();
        java.lang.ref.WeakReference<Activity> activityRef = new java.lang.ref.WeakReference<>(activity);
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            ExternalPasskeyMaker.Result result = null;
            String failure = null;
            try {
                result = ExternalPasskeyMaker.createFromLocalAccount(context, account, "");
            } catch (Throwable error) {
                failure = ProtocolConverter.describe(error);
                FileLog.e(error);
            }
            final ExternalPasskeyMaker.Result created = result;
            final String reason = failure;
            handler.post(() -> {
                Activity owner = activityRef.get();
                if (owner == null || owner.isFinishing() || owner.isDestroyed()) {
                    return;
                }
                if (created == null) {
                    showError(owner, "本机密钥制作失败：" + (reason == null ? "未知错误" : reason));
                } else if (created.zipFiles.isEmpty()) {
                    String detail = created.failures.isEmpty()
                            ? "Telegram 未返回可用的 Passkey。"
                            : TextUtils.join("\n", created.failures);
                    showError(owner, "本机密钥制作失败：\n" + detail);
                } else {
                    showResult(owner, created.zipFiles, created.failures, created.publicDirectory);
                }
            });
        }, "protocol-passkey-local").start();
    }

    // -------------------------------------------------------------- 本机账号

    private static void confirmAndExportCurrentAccount(BaseFragment fragment) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
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

    private static void exportLocalAccount(BaseFragment fragment, int accountNum) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30 && !ProtocolPaths.hasAllFilesAccess()) {
            requestStorageAccess(activity);
            return;
        }
        ProtocolWebUi.showBusy("正在生成协议", "正在读取本机授权密钥并写入 session、JSON 与 tdata…");
        Context context = activity.getApplicationContext();
        java.lang.ref.WeakReference<Activity> activityRef = new java.lang.ref.WeakReference<>(activity);
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

    // -------------------------------------------------------------- 协议转换

    /** 由发起文件选择的 Fragment 的 onActivityResultFragment 回调。 */
    public static void onConvertFilePicked(BaseFragment fragment, Uri uri, int requestCode) {
        Activity activity = fragment.getParentActivity();
        if (activity == null || uri == null) {
            return;
        }
        String inputKind = requestCode == REQUEST_PICK_CONVERT_TDATA ? "tdata"
                : requestCode == REQUEST_PICK_CONVERT_JSON ? "json" : "session";
        showOutputFormatDialog(activity, uri, inputKind);
    }

    /** Handles a selected external Passkey source from either protocol-builder entry point. */
    public static void onExternalPasskeyFilePicked(BaseFragment fragment, Uri uri, int requestCode) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null || uri == null) {
            return;
        }
        String kind = requestCode == REQUEST_PICK_PASSKEY_TDATA ? "tdata"
                : requestCode == REQUEST_PICK_PASSKEY_JSON ? "json" : "session";
        String label = "tdata".equals(kind) ? "tdata 压缩包"
                : "json".equals(kind) ? "JSON 文件或 ZIP 压缩包" : "session 文件或协议 ZIP 压缩包";
        String warning = "注意：Telegram 可能要求该 session/tdata 授权已满 24 小时；未满一天时可能无法创建，请等待满一天后重试。注册可能成功或失败，失败原因会显示在结果中。";
        SpannableStringBuilder message = new SpannableStringBuilder();
        message.append("即将使用所选 ").append(label)
                .append(" 在 Telegram 账号上注册一个新的 Passkey，并导出包含私钥的 .Passkey 凭据压缩包。\n\n")
                .append("该凭据可用于登录账号，生成文件会保存在 /storage/emulated/0/黄昏/协议制作/外部Passkey制作/。请只处理本人账号并妥善保管。\n\n");
        int warningStart = message.length();
        message.append(warning);
        message.setSpan(new ForegroundColorSpan(0xFFD32F2F), warningStart, message.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        showTwoFactorPasswordPrompt(activity, uri, kind, message);
    }

    private static void showTwoFactorPasswordPrompt(Activity activity, Uri uri, String kind,
                                                    CharSequence confirmationMessage) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        int sidePadding = AndroidUtilities.dp(22);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(sidePadding, AndroidUtilities.dp(8), sidePadding, AndroidUtilities.dp(12));

        TextView description = new TextView(activity);
        description.setText("如果当前 Telegram 账号已设置两步验证密码，可在下方填写。此密码会写入生成的 Passkey 凭据，之后登录时可自动使用。");
        description.setTextSize(14);
        description.setTextColor(0xFF667085);
        description.setLineSpacing(AndroidUtilities.dp(2), 1.08f);
        content.addView(description, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout field = new LinearLayout(activity);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(android.view.Gravity.CENTER_VERTICAL);
        field.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(10),
                AndroidUtilities.dp(4));
        GradientDrawable fieldBackground = new GradientDrawable();
        fieldBackground.setColor(0xFFF7F8FC);
        fieldBackground.setCornerRadius(AndroidUtilities.dp(14));
        fieldBackground.setStroke(AndroidUtilities.dp(1), 0xFFE0E4EC);
        field.setBackground(fieldBackground);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = AndroidUtilities.dp(18);
        content.addView(field, fieldParams);

        TextView prefix = new TextView(activity);
        prefix.setText("2FA");
        prefix.setTextSize(11);
        prefix.setTextColor(0xFF5C67B7);
        prefix.setGravity(android.view.Gravity.CENTER);
        prefix.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(6), AndroidUtilities.dp(9),
                AndroidUtilities.dp(6));
        GradientDrawable prefixBackground = new GradientDrawable();
        prefixBackground.setColor(0xFFE9EBFF);
        prefixBackground.setCornerRadius(AndroidUtilities.dp(9));
        prefix.setBackground(prefixBackground);
        field.addView(prefix, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("请输入密码");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextSize(16);
        input.setTextColor(0xFF202536);
        input.setHintTextColor(0xFF98A0AF);
        input.setBackground(null);
        input.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(12));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        field.addView(input, inputParams);

        TextView visibilityToggle = new TextView(activity);
        visibilityToggle.setText("显示");
        visibilityToggle.setTextSize(13);
        visibilityToggle.setTextColor(0xFF5967C2);
        visibilityToggle.setGravity(android.view.Gravity.CENTER);
        visibilityToggle.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(10),
                AndroidUtilities.dp(2), AndroidUtilities.dp(10));
        field.addView(visibilityToggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        visibilityToggle.setOnClickListener(v -> {
            boolean showPassword = "隐藏".contentEquals(visibilityToggle.getText());
            int selection = input.getSelectionStart();
            input.setInputType(InputType.TYPE_CLASS_TEXT | (showPassword
                    ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
            if (selection >= 0 && selection <= input.length()) {
                input.setSelection(selection);
            }
            visibilityToggle.setText(showPassword ? "显示" : "隐藏");
        });

        TextView optionalHint = new TextView(activity);
        optionalHint.setText("可留空，非强制要求。留空时可直接创建 Passkey。");
        optionalHint.setTextSize(12);
        optionalHint.setTextColor(0xFF8A92A2);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = AndroidUtilities.dp(10);
        content.addView(optionalHint, hintParams);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Passkey 登录密码（可选）")
                .setView(content)
                .setPositiveButton("直接创建", (d, which) -> { })
                .setNegativeButton("取消", null)
                .create();
        styleDialog(dialog);
        dialog.show();
        View positiveButton = dialog.getButton(-1);
        if (positiveButton != null) {
            positiveButton.setOnClickListener(v -> {
                String twoFactorPassword = input.getText() == null ? "" : input.getText().toString();
                dialog.dismiss();
                confirmSensitiveAction(activity, confirmationMessage,
                        () -> createExternalPasskey(activity, uri, kind, twoFactorPassword, ""));
            });
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                View button = dialog.getButton(-1);
                if (button instanceof TextView) {
                    ((TextView) button).setText(s == null || s.length() == 0 ? "直接创建" : "创建");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private static void showOutputFormatDialog(Activity activity, Uri uri, String inputKind) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        String[] labels;
        int[] formats;
        String sourceLabel;
        if ("json".equals(inputKind)) {
            sourceLabel = "JSON";
            labels = new String[]{"session (.session)", "tdata"};
            formats = new int[]{ProtocolExporter.FORMAT_TELETHON, ProtocolExporter.FORMAT_TDATA};
        } else if ("tdata".equals(inputKind)) {
            sourceLabel = "tdata";
            labels = new String[]{"session (.session)", "JSON (.json)"};
            formats = new int[]{ProtocolExporter.FORMAT_TELETHON, ProtocolExporter.FORMAT_JSON};
        } else {
            sourceLabel = "session (.session)";
            labels = new String[]{"JSON (.json)", "tdata"};
            formats = new int[]{ProtocolExporter.FORMAT_JSON, ProtocolExporter.FORMAT_TDATA};
        }

        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = AndroidUtilities.dp(24);
        choices.setPadding(horizontalPadding, AndroidUtilities.dp(4), horizontalPadding,
                AndroidUtilities.dp(8));
        CheckBox[] checkBoxes = new CheckBox[labels.length];
        for (int i = 0; i < labels.length; i++) {
            CheckBox checkBox = new CheckBox(activity);
            checkBox.setText(labels[i]);
            checkBox.setMinHeight(AndroidUtilities.dp(48));
            choices.addView(checkBox, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            checkBoxes[i] = checkBox;
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("选择转换格式")
                .setMessage("已选择输入格式：" + sourceLabel + "\n请勾选要生成的目标格式，可选择一种或两种。")
                .setView(choices)
                .setPositiveButton("开始转换", (d, which) -> { })
                .setNegativeButton("取消", null)
                .create();
        styleDialog(dialog);
        dialog.show();
        View positiveButton = dialog.getButton(-1);
        if (positiveButton != null) {
            positiveButton.setOnClickListener(v -> {
                int selectedFormats = 0;
                for (int i = 0; i < checkBoxes.length; i++) {
                    if (checkBoxes[i].isChecked()) {
                        selectedFormats |= formats[i];
                    }
                }
                if (selectedFormats == 0) {
                    Toast.makeText(activity, "请至少勾选一种要转换的目标格式。", Toast.LENGTH_SHORT).show();
                    return;
                }
                final int outputFormats = selectedFormats;
                dialog.dismiss();
                confirmSensitiveAction(activity,
                        "转换会重新生成包含授权密钥的登录协议文件。该文件可直接登录对应账号，请确保你有权处理此账号。",
                        () -> convertSelectedFile(activity, uri, outputFormats, inputKind, ""));
            });
        }
    }

    private static void convertSelectedFile(Activity activity, Uri uri, int formats,
                                            String inputKind, String localPasscode) {
        if (Build.VERSION.SDK_INT >= 30 && !ProtocolPaths.hasAllFilesAccess()) {
            requestStorageAccess(activity);
            return;
        }
        ProtocolWebUi.showBusy("正在转换协议", "正在解析所选文件并重新生成协议压缩包…");
        Context context = activity.getApplicationContext();
        java.lang.ref.WeakReference<Activity> activityRef = new java.lang.ref.WeakReference<>(activity);
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            File temp = null;
            ProtocolConverter.Result result = null;
            String failure = null;
            boolean passwordRequired = false;
            try {
                if (DocumentsContract.isTreeUri(uri)) {
                    temp = ProtocolPaths.createTemporaryDirectory(context, "picked_tdata_");
                    String documentId = DocumentsContract.getTreeDocumentId(uri);
                    copyDocumentTree(context, uri, documentId, temp, 0, new TreeCopyState());
                } else {
                    temp = ProtocolPaths.createTemporaryFile(context, "picked_file_", selectedExtension(context, uri));
                    copyUriToFile(context, uri, temp);
                }
                if ("tdata".equals(inputKind) && (!temp.isFile() || !ZipUtil.isZip(temp))) {
                    throw new java.io.IOException("tdata 输入必须是包含完整 tdata 目录的 ZIP 压缩包");
                }
                result = ProtocolConverter.convert(context, temp, formats, inputKind, localPasscode);
                passwordRequired = (localPasscode == null || localPasscode.isEmpty())
                        && hasTdataPasscodeFailure(result.failures);
                if (passwordRequired) {
                    for (File file : result.zipFiles) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                }
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
            final boolean requestPassword = passwordRequired;
            handler.post(() -> {
                Activity owner = activityRef.get();
                if (owner == null || owner.isFinishing() || owner.isDestroyed()) {
                    return;
                }
                if (converted == null) {
                    showError(owner, "转换失败：" + reason);
                    return;
                }
                if (requestPassword) {
                    requestTdataPasscode(owner,
                            () -> showError(owner, "已取消 tdata 解密。"),
                            passcode -> convertSelectedFile(owner, uri, formats, inputKind, passcode),
                            "检测到压缩包或 tdata 目录中的 Telegram Desktop 本地密码保护。请输入本地密码；这不是 Telegram 两步验证密码。");
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

    private static void createExternalPasskey(Activity activity, Uri uri, String kind,
                                              String twoFactorPassword, String localPasscode) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30 && !ProtocolPaths.hasAllFilesAccess()) {
            requestStorageAccess(activity);
            return;
        }
        ProtocolWebUi.showBusy("正在制作外部【通行密钥】 Passkey", "正在读取会话并验证 Telegram 授权…");
        Context context = activity.getApplicationContext();
        java.lang.ref.WeakReference<Activity> activityRef = new java.lang.ref.WeakReference<>(activity);
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            File temp = null;
            ExternalPasskeyMaker.Result result = null;
            String failure = null;
            boolean passwordRequired = false;
            try {
                if (DocumentsContract.isTreeUri(uri)) {
                    temp = ProtocolPaths.createTemporaryDirectory(context, "picked_passkey_tdata_");
                    String documentId = DocumentsContract.getTreeDocumentId(uri);
                    copyDocumentTree(context, uri, documentId, temp, 0, new TreeCopyState());
                } else {
                    temp = ProtocolPaths.createTemporaryFile(context, "picked_passkey_",
                            selectedExtension(context, uri));
                    copyUriToFile(context, uri, temp);
                }
                result = ExternalPasskeyMaker.create(context, temp, kind, localPasscode,
                        twoFactorPassword);
                passwordRequired = (localPasscode == null || localPasscode.isEmpty())
                        && ((result != null && hasTdataPasscodeFailure(result.failures))
                        || (failure != null && failure.contains("tdata 本地密码")));
            } catch (Throwable error) {
                failure = ProtocolConverter.describe(error);
                passwordRequired = (localPasscode == null || localPasscode.isEmpty())
                        && failure.contains("tdata 本地密码");
                FileLog.e(error);
            } finally {
                if (temp != null) {
                    ProtocolPaths.deleteRecursive(temp);
                }
            }
            final ExternalPasskeyMaker.Result created = result;
            final String reason = failure;
            final boolean requestPassword = passwordRequired;
            handler.post(() -> {
                Activity owner = activityRef.get();
                if (owner == null || owner.isFinishing() || owner.isDestroyed()) {
                    return;
                }
                if (created == null) {
                    if (requestPassword) {
                        requestTdataPasscode(owner,
                                () -> showError(owner, "已取消 tdata 解密。"),
                                passcode -> createExternalPasskey(owner, uri, kind,
                                        twoFactorPassword, passcode),
                                "检测到 Telegram Desktop tdata 设置了本地密码。请输入本地密码；这不是 Telegram 两步验证密码。");
                        return;
                    }
                    if (isLocalOnlyJsonError(reason)) {
                        showError(owner, "无法创建外部【通行密钥】 Passkey：\n" + reason);
                    } else {
                        showError(owner, "外部【通行密钥】 Passkey 制作失败：" + reason);
                    }
                    return;
                }
                if (requestPassword) {
                    requestTdataPasscode(owner,
                            () -> showError(owner, "已取消 tdata 解密。"),
                            passcode -> createExternalPasskey(owner, uri, kind,
                                    twoFactorPassword, passcode),
                            "检测到压缩包中的 Telegram Desktop tdata 设置了本地密码。请输入本地密码；这不是 Telegram 两步验证密码。");
                    return;
                }
                if (created.zipFiles.isEmpty()) {
                    String detail = created.failures.isEmpty()
                            ? "没有识别到可用的授权会话。"
                            : TextUtils.join("\n", created.failures);
                    if (isLocalOnlyJsonError(detail)) {
                        showError(owner, "无法创建外部【通行密钥】 Passkey：\n" + detail);
                    } else {
                        showError(owner, "没有成功注册 Passkey：\n" + detail);
                    }
                    return;
                }
                showResult(owner, created.zipFiles, created.failures, created.publicDirectory);
            });
        }, "protocol-passkey-create").start();
    }

    private interface PasswordPromptCallback {
        void onPassword(String password);
    }

    private static boolean hasTdataPasscodeFailure(List<String> failures) {
        if (failures == null) {
            return false;
        }
        for (String failure : failures) {
            if (failure != null && failure.contains("tdata 本地密码")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocalOnlyJsonError(String message) {
        return message != null && message.contains(SessionCodec.LOCAL_ONLY_JSON_AUTH_KEY_ERROR);
    }

    private static void requestTdataPasscode(Activity activity, Runnable onCancel,
                                             PasswordPromptCallback callback, String message) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("Telegram Desktop 本地密码");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int padding = AndroidUtilities.dp(24);
        input.setPadding(padding, AndroidUtilities.dp(12), padding, AndroidUtilities.dp(12));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("需要 tdata 本地密码")
                .setMessage(message + "\n密码仅在本机用于解密，不会上传或保存。")
                .setView(input)
                .setPositiveButton("解密并继续", (d, which) -> { })
                .setNegativeButton("取消", (d, which) -> {
                    if (onCancel != null) onCancel.run();
                })
                .create();
        styleDialog(dialog);
        dialog.show();
        View positiveButton = dialog.getButton(-1);
        if (positiveButton != null) {
            positiveButton.setOnClickListener(v -> {
                String password = input.getText() == null ? "" : input.getText().toString();
                if (password.isEmpty()) {
                    Toast.makeText(activity, "请输入 Telegram Desktop 本地密码。", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                if (callback != null) callback.onPassword(password);
            });
        }
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

    // -------------------------------------------------------------- 结果与错误

    public static void showResult(Context context, List<File> files, List<String> failures,
                                  boolean publicDirectory) {
        if (context instanceof Activity && (((Activity) context).isFinishing()
                || ((Activity) context).isDestroyed())) {
            return;
        }
        if (ProtocolWebUi.presentResult(files, failures, publicDirectory)) {
            return;
        }
        final List<File> shareFiles = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file != null) {
                    shareFiles.add(file);
                }
            }
        }
        StringBuilder message = new StringBuilder();
        message.append("已生成 ").append(shareFiles.size()).append(" 个协议压缩包：\n");
        for (File file : shareFiles) {
            message.append("· ").append(file.getName()).append("（")
                    .append(file.length() / 1024).append(" KB）\n");
        }
        message.append("\n保存位置：\n");
        if (!shareFiles.isEmpty()) {
            File parent = shareFiles.get(0).getParentFile();
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
        message.append("\n协议或 Passkey 文件包含登录凭据，收件人可能使用它登录账号。请仅分享给可信对象。");
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle("协议制作完成")
                .setMessage(message.toString().trim())
                .setPositiveButton("好", null);
        if (!shareFiles.isEmpty()) {
            builder.setNeutralButton("分享", (d, which) -> ProtocolShareHelper.share(context, shareFiles));
        }
        AlertDialog dialog = builder.create();
        styleDialog(dialog);
        dialog.show();
    }

    public static void showError(Context context, String text) {
        if (context instanceof Activity && (((Activity) context).isFinishing()
                || ((Activity) context).isDestroyed())) {
            return;
        }
        if (ProtocolWebUi.presentError(text)) {
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

    private static void confirmSensitiveAction(Activity activity, CharSequence message, Runnable action) {
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
}
