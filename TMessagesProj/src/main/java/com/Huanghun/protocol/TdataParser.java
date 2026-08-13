package com.Huanghun.protocol;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal, read-only Telegram Desktop tdata reader.
 *
 * <p>This reader supports the standard Telegram Desktop tdata layout without a
 * local passcode. It decrypts data only in the application's private temporary
 * import directory, extracts the 256-byte MTProto authorization key, and returns
 * it to the existing server-verification path. No credentials are logged or
 * persisted by this parser.</p>
 */
public final class TdataParser {
    private static final int MAX_FILE_SIZE = 32 * 1024 * 1024;
    private static final int AUTHORIZATION_BLOCK = 0x4b;

    private TdataParser() {
    }

    public static ArrayList<ProtocolParser.SessionData> parse(File tdataDirectory) throws Exception {
        if (tdataDirectory == null || !tdataDirectory.isDirectory()) {
            throw new IOException("tdata 目录不存在");
        }
        File keyDatas = new File(tdataDirectory, "key_datas");
        if (!keyDatas.isFile()) {
            throw new IOException("tdata 中缺少 key_datas");
        }

        byte[] localKey = readLocalKey(keyDatas);
        File[] accountFiles = tdataDirectory.listFiles(file -> file.isFile()
                && !"key_datas".equals(file.getName())
                && file.getName().endsWith("s"));
        if (accountFiles == null || accountFiles.length == 0) {
            throw new IOException("tdata 中未找到账号授权数据");
        }
        Arrays.sort(accountFiles, Comparator.comparing(File::getName));

        ArrayList<ProtocolParser.SessionData> result = new ArrayList<>();
        for (File accountFile : accountFiles) {
            try {
                ProtocolParser.SessionData session = readAccountAuthorization(accountFile, localKey);
                if (session != null) {
                    result.add(session);
                }
            } catch (Exception ignore) {
                // A multi-account tdata may contain stale or unrelated files.
                // Other valid account files must still be considered.
            }
        }
        if (result.isEmpty()) {
            throw new IOException("tdata 中没有可用的 MTProto 授权密钥");
        }
        return result;
    }

    private static byte[] readLocalKey(File keyDatas) throws Exception {
        byte[] payload = readTdfPayload(keyDatas);
        Cursor cursor = new Cursor(payload);
        byte[] salt = cursor.readByteArray();
        byte[] encryptedKey = cursor.readByteArray();
        byte[] encryptedInfo = cursor.readByteArray();
        cursor.requireFinished();

        byte[] passcodeHash = digest("SHA-512", concat(salt, salt));
        byte[] passcodeKey = pbkdf2Sha512(passcodeHash, salt, 1, 256);
        byte[] localKey = decryptLocal(encryptedKey, passcodeKey);
        if (localKey.length != 256) {
            throw new IOException("tdata 本地密钥长度无效");
        }
        // Verify the key before inspecting account data. The resulting metadata
        // is intentionally discarded because it contains no authentication data.
        decryptLocal(encryptedInfo, localKey);
        return localKey;
    }

    private static ProtocolParser.SessionData readAccountAuthorization(File accountFile, byte[] localKey) throws Exception {
        byte[] payload = readTdfPayload(accountFile);
        Cursor wrapper = new Cursor(payload);
        byte[] encrypted = wrapper.readByteArray();
        wrapper.requireFinished();
        byte[] records = decryptLocal(encrypted, localKey);
        Cursor cursor = new Cursor(records);

        while (cursor.remaining() > 0) {
            long blockId = cursor.readUInt32BE();
            byte[] block = cursor.readByteArray();
            if (blockId != AUTHORIZATION_BLOCK) {
                continue;
            }
            ProtocolParser.SessionData session = parseAuthorizationBlock(block);
            if (session != null) {
                return session;
            }
        }
        return null;
    }

    private static ProtocolParser.SessionData parseAuthorizationBlock(byte[] block) throws Exception {
        Cursor cursor = new Cursor(block);
        cursor.readInt32BE(); // legacy flags
        int legacyDc = cursor.readInt32BE();
        final int mainDc;
        if (legacyDc == -1) {
            cursor.skip(8); // legacy user id / reserved data
            mainDc = cursor.readInt32BE();
        } else {
            mainDc = legacyDc;
        }
        long keyCountLong = cursor.readUInt32BE();
        if (keyCountLong > 64) {
            throw new IOException("tdata 授权密钥数量异常");
        }

        byte[] mainKey = null;
        for (int i = 0; i < (int) keyCountLong; i++) {
            int dcId = cursor.readInt32BE();
            byte[] key = cursor.readBytes(256);
            if (dcId == mainDc && isValidDc(dcId)) {
                mainKey = key;
            }
        }
        if (mainKey == null || !isValidDc(mainDc)) {
            return null;
        }

        ProtocolParser.SessionData data = new ProtocolParser.SessionData();
        data.dcId = mainDc;
        data.authKey = mainKey;
        data.address = addressForDc(mainDc);
        data.port = 443;
        return data;
    }

    private static boolean isValidDc(int dcId) {
        return dcId >= 1 && dcId <= 5;
    }

    private static String addressForDc(int dcId) throws IOException {
        switch (dcId) {
            case 1:
                return "149.154.175.50";
            case 2:
                return "149.154.167.51";
            case 3:
                return "149.154.175.100";
            case 4:
                return "149.154.167.91";
            case 5:
                return "91.108.56.130";
            default:
                throw new IOException("未知 Telegram 数据中心");
        }
    }

    private static byte[] readTdfPayload(File file) throws Exception {
        byte[] raw = readFile(file);
        if (raw.length < 24 || raw[0] != 'T' || raw[1] != 'D' || raw[2] != 'F' || raw[3] != '$') {
            throw new IOException("不是有效的 Telegram Desktop 数据文件");
        }
        int dataSize = raw.length - 24;
        byte[] version = Arrays.copyOfRange(raw, 4, 8);
        byte[] data = Arrays.copyOfRange(raw, 8, 8 + dataSize);
        byte[] expectedChecksum = Arrays.copyOfRange(raw, 8 + dataSize, raw.length);
        byte[] sizeLe = new byte[] {
                (byte) dataSize, (byte) (dataSize >>> 8), (byte) (dataSize >>> 16), (byte) (dataSize >>> 24)
        };
        byte[] calculatedChecksum = digest("MD5", concat(data, sizeLe, version, "TDF$".getBytes(StandardCharsets.US_ASCII)));
        if (!MessageDigest.isEqual(calculatedChecksum, expectedChecksum)) {
            throw new IOException("tdata 文件校验失败");
        }
        return data;
    }

    private static byte[] decryptLocal(byte[] encrypted, byte[] localKey) throws Exception {
        if (encrypted == null || encrypted.length < 32 || (encrypted.length - 16) % 16 != 0) {
            throw new IOException("tdata 加密数据无效");
        }
        byte[] messageKey = Arrays.copyOfRange(encrypted, 0, 16);
        byte[] cipherText = Arrays.copyOfRange(encrypted, 16, encrypted.length);
        byte[][] keyAndIv = deriveIgeKey(localKey, messageKey);
        byte[] plain = aesIgeDecrypt(cipherText, keyAndIv[0], keyAndIv[1]);
        byte[] expectedMessageKey = Arrays.copyOf(digest("SHA-1", plain), 16);
        if (!MessageDigest.isEqual(expectedMessageKey, messageKey)) {
            throw new IOException("tdata 本地密码无效或文件已损坏");
        }
        if (plain.length < 4) {
            throw new IOException("tdata 明文长度无效");
        }
        int declaredLength = (plain[0] & 0xff)
                | ((plain[1] & 0xff) << 8)
                | ((plain[2] & 0xff) << 16)
                | ((plain[3] & 0xff) << 24);
        if (declaredLength < 4 || declaredLength > plain.length) {
            throw new IOException("tdata 数据长度无效");
        }
        return Arrays.copyOfRange(plain, 4, declaredLength);
    }

    private static byte[][] deriveIgeKey(byte[] authKey, byte[] messageKey) throws Exception {
        if (authKey == null || authKey.length < 136 || messageKey == null || messageKey.length != 16) {
            throw new IOException("tdata 密钥材料无效");
        }
        int x = 8;
        byte[] sha1a = digest("SHA-1", concat(messageKey, slice(authKey, x, 32)));
        byte[] sha1b = digest("SHA-1", concat(slice(authKey, 32 + x, 16), messageKey, slice(authKey, 48 + x, 16)));
        byte[] sha1c = digest("SHA-1", concat(slice(authKey, 64 + x, 32), messageKey));
        byte[] sha1d = digest("SHA-1", concat(messageKey, slice(authKey, 96 + x, 32)));
        byte[] aesKey = concat(slice(sha1a, 0, 8), slice(sha1b, 8, 12), slice(sha1c, 4, 12));
        byte[] aesIv = concat(slice(sha1a, 8, 12), slice(sha1b, 0, 8), slice(sha1c, 16, 4), slice(sha1d, 0, 8));
        return new byte[][] {aesKey, aesIv};
    }

    private static byte[] aesIgeDecrypt(byte[] cipherText, byte[] key, byte[] iv) throws Exception {
        if (cipherText.length == 0 || cipherText.length % 16 != 0 || iv.length != 32) {
            throw new IOException("AES-IGE 参数无效");
        }
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] previousCipher = Arrays.copyOfRange(iv, 0, 16);
        byte[] previousPlain = Arrays.copyOfRange(iv, 16, 32);
        byte[] output = new byte[cipherText.length];
        for (int position = 0; position < cipherText.length; position += 16) {
            byte[] block = Arrays.copyOfRange(cipherText, position, position + 16);
            byte[] mixed = xor(block, previousPlain);
            byte[] decrypted = cipher.doFinal(mixed);
            byte[] plainBlock = xor(decrypted, previousCipher);
            System.arraycopy(plainBlock, 0, output, position, 16);
            previousCipher = block;
            previousPlain = plainBlock;
        }
        return output;
    }

    /** PBKDF2-HMAC-SHA512 using binary password bytes, matching Telegram Desktop. */
    private static byte[] pbkdf2Sha512(byte[] password, byte[] salt, int iterations, int outputBytes) throws Exception {
        if (iterations <= 0 || outputBytes <= 0) {
            throw new IOException("PBKDF2 参数无效");
        }
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(password, "HmacSHA512"));
        int hashSize = mac.getMacLength();
        int blocks = (outputBytes + hashSize - 1) / hashSize;
        byte[] output = new byte[outputBytes];
        int outputOffset = 0;
        for (int blockIndex = 1; blockIndex <= blocks; blockIndex++) {
            mac.update(salt);
            mac.update((byte) (blockIndex >>> 24));
            mac.update((byte) (blockIndex >>> 16));
            mac.update((byte) (blockIndex >>> 8));
            mac.update((byte) blockIndex);
            byte[] u = mac.doFinal();
            byte[] t = Arrays.copyOf(u, u.length);
            for (int round = 1; round < iterations; round++) {
                u = mac.doFinal(u);
                for (int i = 0; i < t.length; i++) {
                    t[i] ^= u[i];
                }
            }
            int copyLength = Math.min(t.length, output.length - outputOffset);
            System.arraycopy(t, 0, output, outputOffset, copyLength);
            outputOffset += copyLength;
        }
        return output;
    }

    private static byte[] readFile(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_FILE_SIZE) {
            throw new IOException("tdata 文件大小无效");
        }
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static byte[] digest(String algorithm, byte[] data) throws Exception {
        return MessageDigest.getInstance(algorithm).digest(data);
    }

    private static byte[] slice(byte[] source, int offset, int length) {
        return Arrays.copyOfRange(source, offset, offset + length);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static byte[] xor(byte[] left, byte[] right) {
        byte[] result = new byte[left.length];
        for (int i = 0; i < left.length; i++) {
            result[i] = (byte) (left[i] ^ right[i]);
        }
        return result;
    }

    private static final class Cursor {
        private final byte[] data;
        private int position;

        Cursor(byte[] data) {
            this.data = data == null ? new byte[0] : data;
        }

        int remaining() {
            return data.length - position;
        }

        void requireFinished() throws IOException {
            if (position != data.length) {
                throw new IOException("tdata 数据包含未预期的尾部内容");
            }
        }

        void skip(int count) throws IOException {
            readBytes(count);
        }

        byte[] readByteArray() throws IOException {
            long length = readUInt32BE();
            if (length == 0 || length == 0xffffffffL) {
                return new byte[0];
            }
            if (length > Integer.MAX_VALUE) {
                throw new IOException("tdata 字节数组过大");
            }
            return readBytes((int) length);
        }

        long readUInt32BE() throws IOException {
            return ((long) (readByte() & 0xff) << 24)
                    | ((long) (readByte() & 0xff) << 16)
                    | ((long) (readByte() & 0xff) << 8)
                    | (long) (readByte() & 0xff);
        }

        int readInt32BE() throws IOException {
            return (int) readUInt32BE();
        }

        byte[] readBytes(int count) throws IOException {
            if (count < 0 || count > remaining()) {
                throw new IOException("tdata 数据截断");
            }
            byte[] result = Arrays.copyOfRange(data, position, position + count);
            position += count;
            return result;
        }

        byte readByte() throws IOException {
            if (position >= data.length) {
                throw new IOException("tdata 数据截断");
            }
            return data[position++];
        }
    }
}
