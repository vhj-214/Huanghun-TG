/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.view.ViewParent;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GiftAuctionController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.LeakDetector;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.StoryViewer;
import tw.nekomimi.nekogram.settings.HuanghunWallpaperLayout;
import org.telegram.ui.bots.BotWebViewAttachedSheet;

import java.util.ArrayList;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.CloudStorageHelper;
import tw.nekomimi.nekogram.helpers.UserHelper;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import tw.nekomimi.nekogram.utils.AndroidUtil;
import tw.nekomimi.nekogram.ui.glass.HuanghunGlassBackdropDrawable;
import tw.nekomimi.nekogram.ui.glass.HuanghunGlassPainter;
import tw.nekomimi.nekogram.ui.glass.HuanghunGlassPalette;
import tw.nekomimi.nekogram.ui.glass.HuanghunGlassSurface;
import xyz.nextalone.nagram.NaConfig;

public abstract class BaseFragment {

    public boolean isFinished;
    protected boolean finishing;
    public Dialog visibleDialog;
    protected int currentAccount = UserConfig.selectedAccount;

    public View fragmentView;
    protected INavigationLayout parentLayout;
    protected ActionBar actionBar;
    protected boolean inPreviewMode;
    protected boolean inMenuMode;
    protected boolean inBubbleMode;
    protected int classGuid;
    protected Bundle arguments;
    protected boolean hasOwnBackground = false;
    protected boolean isPaused = true;
    protected Dialog parentDialog;
    protected boolean inTransitionAnimation = false;
    protected boolean fragmentBeginToShow;
    private boolean removingFromStack;
    private PreviewDelegate previewDelegate;
    protected Theme.ResourcesProvider resourceProvider;
    private boolean isFullyVisible;
//    public ArrayList<StoryViewer> storyViewerStack;
//    public ArrayList<BotWebViewAttachedSheet> botsStack;
//
    public ArrayList<AttachedSheet> sheetsStack;

    public static interface AttachedSheet {
        public View getWindowView();
        public boolean isShown();
        public void dismiss();
        default void dismiss(boolean tabs) { dismiss(); }
        public void release();

        public boolean isFullyVisible();

        public boolean attachedToParent();

        public boolean onAttachedBackPressed();
        public boolean showDialog(Dialog dialog);

        public void setKeyboardHeightFromParent(int keyboardHeight);

        public boolean isAttachedLightStatusBar();
        public int getNavigationBarColor(int color);

        public void setOnDismissListener(Runnable onDismiss);

        default void setLastVisible(boolean lastVisible) {};

        default public BulletinFactory getBulletinFactory() {
            return null;
        }
    }

    public static interface AttachedSheetWindow {}

    @Nullable
    public StoryViewer getLastStoryViewer() {
        if (sheetsStack == null || sheetsStack.isEmpty())
            return null;
        for (int i = sheetsStack.size() - 1; i >= 0; --i) {
            if (sheetsStack.get(i) instanceof StoryViewer && sheetsStack.get(i).isShown()) {
                return (StoryViewer) sheetsStack.get(i);
            }
        }
        return null;
    }

    public AttachedSheet getLastSheet() {
        if (sheetsStack == null || sheetsStack.isEmpty())
            return null;
        for (int i = sheetsStack.size() - 1; i >= 0; --i) {
            if (sheetsStack.get(i).isShown()) {
                return sheetsStack.get(i);
            }
        }
        return null;
    }

    public boolean hasStoryViewer() {
        return getLastStoryViewer() != null;
    }

    public boolean hasSheet() {
        return sheetsStack != null && !sheetsStack.isEmpty();
    }

    public boolean hasShownSheet() {
        if (!hasSheet()) return false;
        for (int i = sheetsStack.size() - 1; i >= 0; --i) {
            if (sheetsStack.get(i).isShown()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasShownFullyVisibleSheet() {
        if (!hasSheet()) return false;
        for (int i = sheetsStack.size() - 1; i >= 0; --i) {
            if (sheetsStack.get(i).isShown() && sheetsStack.get(i).isFullyVisible()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasSheets(BaseFragment fragment) {
        if (fragment == null) return false;
        if (fragment.hasShownSheet()) return true;
        if (!(fragment.getParentLayout() instanceof ActionBarLayout)) return false;
        final BaseFragment sheetFragment = ((ActionBarLayout) fragment.getParentLayout()).getSheetFragment(false);
        return sheetFragment != null && sheetFragment.hasShownSheet();
    }

    public static boolean hasFullyVisibleSheets(BaseFragment fragment) {
        if (fragment == null) return false;
        if (fragment.hasShownFullyVisibleSheet()) return true;
        if (!(fragment.getParentLayout() instanceof ActionBarLayout)) return false;
        final BaseFragment sheetFragment = ((ActionBarLayout) fragment.getParentLayout()).getSheetFragment(false);
        return sheetFragment != null && sheetFragment.hasShownFullyVisibleSheet();
    }

    public void clearSheets() {
        if (sheetsStack == null || sheetsStack.isEmpty())
            return;
        for (int i = sheetsStack.size() - 1; i >= 0; --i) {
            sheetsStack.get(i).dismiss(true);
        }
        sheetsStack.clear();
    }

    public BaseFragment() {
        this(null);
    }

    public BaseFragment(Bundle args) {
        arguments = args;
        classGuid = ConnectionsManager.generateClassGuid();
        if (BuildConfig.DEBUG) {
            LeakDetector.getInstance().add(this);
        }

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return isSupportEdgeToEdge() ? AndroidUtilities.navigationBarHeight : 0;
            }
        });
    }

    public void setCurrentAccount(int account) {
        if (fragmentView != null) {
            throw new IllegalStateException("trying to set current account when fragment UI already created");
        }
        currentAccount = account;
    }

    public boolean hasOwnBackground() {
        return hasOwnBackground;
    }

    public void setHasOwnBackground(boolean hasOwnBackground) {
        this.hasOwnBackground = hasOwnBackground;
    }

    public boolean getFragmentBeginToShow() {
        return fragmentBeginToShow;
    }

    public ActionBar getActionBar() {
        return actionBar;
    }

    public View getFragmentView() {
        return fragmentView;
    }

    public void setFragmentView(View fragmentView) {
        this.fragmentView = fragmentView;
    }

    public View createView(Context context) {
        return null;
    }

    public Bundle getArguments() {
        return arguments;
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    public int getClassGuid() {
        return classGuid;
    }

    public boolean isSwipeBackEnabled(MotionEvent event) {
        return true;
    }

    public void setInBubbleMode(boolean value) {
        inBubbleMode = value;
    }

    public boolean isInBubbleMode() {
        return inBubbleMode;
    }

    public boolean isInPreviewMode() {
        return inPreviewMode;
    }

    public boolean getInPassivePreviewMode() {
        return parentLayout != null && parentLayout.isInPassivePreviewMode();
    }

    public boolean isActionBarCrossfadeEnabled() {
        if (NaConfig.INSTANCE.getBackAnimationStyle().Int() == ActionBarLayout.BACK_ANIMATION_SPRING) {
            if (getLastStoryViewer() != null && getLastStoryViewer().attachedToParent()) {
                return false;
            }
            return actionBar != null && !actionBar.isActionModeShowed();
        }
        return actionBar != null;
    }

    public INavigationLayout.BackButtonState getBackButtonState() {
        return actionBar != null ? actionBar.getBackButtonState() : null;
    }

    public void setInPreviewMode(boolean value) {
        inPreviewMode = value;
        if (actionBar != null) {
            actionBar.setOccupyStatusBar(!inPreviewMode);
        }
    }

    public void setInMenuMode(boolean value) {
        inMenuMode = value;
    }

    public void onPreviewOpenAnimationEnd() {
    }

    protected boolean hideKeyboardOnShow() {
        return true;
    }

    public void clearViews() {
        if (fragmentView != null) {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                try {
                    onRemoveFromParent();
                    parent.removeViewInLayout(fragmentView);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            fragmentView = null;
        }
        if (actionBar != null) {
            ViewGroup parent = (ViewGroup) actionBar.getParent();
            if (parent != null) {
                try {
                    parent.removeViewInLayout(actionBar);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            actionBar = null;
        }
        clearSheets();
        parentLayout = null;
    }

    public void onRemoveFromParent() {
        if (sheetsStack == null || sheetsStack.isEmpty())
            return;
        updateSheetsVisibility();
    }

    public void setParentFragment(BaseFragment fragment) {
        setParentLayout(fragment.parentLayout);
        fragmentView = createView(parentLayout.getView().getContext());
        if (NekoConfig.disableVibration.Bool()) {
            AndroidUtil.disableHapticFeedback(fragmentView);
        }
    }

    public void setParentLayout(INavigationLayout layout) {
        if (parentLayout != layout) {
            parentLayout = layout;
            inBubbleMode = parentLayout != null && parentLayout.isInBubbleMode();
            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup) fragmentView.getParent();
                if (parent != null) {
                    try {
                        onRemoveFromParent();
                        parent.removeViewInLayout(fragmentView);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (parentLayout != null && parentLayout.getView().getContext() != fragmentView.getContext()) {
                    fragmentView = null;
                    clearSheets();
                }
            }
            if (actionBar != null) {
                boolean differentParent = parentLayout != null && parentLayout.getView().getContext() != actionBar.getContext();
                if (actionBar.shouldAddToContainer() || differentParent) {
                    ViewGroup parent = (ViewGroup) actionBar.getParent();
                    if (parent != null) {
                        try {
                            parent.removeViewInLayout(actionBar);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
                if (differentParent) {
                    actionBar = null;
                }
            }
            if (parentLayout != null && actionBar == null) {
                actionBar = createActionBar(parentLayout.getView().getContext());
                if (actionBar != null) {
                    actionBar.parentFragment = this;
                }
            }
        }
    }

    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context, getResourceProvider());
        actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), true);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), true);
        if (inPreviewMode || inBubbleMode || parentLayout != null && parentLayout.isLayersLayout()) {
            actionBar.setOccupyStatusBar(false);
        }
        return actionBar;
    }

    public void movePreviewFragment(float dy) {
        parentLayout.movePreviewFragment(dy);
    }

    public void finishPreviewFragment() {
        if (parentLayout != null) {
            parentLayout.finishPreviewFragment();
        }
    }

    public void finishFragment() {
        if (parentDialog != null) {
            parentDialog.dismiss();
            return;
        }
        if (inPreviewMode && previewDelegate != null) {
            previewDelegate.finishFragment();
        } else {
            finishFragment(true);
        }
    }

    public void setFinishing(boolean finishing) {
        this.finishing = finishing;
    }

    public boolean finishFragment(boolean animated) {
        if (isFinished || parentLayout == null) {
            return false;
        }
        finishing = true;
        parentLayout.closeLastFragment(animated);
        return true;
    }

    public void removeSelfFromStack() {
        removeSelfFromStack(false);
    }

    public void removeSelfFromStack(boolean immediate) {
        if (isFinished || parentLayout == null) {
            return;
        }
        if (parentDialog != null) {
            parentDialog.dismiss();
            return;
        }
        parentLayout.removeFragmentFromStack(this, immediate);
    }

    public boolean allowFinishFragmentInsteadOfRemoveFromStack() {
        return true;
    }

    protected boolean isFinishing() {
        return finishing;
    }

    public boolean onFragmentCreate() {
        return true;
    }

    @CallSuper
    public void onFragmentDestroy() {
        getConnectionsManager().cancelRequestsForGuid(classGuid);
        getMessagesStorage().cancelTasksForGuid(classGuid);
        isFinished = true;
        if (actionBar != null) {
            actionBar.setEnabled(false);
        }

        if (hasForceLightStatusBar() && !AndroidUtilities.isTablet() && getParentLayout().getLastFragment() == this && getParentActivity() != null && !finishing) {
            AndroidUtilities.setLightStatusBar(getParentActivity(), ColorUtils.calculateLuminance(Theme.getColor(Theme.key_actionBarDefault)) > 0.7f);
        }

        if (sheetsStack != null) {
            for (int i = sheetsStack.size() - 1; i >= 0; --i) {
                AttachedSheet sheet = sheetsStack.get(i);
                sheet.setLastVisible(false);
                sheet.dismiss(true);
                sheetsStack.remove(i);
            }
        }
        // Drop the glass bookkeeping for this page. The glass viewport itself is
        // shared by the navigation container and stays alive for the next page.
        if (fragmentView != null) {
            glassSurfaces.remove(fragmentView);
            glassWallpaperRoots.remove(fragmentView);
        }
    }

    public boolean needDelayOpenAnimation() {
        return false;
    }

    protected void resumeDelayedFragmentAnimation() {
        if (parentLayout != null) {
            parentLayout.resumeDelayedFragmentAnimation();
        }
    }

    public void onUserLeaveHint() {}

    @CallSuper
    public void onResume() {
        isPaused = false;
        applySettingsGlassWallpaper();
        if (actionBar != null) {
            actionBar.onResume();
        }
        if (getLastStoryViewer() != null) {
            getLastStoryViewer().onResume();
            getLastStoryViewer().updatePlayingMode();
        }
    }

    /** Apply the settings wallpaper consistently to legacy settings fragments. */
    private void applySettingsGlassWallpaper() {
        if (fragmentView == null
                || fragmentView instanceof org.telegram.ui.Components.LoginBackgroundLayout
                || fragmentView instanceof HuanghunWallpaperLayout) {
            // Pages that own a wallpaper-backed root (login, NekoX settings, Huanghun
            // feature screens) paint the wallpaper themselves, including the area
            // behind the action bar. Applying the legacy surface on top would only
            // add a second, misaligned wallpaper layer.
            return;
        }
        // Never wait for Theme's wallpaper worker on the UI thread while a page
        // is being opened. If the cache is not ready, retain the page's safe
        // native background and let the next resume apply the wallpaper.
        final Drawable cachedWallpaper = Theme.getCachedWallpaperNonBlocking();
        if (cachedWallpaper == null && wallpaperApplyAttempt < 4) {
            // The wallpaper cache is rebuilt on the theme worker. Retry a few
            // times, then continue: the glass surface paints a theme-coloured
            // material when no wallpaper exists, so the page is never black.
            wallpaperApplyAttempt++;
            final View retryTarget = fragmentView;
            retryTarget.postDelayed(() -> {
                if (fragmentView == retryTarget && !isPaused && !isFinished) {
                    applySettingsGlassWallpaper();
                }
            }, 220);
            return;
        }
        wallpaperApplyAttempt = 0;
        final String name = getClass().getName();
        boolean huanghunServiceScreen = name.equals("com.Huanghun.HuanghunServiceActivity")
                || name.equals("com.Huanghun.HuanghunDeviceDetailsActivity");
        if ((!name.startsWith("org.telegram.ui.") && !huanghunServiceScreen) || name.equals("org.telegram.ui.SettingsActivity")) return;
        final String simpleName = name.substring(name.lastIndexOf('.') + 1);
        // This covers the complete Settings -> Chat through Power Saving
        // navigation chain, including child screens opened from those entries.
        // Keep the list explicit enough not to turn unrelated chat, profile,
        // or dialog fragments into wallpaper-backed settings pages.
        if (!(simpleName.contains("Settings") || simpleName.contains("Privacy")
                || simpleName.contains("Stickers") || simpleName.contains("Theme")
                || simpleName.contains("Sessions") || simpleName.contains("LanguageSelect")
                || simpleName.contains("Passcode") || simpleName.contains("Passkeys")
                || simpleName.contains("TwoStepVerification") || simpleName.contains("DataAutoDownload")
                || simpleName.contains("DataUsage") || simpleName.contains("Notifications")
                || simpleName.contains("ArchiveSettings") || simpleName.contains("Cache")
                || simpleName.contains("Filter") || simpleName.contains("Proxy")
                || simpleName.contains("QuickReplies") || simpleName.contains("SaveToGallery")
                || simpleName.contains("LiteMode") || simpleName.contains("AutoDeleteMessages")
                || simpleName.contains("WallpapersList") || simpleName.contains("PeerColor")
                || simpleName.contains("Reactions") || simpleName.contains("PinnedReactions")
                || simpleName.contains("Logout")
                || simpleName.contains("HuanghunService") || simpleName.contains("HuanghunDeviceDetails"))) return;

        // iOS-style glass: the whole page is backed by one blurred backdrop and
        // every material on top of it (action bar, section cards, overlays)
        // shares the same source, so the surfaces line up without seams.
        View wallpaperViewport = parentLayout != null ? parentLayout.getView() : null;
        if (wallpaperViewport == null) {
            wallpaperViewport = fragmentView.getRootView();
        }
        if (wallpaperViewport == null) {
            wallpaperViewport = fragmentView;
        }
        HuanghunGlassSurface glassSurface = null;
        if (wallpaperViewport instanceof ViewGroup) {
            glassSurface = HuanghunGlassSurface.get((ViewGroup) wallpaperViewport);
            glassSurface.setViewportSize(wallpaperViewport.getWidth(), wallpaperViewport.getHeight(), 0);
            glassSurface.setWallpaper(cachedWallpaper);
            glassSurface.onThemeChanged();
        }
        if (glassSurface != null) {
            glassSurfaces.put(fragmentView, glassSurface);
        }
        if (glassSurface != null) {
            glassSurface.registerBackdropView(fragmentView);
            if (!(fragmentView.getBackground() instanceof HuanghunGlassBackdropDrawable)) {
                fragmentView.setBackground(new HuanghunGlassBackdropDrawable(glassSurface));
            }
            ensureGlassLayoutListener(wallpaperViewport, glassSurface);
        }
        glassWallpaperRoots.put(fragmentView, Boolean.TRUE);
        if (getParentActivity() != null && getParentActivity().getWindow() != null) {
            getParentActivity().getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
        if (actionBar != null) {
            actionBar.disableAdaptiveBackground();
            boolean applied = false;
            if (glassSurface != null) {
                BlurredBackgroundDrawable current = glassActionBarBackgrounds.get(actionBar);
                if (current == null || actionBar.getBackground() != current) {
                    current = glassSurface.createBackground(actionBar, HuanghunGlassPalette.VARIANT_ACTION_BAR, 0);
                    if (current != null) {
                        glassActionBarBackgrounds.put(actionBar, current);
                        actionBar.setWallpaperBackground(current);
                    }
                }
                applied = current != null && actionBar.getBackground() == current;
            }
            if (!applied) {
                // Last-resort surface: a translucent theme colour, never a
                // transparent bar (transparency is what shows the black window).
                actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
            }
            actionBar.setCastShadows(false);
        }
        applyTransparentSettingsSections(fragmentView);
        clearOpaqueSettingsFills(fragmentView);
        // Native settings lists may bind their first cells after onResume().
        // Perform one cleanup after that initial layout, but never mutate
        // backgrounds from a per-frame drawing callback: doing so races
        // RecyclerView/blurred wallpaper rendering and briefly exposes the
        // window black fallback on every scroll frame.
        final View targetView = fragmentView;
        targetView.post(() -> {
            if (fragmentView == targetView && !isPaused && !isFinished && targetView.getParent() != null) {
                clearOpaqueSettingsFills(targetView);
            }
        });
    }

    protected final boolean refreshSettingsGlassWallpaper() {
        Drawable oldRootBackground = fragmentView != null ? fragmentView.getBackground() : null;
        Drawable oldActionBarBackground = actionBar != null ? actionBar.getBackground() : null;
        applySettingsGlassWallpaper();
        return fragmentView != null && oldRootBackground != fragmentView.getBackground()
                || actionBar != null && oldActionBarBackground != actionBar.getBackground();
    }

    private static final java.util.WeakHashMap<View, Boolean> glassLayoutWatch = new java.util.WeakHashMap<>();

    /**
     * Uploads the glass backdrop once the viewport has a real size. Registering
     * a listener (instead of re-applying on every resume) keeps the first frame
     * correct even when the page is opened before the navigation container has
     * been measured.
     */
    private static void ensureGlassLayoutListener(@NonNull View viewport, @NonNull HuanghunGlassSurface surface) {
        if (glassLayoutWatch.containsKey(viewport)) {
            return;
        }
        glassLayoutWatch.put(viewport, Boolean.TRUE);
        viewport.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            surface.setViewportSize(right - left, bottom - top, 0);
            surface.setWallpaper(Theme.getCachedWallpaperNonBlocking());
        });
    }

    private int wallpaperApplyAttempt;

    /** Roots that paint the settings wallpaper themselves; theme refreshes must not repaint them. */
    private static final java.util.WeakHashMap<View, Boolean> glassWallpaperRoots = new java.util.WeakHashMap<>();
    /** Glass viewport used by each wallpaper-backed page. */
    private static final java.util.WeakHashMap<View, HuanghunGlassSurface> glassSurfaces = new java.util.WeakHashMap<>();
    /** Action bar glass background per bar, so onResume never rebuilds it. */
    private static final java.util.WeakHashMap<View, BlurredBackgroundDrawable> glassActionBarBackgrounds = new java.util.WeakHashMap<>();
    /** Recycler lists that already received the glass section renderer and child cleanup. */
    private static final java.util.WeakHashMap<View, Boolean> glassSettingsLists = new java.util.WeakHashMap<>();

    public static boolean isInGlassPage(@Nullable View view) {
        View current = view;
        while (current != null) {
            if (glassWallpaperRoots.containsKey(current)) {
                return true;
            }
            final ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
    @Nullable
    private Drawable cloneWallpaperForView(@NonNull Drawable source, @NonNull View target) {
        Drawable.ConstantState constantState = source.getConstantState();
        if (constantState == null) {
            return null;
        }
        try {
            return constantState.newDrawable(target.getResources()).mutate();
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    @Nullable
    private Drawable createWallpaperViewportDrawable(@NonNull Drawable source, @NonNull View target, @NonNull View viewport) {
        Drawable wallpaper = cloneWallpaperForView(source, target);
        if (wallpaper == null) {
            wallpaper = source;
        }
        wallpaper.setCallback(viewport);
        if (wallpaper instanceof org.telegram.ui.Components.MotionBackgroundDrawable) {
            ((org.telegram.ui.Components.MotionBackgroundDrawable) wallpaper).setParentView(viewport);
        }
        return new WallpaperViewportDrawable(wallpaper, source, target, viewport);
    }

    private boolean isWallpaperViewportDrawable(@Nullable Drawable drawable, @NonNull Drawable source, @NonNull View target, @NonNull View viewport) {
        if (!(drawable instanceof WallpaperViewportDrawable)) {
            return false;
        }
        WallpaperViewportDrawable wallpaper = (WallpaperViewportDrawable) drawable;
        return wallpaper.source == source && wallpaper.target == target && wallpaper.viewport == viewport;
    }

    /** Draw one clipped part of a wallpaper against a shared full-page coordinate system. */
    private static final class WallpaperViewportDrawable extends Drawable {
        private final Drawable wallpaper;
        private final Drawable source;
        private final View target;
        private final View viewport;
        private final int[] targetLocation = new int[2];
        private final int[] viewportLocation = new int[2];

        private WallpaperViewportDrawable(Drawable wallpaper, Drawable source, View target, View viewport) {
            this.wallpaper = wallpaper;
            this.source = source;
            this.target = target;
            this.viewport = viewport;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            target.getLocationInWindow(targetLocation);
            viewport.getLocationInWindow(viewportLocation);
            int width = viewport.getWidth();
            int height = viewport.getHeight();
            if (width <= 0) {
                width = Math.max(target.getWidth(), bounds.width());
            }
            if (height <= 0) {
                height = Math.max(target.getHeight(), bounds.height());
            }

            final int offsetX = targetLocation[0] - viewportLocation[0];
            final int offsetY = targetLocation[1] - viewportLocation[1];
            final Rect previousBounds = wallpaper.copyBounds();
            try {
                final int left = bounds.left - offsetX;
                final int top = bounds.top - offsetY;
                wallpaper.setBounds(left, top, left + width, top + height);
                wallpaper.draw(canvas);
            } finally {
                wallpaper.setBounds(previousBounds);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            wallpaper.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            wallpaper.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Glass viewport this page paints with, or {@code null} before the first resume. */
    @Nullable
    private HuanghunGlassSurface resolveGlassSurface() {
        if (fragmentView != null) {
            final HuanghunGlassSurface surface = glassSurfaces.get(fragmentView);
            if (surface != null) {
                return surface;
            }
        }
        if (parentLayout != null) {
            final View view = parentLayout.getView();
            if (view instanceof ViewGroup) {
                return HuanghunGlassSurface.get((ViewGroup) view);
            }
        }
        return null;
    }

    /** Replace the legacy opaque white section renderer only on wallpaper-backed settings pages. */
    private void applyTransparentSettingsSections(View view) {
        if (view instanceof WebView) {
            return;
        }
        if (view instanceof RecyclerListView) {
            RecyclerListView listView = (RecyclerListView) view;
            if (!glassSettingsLists.containsKey(listView)) {
                glassSettingsLists.put(listView, Boolean.TRUE);
                // Cells are recycled and rebound while scrolling; a one-shot cleanup in
                // onResume leaves every newly bound white cell visible.
                listView.addOnChildAttachStateChangeListener(new RecyclerListView.OnChildAttachStateChangeListener() {
                    @Override
                    public void onChildViewAttachedToWindow(@NonNull View child) {
                        clearOpaqueSettingsFills(child);
                    }

                    @Override
                    public void onChildViewDetachedFromWindow(@NonNull View child) {
                    }
                });
            }
            // Wallpaper-backed glass rows are translucent. RecyclerView's default
            // change cross-fade draws old and new holders over one another, which
            // appears as ghosted text and panels during async refreshes.
            if (listView.getItemAnimator() instanceof SimpleItemAnimator) {
                ((SimpleItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
            }
            final HuanghunGlassSurface sectionSurface = resolveGlassSurface();
            final float sectionLuminance = sectionSurface != null ? sectionSurface.getWallpaperLuminance() : 0.5f;
            final HuanghunGlassPalette cardPalette = new HuanghunGlassPalette(
                    HuanghunGlassPalette.VARIANT_CARD, Theme.isCurrentThemeDark(), sectionLuminance);
            listView.setSections(
                    sectionView -> !(sectionView instanceof org.telegram.ui.Cells.ShadowSectionCell),
                    AndroidUtilities.dp(12),
                    AndroidUtilities.dp(16),
                    (canvas, rect, topRadius, bottomRadius, alpha) -> {
                        cardPalette.update(HuanghunGlassPalette.VARIANT_CARD, Theme.isCurrentThemeDark(), sectionLuminance);
                        HuanghunGlassPainter.drawPanel(canvas, rect, Math.max(topRadius, bottomRadius), cardPalette);
                    },
                    false
            );
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTransparentSettingsSections(group.getChildAt(i));
            }
        }
    }

    private void clearOpaqueSettingsFills(View view) {
        // WebView owns an internal rendering hierarchy; its page background is
        // controlled by the HTML/CSS and must not be rewritten recursively.
        if (view instanceof WebView) {
            return;
        }
        if (view != fragmentView && view.getBackground() instanceof ColorDrawable) {
            int color = ((ColorDrawable) view.getBackground()).getColor();
            if (color == Theme.getColor(Theme.key_windowBackgroundWhite)
                    || color == Theme.getColor(Theme.key_windowBackgroundGray)) {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearOpaqueSettingsFills(group.getChildAt(i));
            }
        }
    }

    @CallSuper
    public void onPause() {
        if (actionBar != null) {
            actionBar.onPause();
        }
        isPaused = true;
        try {
            if (visibleDialog != null && visibleDialog.isShowing() && dismissDialogOnPause(visibleDialog)) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (getLastStoryViewer() != null) {
            getLastStoryViewer().onPause();
            getLastStoryViewer().updatePlayingMode();
        }
    }

    public void setPaused(boolean paused) {
        if (isPaused == paused) {
            return;
        }

        if (paused) {
            onPause();
        } else {
            onResume();
        }
    }

    public boolean isPaused() {
        return isPaused;
    }

    public BaseFragment getFragmentForAlert(int offset) {
        if (parentLayout == null || parentLayout.getFragmentStack().size() <= 1 + offset) {
            return this;
        }
        return parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 2 - offset);
    }

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {

    }

    public boolean onBackPressed(boolean invoked) {
        if (hasShownSheet()) {
            if (invoked) closeSheet();
            return false;
        }
        return true;
    }

    public boolean closeSheet() {
        if (sheetsStack != null) {
            for (int i = sheetsStack.size() - 1; i >= 0; --i) {
                if (sheetsStack.get(i).isShown()) {
                    return sheetsStack.get(i).onAttachedBackPressed();
                }
            }
        }
        return false;
    }

    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {

    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {

    }

    public void saveSelfArgs(Bundle args) {

    }

    public void restoreSelfArgs(Bundle args) {

    }

    public boolean isLastFragment() {
        return parentLayout != null && parentLayout.getLastFragment() == this;
    }

    public INavigationLayout getParentLayout() {
        return parentLayout;
    }

    public FrameLayout getLayoutContainer() {
        if (fragmentView != null) {
            final ViewParent parent = fragmentView.getParent();
            if (parent instanceof FrameLayout) {
                return (FrameLayout) parent;
            }
        }
        return null;
    }

    public FrameLayout getBulletinLayoutContainer() {
        return getLayoutContainer();
    }

    public boolean presentFragmentAsPreview(BaseFragment fragment) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragmentAsPreview(fragment);
    }

    public boolean presentFragmentAsPreviewWithMenu(BaseFragment fragment, ActionBarPopupWindow.ActionBarPopupWindowLayout menu) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragmentAsPreviewWithMenu(fragment, menu);
    }

    public boolean presentFragment(BaseFragment fragment) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment, removeLast);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true, false, null);
    }

    public boolean presentFragment(INavigationLayout.NavigationParams params) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(params);
    }

    public Activity getParentActivity() {
        if (parentLayout != null) {
            return parentLayout.getParentActivity();
        }
        return null;
    }

    public Context getContext() {
        return getParentActivity();
    }

    protected void setParentActivityTitle(CharSequence title) {
        Activity activity = getParentActivity();
        if (activity != null) {
            activity.setTitle(title);
        }
    }

    public void startActivityForResult(final Intent intent, final int requestCode) {
        if (parentLayout != null) {
            parentLayout.startActivityForResult(intent, requestCode);
        }
    }

    public void dismissCurrentDialog() {
        if (visibleDialog == null) {
            return;
        }
        try {
            visibleDialog.dismiss();
            visibleDialog = null;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean dismissDialogOnPause(Dialog dialog) {
        return true;
    }

    public boolean canBeginSlide() {
        return true;
    }

    public void onBeginSlide() {
        try {
            if (visibleDialog != null && visibleDialog.isShowing()) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (actionBar != null) {
            actionBar.onPause();
        }
    }

    public void onSlideProgress(boolean isOpen, float progress) {

    }

    public void onTransitionAnimationProgress(boolean isOpen, float progress) {

    }

    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        inTransitionAnimation = true;
        if (isOpen) {
            fragmentBeginToShow = true;
        }
    }

    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        inTransitionAnimation = false;
    }

    public void onBecomeFullyVisible() {
        isFullyVisible = true;
        AccessibilityManager mgr = (AccessibilityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (mgr.isEnabled()) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                String title = actionBar.getTitle();
                if (!TextUtils.isEmpty(title)) {
                    setParentActivityTitle(title);
                }
            }
        }
        if (fullyVisibleListener != null) {
            Runnable c = fullyVisibleListener;
            fullyVisibleListener = null;
            c.run();
        }
        updateSheetsVisibility();
        checkSystemBarColors();
    }

    protected void checkSystemBarColors() {
        Activity activity = getParentActivity();
        if (activity instanceof LaunchActivity) {
            ((LaunchActivity) activity).checkSystemBarColors(true, true, true);
        }
    }

    protected void updateSheetsVisibility() {
        if (sheetsStack == null) return;
        for (int i = 0; i < sheetsStack.size(); ++i) {
            AttachedSheet sheet = sheetsStack.get(i);
            sheet.setLastVisible(i == sheetsStack.size() - 1 && isFullyVisible);
        }
    }

    private Runnable fullyVisibleListener;
    public void whenFullyVisible(Runnable callback) {
        fullyVisibleListener = callback;
    }

    public int getPreviewHeight() {
        return LayoutHelper.MATCH_PARENT;
    }

    public void onBecomeFullyHidden() {
        isFullyVisible = false;
        updateSheetsVisibility();
    }

    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        return null;
    }

    public void onLowMemory() {

    }

    public Dialog showDialog(Dialog dialog) {
        return showDialog(dialog, false, null);
    }

    public Dialog showDialog(Dialog dialog, Dialog.OnDismissListener onDismissListener) {
        return showDialog(dialog, false, onDismissListener);
    }

    public Dialog showDialog(Dialog dialog, boolean allowInTransition, final Dialog.OnDismissListener onDismissListener) {
        if (dialog == null || parentLayout == null || parentLayout.isTransitionAnimationInProgress() || parentLayout.isSwipeInProgress() || !allowInTransition && parentLayout.checkTransitionAnimation()) {
            return null;
        }
        if (sheetsStack != null) {
            for (int i = sheetsStack.size() - 1; i >= 0; --i) {
                if (sheetsStack.get(i).isShown()) {
                    if (sheetsStack.get(i).showDialog(dialog)) {
                        return dialog;
                    }
                }
            }
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            visibleDialog = dialog;
            visibleDialog.setCanceledOnTouchOutside(true);
            visibleDialog.setOnDismissListener(dialog1 -> {
                if (onDismissListener != null) {
                    onDismissListener.onDismiss(dialog1);
                }
                onDialogDismiss((Dialog) dialog1);
                if (dialog1 == visibleDialog) {
                    visibleDialog = null;
                }
            });
            visibleDialog.show();
            return visibleDialog;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    protected void onDialogDismiss(Dialog dialog) {

    }

    protected void onPanTranslationUpdate(float y) {

    }

    protected void onPanTransitionStart() {

    }

    protected void onPanTransitionEnd() {

    }

    public Dialog getVisibleDialog() {
        return visibleDialog;
    }

    public void setVisibleDialog(Dialog dialog) {
        visibleDialog = dialog;
    }

    public boolean extendActionMode(Menu menu) {
        return false;
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return new ArrayList<>();
    }

    public AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(currentAccount);
    }

    public MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }

    public GiftAuctionController getGiftAuctionsController() {
        return getAccountInstance().getGiftAuctionsController();
    }

    protected ContactsController getContactsController() {
        return getAccountInstance().getContactsController();
    }

    public MediaDataController getMediaDataController() {
        return getAccountInstance().getMediaDataController();
    }

    public ConnectionsManager getConnectionsManager() {
        return getAccountInstance().getConnectionsManager();
    }

    public LocationController getLocationController() {
        return getAccountInstance().getLocationController();
    }

    protected NotificationsController getNotificationsController() {
        return getAccountInstance().getNotificationsController();
    }

    public MessagesStorage getMessagesStorage() {
        return getAccountInstance().getMessagesStorage();
    }

    public SendMessagesHelper getSendMessagesHelper() {
        return getAccountInstance().getSendMessagesHelper();
    }

    public FileLoader getFileLoader() {
        return getAccountInstance().getFileLoader();
    }

    protected SecretChatHelper getSecretChatHelper() {
        return getAccountInstance().getSecretChatHelper();
    }

    public DownloadController getDownloadController() {
        return getAccountInstance().getDownloadController();
    }

    protected SharedPreferences getNotificationsSettings() {
        return getAccountInstance().getNotificationsSettings();
    }

    public NotificationCenter getNotificationCenter() {
        return getAccountInstance().getNotificationCenter();
    }

    public MediaController getMediaController() {
        return MediaController.getInstance();
    }

    public UserConfig getUserConfig() {
        return getAccountInstance().getUserConfig();
    }

    public void setFragmentPanTranslationOffset(int offset) {
        if (parentLayout != null) {
            parentLayout.setFragmentPanTranslationOffset(offset);
        }
    }

    public MessageHelper getMessageHelper() {
        return getAccountInstance().getMessageHelper();
    }

    public UserHelper getUserHelper() {
        return getAccountInstance().getUserHelper();
    }

    public CloudStorageHelper getCloudStorageHelper() {
        return getAccountInstance().getCloudStorageHelper();
    }

    public void saveKeyboardPositionBeforeTransition() {

    }

    public Animator getCustomSlideTransition(boolean topFragment, boolean backAnimation, float distanceToMove) {
        return null;
    }

    protected boolean shouldOverrideSlideTransition(boolean topFragment, boolean backAnimation) {
        return false;
    }

    public void prepareFragmentToSlide(boolean topFragment, boolean beginSlide) {

    }

    public INavigationLayout[] showAsSheet(BaseFragment fragment) {
        return showAsSheet(fragment, null);
    }

    public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params) {
        if (getParentActivity() == null) {
            return null;
        }
        BottomSheet[] bottomSheet = new BottomSheet[1];
        INavigationLayout[] actionBarLayout = new INavigationLayout[]{INavigationLayout.newLayout(getParentActivity(), false, () -> bottomSheet[0])};
        actionBarLayout[0].setIsSheet(true);
        LaunchActivity.instance.sheetFragmentsStack.add(actionBarLayout[0]);
        fragment.onTransitionAnimationStart(true, false);
        bottomSheet[0] = new BottomSheet(getParentActivity(), true, fragment.getResourceProvider()) {
            {
                occupyNavigationBar = params != null && params.occupyNavigationBar;
                drawNavigationBar = !occupyNavigationBar;
                actionBarLayout[0].setFragmentStack(new ArrayList<>());
                actionBarLayout[0].addFragmentToStack(fragment);
                actionBarLayout[0].showLastFragment();
                actionBarLayout[0].getView().setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
                ViewGroup view = actionBarLayout[0].getView();
                containerView = view;
                setApplyBottomPadding(false);
                setOnDismissListener(dialog -> {
                    fragment.onPause();
                    fragment.onFragmentDestroy();
                    if (params != null && params.onDismiss != null) {
                        params.onDismiss.run();
                    }
                });
            }

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                actionBarLayout[0].setWindow(bottomSheet[0].getWindow());
                if (params == null || !params.occupyNavigationBar) {
                    fixNavigationBar(Theme.getColor(Theme.key_dialogBackgroundGray, fragment.getResourceProvider()));
                } else {
                    AndroidUtilities.setLightNavigationBar(bottomSheet[0], true);
                }
                AndroidUtilities.setLightStatusBar(this, fragment.isLightStatusBar());
                fragment.onBottomSheetCreated();
            }

            @Override
            protected boolean canDismissWithSwipe() {
                return false;
            }

            @Override
            protected boolean canSwipeToBack(MotionEvent event) {
                if (params != null && params.transitionFromLeft && actionBarLayout[0] != null && actionBarLayout[0].getFragmentStack().size() <= 1) {
                    if (actionBarLayout[0].getFragmentStack().size() == 1) {
                        BaseFragment lastFragment = actionBarLayout[0].getFragmentStack().get(0);
                        if (!lastFragment.isSwipeBackEnabled(event)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onBackPressed() {
                if (actionBarLayout[0] == null || actionBarLayout[0].getFragmentStack().size() <= 1) {
                    super.onBackPressed();
                } else {
                    actionBarLayout[0].onBackPressed();
                }
            }

            @Override
            public void dismiss() {
                if (!isDismissed()) {
                    if (params != null && params.onPreFinished != null) {
                        params.onPreFinished.run();
                    }
                }
                super.dismiss();
                LaunchActivity.instance.sheetFragmentsStack.remove(actionBarLayout[0]);
                actionBarLayout[0] = null;
            }

            @Override
            public void onOpenAnimationEnd() {
                fragment.onTransitionAnimationEnd(true, false);
                if (params != null && params.onOpenAnimationFinished != null) {
                    params.onOpenAnimationFinished.run();
                }
            }

            @Override
            protected void onInsetsChanged() {
                if (actionBarLayout[0] != null) {
                    for (BaseFragment baseFragment : actionBarLayout[0].getFragmentStack()) {
                        if (baseFragment.getFragmentView() != null) {
                            baseFragment.getFragmentView().requestLayout();
                        }
                    }
                }
            }
        };
        if (params != null) {
            bottomSheet[0].setAllowNestedScroll(params.allowNestedScroll);
            bottomSheet[0].transitionFromRight(params.transitionFromLeft);
        }
        fragment.setParentDialog(bottomSheet[0]);
        bottomSheet[0].setOpenNoDelay(true);
        bottomSheet[0].show();

        return actionBarLayout;
    }

    public int getThemedColor(int key) {
        return Theme.getColor(key, getResourceProvider());
    }

    public Paint getThemedPaint(String paintKey) {
        Paint paint = getResourceProvider() != null ? getResourceProvider().getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public Drawable getThemedDrawable(String key) {
        return Theme.getThemeDrawable(key);
    }

    /**
     * @return If this fragment should have light status bar even if it's disabled in debug settings
     */
    public boolean hasForceLightStatusBar() {
        return false;
    }

    public int getNavigationBarColor() {
        int color = Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider());
        if (sheetsStack != null) {
            for (int i = 0; i < sheetsStack.size(); ++i) {
                AttachedSheet sheet = sheetsStack.get(i);
                if (sheet.attachedToParent()) {
                    color = sheet.getNavigationBarColor(color);
                }
            }
        }
        return color;
    }

    public void setNavigationBarColor(int color) {
        if (isSupportEdgeToEdge()) {
            return;
        }

        Activity activity = getParentActivity();
        if (activity instanceof LaunchActivity) {
            LaunchActivity launchActivity = (LaunchActivity) activity;
            launchActivity.setNavigationBarColor(color);
        } else {
            if (activity != null) {
                Window window = activity.getWindow();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && window != null && window.getNavigationBarColor() != color) {
                    // window.setNavigationBarColor(color);
                }
            }
        }

        final float brightness = AndroidUtilities.computePerceivedBrightness(color);
        AndroidUtilities.setLightNavigationBar(activity, brightness >= 0.721f);

        if (parentLayout != null) {
            parentLayout.setNavigationBarColor(color);
        }
    }

    public boolean isBeginToShow() {
        return fragmentBeginToShow;
    }

    private void setParentDialog(Dialog dialog) {
        parentDialog = dialog;
    }

    public Theme.ResourcesProvider getResourceProvider() {
        return resourceProvider;
    }

    protected boolean allowPresentFragment() {
        return true;
    }

    public boolean isRemovingFromStack() {
        return removingFromStack;
    }

    public void setRemovingFromStack(boolean b) {
        removingFromStack = b;
    }

    public boolean isLightStatusBar() {
        if (getLastStoryViewer() != null && getLastStoryViewer().isShown()) {
            return false;
        }
        if (hasForceLightStatusBar() && !Theme.getCurrentTheme().isDark()) {
            return true;
        }
        Theme.ResourcesProvider resourcesProvider = getResourceProvider();
        int color;
        int key = Theme.key_actionBarDefault;
        if (actionBar != null && actionBar.isActionModeShowed()) {
            key = Theme.key_actionBarActionModeDefault;
        }
        if (resourcesProvider != null) {
            color = resourcesProvider.getColorOrDefault(key);
        } else {
            color = Theme.getColor(key, null, true);
        }
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    public void setPreviewOpenedProgress(float progress) {

    }

    public void setPreviewReplaceProgress(float progress) {

    }

    public boolean closeLastFragment() {
        return false;
    }

    public void setPreviewDelegate(PreviewDelegate previewDelegate) {
        this.previewDelegate = previewDelegate;
    }

    public void resetFragment() {
        if (isFinished) {
            clearViews();
            isFinished = false;
            finishing = false;
        }
    }

    public void setResourceProvider(Theme.ResourcesProvider resourceProvider) {
        this.resourceProvider = resourceProvider;
    }

    public void onFragmentClosed() {

    }

    public void attachSheets(ActionBarLayout.LayoutContainer parentLayout) {
        if (sheetsStack != null) {
            for (int i = 0; i < sheetsStack.size(); ++i) {
                AttachedSheet sheet = sheetsStack.get(i);
                if (sheet != null && sheet.attachedToParent()) {
                    AndroidUtilities.removeFromParent(sheet.getWindowView());
                    parentLayout.addView(sheet.getWindowView());
                }
            }
        }
    }

    public void detachSheets() {
        if (sheetsStack != null) {
            for (int i = 0; i < sheetsStack.size(); ++i) {
                AttachedSheet sheet = sheetsStack.get(i);
                if (sheet != null && sheet.attachedToParent()) {
                    AndroidUtilities.removeFromParent(sheet.getWindowView());
                }
            }
        }
    }

    public boolean isStoryViewer(View child) {
        if (sheetsStack != null) {
            for (int i = 0; i < sheetsStack.size(); ++i) {
                AttachedSheet sheet = sheetsStack.get(i);
                if (sheet instanceof StoryViewer && child == sheet.getWindowView()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isBotView(View child) {
        if (sheetsStack != null) {
            for (int i = 0; i < sheetsStack.size(); ++i) {
                AttachedSheet sheet = sheetsStack.get(i);
                if (sheet instanceof BotWebViewAttachedSheet && child == sheet.getWindowView()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setKeyboardHeightFromParent(int keyboardHeight) {
        if (sheetsStack != null) {
            for (int i = 0; i < sheetsStack.size(); ++i) {
                AttachedSheet storyViewer = sheetsStack.get(i);
                if (storyViewer != null) {
                    storyViewer.setKeyboardHeightFromParent(keyboardHeight);
                }
            }
        }
    }

    public interface PreviewDelegate {
        void finishFragment();
    }

    public StoryViewer getOrCreateStoryViewer() {
        if (sheetsStack == null) {
            sheetsStack = new ArrayList<>();
        }
        StoryViewer storyViewer = null;
        if (!sheetsStack.isEmpty() && sheetsStack.get(sheetsStack.size() - 1) instanceof StoryViewer) {
            storyViewer = (StoryViewer) sheetsStack.get(sheetsStack.size() - 1);
        }
        if (storyViewer == null) {
            storyViewer = new StoryViewer(this);
            if (parentLayout != null && parentLayout.isSheet()) {
                storyViewer.fromBottomSheet = true;
            }
            sheetsStack.add(storyViewer);
            updateSheetsVisibility();
        }
        return storyViewer;
    }

    public StoryViewer getOrCreateStoryViewer(int account) {
        if (sheetsStack == null) {
            sheetsStack = new ArrayList<>();
        }
        StoryViewer storyViewer = null;
        if (!sheetsStack.isEmpty() && sheetsStack.get(sheetsStack.size() - 1) instanceof StoryViewer) {
            storyViewer = (StoryViewer) sheetsStack.get(sheetsStack.size() - 1);
        }
        if (storyViewer != null && storyViewer.currentAccount != account) {
            storyViewer.close(true);
            removeSheet(storyViewer);
            storyViewer = null;
        }
        if (storyViewer == null) {
            storyViewer = new StoryViewer(this);
            if (parentLayout != null && parentLayout.isSheet()) {
                storyViewer.fromBottomSheet = true;
            }
            sheetsStack.add(storyViewer);
            updateSheetsVisibility();
        }
        return storyViewer;
    }


    public void setTitleOverlayTextIfActionBarAttached(String title, int titleId, Runnable action) {
        if (actionBar != null && actionBar.shouldAddToContainer()) {
            setTitleOverlayText(title, titleId, action);
        }
    }

    public void setTitleOverlayText(String title, int titleId, Runnable action) {
        if (actionBar != null) {
            actionBar.setTitleOverlayText(title, titleId, action);
        }
    }

    public void removeSheet(BaseFragment.AttachedSheet sheet) {
        if (sheetsStack == null) return;
        sheetsStack.remove(sheet);
        updateSheetsVisibility();
    }

    public void addSheet(BaseFragment.AttachedSheet sheet) {
        if (sheetsStack == null) {
            sheetsStack = new ArrayList<>();
        }
        StoryViewer storyViewer = getLastStoryViewer();
        if (storyViewer != null) {
            storyViewer.listenToAttachedSheet(sheet);
        }
        sheetsStack.add(sheet);
        updateSheetsVisibility();
    }

    public StoryViewer createOverlayStoryViewer() {
        if (sheetsStack == null) {
            sheetsStack = new ArrayList<>();
        }
        StoryViewer storyViewer = new StoryViewer(this);
        if (parentLayout != null && parentLayout.isSheet()) {
            storyViewer.fromBottomSheet = true;
        }
        sheetsStack.add(storyViewer);
        updateSheetsVisibility();
        return storyViewer;
    }

    public ArticleViewer getArticleViewer() {
        if (getLastSheet() instanceof ArticleViewer.Sheet && getLastSheet().isShown()) {
            return ((ArticleViewer.Sheet) getLastSheet()).getArticleViewer();
        }
        if (
            parentLayout instanceof ActionBarLayout &&
            ((ActionBarLayout) parentLayout).getSheetFragment(false) != null &&
            ((ActionBarLayout) parentLayout).getSheetFragment(false).getLastSheet() instanceof ArticleViewer.Sheet
        ) {
            ArticleViewer.Sheet lastSheet = (ArticleViewer.Sheet) ((ActionBarLayout) parentLayout).getSheetFragment(false).getLastSheet();
            if (lastSheet.isShown()) {
                return lastSheet.getArticleViewer();
            }
        }
        return null;
    }

    public ArticleViewer createArticleViewer(boolean forceRecreate) {
        if (sheetsStack == null) {
            sheetsStack = new ArrayList<>();
        }
        if (!forceRecreate) {
            if (getLastSheet() instanceof ArticleViewer.Sheet && getLastSheet().isShown()) {
                return ((ArticleViewer.Sheet) getLastSheet()).getArticleViewer();
            }
            if (
                parentLayout instanceof ActionBarLayout &&
                ((ActionBarLayout) parentLayout).getSheetFragment(false) != null &&
                ((ActionBarLayout) parentLayout).getSheetFragment(false).getLastSheet() instanceof ArticleViewer.Sheet
            ) {
                ArticleViewer.Sheet lastSheet = (ArticleViewer.Sheet) ((ActionBarLayout) parentLayout).getSheetFragment(false).getLastSheet();
                if (lastSheet.isShown()) {
                    return lastSheet.getArticleViewer();
                }
            }
        }
        ArticleViewer articleViewer = ArticleViewer.makeSheet(this);
        addSheet(articleViewer.sheet);
        BottomSheetTabDialog.checkSheet(articleViewer.sheet);
        return articleViewer;
    }

    public void onBottomSheetCreated() {

    }

    public static class BottomSheetParams {
        public boolean transitionFromLeft;
        public boolean allowNestedScroll;
        public Runnable onDismiss;
        public Runnable onOpenAnimationFinished;
        public Runnable onPreFinished;
        public boolean occupyNavigationBar;
    }

    public EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return isSupportEdgeToEdge() ?
            EdgeToEdgeSupportMode.VERTICAL :
            EdgeToEdgeSupportMode.NONE;
    }

    @Deprecated
    public boolean isSupportEdgeToEdge() {
        // warn: overridden method must return a constant
        return false;
    }

    public boolean drawEdgeNavigationBar() {
        return isSupportEdgeToEdge();
    }

    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat windowInsets) {
        final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.statusBars());
        onInsets(insets.left, insets.top, insets.right, bottomInset = insets.bottom);
        return WindowInsetsCompat.CONSUMED;
    }

    private int bottomInset;
    public int getBottomInset() {
        return bottomInset;
    }

    public void onInsets(int left, int top, int right, int bottom) {

    }


    private Bulletin.Delegate bulletinDelegate;

    public void setBulletinDelegate(Bulletin.Delegate bulletinDelegate) {
        this.bulletinDelegate = bulletinDelegate;
    }

    public Bulletin.Delegate getBulletinDelegate() {
        return bulletinDelegate;
    }


    protected void dumpCanvas() {
        AndroidUtilities.dumpCanvas(fragmentView);
    }
}
