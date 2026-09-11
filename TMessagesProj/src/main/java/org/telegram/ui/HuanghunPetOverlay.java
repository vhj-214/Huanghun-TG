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
    private static final long CARE_FIRST_AFTER_MS = 10 * 60 * 1000L;
    private static final long CARE_REPEAT_AFTER_MS = 35 * 60 * 1000L;
    private static final String[] LOCAL_CARE_REPLIES = {
            "辛苦啦，已经陪你一会儿了。",
            "忙了这么久，要不要休息一下？",
            "我一直在这里陪着你。",
            "别太累了，给自己一点时间。",
            "你认真工作的样子，我有看到哦。",
            "喝口水，伸个懒腰吧。",
            "今天也辛苦你了。",
            "一直在线陪着我，我会有点开心呢。",
            "如果累了，可以靠近我一会儿。",
            "你还在忙呀？我会安静陪着你的。"
    };
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
    private boolean partialBody;
    private boolean showDialogEnabled = true;
    private boolean hourlyChimeEnabled = true;
    private boolean autoWalkEnabled = true;
    private boolean allowSleepEnabled = true;
    private boolean midnightEnabled = true;
    private boolean sleeping;
    private int petSizeDp = DEFAULT_SIZE_DP;
    private int minSizeDp = 80;
    private int maxSizeDp = 260;
    private int lastHourlyHour = -1;
    private int lastHourlyDay = -1;
    private float petX;
    private float petY;
    private float velocityX;
    private float velocityY;
    private float roamTargetX;
    private float roamTargetY;
    private boolean hasRoamTarget;
    private boolean roaming;
    private long onlineSinceElapsed;
    private long lastCareElapsed;
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
        onlineSinceElapsed = 0L;
        lastCareElapsed = 0L;
    }

    public void onHostResume() {
        hostResumed = true;
        if (onlineSinceElapsed == 0L) onlineSinceElapsed = android.os.SystemClock.elapsedRealtime();
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
        android.content.SharedPreferences preferences = getContext().getSharedPreferences("huanghun_pets", Context.MODE_PRIVATE);
        showDialogEnabled = preferences.getBoolean("show_dialog", true);
        hourlyChimeEnabled = preferences.getBoolean("hourly_chime", true);
        autoWalkEnabled = preferences.getBoolean("auto_walk", true);
        allowSleepEnabled = preferences.getBoolean("allow_sleep", true);
        midnightEnabled = preferences.getBoolean("allow_midnight_easteregg", true);
        String active = preferences.getString("active_id", "");
        if (active == null) {
            active = "";
        }
        if (active.equals(petId) && pet != null) {
            petSizeDp = Math.max(minSizeDp, Math.min(maxSizeDp, preferences.getInt("pet_size", petSizeDp)));
            if (!showDialogEnabled) {
                bubbleUntil = 0;
                bubbleText = "";
            }
            clampPosition();
            invalidate();
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
            onlineSinceElapsed = android.os.SystemClock.elapsedRealtime();
            lastCareElapsed = 0L;
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
        if (frameWidth == 1 && frameHeight == 1) {
            Bitmap preview = BitmapFactory.decodeFile(pet.preview().getAbsolutePath());
            if (preview != null) {
                frameWidth = Math.max(1, preview.getWidth());
                frameHeight = Math.max(1, preview.getHeight());
                preview.recycle();
            }
        }
        partialBody = manifest.optBoolean("partial_body", false);
        showDialogEnabled = showDialogEnabled && manifest.optBoolean("supports_dialog", true);
        hourlyChimeEnabled = hourlyChimeEnabled && manifest.optBoolean("supports_hourly_chime", true);
        midnightEnabled = midnightEnabled && manifest.optBoolean("supports_midnight_easteregg", true);
        minSizeDp = Math.max(48, manifest.optInt("min_size", 80));
        maxSizeDp = Math.max(minSizeDp, manifest.optInt("max_size", 260));
        int manifestSize = manifest.optInt("default_size", DEFAULT_SIZE_DP);
        petSizeDp = getContext().getSharedPreferences("huanghun_pets", Context.MODE_PRIVATE).getInt("pet_size", Math.max(minSizeDp, Math.min(maxSizeDp, Math.min(DEFAULT_SIZE_DP, manifestSize))));
        JSONObject manifestAnimations = manifest.optJSONObject("animations");
        if (manifestAnimations != null) {
            JSONArray names = manifestAnimations.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.optString(i, "");
                    AnimationState state = readManifestAnimation(name, manifestAnimations.optJSONObject(name));
                    if (state != null) registerState(name, state);
                }
            }
        }
        JSONArray manifestAnimationNames = manifest.optJSONArray("animations");
        if (manifestAnimationNames != null) {
            for (int i = 0; i < manifestAnimationNames.length(); i++) {
                String name = manifestAnimationNames.optString(i, "");
                if (name.isEmpty() || states.containsKey(name)) continue;
                AnimationState state = readDirectoryAnimation(name);
                if (state != null) registerState(name, state);
            }
        }
        File animationConfig = new File(pet.directory, "animations/animations.json");
        if (animationConfig.isFile()) loadAnimationConfig(animationConfig);
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
                                registerState(name, state);
                            }
                        }
                    }
                }
            }
        }
        if (!states.containsKey("idle")) {
            AnimationState fallback = readDirectoryAnimation("idle");
            if (fallback != null) {
                registerState("idle", fallback);
            }
        }
        if (!states.containsKey("idle")) {
            throw new IllegalStateException("桌宠缺少 idle 动画");
        }
    }

    private void registerState(String name, AnimationState state) {
        if (partialBody && ("walk".equals(name) || "jump".equals(name) || "spin".equals(name)
                || "crouch".equals(name) || "squat_rest".equals(name) || "peek".equals(name)
                || "angry_stomp".equals(name) || "stamp_angry".equals(name))) {
            return;
        }
        states.put(name, state);
        String alias = null;
        if ("idle_breath".equals(name)) alias = "idle";
        else if ("touch_click".equals(name)) alias = "clicked";
        else if ("drag_move".equals(name)) alias = "dragged";
        else if ("flatten_bounce".equals(name)) alias = "squish";
        else if ("midnight_easteregg".equals(name)) alias = "midnight";
        else if ("squat_rest".equals(name)) alias = "crouch";
        else if ("sad_emo".equals(name)) alias = "sad";
        else if ("eat_snack".equals(name)) alias = "snack";
        else if ("scare_shake".equals(name)) alias = "scare";
        else if ("wave_goodbye".equals(name)) alias = "bye";
        else if ("stretch_sun".equals(name)) alias = "stretch";
        else if ("shy_dodge".equals(name)) alias = "shy";
        else if ("stamp_angry".equals(name)) alias = "angry_stomp";
        else if ("scratch_head".equals(name)) alias = "confused";
        else if ("giggle_cover".equals(name)) alias = "giggle";
        else if ("clap".equals(name)) alias = "applause";
        else if ("lose_heart".equals(name)) alias = "downcast";
        if (alias != null) {
            AnimationState aliased = new AnimationState(alias, state.loop);
            aliased.canInterrupt = state.canInterrupt;
            aliased.trigger = state.trigger;
            aliased.movementSpeed = state.movementSpeed;
            aliased.weight = state.weight;
            aliased.frames.addAll(state.frames);
            states.put(alias, aliased);
        }
    }

    private AnimationState readManifestAnimation(String name, JSONObject definition) throws Exception {
        if (definition == null) return null;
        JSONArray frames = definition.optJSONArray("frames");
        if (frames == null || frames.length() == 0) return null;
        int fps = Math.max(1, Math.min(30, definition.optInt("fps", 6)));
        AnimationState state = new AnimationState(name, definition.optBoolean("loop", true));
        state.canInterrupt = definition.optBoolean("can_interrupt", true);
        state.trigger = definition.optString("trigger", "");
        state.weight = Math.max(0, definition.optInt("weight", 1));
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

    private void loadAnimationConfig(File file) throws Exception {
        JSONObject config = readJson(file);
        JSONArray names = config.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, "");
            JSONObject definition = config.optJSONObject(name);
            if (definition == null) continue;
            File directory = new File(pet.directory, definition.optString("frame_dir", ""));
            String root = pet.directory.getCanonicalPath() + File.separator;
            if (!directory.getCanonicalPath().startsWith(root)) continue;
            File[] files = directory.listFiles((dir, filename) -> {
                String lower = filename.toLowerCase(Locale.ROOT);
                return lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            });
            if (files == null || files.length == 0) continue;
            java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            int frameCount = Math.min(files.length, Math.max(1, definition.optInt("frame_count", files.length)));
            int fps = Math.max(1, Math.min(30, definition.optInt("fps", 6)));
            AnimationState state = new AnimationState(name, definition.optBoolean("loop", true));
            state.canInterrupt = definition.optBoolean("interruptible", true);
            state.trigger = definition.optString("trigger_time", "");
            state.weight = Math.max(0, definition.optInt("weight", 1));
            for (int j = 0; j < frameCount; j++) {
                state.frames.add(new AnimationFrame(files[j], Math.max(MIN_FRAME_MS, Math.min(MAX_FRAME_MS, 1000 / fps))));
            }
            registerState(name, state);
        }
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
        state.canInterrupt = object.optBoolean("interruptible", object.optBoolean("can_interrupt", true));
        state.trigger = object.optString("trigger_time", object.optString("trigger", ""));
        state.weight = Math.max(0, object.optInt("weight", 1));
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
        if (currentState != null && currentState != state && !currentState.canInterrupt
                && (currentState.loop || frameIndex + 1 < currentState.frames.size())) {
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
        velocityY = 0;
        invalidate();
    }

    private void chooseRoamTarget() {
        float marginX = dp(6);
        float marginY = dp(8);
        float maxX = Math.max(marginX, getWidth() - petWidth() - marginX);
        float maxY = Math.max(marginY, getHeight() - petHeight() - marginY);
        roamTargetX = marginX + random.nextFloat() * Math.max(1, maxX - marginX);
        roamTargetY = marginY + random.nextFloat() * Math.max(1, maxY - marginY);
        hasRoamTarget = true;
    }

    private void startRoaming(long now) {
        if (!states.containsKey("walk") || getWidth() <= 0 || getHeight() <= 0) return;
        AnimationState walk = states.get("walk");
        velocityX = dp(Math.max(0.8f, walk.movementSpeed));
        roaming = true;
        showState("walk");
        chooseRoamTarget();
        nextDecision = now + 3500L + random.nextInt(5500);
    }

    private void updateRoaming(long now) {
        if (dragging) return;
        if (!roaming) {
            if (now >= nextDecision) startRoaming(now);
            return;
        }
        if (!hasRoamTarget) chooseRoamTarget();
        float dx = roamTargetX - petX;
        float dy = roamTargetY - petY;
        float distance = (float) Math.hypot(dx, dy);
        float speed = Math.max(dp(0.8f), velocityX);
        if (distance <= dp(5) || now >= nextDecision) {
            petX = roamTargetX;
            petY = roamTargetY;
            clampPosition();
            roaming = false;
            hasRoamTarget = false;
            triggerAmbient(now);
            return;
        }
        float step = Math.min(distance, speed);
        petX += dx / distance * step;
        petY += dy / distance * step;
        direction = dx < 0 ? -1 : 1;
        clampPosition();
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
        if (!showDialogEnabled) return;
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
            if ((lines == null || lines.isEmpty()) && "greet".equals(category)) lines = dialogs.get("idle_breath");
            if ((lines == null || lines.isEmpty()) && "tap".equals(category)) lines = dialogs.get("touch_click");
        }
        if (lines == null || lines.isEmpty()) {
            lines = dialogs.get("tap");
            if (lines == null || lines.isEmpty()) lines = dialogs.get("clicked");
            if (lines == null || lines.isEmpty()) lines = dialogs.get("touch_click");
        }
        if (lines != null && !lines.isEmpty()) {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            String period = hour < 12 ? "上午" : (hour < 18 ? "下午" : "晚上");
            bubbleText = lines.get(random.nextInt(lines.size()))
                    .replace("{hour}", String.valueOf(hour))
                    .replace("{period}", period);
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

    private void maybeShowLocalCare(long nowElapsed) {
        if (!showDialogEnabled || onlineSinceElapsed == 0L) return;
        long onlineFor = nowElapsed - onlineSinceElapsed;
        if (onlineFor < CARE_FIRST_AFTER_MS) return;
        if (lastCareElapsed != 0L && nowElapsed - lastCareElapsed < CARE_REPEAT_AFTER_MS) return;
        bubbleText = LOCAL_CARE_REPLIES[random.nextInt(LOCAL_CARE_REPLIES.length)];
        bubbleUntil = System.currentTimeMillis() + 5200L;
        lastCareElapsed = nowElapsed;
        invalidate();
    }

    private void triggerAmbient(long now) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int day = calendar.get(Calendar.DAY_OF_YEAR);
        if (midnightEnabled && hour == 0 && lastMidnightDay != day && states.containsKey("midnight")) {
            lastMidnightDay = day;
            showState("midnight");
            speak("midnight");
            nextDecision = now + 2600L;
            return;
        }
        ArrayList<String> available = new ArrayList<>();
        ArrayList<AnimationState> availableStates = new ArrayList<>();
        for (Map.Entry<String, AnimationState> entry : states.entrySet()) {
            String candidate = entry.getKey();
            AnimationState state = entry.getValue();
            if (state == null || state.trigger.length() > 0 || "idle".equals(candidate)
                    || "walk".equals(candidate) || "clicked".equals(candidate)
                    || "dragged".equals(candidate) || "midnight".equals(candidate)
                    || (!allowSleepEnabled && "sleep".equals(candidate))) continue;
            if (!availableStates.contains(state)) {
                available.add(candidate);
                availableStates.add(state);
            }
        }
        if (available.isEmpty()) {
            showState("idle");
            nextDecision = now + 3200L + random.nextInt(4200);
            return;
        }
        int totalWeight = 0;
        for (AnimationState state : availableStates) totalWeight += Math.max(1, state.weight);
        int pick = random.nextInt(Math.max(1, totalWeight));
        String action = available.get(0);
        for (int i = 0; i < available.size(); i++) {
            pick -= Math.max(1, availableStates.get(i).weight);
            if (pick < 0) { action = available.get(i); break; }
        }
        showState(action);
        sleeping = "sleep".equals(action);
        speak(action);
        if ("walk".equals(action)) direction = random.nextBoolean() ? 1 : -1;
        nextDecision = now + 1700L + random.nextInt(3800);
    }

    private void tick() {
        if (!hostResumed || pet == null || getVisibility() != VISIBLE) {
            return;
        }
        long now = System.currentTimeMillis();
        maybeShowLocalCare(android.os.SystemClock.elapsedRealtime());
        maybeHourlyChime();
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
        if (sleeping) {
            if (bubbleUntil > 0 && now >= bubbleUntil) {
                bubbleUntil = 0;
                bubbleText = "";
            }
            invalidate();
            scheduleTick();
            return;
        }
        updateRoaming(now);
        if (!roaming && !dragging && currentState != null && currentState.loop && now >= nextDecision) {
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

    private void maybeHourlyChime() {
        if (!hourlyChimeEnabled) return;
        Calendar calendar = Calendar.getInstance();
        if (calendar.get(Calendar.MINUTE) != 0) return;
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int day = calendar.get(Calendar.DAY_OF_YEAR);
        if (hour == lastHourlyHour && day == lastHourlyDay) return;
        if (!dialogs.containsKey("hourly")) return;
        lastHourlyHour = hour;
        lastHourlyDay = day;
        speak("hourly");
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
        return dp(petSizeDp);
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
        // The bubble is anchored to the rendered top edge, never to the feet.
        float top = Math.max(dp(4), y - dp(46));
        float bottom = top + dp(38);
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

    private void playRandomTapAction() {
        String[] candidates = {"clicked", "jump", "squish", "scare", "cheer", "shy", "giggle"};
        ArrayList<String> available = new ArrayList<>();
        for (String candidate : candidates) if (states.containsKey(candidate)) available.add(candidate);
        String action = available.isEmpty() ? "clicked" : available.get(random.nextInt(available.size()));
        showState(action);
        speak(action.equals("clicked") ? "tap" : action);
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
                if (sleeping && !dragging) {
                    sleeping = false;
                    showState("idle");
                    speak("greeting");
                    nextDecision = System.currentTimeMillis() + 1800L;
                } else if (dragging) {
                    getContext().getSharedPreferences("huanghun_pets", Context.MODE_PRIVATE).edit()
                            .putFloat("pet_x", petX).putFloat("pet_y", petY).apply();
                    showState("squash");
                    speak("squash");
                    nextDecision = System.currentTimeMillis() + 1800L;
                } else {
                    playRandomTapAction();
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
        boolean canInterrupt = true;
        String trigger = "";
        float movementSpeed = 1.7f;
        int weight = 1;
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
