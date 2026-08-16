package com.Huanghun.outfit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** 原创本地装扮的实时 Canvas 效果。所有画面由代码绘制，不包含第三方受保护素材。 */
public final class HuanghunOutfitVisuals {

    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF RECT = new RectF();

    static {
        STROKE.setStyle(Paint.Style.STROKE);
        STROKE.setStrokeCap(Paint.Cap.ROUND);
        STROKE.setStrokeJoin(Paint.Join.ROUND);
    }

    private HuanghunOutfitVisuals() {
    }

    public static void drawPreview(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height) {
        if (item == null) {
            return;
        }
        if (HuanghunOutfitConfig.CATEGORY_BUBBLE.equals(item.category)) {
            drawBubble(canvas, item, progress, width, height);
        } else if (HuanghunOutfitConfig.CATEGORY_AVATAR.equals(item.category)) {
            drawAvatarFrame(canvas, item, progress, width, height);
        } else if (HuanghunOutfitConfig.CATEGORY_CALL.equals(item.category)) {
            drawCallScreen(canvas, item, progress, width, height);
        } else if (HuanghunOutfitConfig.CATEGORY_JOIN.equals(item.category)) {
            drawJoinEffect(canvas, item, progress, width, height);
        }
    }

    public static void drawBubble(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height) {
        canvas.save();
        float l = width * .10f;
        float t = height * .30f;
        float r = width * .90f;
        float b = height * .70f;
        float radius = Math.min(width, height) * .13f;
        PAINT.setShader(new LinearGradient(l, t, r, b, new int[]{item.primary, item.secondary, item.accent}, null, Shader.TileMode.CLAMP));
        RECT.set(l, t, r, b);
        canvas.drawRoundRect(RECT, radius, radius, PAINT);
        PAINT.setShader(null);

        STROKE.setColor(withAlpha(Color.WHITE, 160));
        STROKE.setStrokeWidth(Math.max(2f, width * .014f));
        canvas.drawRoundRect(RECT, radius, radius, STROKE);

        float shineX = l - width * .30f + (width * 1.6f) * progress;
        PAINT.setColor(withAlpha(Color.WHITE, 82));
        canvas.save();
        canvas.clipRect(RECT);
        canvas.rotate(-24f, shineX, height / 2f);
        canvas.drawRect(shineX, t - height, shineX + width * .15f, b + height, PAINT);
        canvas.restore();

        for (int i = 0; i < 6; i++) {
            float p = fract(progress + i * .173f + item.variant * .031f);
            float x = l + (r - l) * p;
            float y = t + (b - t) * fract(i * .37f + progress * .7f);
            PAINT.setColor(withAlpha(Color.WHITE, 100));
            canvas.drawCircle(x, y, 1.5f + (i % 3), PAINT);
        }
        PAINT.setColor(Color.WHITE);
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(Math.max(13f, width * .13f));
        canvas.drawText("黄昏消息", width / 2f, height / 2f + PAINT.getTextSize() * .35f, PAINT);
        canvas.restore();
    }

    public static void drawAvatarFrame(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height) {
        canvas.save();
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = Math.min(width, height) * .25f;
        PAINT.setShader(new LinearGradient(0, 0, width, height, item.primary, item.secondary, Shader.TileMode.MIRROR));
        canvas.drawCircle(cx, cy, radius, PAINT);
        PAINT.setShader(null);
        PAINT.setColor(withAlpha(Color.BLACK, 95));
        canvas.drawCircle(cx, cy, radius * .76f, PAINT);
        PAINT.setColor(withAlpha(Color.WHITE, 210));
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(radius * .65f);
        canvas.drawText("H", cx, cy + radius * .23f, PAINT);

        float ringRadius = radius * (1.16f + .05f * (float) Math.sin(progress * Math.PI * 2f));
        STROKE.setStrokeWidth(radius * .14f);
        STROKE.setColor(item.accent);
        canvas.drawArc(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius, progress * 360f, 205f, false, STROKE);
        STROKE.setColor(item.primary);
        canvas.drawArc(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius, progress * -360f + 110f, 105f, false, STROKE);

        for (int i = 0; i < 9; i++) {
            double angle = (progress * Math.PI * 2d * (i % 2 == 0 ? 1 : -1)) + i * Math.PI * 2d / 9d;
            float orbit = ringRadius * (1.18f + (i % 2) * .12f);
            float x = cx + (float) Math.cos(angle) * orbit;
            float y = cy + (float) Math.sin(angle) * orbit;
            PAINT.setColor(i % 2 == 0 ? item.accent : item.secondary);
            canvas.drawCircle(x, y, radius * (.04f + (i % 3) * .012f), PAINT);
        }
        canvas.restore();
    }

    public static void drawCallScreen(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height) {
        canvas.save();
        PAINT.setShader(new LinearGradient(0, 0, width, height, new int[]{darken(item.primary, .45f), darken(item.secondary, .45f), darken(item.accent, .35f)}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, PAINT);
        PAINT.setShader(null);

        float cx = width / 2f;
        float cy = height * .37f;
        for (int i = 0; i < 3; i++) {
            float radius = Math.min(width, height) * (.20f + i * .09f + .015f * (float) Math.sin((progress + i * .18f) * Math.PI * 2));
            STROKE.setColor(withAlpha(i == 0 ? item.accent : item.secondary, 170 - i * 38));
            STROKE.setStrokeWidth(Math.max(2f, width * .012f));
            canvas.drawCircle(cx, cy, radius, STROKE);
        }
        PAINT.setColor(withAlpha(Color.WHITE, 225));
        canvas.drawCircle(cx, cy, Math.min(width, height) * .16f, PAINT);
        PAINT.setColor(darken(item.primary, .58f));
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(Math.max(16f, width * .11f));
        canvas.drawText("黄", cx, cy + PAINT.getTextSize() * .34f, PAINT);

        PAINT.setColor(Color.WHITE);
        PAINT.setTextSize(Math.max(12f, width * .075f));
        canvas.drawText("黄昏来电", cx, height * .64f, PAINT);
        PAINT.setColor(withAlpha(Color.WHITE, 175));
        PAINT.setTypeface(Typeface.DEFAULT);
        PAINT.setTextSize(Math.max(9f, width * .045f));
        canvas.drawText("本地视觉预览 · 点击试听", cx, height * .71f, PAINT);

        float y = height * .84f;
        PAINT.setColor(Color.rgb(252, 76, 99));
        canvas.drawCircle(width * .30f, y, Math.min(width, height) * .07f, PAINT);
        PAINT.setColor(Color.rgb(48, 209, 88));
        canvas.drawCircle(width * .70f, y, Math.min(width, height) * .07f, PAINT);
        PAINT.setColor(Color.WHITE);
        PAINT.setTextSize(Math.max(12f, width * .06f));
        canvas.drawText("×", width * .30f, y + PAINT.getTextSize() * .33f, PAINT);
        canvas.drawText("✓", width * .70f, y + PAINT.getTextSize() * .33f, PAINT);
        canvas.restore();
    }

    public static void drawJoinEffect(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height) {
        canvas.save();
        PAINT.setColor(darken(item.primary, .70f));
        canvas.drawRect(0, 0, width, height, PAINT);
        float cx = width * (.20f + .60f * easeOut(progress));
        float cy = height * .48f;
        for (int i = 0; i < 12; i++) {
            float p = fract(progress * (1.2f + (i % 3) * .1f) + i * .081f);
            float startX = -width * .1f + p * width * 1.18f;
            float y = height * (.12f + fract(i * .31f + progress * .18f) * .72f);
            STROKE.setColor(withAlpha(i % 2 == 0 ? item.secondary : item.accent, 80 + (i % 3) * 35));
            STROKE.setStrokeWidth(Math.max(1.5f, width * .009f));
            canvas.drawLine(startX - width * .18f, y, startX, y, STROKE);
        }
        PAINT.setShader(new LinearGradient(cx - width * .17f, cy - height * .17f, cx + width * .17f, cy + height * .17f, item.primary, item.accent, Shader.TileMode.MIRROR));
        canvas.drawCircle(cx, cy, Math.min(width, height) * .17f, PAINT);
        PAINT.setShader(null);
        STROKE.setStrokeWidth(Math.max(2f, width * .014f));
        STROKE.setColor(withAlpha(Color.WHITE, 175));
        canvas.drawCircle(cx, cy, Math.min(width, height) * .20f, STROKE);

        RECT.set(width * .12f, height * .73f, width * .88f, height * .86f);
        PAINT.setColor(withAlpha(Color.WHITE, 225));
        canvas.drawRoundRect(RECT, height * .05f, height * .05f, PAINT);
        PAINT.setColor(darken(item.primary, .62f));
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(Math.max(11f, width * .062f));
        canvas.drawText("黄昏 · 已进入群聊", width / 2f, height * .815f, PAINT);
        canvas.restore();
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float easeOut(float value) {
        float inverse = 1f - value;
        return 1f - inverse * inverse * inverse;
    }

    private static int darken(int color, float factor) {
        return Color.rgb((int) (Color.red(color) * factor), (int) (Color.green(color) * factor), (int) (Color.blue(color) * factor));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | ((Math.max(0, Math.min(alpha, 255))) << 24);
    }
}
