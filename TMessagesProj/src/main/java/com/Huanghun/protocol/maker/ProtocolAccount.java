package com.Huanghun.protocol.maker;

import java.util.Arrays;

/**
 * 一份可导出的 Telegram 授权数据：256 字节 MTProto auth_key + DC + 账号信息。
 *
 * <p>只在内存里流转，落盘由调用方按选定格式写出。</p>
 */
public final class ProtocolAccount {

    public static final int KEY_SIZE = 256;

    public byte[] authKey;
    public int dcId = 2;
    public long userId;
    public String phone = "";
    public String country = "";
    public int apiId;
    public String apiHash = "";
    public boolean isBot;
    public String source = "";

    public ProtocolAccount() {
    }

    public ProtocolAccount(byte[] authKey, int dcId) {
        this.authKey = authKey;
        this.dcId = dcId;
    }

    public boolean isValid() {
        return authKey != null && authKey.length == KEY_SIZE && dcId >= 1 && dcId <= 5;
    }

    /** 号码 + 国家，用于协议文件名。没有手机号时用 user_id 兜底。 */
    public String displayName() {
        String base;
        if (phone != null && !phone.isEmpty()) {
            base = phone.startsWith("+") ? phone.substring(1) : phone;
        } else if (userId != 0) {
            base = String.valueOf(userId);
        } else {
            base = "account";
        }
        String region = (country == null || country.isEmpty()) ? "未知" : country;
        return sanitize(base) + "_" + sanitize(region);
    }

    public String label() {
        StringBuilder sb = new StringBuilder();
        sb.append(phone == null || phone.isEmpty() ? "未知号码" : phone);
        if (country != null && !country.isEmpty()) {
            sb.append(" · ").append(country);
        }
        sb.append(" · DC").append(dcId);
        if (userId != 0) {
            sb.append(" · uid ").append(userId);
        }
        return sb.toString();
    }

    public String keyFingerprint() {
        if (authKey == null || authKey.length == 0) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(8, authKey.length);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02x", authKey[i]));
        }
        return sb.toString();
    }

    public ProtocolAccount copy() {
        ProtocolAccount out = new ProtocolAccount();
        out.authKey = authKey == null ? null : Arrays.copyOf(authKey, authKey.length);
        out.dcId = dcId;
        out.userId = userId;
        out.phone = phone;
        out.country = country;
        out.apiId = apiId;
        out.apiHash = apiHash;
        out.isBot = isBot;
        out.source = source;
        return out;
    }

    static String sanitize(String text) {
        if (text == null) {
            return "account";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || c == '-' || c == '_' || c == '(' || c == ')' || c == ' ';
            sb.append(ok ? c : '_');
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "account" : out;
    }
}
