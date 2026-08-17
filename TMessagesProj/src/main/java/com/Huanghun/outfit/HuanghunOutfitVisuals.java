package com.Huanghun.outfit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * 黄昏本地装扮的原创绘制器。
 *
 * 每个主题族不仅改变配色，也改变气泡轮廓、来电主视觉和群聊入场载具；不使用任何
 * 第三方应用的素材、名称或标识。所有动画都由 Canvas 在本机实时绘制。
 */
public final class HuanghunOutfitVisuals {
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
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
        } else if (HuanghunOutfitConfig.CATEGORY_CALL.equals(item.category)) {
            drawCallScreen(canvas, item, progress, width, height);
        } else if (HuanghunOutfitConfig.CATEGORY_JOIN.equals(item.category)) {
            drawJoinEffect(canvas, item, progress, width, height, "示例群聊");
        }
    }

    /**
     * 在 Telegram 原气泡背景之后、消息文字之前绘制完整的本地气泡外观。
     *
     * 不再使用“默认圆角矩形加一条渐变线”的方案。每个主题族都拥有自己的壳层、角标、
     * 材质与低强度随动元素；中间保留干净的文字安全区，避免影响消息正文、时间与状态图标。
     */
    public static void drawBubbleOverlay(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, RectF bubble, boolean outgoing) {
        if (item == null || bubble == null || bubble.width() <= 0 || bubble.height() <= 0) {
            return;
        }
        canvas.save();
        drawPremiumBubble(canvas, item, progress, bubble, outgoing);
        canvas.restore();
    }

    private static void drawPremiumBubble(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, RectF rect, boolean outgoing) {
        final int family = item.group % 10;
        final float h = Math.max(1f, rect.height());
        final float corner = Math.max(10f, h * .26f);
        final boolean animated = item.variant == 1 || item.variant == 3 || item.variant == 4;
        final float visualProgress = animated ? progress : .18f;
        // 录屏中的文字区普遍为深色/高对比芯层。端部才使用高饱和材质与光效，
        // 这样白色消息文字、时间与状态图标不会被浅色渐变吞掉。
        final int corePrimary = darken(blend(item.primary, Color.BLACK, .52f), .56f);
        final int coreSecondary = darken(blend(item.secondary, Color.BLACK, .48f), .50f);
        final int coreAccent = darken(blend(item.accent, Color.BLACK, .42f), .48f);
        final int edgeColor = blend(item.accent, Color.WHITE, .52f);

        RECT.set(rect.left, rect.top, rect.right, rect.bottom);
        PAINT.setColor(withAlpha(Color.BLACK, 68));
        canvas.drawRoundRect(new RectF(rect.left, rect.top + h * .055f, rect.right, rect.bottom + h * .080f), corner, corner, PAINT);
        PAINT.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{corePrimary, coreSecondary, coreAccent}, null, Shader.TileMode.CLAMP));
        drawPremiumShell(canvas, rect, corner, family, visualProgress, PAINT);
        PAINT.setShader(null);
        drawBubbleTail(canvas, rect, item, outgoing, family);
        drawEndOrnaments(canvas, rect, item, visualProgress, family, animated);
        drawPremiumBubbleDetails(canvas, rect, item, visualProgress, family, corner, edgeColor);
    }

    private static void drawPremiumShell(Canvas canvas, RectF rect, float corner, int family, float progress, Paint paint) {
        switch (family) {
            case 0: // 赛博 HUD：两侧带接口凹槽的科技外壳。
                buildTechBubble(rect, Math.max(7f, rect.height() * .14f));
                canvas.drawPath(PATH, paint);
                break;
            case 1: // 镜面棱镜：上下错位的晶体切面。
                buildCrystalBubble(rect, Math.max(7f, rect.height() * .17f));
                canvas.drawPath(PATH, paint);
                break;
            case 2: // 机甲：明确的装甲切角，而不是普通圆角框。
            case 7: // 像素街机：同样采用方形剪影。
                buildCutCornerBubble(rect, Math.max(8f, rect.height() * .19f));
                canvas.drawPath(PATH, paint);
                break;
            case 3: // 漫画：气泡边缘带有受控的爆裂波峰。
                buildComicBubble(rect, Math.max(4f, rect.height() * .075f));
                canvas.drawPath(PATH, paint);
                break;
            case 5: // 果冻：柔软起伏的胶质边缘。
                buildJellyBubble(rect, progress);
                canvas.drawPath(PATH, paint);
                break;
            case 6: // 街头贴纸：纸张撕口、折角和不对称边缘。
                buildStickerBubble(rect, Math.max(6f, rect.height() * .15f));
                canvas.drawPath(PATH, paint);
                break;
            case 8: // 毛球/猫系：耳朵属于气泡壳层，而不是另外贴一张图。
                canvas.drawRoundRect(rect, corner, corner, paint);
                float ear = rect.height() * .18f;
                PATH.reset();
                PATH.moveTo(rect.left + rect.width() * .17f, rect.top + ear * .30f);
                PATH.lineTo(rect.left + rect.width() * .28f, rect.top - ear * 1.35f);
                PATH.lineTo(rect.left + rect.width() * .40f, rect.top + ear * .30f);
                PATH.close();
                canvas.drawPath(PATH, paint);
                PATH.reset();
                PATH.moveTo(rect.right - rect.width() * .40f, rect.top + ear * .30f);
                PATH.lineTo(rect.right - rect.width() * .28f, rect.top - ear * 1.35f);
                PATH.lineTo(rect.right - rect.width() * .17f, rect.top + ear * .30f);
                PATH.close();
                canvas.drawPath(PATH, paint);
                break;
            case 9: // 国风：扇面弧线形成非矩形轮廓。
                buildFanBubble(rect, progress);
                canvas.drawPath(PATH, paint);
                break;
            default:
                canvas.drawRoundRect(rect, corner, corner, paint);
                break;
        }
    }

    private static void drawBubbleTail(Canvas canvas, RectF rect, HuanghunOutfitConfig.OutfitItem item, boolean outgoing, int family) {
        float h = rect.height();
        float y = rect.bottom - h * .30f;
        PAINT.setColor(darken(blend(item.secondary, Color.BLACK, .46f), .54f));
        PATH.reset();
        if (outgoing) {
            PATH.moveTo(rect.right - h * .16f, y);
            PATH.lineTo(rect.right + h * .14f, y + h * .17f);
            PATH.lineTo(rect.right - h * .08f, y + h * .34f);
        } else {
            PATH.moveTo(rect.left + h * .16f, y);
            PATH.lineTo(rect.left - h * .14f, y + h * .17f);
            PATH.lineTo(rect.left + h * .08f, y + h * .34f);
        }
        PATH.close();
        canvas.drawPath(PATH, PAINT);
        if (family == 2 || family == 7) {
            STROKE.setStrokeWidth(Math.max(1f, h * .032f));
            STROKE.setColor(withAlpha(darken(item.accent, .15f), 155));
            canvas.drawPath(PATH, STROKE);
        }
    }

    /**
     * 主题挂件只占气泡两端，中心 55%–65% 始终留作消息文字、时间和状态的安全区。
     * 所有对象均为本地原创几何绘制，绝不使用第三方气泡资产。
     */
    private static void drawEndOrnaments(Canvas canvas, RectF rect, HuanghunOutfitConfig.OutfitItem item, float progress, int family, boolean animated) {
        float h = rect.height();
        float w = rect.width();
        float left = rect.left + w * .12f;
        float right = rect.right - w * .12f;
        float cy = rect.centerY();
        int bright = blend(item.accent, Color.WHITE, .40f);
        int dim = darken(item.primary, .66f);
        switch (family) {
            case 0: // 赛博机库：左右发动机鳍片与能量窗。
                drawMechanicalFin(canvas, left, cy, h, dim, bright, false, progress, animated);
                drawMechanicalFin(canvas, right, cy, h, dim, bright, true, progress, animated);
                break;
            case 1: // 镜面星翼：晶体羽片从两端向中部收束。
                drawCrystalWing(canvas, left, cy, h, item.secondary, bright, false, progress, animated);
                drawCrystalWing(canvas, right, cy, h, item.secondary, bright, true, progress, animated);
                break;
            case 2: // 机甲护翼：有厚度的装甲板与中央能量栓。
                drawArmorWing(canvas, left, cy, h, item.primary, bright, false, progress, animated);
                drawArmorWing(canvas, right, cy, h, item.primary, bright, true, progress, animated);
                break;
            case 3: // 暗夜魔翼：不对称尖刺与红紫能量核。
                drawDemonWing(canvas, left, cy, h, item.secondary, item.accent, false, progress, animated);
                drawDemonWing(canvas, right, cy, h, item.secondary, item.accent, true, progress, animated);
                break;
            case 4: // 星云圣翼：光羽、星点与能量核。
                drawLightWing(canvas, left, cy, h, item.primary, bright, false, progress, animated);
                drawLightWing(canvas, right, cy, h, item.primary, bright, true, progress, animated);
                break;
            case 5: // 糖晶软胶：透明水滴和糖果封口。
                PAINT.setColor(withAlpha(bright, 210));
                canvas.drawCircle(left, rect.top + h * .16f, h * .11f, PAINT);
                canvas.drawCircle(right, rect.bottom - h * .18f, h * .13f, PAINT);
                PAINT.setColor(withAlpha(Color.WHITE, animated ? 165 : 115));
                canvas.drawCircle(left + h * .035f, rect.top + h * .13f, h * .035f, PAINT);
                break;
            case 6: // 街头贴纸：撕纸星章和喷漆点。
                drawStickerStar(canvas, left, cy, h * .19f, item.accent, progress, animated);
                drawStickerStar(canvas, right, cy, h * .15f, bright, progress + .18f, animated);
                break;
            case 7: // 像素街机：像素门框与两侧控制键。
                float px = Math.max(2f, h * .055f);
                PAINT.setColor(withAlpha(bright, 230));
                for (int i = 0; i < 4; i++) {
                    float y = rect.top + h * (.18f + i * .16f);
                    canvas.drawRect(left - px * 1.5f, y, left + px * .5f, y + px * 2f, PAINT);
                    canvas.drawRect(right - px * .5f, y, right + px * 1.5f, y + px * 2f, PAINT);
                }
                break;
            case 8: // 软萌动物：保留猫耳壳层，补充角落腮红与小星。
                PAINT.setColor(withAlpha(item.accent, 180));
                canvas.drawCircle(left, rect.bottom - h * .16f, h * .075f, PAINT);
                canvas.drawCircle(right, rect.bottom - h * .16f, h * .075f, PAINT);
                break;
            default: // 国风：折扇端头与祥云悬饰。
                drawFanOrnament(canvas, left, cy, h, item.primary, bright, false, progress, animated);
                drawFanOrnament(canvas, right, cy, h, item.primary, bright, true, progress, animated);
                break;
        }
    }

    private static void drawMechanicalFin(Canvas canvas, float x, float cy, float h, int base, int glow, boolean mirror, float p, boolean animated) {
        float d = mirror ? -1f : 1f;
        PAINT.setColor(withAlpha(base, 245));
        PATH.reset();
        PATH.moveTo(x, cy - h * .22f); PATH.lineTo(x + d * h * .34f, cy - h * .46f);
        PATH.lineTo(x + d * h * .49f, cy - h * .10f); PATH.lineTo(x + d * h * .30f, cy + h * .26f);
        PATH.lineTo(x, cy + h * .18f); PATH.close(); canvas.drawPath(PATH, PAINT);
        STROKE.setStrokeWidth(Math.max(1f, h * .026f)); STROKE.setColor(withAlpha(glow, 215)); canvas.drawPath(PATH, STROKE);
        PAINT.setColor(withAlpha(glow, animated ? 205 : 130));
        canvas.drawRoundRect(new RectF(x + d * h * .08f - (mirror ? h * .17f : 0), cy - h * .06f, x + d * h * .25f + (mirror ? h * .17f : 0), cy + h * .06f), h * .03f, h * .03f, PAINT);
    }

    private static void drawCrystalWing(Canvas canvas, float x, float cy, float h, int base, int glow, boolean mirror, float p, boolean animated) {
        float d = mirror ? -1f : 1f;
        for (int i = 0; i < 3; i++) {
            float y = cy + (i - 1) * h * .16f;
            float length = h * (.24f + i * .075f);
            PAINT.setColor(withAlpha(i == 1 ? glow : base, 215 - i * 25));
            PATH.reset(); PATH.moveTo(x, y); PATH.lineTo(x + d * length, y - h * .12f);
            PATH.lineTo(x + d * (length + h * .10f), y + h * .08f); PATH.lineTo(x + d * h * .07f, y + h * .13f); PATH.close();
            canvas.drawPath(PATH, PAINT);
            STROKE.setStrokeWidth(Math.max(1f, h * .018f)); STROKE.setColor(withAlpha(Color.WHITE, animated ? 190 : 115)); canvas.drawPath(PATH, STROKE);
        }
    }

    private static void drawArmorWing(Canvas canvas, float x, float cy, float h, int base, int glow, boolean mirror, float p, boolean animated) {
        float d = mirror ? -1f : 1f;
        for (int i = 0; i < 3; i++) {
            float y = cy + (i - 1) * h * .18f;
            PAINT.setColor(withAlpha(darken(base, .72f + i * .06f), 250));
            RECT.set(Math.min(x, x + d * h * (.26f + i * .05f)), y - h * .075f, Math.max(x, x + d * h * (.26f + i * .05f)), y + h * .075f);
            canvas.drawRoundRect(RECT, h * .035f, h * .035f, PAINT);
            STROKE.setStrokeWidth(Math.max(1f, h * .02f)); STROKE.setColor(withAlpha(glow, 190)); canvas.drawRoundRect(RECT, h * .035f, h * .035f, STROKE);
        }
        PAINT.setColor(withAlpha(glow, animated ? 235 : 150)); canvas.drawCircle(x + d * h * .09f, cy, h * .07f, PAINT);
    }

    private static void drawDemonWing(Canvas canvas, float x, float cy, float h, int base, int glow, boolean mirror, float p, boolean animated) {
        float d = mirror ? -1f : 1f;
        PAINT.setColor(withAlpha(darken(base, .62f), 248));
        PATH.reset(); PATH.moveTo(x, cy); PATH.lineTo(x + d * h * .45f, cy - h * .52f); PATH.lineTo(x + d * h * .29f, cy - h * .05f);
        PATH.lineTo(x + d * h * .59f, cy + h * .08f); PATH.lineTo(x + d * h * .30f, cy + h * .43f); PATH.close(); canvas.drawPath(PATH, PAINT);
        STROKE.setStrokeWidth(Math.max(1f, h * .025f)); STROKE.setColor(withAlpha(glow, 205)); canvas.drawPath(PATH, STROKE);
        PAINT.setColor(withAlpha(glow, animated ? 210 : 125)); canvas.drawCircle(x + d * h * .18f, cy - h * .05f, h * .07f, PAINT);
    }

    private static void drawLightWing(Canvas canvas, float x, float cy, float h, int base, int glow, boolean mirror, float p, boolean animated) {
        float d = mirror ? -1f : 1f;
        for (int i = 0; i < 4; i++) {
            float y = cy + (i - 1.5f) * h * .13f;
            PAINT.setColor(withAlpha(i % 2 == 0 ? glow : base, 210));
            PATH.reset(); PATH.moveTo(x, y); PATH.quadTo(x + d * h * .20f, y - h * .13f, x + d * h * (.35f + i * .04f), y + h * .02f);
            PATH.quadTo(x + d * h * .17f, y + h * .15f, x, y + h * .06f); PATH.close(); canvas.drawPath(PATH, PAINT);
        }
        PAINT.setColor(withAlpha(Color.WHITE, animated ? 200 : 110)); canvas.drawCircle(x + d * h * .16f, cy, h * (.055f + (animated ? .018f * (float) Math.sin(p * Math.PI * 2f) : 0f)), PAINT);
    }

    private static void drawStickerStar(Canvas canvas, float cx, float cy, float size, int color, float p, boolean animated) {
        PAINT.setColor(withAlpha(color, 220)); PATH.reset();
        for (int i = 0; i < 10; i++) { float a = (float) (-Math.PI / 2d + i * Math.PI / 5d); float r = i % 2 == 0 ? size : size * .45f; float x = cx + (float) Math.cos(a) * r; float y = cy + (float) Math.sin(a) * r; if (i == 0) PATH.moveTo(x, y); else PATH.lineTo(x, y); }
        PATH.close(); canvas.save(); if (animated) canvas.rotate((float) Math.sin(p * Math.PI * 2d) * 7f, cx, cy); canvas.drawPath(PATH, PAINT); canvas.restore();
    }

    private static void drawFanOrnament(Canvas canvas, float x, float cy, float h, int base, int glow, boolean mirror, float p, boolean animated) {
        float d = mirror ? -1f : 1f; PAINT.setColor(withAlpha(base, 225));
        PATH.reset(); PATH.moveTo(x, cy + h * .18f); PATH.lineTo(x + d * h * .37f, cy - h * .28f); PATH.lineTo(x + d * h * .42f, cy + h * .24f); PATH.close(); canvas.drawPath(PATH, PAINT);
        STROKE.setStrokeWidth(Math.max(1f, h * .02f)); STROKE.setColor(withAlpha(glow, 200)); canvas.drawPath(PATH, STROKE);
        PAINT.setColor(withAlpha(glow, animated ? 190 : 120)); canvas.drawCircle(x + d * h * .18f, cy + h * .05f, h * .055f, PAINT);
    }

    private static void drawPremiumBubbleDetails(Canvas canvas, RectF rect, HuanghunOutfitConfig.OutfitItem item, float progress, int family, float corner, int edgeColor) {
        final float h = rect.height();
        final float w = rect.width();
        STROKE.setStrokeWidth(Math.max(1.5f, h * .038f));
        STROKE.setColor(withAlpha(edgeColor, 190));
        // drawBubbleTail 会复用 PATH；描边必须重新以气泡壳层为准，不能误描尾巴。
        drawPremiumShell(canvas, rect, corner, family, progress, STROKE);
        switch (family) {
            case 0: // 赛博 HUD
                PAINT.setColor(withAlpha(item.accent, 165));
                RECT.set(rect.right - w * .22f, rect.top + h * .11f, rect.right - w * .08f, rect.top + h * .23f);
                canvas.drawRoundRect(RECT, h * .05f, h * .05f, PAINT);
                for (int i = 0; i < 3; i++) {
                    PAINT.setColor(i == ((int) (progress * 5f) % 3) ? Color.WHITE : withAlpha(item.secondary, 175));
                    canvas.drawCircle(rect.left + w * (.11f + i * .055f), rect.bottom - h * .15f, h * .035f, PAINT);
                }
                drawCornerGlow(canvas, rect, item.accent, progress, true);
                break;
            case 1: // 镜面棱镜
                PAINT.setColor(withAlpha(Color.WHITE, 130));
                PATH.reset();
                PATH.moveTo(rect.left + w * .10f, rect.top + h * .12f);
                PATH.lineTo(rect.left + w * .25f, rect.top + h * .12f);
                PATH.lineTo(rect.left + w * .17f, rect.top + h * .36f);
                PATH.close();
                canvas.drawPath(PATH, PAINT);
                PAINT.setColor(withAlpha(item.accent, 125));
                canvas.drawCircle(rect.right - h * .22f, rect.bottom - h * .23f, h * (.075f + .018f * (float) Math.sin(progress * Math.PI * 2f)), PAINT);
                break;
            case 2: // 机甲甲片
                STROKE.setStrokeWidth(Math.max(1f, h * .025f));
                STROKE.setColor(withAlpha(item.accent, 185));
                canvas.drawLine(rect.left + w * .12f, rect.top + h * .18f, rect.left + w * .38f, rect.top + h * .18f, STROKE);
                canvas.drawLine(rect.right - w * .34f, rect.bottom - h * .17f, rect.right - w * .12f, rect.bottom - h * .17f, STROKE);
                for (int i = 0; i < 4; i++) {
                    PAINT.setColor(i == ((int) (progress * 6f) % 4) ? Color.WHITE : withAlpha(item.accent, 145));
                    canvas.drawRect(rect.right - w * (.10f + i * .035f), rect.top + h * .14f, rect.right - w * (.08f + i * .035f), rect.top + h * .22f, PAINT);
                }
                break;
            case 3: // 暗夜漫画分镜
                drawComicBurst(canvas, rect, item, progress);
                break;
            case 4: // 星云玻璃
                PAINT.setColor(withAlpha(item.accent, 118));
                canvas.drawCircle(rect.right - h * .27f, rect.top + h * .30f, h * .16f, PAINT);
                PAINT.setColor(withAlpha(Color.WHITE, 170));
                canvas.drawCircle(rect.right - h * .33f, rect.top + h * .24f, h * .045f, PAINT);
                for (int i = 0; i < 5; i++) {
                    float x = rect.left + w * (.10f + fract(progress * .24f + i * .19f) * .68f);
                    float y = rect.bottom - h * (.14f + (i % 3) * .12f);
                    PAINT.setColor(withAlpha(i % 2 == 0 ? Color.WHITE : item.accent, 150));
                    canvas.drawCircle(x, y, h * .025f, PAINT);
                }
                break;
            case 5: // 果冻糖晶
                STROKE.setColor(withAlpha(Color.WHITE, 205));
                PAINT.setColor(withAlpha(Color.WHITE, 115));
                canvas.drawOval(new RectF(rect.left + w * .10f, rect.top + h * .12f, rect.left + w * .39f, rect.top + h * .28f), PAINT);
                for (int i = 0; i < 3; i++) {
                    PAINT.setColor(withAlpha(item.accent, 135));
                    canvas.drawCircle(rect.right - h * (.24f + i * .13f), rect.bottom - h * (.20f + (i % 2) * .10f), h * .055f, PAINT);
                }
                break;
            case 6: // 街头贴纸
                canvas.save();
                canvas.rotate(-3f + 6f * (float) Math.sin(progress * Math.PI * 2f), rect.centerX(), rect.centerY());
                STROKE.setColor(withAlpha(edgeColor, 205));
                drawPremiumShell(canvas, rect, corner, family, progress, STROKE);
                canvas.restore();
                drawStickerPatch(canvas, rect, item, progress);
                break;
            case 7: // 像素街机
                float px = Math.max(2f, h * .075f);
                for (int i = 0; i < 4; i++) {
                    PAINT.setColor(i == ((int) (progress * 8f) % 4) ? Color.WHITE : withAlpha(item.accent, 155));
                    canvas.drawRect(rect.left + w * (.12f + i * .07f), rect.top + h * .13f, rect.left + w * (.12f + i * .07f) + px, rect.top + h * .13f + px, PAINT);
                }
                PAINT.setColor(withAlpha(item.secondary, 160));
                canvas.drawCircle(rect.right - h * .25f, rect.bottom - h * .22f, h * .07f, PAINT);
                canvas.drawCircle(rect.right - h * .12f, rect.bottom - h * .31f, h * .045f, PAINT);
                break;
            case 8: // 毛球猫爪
                STROKE.setColor(withAlpha(edgeColor, 165));
                drawPremiumShell(canvas, rect, corner, family, progress, STROKE);
                drawPaw(canvas, rect.left + h * .25f, rect.bottom - h * .23f, h * .12f, item.accent);
                drawPaw(canvas, rect.right - h * .24f, rect.top + h * .25f, h * .09f, withAlpha(item.secondary, 185));
                break;
            default: // 国风扇面
                STROKE.setStrokeWidth(Math.max(1f, h * .022f));
                STROKE.setColor(withAlpha(item.accent, 150));
                for (int i = 0; i < 5; i++) {
                    canvas.drawLine(rect.centerX(), rect.bottom - h * .12f, rect.left + w * (.20f + i * .15f), rect.top + h * .20f, STROKE);
                }
                drawCloudCurl(canvas, rect.left + w * .13f, rect.top + h * .25f, h * .09f, item.secondary);
                break;
        }
    }

    private static void drawCornerGlow(Canvas canvas, RectF rect, int color, float progress, boolean right) {
        float h = rect.height();
        float x = right ? rect.right - h * .17f : rect.left + h * .17f;
        PAINT.setColor(withAlpha(color, 95));
        canvas.drawCircle(x, rect.top + h * (.45f + .10f * (float) Math.sin(progress * Math.PI * 2f)), h * .12f, PAINT);
    }

    private static void drawComicBurst(Canvas canvas, RectF rect, HuanghunOutfitConfig.OutfitItem item, float progress) {
        float cx = rect.right - rect.height() * .28f;
        float cy = rect.top + rect.height() * .34f;
        STROKE.setStrokeWidth(Math.max(1f, rect.height() * .027f));
        STROKE.setColor(withAlpha(item.accent, 175));
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2d / 7d + progress * .35d;
            canvas.drawLine(cx, cy, cx + (float) Math.cos(a) * rect.height() * .18f, cy + (float) Math.sin(a) * rect.height() * .18f, STROKE);
        }
    }

    private static void drawStickerPatch(Canvas canvas, RectF rect, HuanghunOutfitConfig.OutfitItem item, float progress) {
        float s = rect.height() * .15f;
        PAINT.setColor(withAlpha(item.accent, 195));
        PATH.reset();
        PATH.moveTo(rect.left + s, rect.top + s * .55f);
        PATH.lineTo(rect.left + s * 1.45f, rect.top + s);
        PATH.lineTo(rect.left + s * 2.05f, rect.top + s * .48f);
        PATH.lineTo(rect.left + s * 1.58f, rect.top + s * 1.10f);
        PATH.close();
        canvas.drawPath(PATH, PAINT);
        PAINT.setColor(withAlpha(Color.WHITE, 170));
        canvas.drawCircle(rect.left + rect.width() * (.70f + .06f * (float) Math.sin(progress * Math.PI * 2f)), rect.bottom - rect.height() * .20f, s * .26f, PAINT);
    }

    private static void drawPaw(Canvas canvas, float cx, float cy, float size, int color) {
        PAINT.setColor(color);
        canvas.drawCircle(cx, cy, size, PAINT);
        canvas.drawCircle(cx - size * .90f, cy - size * 1.05f, size * .44f, PAINT);
        canvas.drawCircle(cx, cy - size * 1.38f, size * .44f, PAINT);
        canvas.drawCircle(cx + size * .90f, cy - size * 1.05f, size * .44f, PAINT);
    }

    private static void drawCloudCurl(Canvas canvas, float cx, float cy, float size, int color) {
        STROKE.setColor(withAlpha(color, 165));
        STROKE.setStrokeWidth(Math.max(1f, size * .24f));
        canvas.drawArc(cx - size, cy - size * .55f, cx + size, cy + size * .55f, 25f, 210f, false, STROKE);
        canvas.drawArc(cx - size * .42f, cy - size * .15f, cx + size * 1.25f, cy + size * .90f, 205f, 150f, false, STROKE);
    }

    public static void drawBubble(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height) {
        canvas.save();
        float left = width * .10f;
        float top = height * .30f;
        float right = width * .90f;
        float bottom = height * .70f;
        RECT.set(left, top, right, bottom);
        drawBubbleOverlay(canvas, item, progress, RECT, true);
        PAINT.setColor(Color.WHITE);
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(Math.max(13f, width * .13f));
        canvas.drawText("黄昏消息", width / 2f, height / 2f + PAINT.getTextSize() * .35f, PAINT);
        canvas.restore();
    }

    private static void drawBubbleShell(Canvas canvas, RectF rect, float radius, int family, HuanghunOutfitConfig.OutfitItem item, float progress, Paint paint) {
        switch (family) {
            case 1: // 云团气泡
                canvas.drawRoundRect(rect, radius, radius, paint);
                float cloud = rect.height() * .28f;
                canvas.drawCircle(rect.left + cloud, rect.top + cloud, cloud, paint);
                canvas.drawCircle(rect.right - cloud * 1.15f, rect.bottom - cloud, cloud * .9f, paint);
                break;
            case 2: // 像素台阶气泡
                buildPixelBubble(rect, Math.max(4f, rect.height() * .16f));
                canvas.drawPath(PATH, paint);
                break;
            case 3: // 软胶波浪气泡
                buildJellyBubble(rect, progress);
                canvas.drawPath(PATH, paint);
                break;
            case 4: // 机甲切角气泡
                buildCutCornerBubble(rect, Math.max(7f, rect.height() * .22f));
                canvas.drawPath(PATH, paint);
                break;
            case 5: // 猫耳软糖气泡
                canvas.drawRoundRect(rect, radius, radius, paint);
                PATH.reset();
                PATH.moveTo(rect.left + rect.width() * .18f, rect.top + rect.height() * .12f);
                PATH.lineTo(rect.left + rect.width() * .30f, rect.top - rect.height() * .20f);
                PATH.lineTo(rect.left + rect.width() * .42f, rect.top + rect.height() * .12f);
                PATH.close();
                canvas.drawPath(PATH, paint);
                PATH.reset();
                PATH.moveTo(rect.right - rect.width() * .42f, rect.top + rect.height() * .12f);
                PATH.lineTo(rect.right - rect.width() * .30f, rect.top - rect.height() * .20f);
                PATH.lineTo(rect.right - rect.width() * .18f, rect.top + rect.height() * .12f);
                PATH.close();
                canvas.drawPath(PATH, paint);
                break;
            case 6: // 贴纸纸片气泡
                canvas.save();
                canvas.rotate((float) Math.sin(progress * Math.PI * 2f) * 1.4f, rect.centerX(), rect.centerY());
                canvas.drawRoundRect(rect, radius * .55f, radius * .55f, paint);
                canvas.restore();
                break;
            case 7: // 街机对话框
                canvas.drawRoundRect(rect, radius * .45f, radius * .45f, paint);
                break;
            case 8: // 明信片票根
                canvas.drawRoundRect(rect, radius * .35f, radius * .35f, paint);
                break;
            case 9: // 国风折扇轮廓
                buildFanBubble(rect, progress);
                canvas.drawPath(PATH, paint);
                break;
            default: // 霓虹胶囊
                canvas.drawRoundRect(rect, radius, radius, paint);
                break;
        }
    }

    private static void drawBubbleDecoration(Canvas canvas, RectF rect, float radius, int family, HuanghunOutfitConfig.OutfitItem item, float progress, boolean outgoing) {
        float thickness = Math.max(1.5f, rect.height() * .055f);
        STROKE.setStrokeWidth(thickness);
        STROKE.setColor(withAlpha(Color.WHITE, 195));
        switch (family) {
            case 0:
                canvas.drawRoundRect(rect, radius, radius, STROKE);
                drawShimmer(canvas, rect, progress, 0.20f);
                drawOrbitDots(canvas, rect, item.accent, progress, 5);
                break;
            case 1:
                STROKE.setColor(withAlpha(Color.WHITE, 170));
                canvas.drawRoundRect(rect, radius, radius, STROKE);
                for (int i = 0; i < 4; i++) {
                    float x = rect.left + rect.width() * (.16f + i * .23f);
                    float y = rect.top + rect.height() * (.26f + .12f * (float) Math.sin((progress + i * .19f) * Math.PI * 2));
                    PAINT.setColor(withAlpha(Color.WHITE, 150));
                    canvas.drawCircle(x, y, rect.height() * (.035f + (i % 2) * .012f), PAINT);
                }
                break;
            case 2:
                STROKE.setColor(withAlpha(Color.WHITE, 185));
                canvas.drawPath(PATH, STROKE);
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 5; col++) {
                        if ((row + col + item.variant) % 2 == 0) {
                            PAINT.setColor(withAlpha(item.accent, 150));
                            float side = Math.max(2f, rect.height() * .11f);
                            canvas.drawRect(rect.left + rect.width() * (.12f + col * .16f), rect.top + rect.height() * (.25f + row * .35f), rect.left + rect.width() * (.12f + col * .16f) + side, rect.top + rect.height() * (.25f + row * .35f) + side, PAINT);
                        }
                    }
                }
                break;
            case 3:
                STROKE.setColor(withAlpha(Color.WHITE, 185));
                canvas.drawPath(PATH, STROKE);
                for (int i = 0; i < 3; i++) {
                    float y = rect.top + rect.height() * (.26f + i * .23f);
                    STROKE.setStrokeWidth(Math.max(1f, rect.height() * .026f));
                    STROKE.setColor(withAlpha(Color.WHITE, 85 + i * 25));
                    canvas.drawArc(rect.left + rect.width() * (progress - .3f), y - rect.height() * .15f, rect.right + rect.width() * (progress - .3f), y + rect.height() * .15f, 180f, 180f, false, STROKE);
                }
                break;
            case 4:
                STROKE.setColor(withAlpha(item.accent, 230));
                canvas.drawPath(PATH, STROKE);
                STROKE.setStrokeWidth(Math.max(1f, rect.height() * .028f));
                STROKE.setColor(withAlpha(Color.WHITE, 145));
                canvas.drawLine(rect.left + rect.width() * .13f, rect.top + rect.height() * .22f, rect.right - rect.width() * .13f, rect.top + rect.height() * .22f, STROKE);
                for (int i = 0; i < 3; i++) {
                    PAINT.setColor(i == ((int) (progress * 5) % 3) ? Color.WHITE : withAlpha(item.accent, 150));
                    canvas.drawCircle(rect.right - rect.height() * (.28f + i * .11f), rect.bottom - rect.height() * .23f, rect.height() * .035f, PAINT);
                }
                break;
            case 5:
                STROKE.setColor(withAlpha(Color.WHITE, 185));
                canvas.drawRoundRect(rect, radius, radius, STROKE);
                PAINT.setColor(withAlpha(Color.WHITE, 220));
                float eyeY = rect.centerY() - rect.height() * .04f;
                canvas.drawCircle(rect.centerX() - rect.width() * .13f, eyeY, rect.height() * .045f, PAINT);
                canvas.drawCircle(rect.centerX() + rect.width() * .13f, eyeY, rect.height() * .045f, PAINT);
                STROKE.setStrokeWidth(Math.max(1f, rect.height() * .025f));
                canvas.drawLine(rect.centerX() - rect.width() * .08f, rect.centerY() + rect.height() * .10f, rect.centerX() + rect.width() * .08f, rect.centerY() + rect.height() * .10f, STROKE);
                break;
            case 6:
                STROKE.setColor(withAlpha(Color.WHITE, 210));
                canvas.drawRoundRect(rect, radius * .55f, radius * .55f, STROKE);
                PAINT.setColor(withAlpha(Color.WHITE, 115));
                canvas.drawRect(rect.left + rect.width() * .08f, rect.top + rect.height() * .08f, rect.right - rect.width() * .08f, rect.top + rect.height() * .17f, PAINT);
                drawStickerStars(canvas, rect, item, progress);
                break;
            case 7:
                STROKE.setColor(withAlpha(Color.WHITE, 190));
                canvas.drawRoundRect(rect, radius * .45f, radius * .45f, STROKE);
                for (int i = 0; i < 4; i++) {
                    PAINT.setColor(i == ((int) (progress * 7) % 4) ? Color.WHITE : withAlpha(item.accent, 150));
                    canvas.drawCircle(rect.left + rect.width() * (.18f + i * .20f), rect.bottom - rect.height() * .19f, rect.height() * .055f, PAINT);
                }
                break;
            case 8:
                STROKE.setColor(withAlpha(Color.WHITE, 195));
                canvas.drawRoundRect(rect, radius * .35f, radius * .35f, STROKE);
                STROKE.setStrokeWidth(Math.max(1f, rect.height() * .023f));
                STROKE.setColor(withAlpha(item.accent, 190));
                canvas.drawLine(rect.left + rect.width() * .20f, rect.top + rect.height() * .10f, rect.left + rect.width() * .20f, rect.bottom - rect.height() * .10f, STROKE);
                PAINT.setColor(withAlpha(Color.WHITE, 155));
                canvas.drawCircle(rect.right - rect.height() * .24f, rect.top + rect.height() * .24f, rect.height() * .11f, PAINT);
                break;
            case 9:
                STROKE.setColor(withAlpha(Color.WHITE, 210));
                canvas.drawPath(PATH, STROKE);
                STROKE.setStrokeWidth(Math.max(1f, rect.height() * .024f));
                for (int i = 0; i < 5; i++) {
                    float x = rect.left + rect.width() * (.16f + i * .17f);
                    canvas.drawLine(rect.centerX(), rect.bottom - rect.height() * .13f, x, rect.top + rect.height() * .15f, STROKE);
                }
                break;
            default:
                STROKE.setColor(withAlpha(Color.WHITE, 205));
                canvas.drawRoundRect(rect, radius, radius, STROKE);
                drawShimmer(canvas, rect, progress, .18f);
                break;
        }
        if (!outgoing) {
            STROKE.setColor(withAlpha(Color.BLACK, 35));
            STROKE.setStrokeWidth(Math.max(1f, rect.height() * .03f));
            canvas.drawRoundRect(rect, radius, radius, STROKE);
        }
    }

    public static void drawCallScreen(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height) {
        canvas.save();
        int family = (item.group + item.variant) % 10;
        PAINT.setShader(new LinearGradient(0, 0, width, height,
                new int[]{darken(item.primary, .42f), darken(item.secondary, .52f), darken(item.accent, .40f)}, null, Shader.TileMode.MIRROR));
        canvas.drawRect(0, 0, width, height, PAINT);
        PAINT.setShader(null);
        drawCallBackdrop(canvas, item, progress, width, height, family);

        float cx = width / 2f;
        float cy = height * .36f;
        PAINT.setColor(withAlpha(Color.WHITE, 238));
        canvas.drawCircle(cx, cy, Math.min(width, height) * .145f, PAINT);
        PAINT.setColor(darken(item.primary, .55f));
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(Math.max(16f, width * .105f));
        canvas.drawText("黄", cx, cy + PAINT.getTextSize() * .34f, PAINT);
        PAINT.setColor(Color.WHITE);
        PAINT.setTextSize(Math.max(14f, width * .074f));
        canvas.drawText(item.name, cx, height * .61f, PAINT);
        PAINT.setColor(withAlpha(Color.WHITE, 190));
        PAINT.setTypeface(Typeface.DEFAULT);
        PAINT.setTextSize(Math.max(10f, width * .046f));
        canvas.drawText("黄昏来电 · 本地主题", cx, height * .68f, PAINT);
        drawCallButtons(canvas, width, height, item, progress);
        canvas.restore();
    }

    private static void drawCallBackdrop(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height, int family) {
        float cx = width / 2f;
        float cy = height * .36f;
        switch (family) {
            case 0: // 霓虹光环
                for (int i = 0; i < 4; i++) {
                    float pulse = (float) Math.sin((progress + i * .16f) * Math.PI * 2f) * .02f;
                    STROKE.setColor(withAlpha(i % 2 == 0 ? item.accent : item.secondary, 165 - i * 23));
                    STROKE.setStrokeWidth(Math.max(2f, width * .010f));
                    canvas.drawCircle(cx, cy, Math.min(width, height) * (.19f + i * .075f + pulse), STROKE);
                }
                break;
            case 1: // 流星雨
                for (int i = 0; i < 16; i++) {
                    float p = fract(progress + i * .071f);
                    float x = width * fract(i * .37f + p * .65f);
                    float y = height * (p * 1.08f - .10f);
                    STROKE.setColor(withAlpha(i % 2 == 0 ? item.accent : Color.WHITE, 120));
                    STROKE.setStrokeWidth(Math.max(1f, width * .007f));
                    canvas.drawLine(x, y, x - width * .08f, y - height * .06f, STROKE);
                }
                break;
            case 2: // 像素均衡器
                for (int col = 0; col < 9; col++) {
                    float amp = .10f + .19f * (0.5f + .5f * (float) Math.sin((progress * 4f + col * .23f) * Math.PI * 2f));
                    PAINT.setColor(withAlpha(col % 2 == 0 ? item.accent : item.secondary, 145));
                    float x = width * (.12f + col * .095f);
                    canvas.drawRect(x, height * .78f - height * amp, x + width * .055f, height * .78f, PAINT);
                }
                break;
            case 3: // 极光丝带
                for (int i = 0; i < 4; i++) {
                    PATH.reset();
                    float y = height * (.12f + i * .17f);
                    PATH.moveTo(-width * .15f, y);
                    for (int p = 0; p < 5; p++) {
                        PATH.quadTo(width * (.12f + p * .24f), y + height * .10f * (float) Math.sin((progress + p * .16f + i * .14f) * Math.PI * 2f), width * (.24f + p * .24f), y);
                    }
                    STROKE.setColor(withAlpha(i % 2 == 0 ? item.primary : item.accent, 115));
                    STROKE.setStrokeWidth(Math.max(2f, width * .021f));
                    canvas.drawPath(PATH, STROKE);
                }
                break;
            case 4: // 机甲网格
                STROKE.setColor(withAlpha(item.accent, 100));
                STROKE.setStrokeWidth(Math.max(1f, width * .004f));
                for (int i = -2; i < 10; i++) {
                    canvas.drawLine(width * i / 8f, 0, width * (i + 3) / 8f, height, STROKE);
                    canvas.drawLine(0, height * i / 8f, width, height * (i + 3) / 8f, STROKE);
                }
                break;
            case 5: // 糖果泡泡
                for (int i = 0; i < 12; i++) {
                    float p = fract(progress + i * .091f);
                    PAINT.setColor(withAlpha(i % 2 == 0 ? item.accent : item.secondary, 125));
                    canvas.drawCircle(width * fract(i * .43f + .12f), height * (.88f - p * .82f), width * (.018f + (i % 3) * .007f), PAINT);
                }
                break;
            case 6: // 涂鸦贴纸
                drawStickerStars(canvas, new RectF(width * .08f, height * .12f, width * .92f, height * .82f), item, progress);
                break;
            case 7: // 街机隧道
                for (int i = 0; i < 8; i++) {
                    float scale = fract(progress + i * .125f);
                    STROKE.setColor(withAlpha(i % 2 == 0 ? item.accent : item.secondary, 155));
                    STROKE.setStrokeWidth(Math.max(1f, width * .006f));
                    float rw = width * (.12f + scale * .78f);
                    float rh = height * (.08f + scale * .55f);
                    canvas.drawRect(cx - rw / 2f, cy - rh / 2f, cx + rw / 2f, cy + rh / 2f, STROKE);
                }
                break;
            case 8: // 信纸与邮戳
                PAINT.setColor(withAlpha(Color.WHITE, 34));
                canvas.drawRoundRect(new RectF(width * .08f, height * .10f, width * .92f, height * .84f), width * .04f, width * .04f, PAINT);
                STROKE.setColor(withAlpha(item.accent, 150));
                STROKE.setStrokeWidth(Math.max(1f, width * .006f));
                canvas.drawCircle(width * .76f, height * .24f, width * .10f, STROKE);
                break;
            default: // 扇面与流光
                for (int i = 0; i < 9; i++) {
                    float angle = (float) (-Math.PI + i * Math.PI / 8f + progress * .18f);
                    STROKE.setColor(withAlpha(i % 2 == 0 ? item.accent : item.secondary, 120));
                    STROKE.setStrokeWidth(Math.max(1.5f, width * .008f));
                    canvas.drawLine(cx, height * .80f, cx + (float) Math.cos(angle) * width * .52f, height * .80f + (float) Math.sin(angle) * height * .58f, STROKE);
                }
                break;
        }
    }

    private static void drawCallButtons(Canvas canvas, float width, float height, HuanghunOutfitConfig.OutfitItem item, float progress) {
        float y = height * .84f;
        float radius = Math.min(width, height) * .075f;
        PAINT.setColor(Color.rgb(252, 76, 99));
        canvas.drawCircle(width * .29f, y, radius, PAINT);
        PAINT.setColor(Color.rgb(48, 209, 88));
        canvas.drawCircle(width * .71f, y, radius * (1f + .05f * (float) Math.sin(progress * Math.PI * 2f)), PAINT);
        PAINT.setColor(Color.WHITE);
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(Math.max(13f, width * .062f));
        canvas.drawText("×", width * .29f, y + PAINT.getTextSize() * .33f, PAINT);
        canvas.drawText("✓", width * .71f, y + PAINT.getTextSize() * .33f, PAINT);
    }

    /**
     * 群聊入场提示采用顶部轻量横幅：只占一行聊天空间，载具与粒子全部裁剪在横幅内，
     * 不再创建遮挡消息内容的大型舞台卡片。显示动画为滑入、短暂停留、淡出。
     */
    public static void drawJoinEffect(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height, String groupName) {
        if (item == null || width <= 0f || height <= 0f) {
            return;
        }
        final int family = (item.group + item.variant) % 10;
        final float enter = easeOut(Math.min(1f, progress / .14f));
        final float leave = progress < .84f ? 1f : Math.max(0f, 1f - (progress - .84f) / .16f);
        final float bannerHeight = Math.max(width * .125f, Math.min(width * .155f, height * .115f));
        final float bannerWidth = width * .90f;
        final float left = (width - bannerWidth) / 2f;
        final float topRest = Math.max(width * .035f, height * .035f);
        final float top = topRest - (1f - enter) * bannerHeight * 1.35f;
        final RectF banner = new RectF(left, top, left + bannerWidth, top + bannerHeight);

        canvas.saveLayerAlpha(0, 0, width, height, (int) (255 * leave));
        PAINT.setColor(withAlpha(darken(item.primary, .46f), 92));
        canvas.drawRoundRect(new RectF(banner.left, banner.top + bannerHeight * .07f, banner.right, banner.bottom + bannerHeight * .10f), bannerHeight * .36f, bannerHeight * .36f, PAINT);
        PAINT.setShader(new LinearGradient(banner.left, banner.top, banner.right, banner.bottom,
                new int[]{withAlpha(darken(item.primary, .52f), 248), withAlpha(darken(item.secondary, .60f), 248), withAlpha(darken(item.accent, .48f), 248)}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(banner, bannerHeight * .34f, bannerHeight * .34f, PAINT);
        PAINT.setShader(null);
        STROKE.setStrokeWidth(Math.max(1.5f, bannerHeight * .027f));
        STROKE.setColor(withAlpha(blend(item.accent, Color.WHITE, .48f), 218));
        canvas.drawRoundRect(banner, bannerHeight * .34f, bannerHeight * .34f, STROKE);

        canvas.save();
        canvas.clipRect(banner);
        drawJoinBannerParticles(canvas, item, progress, banner, family);
        drawJoinBannerVehicle(canvas, item, progress, banner, family);
        canvas.restore();
        drawJoinBannerText(canvas, item, banner, groupName);
        canvas.restore();
    }

    private static void drawJoinBannerParticles(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, RectF banner, int family) {
        final float h = banner.height();
        for (int i = 0; i < 9; i++) {
            float p = fract(progress * (.38f + (i % 3) * .10f) + i * .137f);
            float x = banner.left + banner.width() * p;
            float y = banner.top + h * (.16f + (i % 4) * .19f);
            PAINT.setColor(withAlpha(i % 2 == 0 ? Color.WHITE : item.accent, 66 + (i % 3) * 26));
            canvas.drawCircle(x, y, Math.max(1.2f, h * (.022f + (i % 3) * .010f)), PAINT);
        }
        if (family == 0 || family == 4 || family == 7) {
            STROKE.setColor(withAlpha(item.accent, 86));
            STROKE.setStrokeWidth(Math.max(1f, h * .024f));
            for (int i = -2; i < 5; i++) {
                float offset = fract(progress * .22f + i * .17f);
                canvas.drawLine(banner.left + banner.width() * (offset - .22f), banner.bottom,
                        banner.left + banner.width() * (offset + .10f), banner.top, STROKE);
            }
        }
    }

    private static void drawJoinBannerVehicle(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, RectF banner, int family) {
        final float cruise = .07f + .10f * (float) Math.sin(progress * Math.PI * 2f);
        final float x = banner.left + banner.width() * (.18f + cruise);
        final float y = banner.centerY() + banner.height() * .08f;
        final float scale = banner.height() / 290f;
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        switch (family) {
            case 0: drawSupercar(canvas, item, progress); break;
            case 1: drawAirplane(canvas, item, progress); break;
            case 2: drawRocket(canvas, item, progress); break;
            case 3: drawMech(canvas, item, progress); break;
            case 4: drawYacht(canvas, item, progress); break;
            case 5: drawBalloon(canvas, item, progress); break;
            case 6: drawSkateboard(canvas, item, progress); break;
            case 7: drawUfo(canvas, item, progress); break;
            case 8: drawTrain(canvas, item, progress); break;
            default: drawDragonBoat(canvas, item, progress); break;
        }
        canvas.restore();
    }

    private static void drawJoinBannerText(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, RectF banner, String groupName) {
        final float h = banner.height();
        String safeName = groupName == null || groupName.trim().isEmpty() ? "群聊" : groupName.trim();
        if (safeName.length() > 8) {
            safeName = safeName.substring(0, 8) + "…";
        }
        PAINT.setTextAlign(Paint.Align.LEFT);
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(Math.max(12f, h * .255f));
        PAINT.setColor(Color.WHITE);
        float textLeft = banner.left + banner.width() * .42f;
        canvas.drawText("黄昏进入了「" + safeName + "」", textLeft, banner.centerY() + PAINT.getTextSize() * .35f, PAINT);
        // 右端的一粒主题高光，使横幅在不增加高度的前提下保留入场提示的仪式感。
        PAINT.setColor(withAlpha(blend(item.accent, Color.WHITE, .55f), 220));
        canvas.drawCircle(banner.right - h * .22f, banner.centerY(), h * .055f, PAINT);
    }

    private static void drawSupercar(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        PAINT.setColor(item.primary);
        RECT.set(-130, -20, 130, 38);
        canvas.drawRoundRect(RECT, 26, 26, PAINT);
        PATH.reset();
        PATH.moveTo(-70, -20); PATH.lineTo(-25, -72); PATH.lineTo(68, -72); PATH.lineTo(108, -20); PATH.close();
        PAINT.setColor(item.secondary);
        canvas.drawPath(PATH, PAINT);
        PAINT.setColor(withAlpha(Color.WHITE, 185));
        canvas.drawRoundRect(new RectF(-12, -62, 58, -28), 9, 9, PAINT);
        PAINT.setColor(Color.rgb(20, 24, 34));
        canvas.drawCircle(-72, 39, 25, PAINT); canvas.drawCircle(82, 39, 25, PAINT);
        PAINT.setColor(item.accent);
        canvas.drawCircle(-72, 39, 10, PAINT); canvas.drawCircle(82, 39, 10, PAINT);
        drawTrail(canvas, item.accent, -170, 0, fly);
    }

    private static void drawAirplane(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        PATH.reset();
        PATH.moveTo(-140, 8); PATH.lineTo(95, -18); PATH.lineTo(145, 0); PATH.lineTo(95, 18); PATH.lineTo(-140, 8); PATH.close();
        PAINT.setColor(item.primary); canvas.drawPath(PATH, PAINT);
        PATH.reset(); PATH.moveTo(-15, 0); PATH.lineTo(32, -104); PATH.lineTo(57, -98); PATH.lineTo(46, 4); PATH.close();
        PAINT.setColor(item.secondary); canvas.drawPath(PATH, PAINT);
        PATH.reset(); PATH.moveTo(-10, 6); PATH.lineTo(37, 87); PATH.lineTo(56, 78); PATH.lineTo(44, 3); PATH.close();
        PAINT.setColor(item.accent); canvas.drawPath(PATH, PAINT);
        drawTrail(canvas, withAlpha(Color.WHITE, 185), -160, 8, fly);
    }

    private static void drawRocket(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        canvas.save(); canvas.rotate(-18f);
        PAINT.setColor(item.primary); canvas.drawRoundRect(new RectF(-42, -120, 42, 78), 42, 42, PAINT);
        PATH.reset(); PATH.moveTo(-42, -86); PATH.lineTo(0, -165); PATH.lineTo(42, -86); PATH.close(); PAINT.setColor(item.secondary); canvas.drawPath(PATH, PAINT);
        PAINT.setColor(withAlpha(Color.WHITE, 220)); canvas.drawCircle(0, -50, 19, PAINT);
        PATH.reset(); PATH.moveTo(-42, 30); PATH.lineTo(-92, 90); PATH.lineTo(-32, 68); PATH.close(); PAINT.setColor(item.accent); canvas.drawPath(PATH, PAINT);
        PATH.reset(); PATH.moveTo(42, 30); PATH.lineTo(92, 90); PATH.lineTo(32, 68); PATH.close(); canvas.drawPath(PATH, PAINT);
        drawFlame(canvas, 0, 82, item.accent, fly); canvas.restore();
    }

    private static void drawMech(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        PAINT.setColor(item.primary); canvas.drawRoundRect(new RectF(-60, -112, 60, 45), 18, 18, PAINT);
        PAINT.setColor(item.secondary); canvas.drawRoundRect(new RectF(-90, -35, -55, 78), 14, 14, PAINT); canvas.drawRoundRect(new RectF(55, -35, 90, 78), 14, 14, PAINT);
        canvas.drawRoundRect(new RectF(-48, 38, -10, 140), 14, 14, PAINT); canvas.drawRoundRect(new RectF(10, 38, 48, 140), 14, 14, PAINT);
        PAINT.setColor(item.accent); canvas.drawRect(-42, -80, 42, -47, PAINT); canvas.drawCircle(-25, -64, 7, PAINT); canvas.drawCircle(25, -64, 7, PAINT);
        drawFlame(canvas, -28, 142, item.accent, fly); drawFlame(canvas, 28, 142, item.accent, fly);
    }

    private static void drawYacht(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        PATH.reset(); PATH.moveTo(-145, 20); PATH.lineTo(150, 20); PATH.lineTo(92, 73); PATH.lineTo(-92, 73); PATH.close(); PAINT.setColor(item.primary); canvas.drawPath(PATH, PAINT);
        PATH.reset(); PATH.moveTo(-12, 18); PATH.lineTo(-12, -132); PATH.lineTo(88, 18); PATH.close(); PAINT.setColor(item.secondary); canvas.drawPath(PATH, PAINT);
        PATH.reset(); PATH.moveTo(-18, 18); PATH.lineTo(-18, -89); PATH.lineTo(-94, 18); PATH.close(); PAINT.setColor(item.accent); canvas.drawPath(PATH, PAINT);
        STROKE.setColor(withAlpha(Color.WHITE, 180)); STROKE.setStrokeWidth(8); canvas.drawLine(-190, 90, 155, 90, STROKE); canvas.drawLine(-160, 114, 190, 114, STROKE);
    }

    private static void drawBalloon(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        PAINT.setColor(item.primary); canvas.drawOval(new RectF(-72, -155, 72, 30), PAINT);
        STROKE.setColor(withAlpha(Color.WHITE, 160)); STROKE.setStrokeWidth(6); canvas.drawLine(-45, 5, -28, 75, STROKE); canvas.drawLine(45, 5, 28, 75, STROKE);
        PAINT.setColor(item.secondary); canvas.drawRoundRect(new RectF(-37, 70, 37, 105), 8, 8, PAINT);
        PAINT.setColor(item.accent); canvas.drawArc(-72, -155, 72, 30, fly * 360f, 80, true, PAINT);
    }

    private static void drawSkateboard(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        PAINT.setColor(item.primary); canvas.drawRoundRect(new RectF(-135, 20, 135, 50), 18, 18, PAINT);
        PAINT.setColor(Color.rgb(26, 29, 36)); canvas.drawCircle(-76, 60, 18, PAINT); canvas.drawCircle(76, 60, 18, PAINT);
        PAINT.setColor(item.secondary); canvas.drawCircle(0, -52, 38, PAINT); canvas.drawRoundRect(new RectF(-35, -13, 36, 80), 20, 20, PAINT);
        PAINT.setColor(item.accent); canvas.drawLine(-32, 0, -94, -50, PAINT); canvas.drawLine(32, 0, 94, -50, PAINT);
        drawTrail(canvas, item.accent, -175, 35, fly);
    }

    private static void drawUfo(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        PAINT.setColor(item.primary); canvas.drawOval(new RectF(-150, -10, 150, 68), PAINT);
        PAINT.setColor(item.secondary); canvas.drawOval(new RectF(-65, -80, 65, 17), PAINT);
        PAINT.setColor(withAlpha(item.accent, 145)); canvas.drawOval(new RectF(-104, 43, 104, 158), PAINT);
        for (int i = 0; i < 4; i++) { PAINT.setColor(i == ((int) (fly * 8) % 4) ? Color.WHITE : item.accent); canvas.drawCircle(-80 + i * 53, 28, 9, PAINT); }
    }

    private static void drawTrain(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        PAINT.setColor(item.primary); canvas.drawRoundRect(new RectF(-145, -35, 140, 73), 20, 20, PAINT);
        PAINT.setColor(item.secondary); canvas.drawRoundRect(new RectF(-65, -105, 58, -28), 15, 15, PAINT);
        PAINT.setColor(withAlpha(Color.WHITE, 200)); for (int i = 0; i < 4; i++) canvas.drawRoundRect(new RectF(-105 + i * 57, -11, -66 + i * 57, 20), 6, 6, PAINT);
        PAINT.setColor(Color.rgb(28, 29, 35)); canvas.drawCircle(-75, 77, 20, PAINT); canvas.drawCircle(76, 77, 20, PAINT);
        drawTrail(canvas, item.accent, -178, 28, fly);
    }

    private static void drawDragonBoat(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly) {
        PATH.reset(); PATH.moveTo(-150, 18); PATH.quadTo(0, 88, 150, 18); PATH.lineTo(96, 71); PATH.lineTo(-92, 71); PATH.close(); PAINT.setColor(item.primary); canvas.drawPath(PATH, PAINT);
        PAINT.setColor(item.secondary); canvas.drawCircle(128, -8, 35, PAINT); PAINT.setColor(item.accent); canvas.drawCircle(139, -17, 8, PAINT);
        STROKE.setColor(withAlpha(Color.WHITE, 175)); STROKE.setStrokeWidth(7); for (int i = 0; i < 5; i++) canvas.drawLine(-95 + i * 44, 2, -72 + i * 44, 102, STROKE);
    }

    private static void drawTrail(Canvas canvas, int color, float x, float y, float p) {
        STROKE.setColor(withAlpha(color, 150)); STROKE.setStrokeWidth(12);
        canvas.drawLine(x, y, x - 120f * (0.7f + .3f * p), y, STROKE);
        STROKE.setColor(withAlpha(Color.WHITE, 95)); STROKE.setStrokeWidth(4);
        canvas.drawLine(x - 25, y - 20, x - 155, y - 34, STROKE);
    }

    private static void drawFlame(Canvas canvas, float x, float y, int color, float p) {
        PAINT.setColor(withAlpha(color, 220));
        PATH.reset(); PATH.moveTo(x - 19, y); PATH.quadTo(x, y + 42 + 18 * (float) Math.sin(p * Math.PI * 2f), x + 19, y); PATH.close(); canvas.drawPath(PATH, PAINT);
    }

    private static void buildTechBubble(RectF rect, float notch) {
        PATH.reset();
        PATH.moveTo(rect.left + notch, rect.top);
        PATH.lineTo(rect.right - notch * 1.35f, rect.top);
        PATH.lineTo(rect.right, rect.top + notch * .70f);
        PATH.lineTo(rect.right - notch * .55f, rect.centerY() - notch * .52f);
        PATH.lineTo(rect.right - notch * .55f, rect.centerY() + notch * .52f);
        PATH.lineTo(rect.right, rect.bottom - notch * .70f);
        PATH.lineTo(rect.right - notch * 1.35f, rect.bottom);
        PATH.lineTo(rect.left + notch, rect.bottom);
        PATH.lineTo(rect.left, rect.bottom - notch);
        PATH.lineTo(rect.left + notch * .55f, rect.centerY() + notch * .42f);
        PATH.lineTo(rect.left + notch * .55f, rect.centerY() - notch * .42f);
        PATH.lineTo(rect.left, rect.top + notch);
        PATH.close();
    }

    private static void buildCrystalBubble(RectF rect, float cut) {
        PATH.reset();
        PATH.moveTo(rect.left + cut * 1.35f, rect.top);
        PATH.lineTo(rect.right - cut * .55f, rect.top);
        PATH.lineTo(rect.right, rect.top + cut * .82f);
        PATH.lineTo(rect.right - cut * .72f, rect.bottom - cut * .12f);
        PATH.lineTo(rect.right - cut * 1.55f, rect.bottom);
        PATH.lineTo(rect.left + cut * .42f, rect.bottom);
        PATH.lineTo(rect.left, rect.bottom - cut * 1.18f);
        PATH.lineTo(rect.left + cut * .68f, rect.top + cut * .25f);
        PATH.close();
    }

    private static void buildComicBubble(RectF rect, float spike) {
        PATH.reset();
        int count = 12;
        for (int i = 0; i < count; i++) {
            float p = i / (float) (count - 1);
            float x = rect.left + rect.width() * p;
            float y = rect.top + (i % 2 == 0 ? 0f : spike);
            if (i == 0) PATH.moveTo(x, y); else PATH.lineTo(x, y);
        }
        for (int i = count - 1; i >= 0; i--) {
            float p = i / (float) (count - 1);
            float x = rect.left + rect.width() * p;
            float y = rect.bottom - (i % 2 == 0 ? 0f : spike);
            PATH.lineTo(x, y);
        }
        PATH.close();
    }

    private static void buildStickerBubble(RectF rect, float cut) {
        PATH.reset();
        PATH.moveTo(rect.left + cut, rect.top);
        PATH.lineTo(rect.right - cut * 1.4f, rect.top);
        PATH.lineTo(rect.right, rect.top + cut * .72f);
        PATH.lineTo(rect.right - cut * .55f, rect.top + cut * 1.45f);
        PATH.lineTo(rect.right, rect.bottom - cut * .92f);
        PATH.lineTo(rect.right - cut * 1.2f, rect.bottom);
        PATH.lineTo(rect.left + cut * .65f, rect.bottom - cut * .18f);
        PATH.lineTo(rect.left, rect.bottom - cut * 1.12f);
        PATH.lineTo(rect.left + cut * .64f, rect.centerY());
        PATH.lineTo(rect.left, rect.top + cut * .85f);
        PATH.close();
    }

    private static void buildPixelBubble(RectF rect, float step) {
        PATH.reset();
        PATH.moveTo(rect.left + step, rect.top);
        PATH.lineTo(rect.right - step, rect.top); PATH.lineTo(rect.right - step, rect.top + step); PATH.lineTo(rect.right, rect.top + step);
        PATH.lineTo(rect.right, rect.bottom - step); PATH.lineTo(rect.right - step, rect.bottom - step); PATH.lineTo(rect.right - step, rect.bottom);
        PATH.lineTo(rect.left + step, rect.bottom); PATH.lineTo(rect.left + step, rect.bottom - step); PATH.lineTo(rect.left, rect.bottom - step);
        PATH.lineTo(rect.left, rect.top + step); PATH.lineTo(rect.left + step, rect.top + step); PATH.close();
    }

    private static void buildJellyBubble(RectF rect, float progress) {
        float wave = rect.height() * .08f;
        PATH.reset();
        PATH.moveTo(rect.left + rect.width() * .08f, rect.top + wave);
        PATH.quadTo(rect.left + rect.width() * .25f, rect.top - wave * (float) Math.sin(progress * Math.PI * 2f), rect.left + rect.width() * .42f, rect.top + wave);
        PATH.quadTo(rect.left + rect.width() * .65f, rect.top + wave * 2f, rect.right - rect.width() * .08f, rect.top + wave);
        PATH.quadTo(rect.right + wave, rect.centerY(), rect.right - rect.width() * .08f, rect.bottom - wave);
        PATH.quadTo(rect.left + rect.width() * .68f, rect.bottom + wave * (float) Math.sin(progress * Math.PI * 2f), rect.left + rect.width() * .38f, rect.bottom - wave);
        PATH.quadTo(rect.left + rect.width() * .15f, rect.bottom - wave * 2f, rect.left + rect.width() * .08f, rect.bottom - wave);
        PATH.quadTo(rect.left - wave, rect.centerY(), rect.left + rect.width() * .08f, rect.top + wave);
        PATH.close();
    }

    private static void buildCutCornerBubble(RectF rect, float cut) {
        PATH.reset(); PATH.moveTo(rect.left + cut, rect.top); PATH.lineTo(rect.right - cut, rect.top); PATH.lineTo(rect.right, rect.top + cut);
        PATH.lineTo(rect.right, rect.bottom - cut); PATH.lineTo(rect.right - cut, rect.bottom); PATH.lineTo(rect.left + cut, rect.bottom);
        PATH.lineTo(rect.left, rect.bottom - cut); PATH.lineTo(rect.left, rect.top + cut); PATH.close();
    }

    private static void buildFanBubble(RectF rect, float progress) {
        PATH.reset();
        PATH.moveTo(rect.left + rect.width() * .08f, rect.bottom - rect.height() * .08f);
        PATH.quadTo(rect.centerX(), rect.top - rect.height() * (.10f + .03f * (float) Math.sin(progress * Math.PI * 2f)), rect.right - rect.width() * .08f, rect.bottom - rect.height() * .08f);
        PATH.quadTo(rect.right + rect.width() * .03f, rect.bottom, rect.right - rect.width() * .13f, rect.bottom);
        PATH.lineTo(rect.left + rect.width() * .13f, rect.bottom); PATH.quadTo(rect.left - rect.width() * .03f, rect.bottom, rect.left + rect.width() * .08f, rect.bottom - rect.height() * .08f); PATH.close();
    }

    private static void drawShimmer(Canvas canvas, RectF rect, float progress, float widthFraction) {
        canvas.save(); canvas.clipRect(rect);
        float x = rect.left - rect.width() * .35f + rect.width() * 1.7f * progress;
        PAINT.setColor(withAlpha(Color.WHITE, 75)); canvas.rotate(-22f, x, rect.centerY());
        canvas.drawRect(x, rect.top - rect.height(), x + rect.width() * widthFraction, rect.bottom + rect.height(), PAINT);
        canvas.restore();
    }

    private static void drawOrbitDots(Canvas canvas, RectF rect, int color, float progress, int count) {
        for (int i = 0; i < count; i++) {
            float p = fract(progress + i * .17f);
            PAINT.setColor(withAlpha(i % 2 == 0 ? Color.WHITE : color, 165));
            canvas.drawCircle(rect.left + rect.width() * p, rect.top + rect.height() * (.20f + .60f * fract(i * .31f + progress * .65f)), Math.max(1.4f, rect.height() * .035f), PAINT);
        }
    }

    private static void drawStickerStars(Canvas canvas, RectF rect, HuanghunOutfitConfig.OutfitItem item, float progress) {
        for (int i = 0; i < 7; i++) {
            float x = rect.left + rect.width() * fract(i * .27f + progress * .15f);
            float y = rect.top + rect.height() * fract(i * .43f + progress * .30f);
            float s = Math.max(3f, rect.height() * (.035f + (i % 3) * .012f));
            PAINT.setColor(withAlpha(i % 2 == 0 ? item.accent : Color.WHITE, 180));
            PATH.reset();
            for (int p = 0; p < 8; p++) {
                double a = -Math.PI / 2 + p * Math.PI / 4;
                float radius = p % 2 == 0 ? s * 1.8f : s * .72f;
                float px = x + (float) Math.cos(a) * radius;
                float py = y + (float) Math.sin(a) * radius;
                if (p == 0) PATH.moveTo(px, py); else PATH.lineTo(px, py);
            }
            PATH.close(); canvas.drawPath(PATH, PAINT);
        }
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float easeOut(float value) {
        float inverse = 1f - Math.max(0f, Math.min(1f, value));
        return 1f - inverse * inverse * inverse;
    }

    private static int darken(int color, float factor) {
        return Color.rgb((int) (Color.red(color) * factor), (int) (Color.green(color) * factor), (int) (Color.blue(color) * factor));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | ((Math.max(0, Math.min(alpha, 255))) << 24);
    }

    private static int blend(int color1, int color2, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        float inv = 1f - r;
        int red = (int) (Color.red(color1) * r + Color.red(color2) * inv);
        int green = (int) (Color.green(color1) * r + Color.green(color2) * inv);
        int blue = (int) (Color.blue(color1) * r + Color.blue(color2) * inv);
        return Color.rgb(red, green, blue);
    }
}
