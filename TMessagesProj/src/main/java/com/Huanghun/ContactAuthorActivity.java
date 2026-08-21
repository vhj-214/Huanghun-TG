package com.Huanghun;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Components.LayoutHelper;

/** Loads the bundled 黄昏 author contact page and keeps Telegram links inside this client. */
public class ContactAuthorActivity extends BaseFragment {
    private static final String LOCAL_PAGE_URL = "file:///android_asset/contact_author.html";
    private WebView webView;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("联系作者");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(Color.rgb(23, 24, 22));
        fragmentView = container;

        webView = new WebView(context);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleLink(request == null ? null : request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleLink(url == null ? null : Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 用户页面中的部分 Telegram 链接带 target=_blank。移除该属性后，所有点击
                // 均回到 shouldOverrideUrlLoading，再由当前客户端内部页面处理。
                if (LOCAL_PAGE_URL.equals(url) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    view.evaluateJavascript("(function(){document.querySelectorAll('a[href]').forEach(function(a){var h=a.getAttribute('href')||'';if(/^(tg:|https?:\\/\\/(t\\.me|telegram\\.me)\\/)/i.test(h)){a.removeAttribute('target');}});})();", null);
                }
            }
        });
        container.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        webView.loadUrl(LOCAL_PAGE_URL);
        return fragmentView;
    }

    /**
     * Telegram routes embedded in the author page must remain inside 黄昏. Ordinary web
     * addresses return false and continue following WebView's normal behavior.
     */
    private boolean handleLink(Uri uri) {
        if (uri == null || LOCAL_PAGE_URL.equals(uri.toString())) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean telegramLink = "tg".equalsIgnoreCase(scheme)
                || ("https".equalsIgnoreCase(scheme) && host != null
                && ("t.me".equalsIgnoreCase(host) || "telegram.me".equalsIgnoreCase(host)));
        if (!telegramLink) {
            return false;
        }

        try {
            String username = getTelegramUsername(uri);
            if (username != null) {
                MessagesController.getInstance(currentAccount).openByUserName(username, this, 1);
                return true;
            }

            long userId = getTelegramUserId(uri);
            if (userId > 0) {
                Bundle args = new Bundle();
                args.putLong("user_id", userId);
                presentFragment(new ProfileActivity(args));
                return true;
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
        return true;
    }

    private String getTelegramUsername(Uri uri) {
        String host = uri.getHost();
        if (host != null && ("t.me".equalsIgnoreCase(host) || "telegram.me".equalsIgnoreCase(host))) {
            String username = uri.getLastPathSegment();
            return username == null || username.isEmpty() ? null : username;
        }
        if ("tg".equalsIgnoreCase(uri.getScheme()) && "resolve".equalsIgnoreCase(uri.getHost())) {
            String username = uri.getQueryParameter("domain");
            return username == null || username.isEmpty() ? null : username;
        }
        return null;
    }

    private long getTelegramUserId(Uri uri) {
        if (!"tg".equalsIgnoreCase(uri.getScheme())) {
            return 0;
        }
        String value = uri.getQueryParameter("id");
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return false;
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onFragmentDestroy();
    }
}
