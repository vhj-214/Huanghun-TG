package com.Huanghun.protocol;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Parses the owner-provided JSON .Passkey credential format without logging secrets. */
public final class PasskeyParser {
    private static final int MAX_PASSKEY_BYTES = 32 * 1024;

    private PasskeyParser() {
    }

    public static PasskeyData parse(File passkeyFile) throws Exception {
        if (passkeyFile == null || !passkeyFile.isFile()) {
            throw new Exception("Passkey 文件不存在");
        }
        if (passkeyFile.length() <= 0 || passkeyFile.length() > MAX_PASSKEY_BYTES) {
            throw new Exception("Passkey 文件大小无效");
        }
        byte[] bytes = readFully(passkeyFile);
        JSONObject object = new JSONObject(new String(bytes, StandardCharsets.UTF_8));

        PasskeyData data = new PasskeyData();
        data.credentialId = required(object, "CredentialId");
        data.privateKeyHex = required(object, "PrivateKeyHex").toLowerCase(Locale.ROOT);
        data.userHandle = required(object, "UserHandle");
        data.rpId = required(object, "RpId");
        data.origin = required(object, "Origin");
        data.twoFactorPassword = object.optString("TwoFA", "");
        data.loginFlagsHex = object.optString("LoginFlagsHex", "01");
        data.signCount = object.optInt("SignCount", 0);

        if (!data.credentialId.matches("^[A-Za-z0-9_-]{16,512}$")) {
            throw new Exception("Passkey CredentialId 格式无效");
        }
        if (!data.privateKeyHex.matches("^[0-9a-f]{64}$")) {
            throw new Exception("Passkey 私钥格式无效");
        }
        if (!data.rpId.matches("^[A-Za-z0-9.-]{3,253}$")) {
            throw new Exception("Passkey RpId 格式无效");
        }
        String originHost = data.origin.startsWith("https://") ? data.origin.substring("https://".length()).toLowerCase(Locale.ROOT) : "";
        String normalizedRpId = data.rpId.toLowerCase(Locale.ROOT);
        if (originHost.isEmpty() || !(originHost.equals(normalizedRpId) || originHost.endsWith("." + normalizedRpId))) {
            throw new Exception("Passkey Origin 与 RpId 不匹配");
        }
        String[] handleParts = data.userHandle.split(":", -1);
        if (handleParts.length != 2) {
            throw new Exception("Passkey UserHandle 格式无效");
        }
        try {
            data.datacenterId = Integer.parseInt(handleParts[0]);
            data.userId = Long.parseLong(handleParts[1]);
        } catch (NumberFormatException e) {
            throw new Exception("Passkey UserHandle 格式无效");
        }
        if (data.datacenterId < 1 || data.datacenterId > 5 || data.userId <= 0) {
            throw new Exception("Passkey 数据中心或用户标识无效");
        }
        if (!data.loginFlagsHex.matches("^[0-9a-fA-F]{2}$")) {
            throw new Exception("Passkey 登录标志格式无效");
        }
        if (data.signCount < 0) {
            throw new Exception("Passkey 签名计数无效");
        }
        return data;
    }

    private static String required(JSONObject object, String field) throws Exception {
        String value = object.optString(field, "").trim();
        if (value.isEmpty()) {
            throw new Exception("Passkey 缺少 " + field);
        }
        return value;
    }

    private static byte[] readFully(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > MAX_PASSKEY_BYTES) {
                    throw new Exception("Passkey 文件过大");
                }
            }
            return output.toByteArray();
        }
    }

    public static final class PasskeyData {
        public String credentialId;
        public String privateKeyHex;
        public String userHandle;
        public String rpId;
        public String origin;
        public String twoFactorPassword;
        public String loginFlagsHex;
        public int signCount;
        public int datacenterId;
        public long userId;
    }
}
