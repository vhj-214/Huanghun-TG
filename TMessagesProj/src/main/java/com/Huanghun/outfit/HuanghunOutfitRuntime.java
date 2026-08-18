package com.Huanghun.outfit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.HashMap;

/**
 * 将已选择的黄昏装扮真正叠加到本地 UI。
 * 不改变 Telegram 的消息、通话信令或远端数据，仅改变当前设备上的绘制层。
 */
public final class HuanghunOutfitRuntime {
    private static final Paint BUBBLE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BUBBLE_STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final HashMap<Long, Long> JOIN_PLAY_TIMES = new HashMap<>();
    private static ToneGenerator localCallTone;
    private static boolean localCallToneRunning;
    private static int localCallToneType;
    private static final Runnable LOCAL_CALL_TONE_LOOP = new Runnable() {
        @Override
        public void run() {
            synchronized (HuanghunOutfitRuntime.class) {
                if (!localCallToneRunning || localCallTone == null) {
                    return;
                }
                try {
                    localCallTone.startTone(localCallToneType, 1450);
                } catch (Throwable ignore) {
                    localCallToneRunning = false;
                }
                if (localCallToneRunning) {
                    AndroidUtilities.runOnUIThread(this, 1600L);
                }
            }
        }
    };

    static {
        BUBBLE_STROKE.setStyle(Paint.Style.STROKE);
        BUBBLE_STROKE.setStrokeCap(Paint.Cap.ROUND);
    }

    private HuanghunOutfitRuntime() {
    }

    /** 每组首款为完整静态收藏款；其余四款均有独立主体循环，仅重绘消息背景层。 */
    public static boolean isMessageBubbleReplacementEnabled() {
        return selected(ApplicationLoader.applicationContext, HuanghunOutfitConfig.CATEGORY_BUBBLE) != null;
    }

    /**
     * 第一个液态玻璃款不再重绘额外外壳：它直接使用 Telegram 已为文字、文件和媒体算好的
     * 官方消息边界、圆角和尾巴，默认主题颜色本身提供轻微玻璃材质，因此双方消息都会自然适配。
     * 只有用户选择其他装扮时，才为我方消息启用自定义外壳覆盖层。
     */
    public static boolean shouldReplaceMessageBubble(boolean outgoing) {
        // 聊天区统一交给 Telegram 原生气泡 Drawable 绘制。这样文字、文件、媒体、转发和
        // 不同字体大小都会沿用官方测量、圆角和尾巴，不再在消息外侧叠加装饰壳层。
        return false;
    }

    public static boolean isDefaultLiquidGlassBubbleSelected() {
        HuanghunOutfitConfig.OutfitItem item = selected(ApplicationLoader.applicationContext, HuanghunOutfitConfig.CATEGORY_BUBBLE);
        return item != null && item.group == 0 && item.variant == 0;
    }

    /**
     * 消息列表中的每个可见单元格都以 48ms 频率独立重绘会严重拖慢 RecyclerView，
     * 因此聊天内气泡保持静态外观；预览与单独页面仍可展示动画，不影响正常消息滚动。
     */
    public static boolean shouldAnimateSelectedBubble() {
        return false;
    }

    public static void drawMessageBubbleOverlay(Canvas canvas, Rect bounds, boolean outgoing) {
        if (bounds == null || bounds.width() < AndroidUtilities.dp(40) || bounds.height() < AndroidUtilities.dp(22)) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        HuanghunOutfitConfig.OutfitItem item = selected(context, HuanghunOutfitConfig.CATEGORY_BUBBLE);
        if (item == null) {
            return;
        }
        float phase = (SystemClock.elapsedRealtime() % 3600L) / 3600f;
        RectF rect = new RectF(bounds.left, bounds.top, bounds.right, bounds.bottom);
        // 仅在 Telegram 原气泡背景层之后绘制。消息正文、图片、语音控件、时间及状态始终在此层之上。
        HuanghunOutfitVisuals.drawBubbleOverlay(canvas, item, phase, rect, outgoing);
    }


    public static synchronized void startLocalCallTone(Context context) {
        HuanghunOutfitConfig.OutfitItem item = selected(context, HuanghunOutfitConfig.CATEGORY_CALL);
        if (item == null || localCallToneRunning) {
            return;
        }
        try {
            int[] tones = new int[]{ToneGenerator.TONE_CDMA_ABBR_ALERT, ToneGenerator.TONE_PROP_BEEP2, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ToneGenerator.TONE_SUP_RINGTONE, ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_INTERGROUP};
            localCallToneType = tones[item.variant % tones.length];
            localCallTone = new ToneGenerator(AudioManager.STREAM_RING, 82);
            localCallToneRunning = true;
            AndroidUtilities.runOnUIThread(LOCAL_CALL_TONE_LOOP);
        } catch (Throwable ignore) {
            stopLocalCallTone();
        }
    }

    public static synchronized void stopLocalCallTone() {
        AndroidUtilities.cancelRunOnUIThread(LOCAL_CALL_TONE_LOOP);
        localCallToneRunning = false;
        if (localCallTone != null) {
            try {
                localCallTone.release();
            } catch (Throwable ignore) {
            }
            localCallTone = null;
        }
    }

    public static View createCallSkin(Context context) {
        HuanghunOutfitConfig.OutfitItem item = selected(context, HuanghunOutfitConfig.CATEGORY_CALL);
        if (item == null) {
            return null;
        }
        CallSkinView skin = new CallSkinView(context, item);
        skin.setAlpha(.74f);
        skin.setClickable(false);
        skin.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return skin;
    }

    public static void playJoinEffect(ViewGroup parent, int account, long dialogId) {
        if (parent == null) {
            return;
        }
        HuanghunOutfitConfig.OutfitItem item = selected(parent.getContext(), HuanghunOutfitConfig.CATEGORY_JOIN);
        if (item == null) {
            return;
        }
        long key = (((long) account) << 48) ^ dialogId;
        long now = SystemClock.elapsedRealtime();
        synchronized (JOIN_PLAY_TIMES) {
            Long last = JOIN_PLAY_TIMES.get(key);
            if (last != null && now - last < 12000L) {
                return;
            }
            JOIN_PLAY_TIMES.put(key, now);
        }
        TLRPC.Chat chat = dialogId < 0 ? MessagesController.getInstance(account).getChat(-dialogId) : null;
        String groupName = chat != null ? chat.title : "群聊";
        JoinEffectView overlay = new JoinEffectView(parent.getContext(), item, groupName);
        overlay.setClickable(false);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(overlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        overlay.playThenRemove(parent);
    }

    private static HuanghunOutfitConfig.OutfitItem selected(Context context, String category) {
        return HuanghunOutfitConfig.find(category, HuanghunOutfitConfig.getSelected(context, category));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | ((Math.max(0, Math.min(alpha, 255))) << 24);
    }

    private abstract static class AnimatedOutfitView extends View {
        final HuanghunOutfitConfig.OutfitItem item;
        final ValueAnimator animator;
        float progress;

        AnimatedOutfitView(Context context, HuanghunOutfitConfig.OutfitItem item, long duration) {
            super(context);
            this.item = item;
            setWillNotDraw(false);
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(duration);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            animator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            animator.cancel();
            super.onDetachedFromWindow();
        }
    }

    private static final class CallSkinView extends AnimatedOutfitView {
        CallSkinView(Context context, HuanghunOutfitConfig.OutfitItem item) {
            super(context, item, 3600L);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            HuanghunOutfitVisuals.drawCallScreen(canvas, item, progress, getWidth(), getHeight());
        }
    }

    private static final class JoinEffectView extends AnimatedOutfitView {
        private final String groupName;

        JoinEffectView(Context context, HuanghunOutfitConfig.OutfitItem item, String groupName) {
            super(context, item, 5800L);
            this.groupName = groupName;
        }

        void playThenRemove(ViewGroup parent) {
            animator.setRepeatCount(0);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (getParent() == parent) {
                        parent.removeView(JoinEffectView.this);
                    }
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            HuanghunOutfitVisuals.drawJoinEffect(canvas, item, progress, getWidth(), getHeight(), groupName);
        }
    }
}
