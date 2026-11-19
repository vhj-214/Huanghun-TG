package com.Huanghun.protocol.maker;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 「协议制作」的 HTML 界面容器。
 *
 * <p>界面由 {@code assets/huanghun/protocol_ui.html} 渲染，交互通过
 * {@link Bridge} 回调到原有 Java 实现：导出、转换、外部提取逻辑完全复用，
 * 这里只负责展示与线程切换。</p>
 */
public final class ProtocolWebUi {

    private static final String ASSET_DIR = "file:///android_asset/huanghun/";
    private static final String ASSET_FILE = "huanghun/protocol_ui.html";

    private static ProtocolWebUi current;

    private final WeakReference<BaseFragment> fragmentRef;
    private final boolean settingsMode;
    private Dialog dialog;
    private WebView webView;
    private FrameLayout containerView;
    private float density = 1f;
    private int minHeightPx;
    private int maxHeightPx;
    private boolean pageReady;
    private boolean released;

    private ProtocolWebUi(BaseFragment fragment, boolean settingsMode) {
        this.fragmentRef = new WeakReference<>(fragment);
        this.settingsMode = settingsMode;
    }

    public static void show(BaseFragment fragment, boolean settingsMode) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        dismissCurrent();
        ProtocolWebUi ui = new ProtocolWebUi(fragment, settingsMode);
        if (ui.open(activity)) {
            current = ui;
        }
    }

    public static void dismissCurrent() {
        ProtocolWebUi ui = current;
        current = null;
        if (ui != null) {
            ui.dismiss();
        }
    }

    /** 尝试用 HTML 结果页展示；界面已关闭时返回 false，调用方回退到原生弹窗。 */
    public static boolean presentResult(List<File> files, List<String> failures, boolean publicDirectory) {
        ProtocolWebUi ui = current;
        if (ui == null || !ui.pageReady) {
            return false;
        }
        try {
            JSONArray fileArray = new JSONArray();
            String directory = null;
            if (files != null) {
                for (File file : files) {
                    if (file == null) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("name", file.getName());
                    item.put("size", file.length());
                    fileArray.put(item);
                    if (directory == null && file.getParentFile() != null) {
                        directory = file.getParentFile().getAbsolutePath();
                    }
                }
            }
            JSONArray failureArray = new JSONArray();
            if (failures != null) {
                for (String failure : failures) {
                    failureArray.put(failure == null ? "" : failure);
                }
            }
            JSONObject payload = new JSONObject();
            payload.put("files", fileArray);
            payload.put("failures", failureArray);
            payload.put("publicDirectory", publicDirectory);
            if (directory != null) {
                payload.put("path", directory);
            }
            ui.callJs("HH.result(" + JSONObject.quote(payload.toString()) + ")");
            return true;
        } catch (Throwable error) {
            FileLog.e(error);
            return false;
        }
    }

    /** 尝试用 HTML 错误页展示；界面已关闭时返回 false。 */
    public static boolean presentError(String message) {
        ProtocolWebUi ui = current;
        if (ui == null || !ui.pageReady) {
            return false;
        }
        ui.callJs("HH.error(" + JSONObject.quote(message == null ? "未知错误" : message) + ")");
        return true;
    }

    public static void showBusy(String title, String text) {
        ProtocolWebUi ui = current;
        if (ui == null || !ui.pageReady) {
            return;
        }
        ui.callJs("HH.busy(" + JSONObject.quote(title) + "," + JSONObject.quote(text) + ")");
    }

    // ------------------------------------------------------------------ 构建界面

    private boolean open(Activity activity) {
        try {
            String html = readAsset(activity);
            if (html == null || html.isEmpty()) {
                return false;
            }

            FrameLayout container = new FrameLayout(activity);
            containerView = container;
            GradientDrawable background = new GradientDrawable();
            background.setColor(Theme.isCurrentThemeDark() ? 0xFF101323 : 0xFFF4F6FC);
            background.setCornerRadius(AndroidUtilities.dp(24));
            container.setBackground(background);
            container.setClipToOutline(true);

            webView = new WebView(activity);
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setSupportZoom(false);
            settings.setBuiltInZoomControls(false);
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return true;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return true;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (released) {
                        return;
                    }
                    pageReady = true;
                    callJs("HH.setTheme(" + Theme.isCurrentThemeDark() + ")");
                    callJs("HH.setSettingsMode(" + settingsMode + ")");
                }
            });
            webView.addJavascriptInterface(new Bridge(), "Huanghun");
            container.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            int height = Math.min(AndroidUtilities.dp(640),
                    Math.max(AndroidUtilities.dp(440), (int) (AndroidUtilities.displaySize.y * 0.82f)));
            maxHeightPx = height;
            minHeightPx = Math.min(height, AndroidUtilities.dp(360));
            density = activity.getResources().getDisplayMetrics().density;

            FrameLayout root = new FrameLayout(activity);
            int side = AndroidUtilities.dp(12);
            int vertical = AndroidUtilities.dp(26);
            root.setPadding(side, vertical, side, vertical);
            FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height);
            contentParams.gravity = Gravity.CENTER;
            root.addView(container, contentParams);

            dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            dialog.setCanceledOnTouchOutside(true);
            dialog.setOnDismissListener(d -> {
                released = true;
                pageReady = false;
                if (current == this) {
                    current = null;
                }
                releaseWebView();
            });
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(0.62f);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
            }
            dialog.show();
            webView.loadDataWithBaseURL(ASSET_DIR, html, "text/html", "UTF-8", null);
            return true;
        } catch (Throwable error) {
            FileLog.e(error);
            releaseWebView();
            return false;
        }
    }

    private void dismiss() {
        released = true;
        try {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
        releaseWebView();
    }

    private void releaseWebView() {
        if (webView == null) {
            return;
        }
        WebView view = webView;
        webView = null;
        try {
            view.removeJavascriptInterface("Huanghun");
            view.stopLoading();
            view.loadUrl("about:blank");
            view.destroy();
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    private void applyReportedHeight(float cssHeight) {
        if (containerView == null || dialog == null || !dialog.isShowing()) {
            return;
        }
        int px = (int) (cssHeight * density);
        px = Math.max(minHeightPx, Math.min(maxHeightPx, px));
        ViewGroup.LayoutParams params = containerView.getLayoutParams();
        if (params != null && params.height != px) {
            params.height = px;
            containerView.setLayoutParams(params);
        }
    }

    private void callJs(String script) {
        if (webView == null || released) {
            return;
        }
        try {
            webView.evaluateJavascript("(function(){try{" + script + "}catch(e){}})();", null);
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    private static String readAsset(Activity activity) {
        try (InputStream input = activity.getAssets().open(ASSET_FILE);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    // ------------------------------------------------------------------ JS 桥

    private final class Bridge {

        @JavascriptInterface
        public void ready() {
            AndroidUtilities.runOnUIThread(() -> {
                callJs("HH.setTheme(" + Theme.isCurrentThemeDark() + ")");
                callJs("HH.setSettingsMode(" + settingsMode + ")");
            });
        }

        @JavascriptInterface
        public void exportLocal() {
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment fragment = fragmentRef.get();
                if (fragment != null) {
                    ProtocolBuildUi.exportCurrentAccount(fragment);
                }
            });
        }

        @JavascriptInterface
        public void convert(String kind) {
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment fragment = fragmentRef.get();
                if (fragment != null) {
                    ProtocolBuildUi.convertFromUi(fragment, kind);
                }
            });
        }

        @JavascriptInterface
        public void external(String mode) {
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment fragment = fragmentRef.get();
                if (fragment != null) {
                    ProtocolBuildUi.externalExtractFromUi(fragment, "qr".equalsIgnoreCase(mode));
                }
            });
        }

        @JavascriptInterface
        public void close() {
            AndroidUtilities.runOnUIThread(ProtocolWebUi::dismissCurrent);
        }

        @JavascriptInterface
        public void height(float cssHeight) {
            AndroidUtilities.runOnUIThread(() -> applyReportedHeight(cssHeight));
        }
    }
}
