package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import tw.nekomimi.nekogram.helpers.HuanghunPetHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * In-app desktop pet layer. It is deliberately attached to the client window,
 * not to WindowManager, so it never needs overlay permission and never executes
 * anything from a pet package. Packages provide only images and JSON metadata.
 */
public final class HuanghunPetOverlay extends View {
    private static final long TICK_MS = 50L;
    private static final int DEFAULT_SIZE_DP = 116;
    private static final int MIN_FRAME_MS = 40;
    private static final int MAX_FRAME_MS = 3000;
    private static WeakReference<HuanghunPetOverlay> currentOverlay;

    private final Random random = new Random();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF petRect = new RectF();
    private final HashMap<String, AnimationState> states = new HashMap<>();
    private final HashMap<String, ArrayList<String>> dialogs = new HashMap<>();
    private final Runnable tickRunnable = this::tick;

    private HuanghunPetHelper.PetInfo pet;
    private String petId = "";
    private AnimationState currentState;
    private int frameIndex;
    private long frameDue;
    private long nextDecision;
    private long bubbleUntil;
    private String bubbleText = "";
    private Bitmap currentBitmap;
    private float petX;
    private float petY;
    private float velocityX;
    private int frameWidth = 1;
    private int frameHeight = 1;
    private int direction = 1;
    private boolean positionInitialized;
    private boolean hostResumed = true;
    private boolean dragging;
    private int lastMidnightDay = -1;
    private float downX;
    private float downY;
    private float downPetX;
    private float downPetY;

    public HuanghunPetOverlay(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        bubblePaint.setColor(Color.WHITE);
        bubblePaint.setShadowLayer(dp(5), 0, dp(2), 0x55000000);
        bubbleTextPaint.setColor(0xff252525);
        bubbleTextPaint.setTextSize(dp(14));
        bubbleTextPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        currentOverlay = new WeakReference<>(this);
        setVisibility(INVISIBLE);
    }

    public static void reloadActive() {
        HuanghunPetOverlay overlay = currentOverlay == null ? null : currentOverlay.get();
        if (overlay != null) {
            overlay.reloadFromPreferences();
        }
    }

    public void onHostPause() {
        hostResumed = false;
        removeCallbacks(tickRunnable);
    }

    public void onHostResume() {
        hostResumed = true;
        reloadFromPreferences();
    }

    public void destroy() {
        removeCallbacks(tickRunnable);
        recycleCurrentBitmap();
        if (currentOverlay != null && currentOverlay.get() == this) {
            currentOverlay.clear();
        }
    }

    private void reloadFromPreferences() {
        String active = getContext().getSharedPreferences("huanghun_pets", Context.MODE_PRIVATE)
                .getString("active_id", "");
        if (active == null) {
            active = "";
        }
        if (active.equals(petId) && pet != null) {
            if (hostResumed && getVisibility() != VISIBLE) {
                setVisibility(VISIBLE);
                scheduleTick();
            }
            return;
        }
        removeCallbacks(tickRunnable);
        recycleCurrentBitmap();
        states.clear();
        dialogs.clear();
        currentState = null;
        pet = null;
        petId = active;
        if (active.isEmpty()) {
            setVisibility(INVISIBLE);
            invalidate();
            return;
        }
        try {
            pet = HuanghunPetHelper.get(getContext(), active);
            loadAnimations();
            loadDialogs();
            positionInitialized = false;
            setVisibility(VISIBLE);
            showState("idle");
            initializePositionIfNeeded();
            speak("greet");
            nextDecision = System.currentTimeMillis() + 1800L;
            scheduleTick();
        } catch (Throwable error) {
            FileLog.e(error);
            setVisibility(INVISIBLE);
        }
    }

    private void loadAnimations() throws Exception {
        JSONObject manifest = readJson(new File(pet.directory, "manifest.json"));
        frameWidth = Math.max(1, manifest.optInt("frame_width", 1));
        frameHeight = Math.max(1, manifest.optInt("frame_height", 1));
        JSONObject manifestAnimations = manifest.optJSONObject("animations");
        if (manifestAnimations != null) {
            JSONArray names = manifestAnimations.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.optString(i, "");
                    AnimationState state = readManifestAnimation(name, manifestAnimations.optJSONObject(name));
                    if (state != null) states.put(name, state);
                }
            }
        }
        File indexFile = new File(pet.directory, "animations/index.json");
        if (indexFile.isFile()) {
            JSONObject index = readJson(indexFile);
            JSONObject stateMap = index.optJSONObject("states");
            if (stateMap != null) {
                JSONArray names = stateMap.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String name = names.optString(i, "");
                        String path = stateMap.optString(name, "");
                        if (!name.isEmpty() && !path.isEmpty()) {
                            AnimationState state = readAnimation(name, new File(pet.directory, path));
                            if (state != null) {
                                states.put(name, state);
                            }
                        }
                    }
                }
            }
        }
        if (!states.containsKey("idle")) {
            AnimationState fallback = readDirectoryAnimation("idle");
            if (fallback != null) {
                states.put("idle", fallback);
            }
        }
        if (!states.containsKey("idle")) {
            throw new IllegalStateException("桌宠缺少 idle 动画");
        }
    }

    private AnimationState readManifestAnimation(String name, JSONObject definition) throws Exception {
        if (definition == null) return null;
        JSONArray frames = definition.optJSONArray("frames");
        if (frames == null || frames.length() == 0) return null;
        int fps = Math.max(1, Math.min(30, definition.optInt("fps", 6)));
        AnimationState state = new AnimationState(name, definition.optBoolean("loop", true));
        state.movementSpeed = Math.max(0.2f, Math.min(8f, (float) definition.optDouble("movement_speed", 1.7)));
        for (int i = 0; i < frames.length(); i++) {
            String image = frames.optString(i, "");
            if (image.isEmpty()) continue;
            File imageFile = new File(pet.directory, "frames/" + name + "/" + image);
            String root = pet.directory.getCanonicalPath() + File.separator;
            if (!imageFile.getCanonicalPath().startsWith(root) || !imageFile.isFile()) continue;
            state.frames.add(new AnimationFrame(imageFile, Math.max(MIN_FRAME_MS, Math.min(MAX_FRAME_MS, 1000 / fps))));
        }
        return state.frames.isEmpty() ? null : state;
    }

    private AnimationState readAnimation(String name, File file) throws Exception {
        if (!file.isFile()) {
            return null;
        }
        JSONObject object = readJson(file);
        JSONArray frames = object.optJSONArray("frames");
        if (frames == null || frames.length() == 0) {
            return null;
        }
        AnimationState state = new AnimationState(name, object.optBoolean("loop", true));
        for (int i = 0; i < frames.length(); i++) {
            JSONObject frame = frames.optJSONObject(i);
            if (frame == null) {
                continue;
            }
            String image = frame.optString("image", "");
            if (image.isEmpty()) {
                continue;
            }
            File imageFile = new File(pet.directory, image);
            String root = pet.directory.getCanonicalPath() + File.separator;
            if (!imageFile.getCanonicalPath().startsWith(root) || !imageFile.isFile()) {
                continue;
            }
            int duration = frame.optInt("duration_ms", 180);
            state.frames.add(new AnimationFrame(imageFile, Math.max(MIN_FRAME_MS, Math.min(MAX_FRAME_MS, duration))));
        }
        return state.frames.isEmpty() ? null : state;
    }

    private AnimationState readDirectoryAnimation(String name) {
        File directory = new File(pet.directory, "images/" + name);
        if (!directory.isDirectory()) directory = new File(pet.directory, "frames/" + name);
        File[] files = directory.listFiles((dir, filename) -> {
            String lower = filename.toLowerCase(Locale.ROOT);
            return lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        });
        if (files == null || files.length == 0) {
            return null;
        }
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        AnimationState state = new AnimationState(name, true);
        for (File file : files) {
            state.frames.add(new AnimationFrame(file, 180));
        }
        return state;
    }

    private void loadDialogs() {
        try {
        File file = new File(pet.directory, "dialogs/dialogs.json");
            if (!file.isFile()) file = new File(pet.directory, "dialogues.json");
            if (!file.isFile()) {
                return;
            }
            JSONObject object = readJson(file);
            JSONArray names = object.names();
            if (names == null) {
                return;
            }
            for (int i = 0; i < names.length(); i++) {
                String name = names.optString(i, "");
                JSONArray values = object.optJSONArray(name);
                if (values == null) {
                    continue;
                }
                ArrayList<String> lines = new ArrayList<>();
                for (int j = 0; j < values.length(); j++) {
                    String value = values.optString(j, "").trim();
                    if (!value.isEmpty()) {
                        lines.add(value);
                    }
                }
                if (!lines.isEmpty()) {
                    dialogs.put(name, lines);
                }
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    private JSONObject readJson(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > 1024 * 1024) {
                    throw new Exception("桌宠配置文件过大");
                }
                output.write(buffer, 0, read);
            }
            return new JSONObject(output.toString("UTF-8"));
        }
    }

    private void showState(String name) {
        AnimationState state = states.get(name);
        if (state == null && "squash".equals(name)) state = states.get("squish");
        if (state == null && "squish".equals(name)) state = states.get("squash");
        if (state == null) {
            state = states.get("idle");
        }
        if (state == null || state.frames.isEmpty()) {
            return;
        }
        if (currentState != state) {
            recycleCurrentBitmap();
        }
        currentState = state;
        frameIndex = 0;
        frameDue = 0;
        ensureCurrentBitmap();
        velocityX = "walk".equals(state.name) ? dp(state.movementSpeed) : 0;
        invalidate();
    }

    private void ensureCurrentBitmap() {
        if (currentState == null || currentState.frames.isEmpty()) {
            return;
        }
        AnimationFrame frame = currentState.frames.get(Math.min(frameIndex, currentState.frames.size() - 1));
        if (frame.bitmap == null || frame.bitmap.isRecycled()) {
            frame.bitmap = BitmapFactory.decodeFile(frame.file.getAbsolutePath());
        }
        currentBitmap = frame.bitmap;
        if (currentBitmap != null) {
            frameDue = System.currentTimeMillis() + frame.durationMs;
        }
    }

    private void recycleCurrentBitmap() {
        if (currentState != null) {
            for (AnimationFrame frame : currentState.frames) {
                if (frame.bitmap != null && !frame.bitmap.isRecycled()) {
                    frame.bitmap.recycle();
                }
                frame.bitmap = null;
            }
        }
        currentBitmap = null;
    }

    private void speak(String category) {
        ArrayList<String> lines = dialogs.get(category);
        if (lines == null || lines.isEmpty()) {
            String alias = null;
            if ("greet".equals(category)) alias = "greeting";
            else if ("tap".equals(category)) alias = "clicked";
            else if ("walk".equals(category)) alias = "idle_occasionally";
            else if ("drag".equals(category)) alias = "dragged";
            else if ("angry_stomp".equals(category)) alias = "angry";
            else if ("scare".equals(category)) alias = "scared";
            else if ("snack".equals(category)) alias = "eat";
            else if ("bye".equals(category)) alias = "goodbye";
            else if ("stretch".equals(category)) alias = "strech";
            else if ("sleep".equals(category) || "sleepy_nod".equals(category)) alias = "sleepy";
            else if ("cheer".equals(category) || "applause".equals(category)) alias = "happy";
            if (alias != null) lines = dialogs.get(alias);
        }
        if (lines == null || lines.isEmpty()) {
            lines = dialogs.get("tap");
            if (lines == null || lines.isEmpty()) lines = dialogs.get("clicked");
        }
        if (lines != null && !lines.isEmpty()) {
            bubbleText = lines.get(random.nextInt(lines.size())).replace("{hour}", String.valueOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));
            bubbleUntil = System.currentTimeMillis() + 3600L;
        }
        invalidate();
    }

    private void scheduleTick() {
        if (hostResumed && pet != null && getVisibility() == VISIBLE) {
            removeCallbacks(tickRunnable);
            postDelayed(tickRunnable, TICK_MS);
        }
    }

    private void triggerAmbient(long now) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int day = calendar.get(Calendar.DAY_OF_YEAR);
        if (hour == 0 && lastMidnightDay != day && states.containsKey("midnight")) {
            lastMidnightDay = day;
            showState("midnight");
            speak("midnight");
            nextDecision = now + 2600L;
            return;
        }
        String[] candidates = {"jump", "sway", "spin", "peek", "cheer", "sad", "yawn", "snack", "scare", "bye", "rain_shiver", "stretch", "shy", "angry_stomp", "daze", "confused", "giggle", "sleepy_nod", "applause", "downcast", "crouch", "sleep"};
        ArrayList<String> available = new ArrayList<>();
        for (String candidate : candidates) if (states.containsKey(candidate)) available.add(candidate);
        if (available.isEmpty()) {
            showState("walk");
            direction = random.nextBoolean() ? 1 : -1;
            nextDecision = now + 3200L + random.nextInt(4200);
            return;
        }
        String action = available.get(random.nextInt(available.size()));
        showState(action);
        speak(action);
        if ("walk".equals(action)) direction = random.nextBoolean() ? 1 : -1;
        nextDecision = now + 1700L + random.nextInt(3800);
    }

    private void tick() {
        if (!hostResumed || pet == null || getVisibility() != VISIBLE) {
            return;
        }
        long now = System.currentTimeMillis();
        if (currentState != null && now >= frameDue) {
            if (frameIndex + 1 < currentState.frames.size()) {
                frameIndex++;
                ensureCurrentBitmap();
            } else if (currentState.loop) {
                frameIndex = 0;
                ensureCurrentBitmap();
            } else {
                showState("idle");
                nextDecision = now + 1800L;
            }
        }
        if ("walk".equals(currentState == null ? "" : currentState.name) && !dragging) {
            petX += velocityX * direction;
            float minX = dp(6);
            float maxX = Math.max(minX, getWidth() - petWidth() - dp(6));
            if (petX <= minX || petX >= maxX) {
                petX = Math.max(minX, Math.min(maxX, petX));
                direction *= -1;
            }
            if (now >= nextDecision) {
                showState("idle");
                speak("walk");
                nextDecision = now + 1600L + random.nextInt(2600);
            }
        } else if ("idle".equals(currentState == null ? "" : currentState.name) && now >= nextDecision) {
            triggerAmbient(now);
        } else if (currentState != null && currentState.loop && now >= nextDecision && !dragging) {
            showState("idle");
            nextDecision = now + 1800L + random.nextInt(2600);
        }
        if (bubbleUntil > 0 && now >= bubbleUntil) {
            bubbleUntil = 0;
            bubbleText = "";
        }
        invalidate();
        scheduleTick();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        initializePositionIfNeeded();
    }

    private void initializePositionIfNeeded() {
        if (!positionInitialized && getWidth() > 0 && getHeight() > 0) {
            int defaultWidth = petWidth();
            int defaultHeight = petHeight();
            android.content.SharedPreferences preferences = getContext().getSharedPreferences("huanghun_pets", Context.MODE_PRIVATE);
            float savedX = preferences.getFloat("pet_x", Float.NaN);
            float savedY = preferences.getFloat("pet_y", Float.NaN);
            petX = Float.isNaN(savedX) ? Math.max(dp(8), getWidth() - defaultWidth - dp(18)) : savedX;
            petY = Float.isNaN(savedY) ? Math.max(dp(8), getHeight() - defaultHeight - dp(72)) : savedY;
            positionInitialized = true;
        }
        if (positionInitialized) {
            clampPosition();
        }
    }

    private void clampPosition() {
        petX = Math.max(dp(4), Math.min(Math.max(dp(4), getWidth() - petWidth() - dp(4)), petX));
        petY = Math.max(dp(4), Math.min(Math.max(dp(4), getHeight() - petHeight() - dp(4)), petY));
    }

    private int petHeight() {
        return dp(DEFAULT_SIZE_DP);
    }

    private int petWidth() {
        return Math.max(dp(48), Math.round(petHeight() * (frameWidth / (float) frameHeight)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pet == null || currentBitmap == null || currentBitmap.isRecycled()) {
            return;
        }
        petRect.set(petX, petY, petX + petWidth(), petY + petHeight());
        canvas.drawBitmap(currentBitmap, null, petRect, bitmapPaint);
        if (bubbleUntil > System.currentTimeMillis() && !bubbleText.isEmpty()) {
            drawBubble(canvas, bubbleText, petX, petY);
        }
    }

    private void drawBubble(Canvas canvas, String text, float x, float y) {
        float maxWidth = dp(220);
        float textWidth = Math.min(maxWidth, Math.max(dp(72), bubbleTextPaint.measureText(text) + dp(24)));
        float left = Math.max(dp(4), Math.min(getWidth() - textWidth - dp(4), x - dp(52)));
        float bottom = Math.max(dp(44), y - dp(8));
        float top = bottom - dp(38);
        bubblePaint.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(left, top, left + textWidth, bottom), dp(14), dp(14), bubblePaint);
        bubbleTextPaint.setColor(0xff252525);
        String display = text;
        if (bubbleTextPaint.measureText(display) > textWidth - dp(20)) {
            int count = Math.max(1, bubbleTextPaint.breakText(display, true, textWidth - dp(20), null));
            display = display.substring(0, count) + "…";
        }
        float textY = top + dp(24);
        canvas.drawText(display, left + dp(10), textY, bubbleTextPaint);
    }

    private boolean hitPet(float x, float y) {
        return x >= petX - dp(8) && x <= petX + petWidth() + dp(8) && y >= petY - dp(8) && y <= petY + petHeight() + dp(8);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pet == null || getVisibility() != VISIBLE) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!hitPet(event.getX(), event.getY())) {
                    return false;
                }
                downX = event.getX();
                downY = event.getY();
                downPetX = petX;
                downPetY = petY;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.hypot(event.getX() - downX, event.getY() - downY) > dp(8)) {
                    dragging = true;
                    showState("dragged");
                    speak("drag");
                }
                if (dragging) {
                    petX = downPetX + event.getX() - downX;
                    petY = downPetY + event.getY() - downY;
                    clampPosition();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    getContext().getSharedPreferences("huanghun_pets", Context.MODE_PRIVATE).edit()
                            .putFloat("pet_x", petX).putFloat("pet_y", petY).apply();
                    showState("squash");
                    speak("squash");
                    nextDecision = System.currentTimeMillis() + 1800L;
                } else {
                    showState("clicked");
                    speak("tap");
                    nextDecision = System.currentTimeMillis() + 1800L;
                    performClick();
                }
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                showState("idle");
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private static final class AnimationState {
        final String name;
        final boolean loop;
        float movementSpeed = 1.7f;
        final ArrayList<AnimationFrame> frames = new ArrayList<>();

        AnimationState(String name, boolean loop) {
            this.name = name;
            this.loop = loop;
        }
    }

    private static final class AnimationFrame {
        final File file;
        final int durationMs;
        Bitmap bitmap;

        AnimationFrame(File file, int durationMs) {
            this.file = file;
            this.durationMs = durationMs;
        }
    }
}
