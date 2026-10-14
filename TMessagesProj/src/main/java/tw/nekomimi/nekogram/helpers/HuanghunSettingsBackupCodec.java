package tw.nekomimi.nekogram.helpers;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Compresses feature snapshots so they can safely travel in a settings-link query parameter. */
public final class HuanghunSettingsBackupCodec {
    public static final String PREFIX = "hhcfg1:";
    private static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;

    private HuanghunSettingsBackupCodec() {
    }

    public static String encode(JSONObject snapshot) {
        if (snapshot == null) return null;
        try {
            byte[] json = snapshot.toString().getBytes(StandardCharsets.UTF_8);
            if (json.length > MAX_SNAPSHOT_BYTES) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(json);
            }
            return PREFIX + Base64.encodeToString(output.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isEncoded(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static JSONObject decode(String value) {
        if (!isEncoded(value) || value.length() > MAX_SNAPSHOT_BYTES * 2) return null;
        try {
            byte[] compressed = Base64.decode(value.substring(PREFIX.length()), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                while ((read = gzip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_SNAPSHOT_BYTES) return null;
                    output.write(buffer, 0, read);
                }
            }
            return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
