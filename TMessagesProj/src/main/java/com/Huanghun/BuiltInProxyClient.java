package com.Huanghun;

import android.content.SharedPreferences;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only client for the Huanghun service API. */
public final class BuiltInProxyClient {
    private static final String API_BASE = "https://xxs100000.alwaysdata.net/api.php";
    private static final String API_KEY = "hh_a471f216de6a72d466184a18b50dde59";
    private static final String PREFS = "mainconfig";
    private static final String MANAGED_KEYS = "huanghun_builtin_proxy_keys";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final AtomicBoolean downloadingApk = new AtomicBoolean(false);

    private BuiltInProxyClient() {}

    public interface ResultCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public static final class ProxySnapshot {
        public final int revision;
        public final List<SharedConfig.ProxyInfo> proxies;
        public ProxySnapshot(int revision, List<SharedConfig.ProxyInfo> proxies) {
            this.revision = revision;
            this.proxies = proxies;
        }
    }

    public static final class RemoteConfig {
        public final int revision;
        public final String latestVersion;
        public final String updateUrl;
        public final String serverDownloadUrl;
        public final String serverApkSha256;
        public final long serverApkSize;
        public final boolean serverDownloadAvailable;
        public final boolean forceUpdate;
        public final String announcement;
        public final int proxyCount;
        public final int proxyRevision;
        public RemoteConfig(JSONObject json) {
            revision = json.optInt("revision", 0);
            latestVersion = json.optString("latest_version", "");
            updateUrl = json.optString("update_url", "");
            serverDownloadUrl = json.optString("server_download_url", "");
            serverApkSha256 = json.optString("server_apk_sha256", "");
            serverApkSize = json.optLong("server_apk_size", 0L);
            serverDownloadAvailable = json.optBoolean("server_download_available", false);
            forceUpdate = json.optBoolean("force_update", false);
            announcement = json.optString("announcement", "");
            proxyCount = json.optInt("proxy_count", 0);
            proxyRevision = json.optInt("proxy_revision", 0);
        }
    }

    public static void fetchProxies(ResultCallback<ProxySnapshot> callback) {
        new Thread(() -> {
            try {
                JSONObject json = request("proxies");
                JSONArray entries = json.optJSONArray("proxies");
                if (entries == null) throw new IllegalArgumentException("服务器返回的代理列表格式无效");
                ArrayList<SharedConfig.ProxyInfo> result = new ArrayList<>();
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.optJSONObject(i);
                    if (item == null) continue;
                    String address = item.optString("address", "").trim();
                    int port = item.optInt("port", 0);
                    if (TextUtils.isEmpty(address) || address.length() > 253 || port < 1 || port > 65535
                            || address.matches(".*[\\s\\x00-\\x1f<>\\\"'].*")) {
                        continue;
                    }
                    result.add(new SharedConfig.ProxyInfo(address, port,
                            item.optString("username", ""), item.optString("password", ""), item.optString("secret", "")));
                }
                callback.onSuccess(new ProxySnapshot(json.optInt("revision", 0), result));
            } catch (Throwable error) {
                callback.onError(safeError(error));
            }
        }, "HuanghunProxyFetch").start();
    }

    public static void fetchConfig(ResultCallback<RemoteConfig> callback) {
        new Thread(() -> {
            try {
                callback.onSuccess(new RemoteConfig(request("config")));
            } catch (Throwable error) {
                callback.onError(safeError(error));
            }
        }, "HuanghunConfigFetch").start();
    }

    public static void downloadServerApk(RemoteConfig config, ResultCallback<File> callback) {
        if (!downloadingApk.compareAndSet(false, true)) {
            callback.onError("已有服务器 APK 下载正在进行，请稍后重试");
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            File temporary = null;
            try {
                if (config == null || !config.serverDownloadAvailable || TextUtils.isEmpty(config.serverDownloadUrl)) {
                    throw new IllegalStateException("服务器尚未上传最新版客户端");
                }
                if (TextUtils.isEmpty(config.serverApkSha256) || !config.serverApkSha256.matches("(?i)[0-9a-f]{64}")) {
                    throw new IllegalStateException("服务器未提供有效的 APK 校验值，已停止安装");
                }
                URL apiUrl = new URL(API_BASE);
                URL downloadUrl = new URL(config.serverDownloadUrl);
                if (!"https".equalsIgnoreCase(downloadUrl.getProtocol())
                        || !apiUrl.getHost().equalsIgnoreCase(downloadUrl.getHost())
                        || (downloadUrl.getPort() != -1 && downloadUrl.getPort() != 443)) {
                    throw new IllegalStateException("服务器下载地址不符合安全策略");
                }
                connection = (HttpURLConnection) downloadUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(60_000);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("X-Huanghun-Key", API_KEY);
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException(status == 404 ? "服务器上的 APK 文件不存在" : "服务器下载失败（HTTP " + status + "）");
                }
                long expectedSize = connection.getContentLengthLong();
                if (expectedSize > 256L * 1024 * 1024) throw new IllegalStateException("APK 文件超过 256 MiB 限制");
                File destination = new File(ApplicationLoader.applicationContext.getCacheDir(), "huanghun-latest.apk");
                temporary = File.createTempFile("huanghun-latest-", ".part", destination.getParentFile());
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long total = 0;
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     DigestInputStream hashing = new DigestInputStream(input, digest);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = hashing.read(buffer)) != -1) {
                        total += count;
                        if (total > 256L * 1024 * 1024) throw new IllegalStateException("APK 文件超过 256 MiB 限制");
                        output.write(buffer, 0, count);
                    }
                    output.getFD().sync();
                }
                if (total == 0 || (expectedSize > 0 && total != expectedSize)
                        || (config.serverApkSize > 0 && total != config.serverApkSize)) {
                    throw new IllegalStateException("下载文件长度不完整或与服务器版本信息不符");
                }
                String actualHash = toHex(digest.digest());
                if (!config.serverApkSha256.equalsIgnoreCase(actualHash)) {
                    throw new IllegalStateException("APK 校验失败，文件可能已损坏");
                }
                if (destination.exists() && !destination.delete()) throw new IllegalStateException("无法替换旧下载文件");
                if (!temporary.renameTo(destination)) throw new IllegalStateException("无法保存已下载的 APK");
                temporary = null;
                callback.onSuccess(destination);
            } catch (Throwable error) {
                callback.onError(safeError(error));
            } finally {
                if (temporary != null) temporary.delete();
                if (connection != null) connection.disconnect();
                downloadingApk.set(false);
            }
        }, "HuanghunApkDownload").start();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
        return result.toString();
    }

    private static JSONObject request(String resource) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_BASE + "?resource=" + resource);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Huanghun-Key", API_KEY);
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            JSONObject json = new JSONObject(body);
            if (status < 200 || status >= 300 || !json.optBoolean("ok", false)) {
                if (status == 401) throw new IllegalStateException("服务认证失败，请联系管理员");
                throw new IllegalStateException(json.optString("error", "服务器暂时无法访问（HTTP " + status + "）"));
            }
            return json;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        try (InputStream input = new BufferedInputStream(stream); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (output.size() + read > 2 * 1024 * 1024) throw new IllegalStateException("服务器响应过大");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String safeError(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (TextUtils.isEmpty(message)) return "连接服务器失败，请检查网络后重试";
        if (error instanceof java.net.SocketTimeoutException) return "连接服务器超时，请稍后重试";
        return message;
    }

    public static void setManagedProxies(List<SharedConfig.ProxyInfo> proxies) {
        JSONArray json = new JSONArray();
        for (SharedConfig.ProxyInfo proxy : proxies) json.put(fingerprint(proxy));
        preferences().edit().putString(MANAGED_KEYS, json.toString()).apply();
        synchronized (BuiltInProxyClient.class) {
            cachedManagedKeys = null;
        }
    }

    private static Set<String> cachedManagedKeys;
    public static boolean isManagedProxy(SharedConfig.ProxyInfo proxy) {
        if (proxy == null) return false;
        Set<String> keys;
        synchronized (BuiltInProxyClient.class) {
            if (cachedManagedKeys == null) {
                cachedManagedKeys = new HashSet<>();
                String saved = preferences().getString(MANAGED_KEYS, "[]");
                try {
                    JSONArray json = new JSONArray(saved);
                    for (int i = 0; i < json.length(); i++) cachedManagedKeys.add(json.optString(i));
                } catch (Exception ignore) {}
            }
            keys = cachedManagedKeys;
        }
        return keys.contains(fingerprint(proxy));
    }

    private static String fingerprint(SharedConfig.ProxyInfo proxy) {
        try {
            String material = safe(proxy.address) + "\n" + proxy.port + "\n" + safe(proxy.username) + "\n"
                    + safe(proxy.password) + "\n" + safe(proxy.secret);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) result.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            return result.toString();
        } catch (Exception ignored) {
            return safe(proxy.address) + ":" + proxy.port;
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
