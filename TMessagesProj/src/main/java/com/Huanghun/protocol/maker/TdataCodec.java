package com.Huanghun.protocol.maker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Telegram Desktop tdata 目录写入器。
 *
 * <p>与 {@link com.Huanghun.protocol.TdataParser} 的读取逻辑严格对称：</p>
 * <pre>
 * tdata/key_datas = TDF { salt, keyEncrypted, infoEncrypted }
 *   passcodeKey  = PBKDF2-HMAC-SHA512(SHA512(salt + salt), salt, 1, 256)
 *   keyEncrypted = Encrypted(localKey 256B, passcodeKey)
 *   infoEncrypted= Encrypted(int32 count + int32 index..., localKey)
 * tdata/data      = TDF { Encrypted( {uint32 0x4b, bytearray authorization} , localKey) }
 *   authorization = int32 flags + int32 dcId + uint32 keyCount
 *                 + (int32 dcId + 256B authKey) * keyCount
 * tdata/data#N    = 第 N+1 个附加账号
 * </pre>
 *
 * <p>全部为纯 Java 实现，不依赖 Android API，便于离线校验。</p>
 */
public final class TdataCodec {

    /** TDF 容器在 Desktop 侧没有强制版本上限，写入当前桌面格式版本号即可。 */
    private static final int TDF_VERSION = 4008000;
    /** Telegram Desktop 授权块 id。 */
    private static final int BLOCK_AUTHORIZATION = 0x4b;
    private static final int LOCAL_SALT_SIZE = 32;
    private static final int KEY_SIZE = 256;

    private TdataCodec() {
    }

    /**
     * 把单个账号写成标准的 tdata 目录。
     *
     * @param accounts 账号列表，顺序决定 data、data#1、data#2 的文件名。
     * @param directory 目标目录，不存在会自动创建。
     */
    public static void writeTdata(List<ProtocolAccount> accounts, File directory) throws Exception {
        if (accounts == null || accounts.isEmpty()) {
            throw new IOException("没有可写入的账号");
        }
        if (accounts.size() != 1) {
            throw new IOException("一个 tdata 目录只支持写入一个账号");
        }
        for (ProtocolAccount account : accounts) {
            if (account == null || !account.isValid()) {
                throw new IOException("账号授权数据不完整，无法生成 tdata");
            }
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建 tdata 目录");
        }
        clearOldData(directory);

        byte[] localKey = new byte[KEY_SIZE];
        byte[] salt = new byte[LOCAL_SALT_SIZE];
        SecureRandom random = new SecureRandom();
        random.nextBytes(localKey);
        random.nextBytes(salt);

        byte[] passcodeKey = pbkdf2Sha512(sha512(concat(salt, salt)), salt, 1, KEY_SIZE);
        byte[] keyEncrypted = encryptLocal(localKey, passcodeKey);

        ByteArrayOutputStream infoPlain = new ByteArrayOutputStream();
        infoPlain.write(int32be(accounts.size()));
        for (int i = 0; i < accounts.size(); i++) {
            infoPlain.write(int32be(i));
        }
        byte[] infoEncrypted = encryptLocal(infoPlain.toByteArray(), localKey);

        byte[] keyDatas = tdfPack(concat(
                qbyteArray(salt),
                qbyteArray(keyEncrypted),
                qbyteArray(infoEncrypted)));
        writeFile(new File(directory, "key_datas"), keyDatas);

        // 桌面端的账号数据文件名由 "key_data" 的 MD5 派生（16 位大写十六进制），
        // 授权数据落在 tdata/<NAME>s，map 落在 tdata/<NAME>/maps。
        // 这里一个目录只写一个账号，避免多账号文件名规则在不同版本间产生歧义。
        String dataName = computeDataNameKey("data");
        byte[] block = authorizationBlock(accounts.get(0));
        byte[] records = concat(int32be(BLOCK_AUTHORIZATION), qbyteArray(block));
        writeFile(new File(directory, dataName + "s"), tdfPack(qbyteArray(encryptLocal(records, localKey))));

        // 桌面端启动账号前会先读 map，缺失会直接跳过这个账号，
        // 所以即使没有本地草稿也要写一份空 map。
        File mapDirectory = new File(directory, dataName);
        if (!mapDirectory.exists() && !mapDirectory.mkdirs()) {
            throw new IOException("无法创建账号数据目录");
        }
        byte[] mapPayload = concat(
                qbyteArray(new byte[0]),
                qbyteArray(new byte[0]),
                qbyteArray(encryptLocal(new byte[0], localKey)));
        byte[] packedMap = tdfPack(mapPayload);
        writeFile(new File(mapDirectory, "maps"), packedMap);
        // 不同版本/解析器对 map 的落点有两种约定，两份都写，读取时命中任意一份即可。
        writeFile(new File(directory, "maps"), packedMap);

    }

    private static void clearOldData(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            if (file.isFile() && (name.equals("key_datas")
                    || name.equals("data") || name.startsWith("data#")
                    || name.equals("map") || name.startsWith("map#") || name.equals("maps")
                    || name.endsWith("s") && name.length() == 17)) {
                //noinspection ResultOfMethodCallIgnored
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static byte[] authorizationBlock(ProtocolAccount account) throws IOException {
        // 布局与 Desktop 的 MTP::writeMtpAuthorization 一致：
        //   int64 kWideIdsTag(-1) + int64 userId + int32 mainDcId
        //   + keys( count + (dc, 256B key) * count )
        //   + keysToDestroy( count )
        // 读取端先读两个 int32，见到 -1/-1 才按宽 id 继续读，所以标记位必须原样保留。
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(int64be(-1L));
        out.write(int64be(account.userId));
        out.write(int32be(account.dcId));
        out.write(int32be(1));
        out.write(int32be(account.dcId));
        out.write(account.authKey);
        out.write(int32be(0)); // keysToDestroy 为空
        return out.toByteArray();
    }

    /** MD5(账号数据名，默认 "data") 小端展开成 16 位大写十六进制，与桌面端 ComputeDataNameKey 一致。 */
    static String computeDataNameKey(String keyFile) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5")
                .digest(keyFile.getBytes(StandardCharsets.UTF_8));
        // 桌面端把 MD5 当 128 位小端整数只取低 64 位，先输出低半字节。
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 8; i++) {
            sb.append(Character.toUpperCase(Character.forDigit(digest[i] & 0xF, 16)));
            sb.append(Character.toUpperCase(Character.forDigit((digest[i] >> 4) & 0xF, 16)));
        }
        return sb.toString();
    }

    static byte[] int64be(long value) {
        byte[] out = new byte[8];
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (value >>> (56 - 8 * i));
        }
        return out;
    }

    // ---------------------------------------------------------------- TDF 容器

    /** TDF$ + version + payload + MD5(payload + sizeLE + version + "TDF$")。 */
    static byte[] tdfPack(byte[] payload) throws Exception {
        int size = payload.length;
        byte[] version = int32le(TDF_VERSION);
        byte[] sizeLe = int32le(size);
        byte[] footer = concat(payload, sizeLe, version, "TDF$".getBytes(StandardCharsets.US_ASCII));
        byte[] checksum = MessageDigest.getInstance("MD5").digest(footer);

        ByteArrayOutputStream out = new ByteArrayOutputStream(size + 24);
        out.write("TDF$".getBytes(StandardCharsets.US_ASCII));
        out.write(version);
        out.write(payload);
        out.write(checksum);
        return out.toByteArray();
    }

    // -------------------------------------------------------------- 本地加密层

    /**
     * Telegram Desktop 的 PrepareEncrypted：明文前置 4 字节长度，补零到 16 字节对齐，
     * 取 SHA1(明文) 前 16 字节作 messageKey，再以 AES-IGE 加密，输出 messageKey + 密文。
     */
    static byte[] encryptLocal(byte[] plain, byte[] key) throws Exception {
        int bodyLength = 4 + plain.length;
        int paddedLength = ((bodyLength + 15) / 16) * 16;
        byte[] padded = new byte[paddedLength];
        // Desktop 写入的长度字段是「4 字节头 + 数据」的总长，读取端按此校验，
        // 写成纯数据长度会被判为 bad decrypted part size。
        System.arraycopy(int32le(bodyLength), 0, padded, 0, 4);
        System.arraycopy(plain, 0, padded, 4, plain.length);

        byte[] messageKey = Arrays.copyOf(sha1(padded), 16);
        byte[][] keyAndIv = deriveIgeKey(key, messageKey);
        byte[] cipherText = aesIgeEncrypt(padded, keyAndIv[0], keyAndIv[1]);

        ByteArrayOutputStream out = new ByteArrayOutputStream(messageKey.length + cipherText.length);
        out.write(messageKey);
        out.write(cipherText);
        return out.toByteArray();
    }

    static byte[][] deriveIgeKey(byte[] authKey, byte[] messageKey) throws Exception {
        if (authKey == null || authKey.length < 136 || messageKey == null || messageKey.length != 16) {
            throw new IOException("本地密钥长度无效");
        }
        int x = 8;
        byte[] sha1a = sha1(concat(messageKey, slice(authKey, x, 32)));
        byte[] sha1b = sha1(concat(slice(authKey, 32 + x, 16), messageKey, slice(authKey, 48 + x, 16)));
        byte[] sha1c = sha1(concat(slice(authKey, 64 + x, 32), messageKey));
        byte[] sha1d = sha1(concat(messageKey, slice(authKey, 96 + x, 32)));
        byte[] aesKey = concat(slice(sha1a, 0, 8), slice(sha1b, 8, 12), slice(sha1c, 4, 12));
        byte[] aesIv = concat(slice(sha1a, 8, 12), slice(sha1b, 0, 8), slice(sha1c, 16, 4), slice(sha1d, 0, 8));
        return new byte[][]{aesKey, aesIv};
    }

    static byte[] aesIgeEncrypt(byte[] plainText, byte[] key, byte[] iv) throws Exception {
        if (plainText.length == 0 || plainText.length % 16 != 0 || iv.length != 32) {
            throw new IOException("AES-IGE 参数无效");
        }
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] previousCipher = Arrays.copyOfRange(iv, 0, 16);
        byte[] previousPlain = Arrays.copyOfRange(iv, 16, 32);
        byte[] output = new byte[plainText.length];
        for (int position = 0; position < plainText.length; position += 16) {
            byte[] block = Arrays.copyOfRange(plainText, position, position + 16);
            byte[] mixed = xor(block, previousCipher);
            byte[] encrypted = cipher.doFinal(mixed);
            byte[] cipherBlock = xor(encrypted, previousPlain);
            System.arraycopy(cipherBlock, 0, output, position, 16);
            previousCipher = cipherBlock;
            previousPlain = block;
        }
        return output;
    }

    static byte[] pbkdf2Sha512(byte[] password, byte[] salt, int iterations, int outputBytes) throws Exception {
        if (iterations <= 0 || outputBytes <= 0) {
            throw new IOException("PBKDF2 参数无效");
        }
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(password, "HmacSHA512"));
        int hashSize = mac.getMacLength();
        int blocks = (outputBytes + hashSize - 1) / hashSize;
        byte[] output = new byte[outputBytes];
        int offset = 0;
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
            int copyLength = Math.min(t.length, output.length - offset);
            System.arraycopy(t, 0, output, offset, copyLength);
            offset += copyLength;
        }
        return output;
    }

    // ------------------------------------------------------------------ 基础工具

    static byte[] qbyteArray(byte[] data) {
        return concat(int32be(data == null ? 0 : data.length), data == null ? new byte[0] : data);
    }

    static byte[] int32be(int value) {
        return new byte[]{
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        };
    }

    static byte[] int32le(int value) {
        return new byte[]{
                (byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)
        };
    }

    static byte[] sha1(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-1").digest(data);
    }

    static byte[] sha512(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-512").digest(data);
    }

    static byte[] slice(byte[] source, int offset, int length) {
        return Arrays.copyOfRange(source, offset, offset + length);
    }

    static byte[] concat(byte[]... parts) {
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

    static byte[] xor(byte[] left, byte[] right) {
        byte[] result = new byte[left.length];
        for (int i = 0; i < left.length; i++) {
            result[i] = (byte) (left[i] ^ right[i]);
        }
        return result;
    }

    static void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
            output.flush();
        }
    }

    // -------------------------------------------------------------- 回读自检

    /**
     * 用与读取端一致的算法把刚写出的 tdata 解回来，确认 auth_key / DC 完全一致。
     * 打包前调用一次，避免把结构错误的目录塞进压缩包。
     */
    public static List<ProtocolAccount> readBack(File directory) throws Exception {
        byte[] keyDatas = readAll(new File(directory, "key_datas"));
        Reader keyReader = new Reader(tdfUnpack(keyDatas));
        byte[] salt = keyReader.readByteArray();
        byte[] keyEncrypted = keyReader.readByteArray();
        byte[] infoEncrypted = keyReader.readByteArray();
        if (salt.length != LOCAL_SALT_SIZE) {
            throw new IOException("key_datas salt 长度异常");
        }
        byte[] passcodeKey = pbkdf2Sha512(sha512(concat(salt, salt)), salt, 1, KEY_SIZE);
        byte[] localKey = decryptLocal(keyEncrypted, passcodeKey);
        if (localKey.length != KEY_SIZE) {
            throw new IOException("local key 长度异常");
        }
        byte[] info = decryptLocal(infoEncrypted, localKey);
        Reader infoReader = new Reader(info);
        int count = infoReader.readInt32();
        if (count <= 0) {
            throw new IOException("账号数量异常");
        }

        List<ProtocolAccount> result = new ArrayList<>();
        String dataName = computeDataNameKey("data");
        File[] attempts = {
                new File(directory, dataName + "s"),
                new File(directory, dataName)
        };
        File dataFile = null;
        for (File candidate : attempts) {
            if (candidate.isFile()) {
                dataFile = candidate;
                break;
            }
        }
        if (dataFile == null) {
            throw new IOException("tdata 中缺少账号授权文件");
        }
        {
            byte[] records = decryptLocal(
                    new Reader(tdfUnpack(readAll(dataFile))).readByteArray(), localKey);
            Reader reader = new Reader(records);
            while (reader.remaining() > 0) {
                int blockId = reader.readInt32();
                byte[] block = reader.readByteArray();
                if (blockId != BLOCK_AUTHORIZATION) {
                    continue;
                }
                Reader auth = new Reader(block);
                auth.readInt32();
                int marker = auth.readInt32();
                long uid = 0;
                int dcId = marker;
                if (marker == -1) {
                    uid = auth.readLong();
                    dcId = auth.readInt32();
                }
                int keyCount = auth.readInt32();
                for (int k = 0; k < keyCount; k++) {
                    int keyDc = auth.readInt32();
                    byte[] key = auth.readBytes(KEY_SIZE);
                    if (keyDc == dcId) {
                        ProtocolAccount parsed = new ProtocolAccount(key, dcId);
                        parsed.userId = uid;
                        result.add(parsed);
                    }
                }
            }
        }
        if (result.isEmpty()) {
            throw new IOException("回读没有找到授权数据");
        }
        return result;
    }

    static byte[] decryptLocal(byte[] encrypted, byte[] localKey) throws Exception {
        if (encrypted == null || encrypted.length < 32 || (encrypted.length - 16) % 16 != 0) {
            throw new IOException("加密数据长度无效");
        }
        byte[] messageKey = Arrays.copyOfRange(encrypted, 0, 16);
        byte[] cipherText = Arrays.copyOfRange(encrypted, 16, encrypted.length);
        byte[][] keyAndIv = deriveIgeKey(localKey, messageKey);
        byte[] plain = aesIgeDecrypt(cipherText, keyAndIv[0], keyAndIv[1]);
        byte[] expected = Arrays.copyOf(sha1(plain), 16);
        if (!MessageDigest.isEqual(expected, messageKey)) {
            throw new IOException("本地密钥校验失败");
        }
        int declared = (plain[0] & 0xff)
                | ((plain[1] & 0xff) << 8)
                | ((plain[2] & 0xff) << 16)
                | ((plain[3] & 0xff) << 24);
        if (declared < 4 || declared > plain.length) {
            throw new IOException("明文字节长度无效");
        }
        return Arrays.copyOfRange(plain, 4, declared);
    }

    static byte[] aesIgeDecrypt(byte[] cipherText, byte[] key, byte[] iv) throws Exception {
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

    static byte[] tdfUnpack(byte[] raw) throws Exception {
        if (raw.length < 24 || raw[0] != 'T' || raw[1] != 'D' || raw[2] != 'F' || raw[3] != '$') {
            throw new IOException("不是有效的 TDF 文件");
        }
        int dataSize = raw.length - 24;
        byte[] version = Arrays.copyOfRange(raw, 4, 8);
        byte[] data = Arrays.copyOfRange(raw, 8, 8 + dataSize);
        byte[] expected = Arrays.copyOfRange(raw, 8 + dataSize, raw.length);
        byte[] computed = MessageDigest.getInstance("MD5").digest(concat(
                data, int32le(dataSize), version, "TDF$".getBytes(StandardCharsets.US_ASCII)));
        if (!MessageDigest.isEqual(computed, expected)) {
            throw new IOException("TDF 校验失败");
        }
        return data;
    }

    static byte[] readAll(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("文件不存在：" + (file == null ? "null" : file.getName()));
        }
        long length = file.length();
        if (length <= 0 || length > 32L * 1024L * 1024L) {
            throw new IOException("文件大小异常：" + file.getName());
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
            if (offset != buffer.length) {
                throw new IOException("文件读取不完整");
            }
        }
        return buffer;
    }

    /** 与读取端一致的顺序读取器。 */
    static final class Reader {
        private final byte[] data;
        private int position;

        Reader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
        }

        int remaining() {
            return data.length - position;
        }

        int readInt32() throws IOException {
            return (int) readUInt32();
        }

        long readUInt32() throws IOException {
            return ((long) (data[position++] & 0xff) << 24)
                    | ((long) (data[position++] & 0xff) << 16)
                    | ((long) (data[position++] & 0xff) << 8)
                    | (long) (data[position++] & 0xff);
        }

        long readLong() throws IOException {
            long high = readUInt32();
            long low = readUInt32();
            return (high << 32) | low;
        }

        byte[] readByteArray() throws IOException {
            long length = readUInt32();
            if (length == 0 || length == 0xffffffffL) {
                return new byte[0];
            }
            if (length > remaining()) {
                throw new IOException("数据截断");
            }
            return readBytes((int) length);
        }

        byte[] readBytes(int count) throws IOException {
            if (count < 0 || count > remaining()) {
                throw new IOException("数据截断");
            }
            byte[] result = Arrays.copyOfRange(data, position, position + count);
            position += count;
            return result;
        }
    }
}
