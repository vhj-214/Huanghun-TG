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

    public static void drawMessageBubbleOverlay(Canvas canvas, Rect bounds, boolean outgoing) {
        if (bounds == null || bounds.width() < AndroidUtilities.dp(40) || bounds.height() < AndroidUtilities.dp(22)) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        HuanghunOutfitConfig.OutfitItem item = selected(context, HuanghunOutfitConfig.CATEGORY_BUBBLE);
        if (item == null) {
            return;
        }
        float phase = (SystemClock.elapsedRealtime() % 2600L) / 2600f;
        RectF rect = new RectF(bounds.left + AndroidUtilities.dp(2), bounds.top + AndroidUtilities.dp(2), bounds.right - AndroidUtilities.dp(2), bounds.bottom - AndroidUtilities.dp(2));
        float radius = Math.min(AndroidUtilities.dp(16), Math.min(rect.width(), rect.height()) / 3f);
        BUBBLE_STROKE.setColor(withAlpha(item.accent, outgoing ? 145 : 105));
        BUBBLE_STROKE.setStrokeWidth(AndroidUtilities.dp(1.25f));
        canvas.drawRoundRect(rect, radius, radius, BUBBLE_STROKE);

        canvas.save();
        canvas.clipRect(rect);
        float sweep = rect.left - rect.width() * .3f + rect.width() * 1.65f * phase;
        BUBBLE_PAINT.setColor(withAlpha(Color.WHITE, outgoing ? 38 : 28));
        canvas.rotate(-22f, sweep, rect.centerY());
        canvas.drawRect(sweep, rect.top - rect.height(), sweep + AndroidUtilities.dp(8), rect.bottom + rect.height(), BUBBLE_PAINT);
        canvas.restore();
    }

    public static void attachAvatarPendant(ViewGroup container) {
        if (container == null) {
            return;
        }
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            if (container.getChildAt(i) instanceof AvatarPendantView) {
                container.removeViewAt(i);
            }
        }
        HuanghunOutfitConfig.OutfitItem item = selected(container.getContext(), HuanghunOutfitConfig.CATEGORY_AVATAR);
        if (item == null) {
            return;
        }
        AvatarPendantView view = new AvatarPendantView(container.getContext(), item);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(AndroidUtilities.dp(45), AndroidUtilities.dp(45), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        params.leftMargin = AndroidUtilities.dp(1);
        params.topMargin = AndroidUtilities.dp(1);
        container.addView(view, params);
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
        skin.setAlpha(.28f);
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
            if (last != null && now - last < 5000L) {
                return;
            }
            JOIN_PLAY_TIMES.put(key, now);
        }
        JoinEffectView overlay = new JoinEffectView(parent.getContext(), item);
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

    private static final class AvatarPendantView extends AnimatedOutfitView {
        AvatarPendantView(Context context, HuanghunOutfitConfig.OutfitItem item) {
            super(context, item, 2600L);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            HuanghunOutfitVisuals.drawAvatarFrame(canvas, item, progress, getWidth(), getHeight());
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
        JoinEffectView(Context context, HuanghunOutfitConfig.OutfitItem item) {
            super(context, item, 1750L);
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
            float fade = progress < .78f ? 1f : Math.max(0f, 1f - (progress - .78f) / .22f);
            canvas.save();
            canvas.scale(1f + progress * .06f, 1f + progress * .06f, getWidth() / 2f, getHeight() / 2f);
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (255 * fade));
            HuanghunOutfitVisuals.drawJoinEffect(canvas, item, progress, getWidth(), getHeight());
            canvas.restore();
            canvas.restore();
        }
    }
}
