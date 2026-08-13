package com.Huanghun.protocol;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

public final class ProtocolParser {
    private ProtocolParser() {
    }

    public static final class SessionData {
        public int dcId = 2;
        public byte[] authKey;
        public long userId = 0;
        public String address = "";
        public int port = 443;
    }

    public static SessionData parseTelethonSession(File sessionFile) throws Exception {
        if (sessionFile == null || !sessionFile.isFile()) {
            throw new Exception("session 文件不存在");
        }
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                sessionFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            SessionData data = new SessionData();
            
            // 尝试从 sessions 表读取
            try (Cursor cursor = database.rawQuery(
                    "SELECT dc_id, server_address, port, auth_key FROM sessions", null)) {
                if (cursor.moveToFirst()) {
                    data.dcId = cursor.getInt(0);
                    data.address = cursor.getString(1);
                    data.port = cursor.getInt(2);
                    data.authKey = cursor.getBlob(3);
                }
            } catch (Exception ignored) {}

            // 尝试从 entities 或 user 表读取 user_id
            try (Cursor cursor = database.rawQuery(
                    "SELECT id FROM entities LIMIT 1", null)) {
                if (cursor.moveToFirst()) {
                    data.userId = cursor.getLong(0);
                }
            } catch (Exception ignored) {
                try (Cursor cursor = database.rawQuery(
                        "SELECT id FROM sqlite_master WHERE type='table' AND name='sent_files'", null)) {
                    // fallback
                } catch (Exception ignored2) {}
            }

            if (data.authKey == null || data.authKey.length != 256) {
                // Some Telethon versions expose the key through the same sessions table
                // without the other fields. A missing or malformed key is never replaced
                // with synthetic data because that would create a false login success.
                try (Cursor cursor = database.rawQuery(
                        "SELECT auth_key FROM sessions LIMIT 1", null)) {
                    if (cursor.moveToFirst()) {
                        data.authKey = cursor.getBlob(0);
                    }
                } catch (Exception ignored) {}
            }

            if (data.authKey == null || data.authKey.length != 256) {
                throw new Exception(".session 中没有有效的 256 字节 MTProto 授权密钥");
            }
            if (data.dcId <= 0 || data.dcId > 5) {
                throw new Exception(".session 中的数据中心编号无效");
            }
            if (data.port <= 0 || data.port > 65535) {
                throw new Exception(".session 中的服务器端口无效");
            }

            return data;
        } finally {
            database.close();
        }
    }
}
