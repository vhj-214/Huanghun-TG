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

    /** 在 Telegram 原气泡背景之后、消息文字之前绘制完整的本地气泡外观。 */
    public static void drawBubbleOverlay(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, RectF bubble, boolean outgoing) {
        if (item == null || bubble == null || bubble.width() <= 0 || bubble.height() <= 0) {
            return;
        }
        canvas.save();
        float size = Math.min(bubble.width(), bubble.height());
        float radius = Math.max(8f, size * .24f);
        int family = (item.group + item.variant) % 10;

        PAINT.setShader(new LinearGradient(bubble.left, bubble.top, bubble.right, bubble.bottom,
                new int[]{item.primary, item.secondary, item.accent}, null, Shader.TileMode.MIRROR));
        drawBubbleShell(canvas, bubble, radius, family, item, progress, PAINT);
        PAINT.setShader(null);

        drawBubbleDecoration(canvas, bubble, radius, family, item, progress, outgoing);
        canvas.restore();
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

    public static void drawJoinEffect(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height, String groupName) {
        canvas.save();
        int family = (item.group + item.variant) % 10;
        float fly = progress < .72f ? easeOut(progress / .72f) : 1f;
        float fade = progress < .82f ? 1f : Math.max(0f, 1f - (progress - .82f) / .18f);
        canvas.saveLayerAlpha(0, 0, width, height, (int) (255 * fade));
        drawJoinSky(canvas, item, progress, width, height, family);
        drawJoinVehicle(canvas, item, fly, width, height, family);
        drawJoinCard(canvas, item, progress, width, height, groupName);
        canvas.restore();
        canvas.restore();
    }

    private static void drawJoinSky(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height, int family) {
        // 悬浮舞台只占聊天页上半部，不覆盖正常消息内容和输入框。
        RECT.set(width * .04f, height * .10f, width * .96f, height * .62f);
        PAINT.setShader(new LinearGradient(RECT.left, RECT.top, RECT.right, RECT.bottom, new int[]{withAlpha(darken(item.primary, .46f), 235), withAlpha(darken(item.secondary, .52f), 235)}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(RECT, width * .055f, width * .055f, PAINT);
        PAINT.setShader(null);
        canvas.save();
        canvas.clipRect(RECT);
        for (int i = 0; i < 18; i++) {
            float p = fract(progress * (0.55f + (i % 3) * .16f) + i * .083f);
            float x = RECT.left + RECT.width() * fract(i * .37f + p * .33f);
            float y = RECT.top + RECT.height() * fract(i * .19f + p * .79f);
            PAINT.setColor(withAlpha(i % 2 == 0 ? item.accent : Color.WHITE, 55 + (i % 3) * 25));
            canvas.drawCircle(x, y, width * (.006f + (i % 4) * .003f), PAINT);
        }
        if (family == 4 || family == 7) {
            STROKE.setColor(withAlpha(item.accent, 70));
            STROKE.setStrokeWidth(Math.max(1f, width * .004f));
            for (int i = -3; i < 8; i++) {
                canvas.drawLine(RECT.left + RECT.width() * i / 5f, RECT.top, RECT.left + RECT.width() * (i + 2) / 5f, RECT.bottom, STROKE);
            }
        }
        canvas.restore();
    }

    private static void drawJoinVehicle(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float fly, float width, float height, int family) {
        float x = -width * .22f + width * 1.28f * fly;
        float y = height * (.34f + .04f * (float) Math.sin(fly * Math.PI));
        float scale = Math.min(width, height) / 360f;
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        switch (family) {
            case 0:
                drawSupercar(canvas, item, fly);
                break;
            case 1:
                drawAirplane(canvas, item, fly);
                break;
            case 2:
                drawRocket(canvas, item, fly);
                break;
            case 3:
                drawMech(canvas, item, fly);
                break;
            case 4:
                drawYacht(canvas, item, fly);
                break;
            case 5:
                drawBalloon(canvas, item, fly);
                break;
            case 6:
                drawSkateboard(canvas, item, fly);
                break;
            case 7:
                drawUfo(canvas, item, fly);
                break;
            case 8:
                drawTrain(canvas, item, fly);
                break;
            default:
                drawDragonBoat(canvas, item, fly);
                break;
        }
        canvas.restore();
    }

    private static void drawJoinCard(Canvas canvas, HuanghunOutfitConfig.OutfitItem item, float progress, float width, float height, String groupName) {
        float cardWidth = width * .78f;
        float cardHeight = Math.max(62f, height * .14f);
        float left = (width - cardWidth) / 2f;
        float top = height * .54f + (1f - Math.min(1f, progress * 5f)) * height * .10f;
        RECT.set(left, top, left + cardWidth, top + cardHeight);
        PAINT.setColor(withAlpha(Color.WHITE, 242));
        canvas.drawRoundRect(RECT, cardHeight * .28f, cardHeight * .28f, PAINT);
        STROKE.setColor(withAlpha(item.accent, 225));
        STROKE.setStrokeWidth(Math.max(2f, cardHeight * .035f));
        canvas.drawRoundRect(RECT, cardHeight * .28f, cardHeight * .28f, STROKE);
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTypeface(Typeface.DEFAULT_BOLD);
        PAINT.setTextSize(Math.max(13f, width * .048f));
        PAINT.setColor(darken(item.primary, .48f));
        canvas.drawText("黄昏进入了", width / 2f, top + cardHeight * .42f, PAINT);
        PAINT.setTextSize(Math.max(14f, width * .056f));
        PAINT.setColor(darken(item.secondary, .48f));
        String safeName = groupName == null || groupName.trim().isEmpty() ? "群聊" : groupName.trim();
        if (safeName.length() > 14) {
            safeName = safeName.substring(0, 14) + "…";
        }
        canvas.drawText(safeName, width / 2f, top + cardHeight * .75f, PAINT);
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
}
