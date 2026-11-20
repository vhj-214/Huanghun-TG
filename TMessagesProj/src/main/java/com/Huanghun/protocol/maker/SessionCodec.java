package com.Huanghun.protocol.maker;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.BuildVars;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * session / tdata / json 三种协议格式的读写。
 *
 * <p>写出的 .session 与 Telethon、Pyrogram 各自的 SQLite 结构一致，桌面端与脚本都能直接加载。</p>
 */
public final class SessionCodec {
    public static final String LOCAL_ONLY_JSON_AUTH_KEY_ERROR =
            "该 JSON 不包含本机提取数据必需的 auth_key；JSON 仅支持黄昏本机账号提取生成的数据。非本机 JSON 无法转换或创建通行密钥，请改选本机提取的 JSON，或使用 Session 文件/ZIP。";

    public static final int KIND_TELETHON = 0;
    public static final int KIND_PYROGRAM = 1;
    public static final int KIND_AUTO = 2;

    /** 各 DC 的默认入口，写 session 时用；登录后 Telegram 会自己更新。 */
    private static final String[][] DC_ADDRESS = {
            {},
            {"149.154.175.53", "443"},
            {"149.154.167.51", "443"},
            {"149.154.175.100", "443"},
            {"149.154.167.91", "443"},
            {"91.108.56.130", "443"},
    };

    private SessionCodec() {
    }

    public static String addressForDc(int dcId) {
        return dcId >= 1 && dcId <= 5 ? DC_ADDRESS[dcId][0] : DC_ADDRESS[2][0];
    }

    public static int portForDc(int dcId) {
        return dcId >= 1 && dcId <= 5 ? Integer.parseInt(DC_ADDRESS[dcId][1]) : 443;
    }

    // ------------------------------------------------------------------ 写入

    public static File writeTelethonSession(ProtocolAccount account, File target) throws Exception {
        deleteQuietly(target);
        ensureParent(target);
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(target, null);
        try {
            database.execSQL("CREATE TABLE version (version integer primary key)");
            database.execSQL("INSERT INTO version VALUES (8)");
            database.execSQL("CREATE TABLE sessions ("
                    + "dc_id integer primary key,"
                    + "server_address text,"
                    + "port integer,"
                    + "auth_key blob,"
                    + "takeout_id integer,"
                    + "tmp_auth_key blob)");
            database.execSQL("CREATE TABLE entities ("
                    + "id integer primary key,"
                    + "hash integer not null,"
                    + "username text,"
                    + "phone integer,"
                    + "name text,"
                    + "date integer)");
            database.execSQL("CREATE TABLE update_state ("
                    + "id integer primary key,"
                    + "pts integer,"
                    + "qts integer,"
                    + "date integer,"
                    + "seq integer)");

            ContentValues values = new ContentValues();
            values.put("dc_id", account.dcId);
            values.put("server_address", addressForDc(account.dcId));
            values.put("port", portForDc(account.dcId));
            values.put("auth_key", account.authKey);
            database.insertOrThrow("sessions", null, values);
        } finally {
            database.close();
        }
        return target;
    }

    public static File writePyrogramSession(ProtocolAccount account, File target) throws Exception {
        deleteQuietly(target);
        ensureParent(target);
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(target, null);
        try {
            database.execSQL("CREATE TABLE sessions ("
                    + "dc_id INTEGER PRIMARY KEY,"
                    + "api_id INTEGER,"
                    + "test_mode INTEGER,"
                    + "auth_key BLOB,"
                    + "date INTEGER NOT NULL,"
                    + "user_id INTEGER,"
                    + "is_bot INTEGER)");
            database.execSQL("CREATE TABLE peers ("
                    + "id INTEGER PRIMARY KEY,"
                    + "access_hash INTEGER,"
                    + "type INTEGER NOT NULL,"
                    + "username TEXT,"
                    + "phone_number TEXT,"
                    + "last_update_on INTEGER NOT NULL DEFAULT (CAST(STRFTIME('%s', 'now') AS INTEGER)))");
            database.execSQL("CREATE TABLE version (number INTEGER PRIMARY KEY)");
            database.execSQL("CREATE INDEX idx_peers_id ON peers (id)");
            database.execSQL("CREATE INDEX idx_peers_username ON peers (username)");
            database.execSQL("CREATE INDEX idx_peers_phone_number ON peers (phone_number)");
            database.execSQL("INSERT INTO version VALUES (3)");

            ContentValues values = new ContentValues();
            values.put("dc_id", account.dcId);
            values.put("api_id", account.apiId);
            values.put("test_mode", 0);
            values.put("auth_key", account.authKey);
            values.put("date", System.currentTimeMillis() / 1000L);
            values.put("user_id", account.userId);
            values.put("is_bot", account.isBot ? 1 : 0);
            database.insertOrThrow("sessions", null, values);
        } finally {
            database.close();
        }
        return target;
    }

    public static File writeJson(ProtocolAccount account, File target) throws Exception {
        ensureParent(target);
        JSONObject payload = new JSONObject();
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage() == null || locale.getLanguage().isEmpty()
                ? "en" : locale.getLanguage();
        String sessionName = target.getName();
        if (sessionName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            sessionName = sessionName.substring(0, sessionName.length() - 5);
        }
        payload.put("auth_key", hex(account.authKey));
        payload.put("dc_id", account.dcId);
        payload.put("user_id", account.userId);
        payload.put("api_id", account.apiId);
        payload.put("api_hash", account.apiHash);
        payload.put("app_hash", account.apiHash);
        payload.put("device_model", Build.MODEL == null ? "Android" : Build.MODEL);
        payload.put("system_version", Build.VERSION.RELEASE == null ? "Android" : Build.VERSION.RELEASE);
        payload.put("app_version", BuildVars.BUILD_VERSION_STRING + " Android");
        payload.put("system_lang_code", locale.toLanguageTag());
        payload.put("lang_pack", "android");
        payload.put("lang_code", language);
        payload.put("pid", android.os.Process.myPid());
        payload.put("is_bot", account.isBot);
        payload.put("twofa", JSONObject.NULL);
        payload.put("password", JSONObject.NULL);
        payload.put("app_id", account.apiId);
        payload.put("session_file", sessionName);
        payload.put("device", Build.MODEL == null ? "Android" : Build.MODEL);
        payload.put("sex", JSONObject.NULL);
        payload.put("avatar", "img/default.png");
        payload.put("username", account.username == null ? "" : account.username);
        payload.put("package_id", "");
        payload.put("installer", "");
        payload.put("ipv6", false);
        payload.put("SDK", "Android " + Build.VERSION.SDK_INT);
        payload.put("sdk", "Android " + Build.VERSION.SDK_INT);
        payload.put("system_lang_pack", locale.toLanguageTag());
        payload.put("premium", account.premium == null ? JSONObject.NULL : account.premium);
        payload.put("reg_time", JSONObject.NULL);
        if (account.phone != null && !account.phone.isEmpty()) {
            payload.put("phone", account.phone);
        }
        if (account.country != null && !account.country.isEmpty()) {
            payload.put("country", account.country);
        }
        payload.put("exported_at", System.currentTimeMillis() / 1000L);

        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(payload.toString(2).getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
        return target;
    }

    // ------------------------------------------------------------------ 读取

    /**
     * 读取 .session，自动识别 Telethon / Pyrogram 结构。
     */
    public static List<ProtocolAccount> readSession(File file, int kind) throws Exception {
        List<String> tables = listTables(file);
        boolean hasSessions = tables.contains("sessions");
        if (!hasSessions) {
            throw new Exception("不是有效的 session 文件：" + file.getName());
        }
        if (kind == KIND_AUTO) {
            kind = tables.contains("peers") ? KIND_PYROGRAM : KIND_TELETHON;
        }
        return kind == KIND_PYROGRAM ? readPyrogram(file) : readTelethon(file);
    }

    private static List<ProtocolAccount> readTelethon(File file) throws Exception {
        List<String> columns = listColumns(file, "sessions");
        List<ProtocolAccount> result = new ArrayList<>();
        SQLiteDatabase database = openReadOnly(file);
        boolean completed = false;
        try {
            try (Cursor cursor = database.rawQuery(
                    "SELECT dc_id, auth_key FROM sessions", null)) {
                while (cursor.moveToNext()) {
                    byte[] key = cursor.isNull(1) ? null : cursor.getBlob(1);
                    if (key == null || key.length != ProtocolAccount.KEY_SIZE) {
                        if (key != null) java.util.Arrays.fill(key, (byte) 0);
                        continue;
                    }
                    ProtocolAccount account = new ProtocolAccount(key, cursor.getInt(0));
                    account.source = file.getName();
                    result.add(account);
                }
            }
            if (result.isEmpty()) {
                throw new Exception(".session 中没有有效的 256 字节授权密钥");
            }
            // Telethon's entities table contains arbitrary users/chats, not a reliable current-user id.
            // Leave userId unset rather than labeling an unrelated entity as the authenticated account.
            completed = true;
            return result;
        } finally {
            database.close();
            if (!completed) zeroAuthKeys(result);
        }
    }

    private static List<ProtocolAccount> readPyrogram(File file) throws Exception {
        List<ProtocolAccount> result = new ArrayList<>();
        SQLiteDatabase database = openReadOnly(file);
        boolean completed = false;
        try {
            try (Cursor cursor = database.rawQuery(
                    "SELECT dc_id, auth_key, user_id, api_id, is_bot FROM sessions", null)) {
                while (cursor.moveToNext()) {
                    byte[] key = cursor.isNull(1) ? null : cursor.getBlob(1);
                    if (key == null || key.length != ProtocolAccount.KEY_SIZE) {
                        if (key != null) java.util.Arrays.fill(key, (byte) 0);
                        continue;
                    }
                    ProtocolAccount account = new ProtocolAccount(key, cursor.getInt(0));
                    account.userId = cursor.getLong(2);
                    account.apiId = cursor.getInt(3);
                    account.isBot = cursor.getInt(4) != 0;
                    account.source = file.getName();
                    result.add(account);
                }
            }
            if (result.isEmpty()) {
                throw new Exception(".session 中没有有效的 256 字节授权密钥");
            }
            completed = true;
            return result;
        } finally {
            database.close();
            if (!completed) zeroAuthKeys(result);
        }
    }

    private static void zeroAuthKeys(List<ProtocolAccount> accounts) {
        for (ProtocolAccount account : accounts) {
            if (account != null && account.authKey != null) {
                java.util.Arrays.fill(account.authKey, (byte) 0);
            }
        }
    }

    /** 读取 json（支持单个对象或数组，字段与转换工具保持一致）。 */
    public static List<ProtocolAccount> readJson(File file) throws Exception {
        String text = new String(readAll(file), StandardCharsets.UTF_8);
        Object root;
        try {
            String trimmed = text.trim();
            if (!trimmed.isEmpty() && trimmed.charAt(0) == '\ufeff') {
                trimmed = trimmed.substring(1).trim();
            }
            if (trimmed.isEmpty()) {
                throw new Exception("JSON 文件为空");
            }
            root = trimmed.startsWith("[") ? new JSONArray(trimmed) : new JSONObject(trimmed);
        } catch (Exception parseError) {
            throw new Exception("无法直接解析该 JSON；如果文件经过密码加密，请提供文件密码并先解密，或选择未加密的协议 JSON。", parseError);
        }
        List<ProtocolAccount> result = new ArrayList<>();
        boolean containsNonLocalEntry = false;
        if (root instanceof JSONArray) {
            JSONArray array = (JSONArray) root;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (!addJsonAccount(item, file, result)) {
                    containsNonLocalEntry = true;
                }
            }
        } else {
            containsNonLocalEntry = !addJsonAccount((JSONObject) root, file, result);
        }
        if (containsNonLocalEntry) {
            zeroAuthKeys(result);
            result.clear();
            throw new Exception(LOCAL_ONLY_JSON_AUTH_KEY_ERROR);
        }
        if (result.isEmpty()) {
            throw new Exception(LOCAL_ONLY_JSON_AUTH_KEY_ERROR);
        }
        return result;
    }

    private static boolean addJsonAccount(JSONObject item, File sourceFile, List<ProtocolAccount> out) {
        if (item == null || sourceFile == null) {
            return false;
        }
        byte[] key = null;
        try {
            key = item.has("auth_key") ? unhex(item.optString("auth_key", "")) : null;
            if (key == null || key.length != ProtocolAccount.KEY_SIZE) {
                if (key != null) {
                    java.util.Arrays.fill(key, (byte) 0);
                }
                return false;
            }
            int dcId = item.optInt("dc_id", 2);
            ProtocolAccount account = new ProtocolAccount(key, dcId);
            account.userId = item.optLong("user_id", 0);
            account.apiId = item.optInt("api_id", item.optInt("app_id", 0));
            account.apiHash = item.optString("api_hash", item.optString("app_hash", ""));
            account.isBot = item.optBoolean("is_bot", false);
            account.phone = item.optString("phone", "");
            account.country = item.optString("country", "");
            account.username = item.optString("username", "");
            account.premium = item.isNull("premium") || !item.has("premium")
                    ? null : item.optBoolean("premium", false);
            account.source = sourceFile.getName();
            out.add(account);
            return true;
        } catch (Exception ignore) {
            if (key != null) java.util.Arrays.fill(key, (byte) 0);
            return false;
        }
    }

    // ------------------------------------------------------------------ 工具

    static boolean isSqliteDatabase(File file) {
        if (file == null || !file.isFile() || file.length() < 16) {
            return false;
        }
        byte[] signature = new byte[]{'S', 'Q', 'L', 'i', 't', 'e', ' ', 'f', 'o', 'r', 'm', 'a', 't', ' ', '3', 0};
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            for (byte expected : signature) {
                if (input.read() != (expected & 0xff)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static SQLiteDatabase openReadOnly(File file) throws Exception {
        return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    private static List<String> listTables(File file) throws Exception {
        List<String> result = new ArrayList<>();
        try (SQLiteDatabase database = openReadOnly(file);
             Cursor cursor = database.rawQuery(
                     "SELECT name FROM sqlite_master WHERE type='table'", null)) {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0));
            }
        }
        return result;
    }

    private static List<String> listColumns(File file, String table) {
        List<String> result = new ArrayList<>();
        try (SQLiteDatabase database = openReadOnly(file);
             Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(1));
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    static void ensureParent(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
    }

    static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        String[] suffixes = {"", "-wal", "-shm", "-journal"};
        for (String suffix : suffixes) {
            File candidate = new File(file.getAbsolutePath() + suffix);
            if (candidate.exists()) {
                //noinspection ResultOfMethodCallIgnored
                candidate.delete();
            }
        }
    }

    public static String hex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static byte[] unhex(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.trim();
        if (clean.length() % 2 != 0) {
            return null;
        }
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(clean.charAt(i * 2), 16);
            int low = Character.digit(clean.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    static byte[] readAll(File file) throws Exception {
        if (file == null || !file.isFile()) {
            throw new Exception("文件不存在");
        }
        long length = file.length();
        if (length <= 0 || length > 64L * 1024L * 1024L) {
            throw new Exception("文件大小异常");
        }
        byte[] buffer = new byte[(int) length];
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < buffer.length) {
                int read = input.read(buffer, offset, buffer.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        return buffer;
    }
}
