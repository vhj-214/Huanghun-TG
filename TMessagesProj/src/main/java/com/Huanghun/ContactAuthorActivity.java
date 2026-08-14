package com.Huanghun;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

/** Loads the author contact page bundled with 黄昏. */
public class ContactAuthorActivity extends BaseFragment {
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
                return handleExternalLink(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternalLink(Uri.parse(url));
            }
        });
        container.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        webView.loadUrl("file:///android_asset/contact_author.html");
        return fragmentView;
    }

    private boolean handleExternalLink(Uri uri) {
        if (uri == null) {
            return true;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean telegramLink = "tg".equalsIgnoreCase(scheme)
                || ("https".equalsIgnoreCase(scheme) && host != null
                && ("t.me".equalsIgnoreCase(host) || "telegram.me".equalsIgnoreCase(host)));
        if (!telegramLink) {
            return false;
        }
        Uri telegramUri = uri;
        if (host != null && ("t.me".equalsIgnoreCase(host) || "telegram.me".equalsIgnoreCase(host))) {
            String username = uri.getLastPathSegment();
            if (username != null && !username.isEmpty()) {
                telegramUri = Uri.parse("tg://resolve?domain=" + username);
            }
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, telegramUri);
            getParentActivity().startActivity(intent);
        } catch (Throwable error) {
            try {
                getParentActivity().startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Throwable fallbackError) {
                FileLog.e(fallbackError);
            }
        }
        return true;
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
