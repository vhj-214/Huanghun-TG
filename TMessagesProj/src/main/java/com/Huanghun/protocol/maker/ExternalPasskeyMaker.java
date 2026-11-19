package com.Huanghun.protocol.maker;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_account;
import tw.nekomimi.nekogram.NekoXConfig;

import com.Huanghun.protocol.PasskeyParser;
import com.Huanghun.protocol.ProtocolParser;
import com.Huanghun.protocol.TdataParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates portable Telegram Passkey credentials from owner-provided session, JSON or tdata.
 * Imported auth keys are used only in a free temporary account slot and the slot is cleared
 * after each account. API credentials embedded in input files are deliberately ignored;
 * ConnectionsManager is initialized with Huanghun's active NekoXConfig API ID (the
 * BuildConfig default or the client-wide custom API configuration).
 */
public final class ExternalPasskeyMaker {
    private static final int REQUEST_FLAGS = ConnectionsManager.RequestFlagWithoutLogin
            | ConnectionsManager.RequestFlagEnableUnauthorized;
    private static final long RPC_TIMEOUT_SECONDS = 30;
    private static final int MAX_ACCOUNTS = 20;
    private static final String PASSKEY_ORIGIN = "https://web.telegram.org";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ExternalPasskeyMaker() {
    }

    public static final class Result {
        public final List<File> zipFiles = new ArrayList<>();
        public final List<String> failures = new ArrayList<>();
        public boolean publicDirectory = true;
    }

    private static final class InputAccount {
        final ProtocolParser.SessionData session;
        final String phone;
        final String source;

        InputAccount(ProtocolParser.SessionData session, String phone, String source) {
            this.session = session;
            this.phone = phone == null ? "" : phone;
            this.source = source == null ? "account" : source;
        }
    }

    private static final class RpcResult {
        Object response;
        TLRPC.TL_error error;
        boolean timedOut;
    }

    private static final class PendingRegistrationException extends IOException {
        final File archive;

        PendingRegistrationException(File archive, String message) {
            super(message);
            this.archive = archive;
        }
    }

    /** Called from a worker thread. The input is a copied file or a copied tdata directory. */
    public static Result create(Context context, File input, String kind) throws Exception {
        if (context == null || input == null) {
            throw new IOException("缺少输入文件");
        }
        if (NekoXConfig.currentAppId() <= 0 || NekoXConfig.currentAppHash() == null
                || NekoXConfig.currentAppHash().isEmpty()) {
            throw new IOException("黄昏客户端 Telegram API 配置无效");
        }
        Result result = new Result();
        File convertedDirectory = ProtocolPaths.createTemporaryDirectory(context, "passkey_sessions_");
        ArrayList<ProtocolParser.SessionData> parsed = new ArrayList<>();
        ArrayList<InputAccount> accounts = new ArrayList<>();
        try {
            if ("tdata".equalsIgnoreCase(kind)) {
                if (!input.isDirectory()) {
                    throw new IOException("请选择完整的 tdata 文件夹");
                }
                ArrayList<ProtocolParser.SessionData> sessions = TdataParser.parse(input);
                int index = 0;
                for (ProtocolParser.SessionData session : sessions) {
                    File converted = new File(convertedDirectory, "tdata_" + (++index) + ".session");
                    try {
                        ProtocolAccount account = new ProtocolAccount(session.authKey, session.dcId);
                        account.source = "tdata";
                        SessionCodec.writeTelethonSession(account, converted);
                        ProtocolParser.SessionData normalized = ProtocolParser.parseTelethonSession(converted);
                        parsed.add(normalized);
                        accounts.add(new InputAccount(normalized, "", "tdata_" + index));
                    } finally {
                        if (session.authKey != null) Arrays.fill(session.authKey, (byte) 0);
                    }
                }
            } else if ("json".equalsIgnoreCase(kind)) {
                if (!input.isFile()) {
                    throw new IOException("请选择 .json 文件");
                }
                List<ProtocolAccount> jsonAccounts = SessionCodec.readJson(input);
                int index = 0;
                try {
                    for (ProtocolAccount account : jsonAccounts) {
                        File converted = new File(convertedDirectory, "json_" + (++index) + ".session");
                        SessionCodec.writeTelethonSession(account, converted);
                        ProtocolParser.SessionData normalized = ProtocolParser.parseTelethonSession(converted);
                        parsed.add(normalized);
                        accounts.add(new InputAccount(normalized, account.phone, input.getName()));
                    }
                } finally {
                    zeroAccounts(jsonAccounts);
                }
            } else if ("session".equalsIgnoreCase(kind)) {
                if (!input.isFile()) {
                    throw new IOException("请选择 .session 文件");
                }
                ProtocolParser.SessionData session;
                try {
                    session = ProtocolParser.parseTelethonSession(input);
                } catch (Exception telethonError) {
                    // Pyrogram's session database omits server_address; normalize it through
                    // the same temporary Telethon-session writer used for JSON imports.
                    List<ProtocolAccount> parsedAccounts = SessionCodec.readSession(input, SessionCodec.KIND_AUTO);
                    if (parsedAccounts.isEmpty()) {
                        throw telethonError;
                    }
                    ProtocolAccount account = parsedAccounts.get(0);
                    File converted = new File(convertedDirectory, "session_1.session");
                    try {
                        SessionCodec.writeTelethonSession(account, converted);
                        session = ProtocolParser.parseTelethonSession(converted);
                    } finally {
                        zeroAccounts(parsedAccounts);
                    }
                }
                parsed.add(session);
                accounts.add(new InputAccount(session, "", input.getName()));
            } else {
                throw new IOException("不支持的输入格式");
            }

            if (accounts.isEmpty()) {
                throw new IOException("没有找到可用的授权会话");
            }
            if (accounts.size() > MAX_ACCOUNTS) {
                throw new IOException("单次最多处理 " + MAX_ACCOUNTS + " 个账号，请拆分文件后重试");
            }

            for (int i = 0; i < accounts.size(); i++) {
                InputAccount account = accounts.get(i);
                try {
                    File archive = createOne(context, account, i + 1);
                    result.zipFiles.add(archive);
                } catch (PendingRegistrationException pending) {
                    if (pending.archive != null && pending.archive.isFile()) {
                        result.zipFiles.add(pending.archive);
                    }
                    String source = account.phone.isEmpty() ? account.source : account.phone;
                    result.failures.add(failureDetails(source, pending));
                } catch (Throwable error) {
                    String source = account.phone.isEmpty() ? account.source : account.phone;
                    result.failures.add(failureDetails(source, error));
                }
            }
            return result;
        } finally {
            for (ProtocolParser.SessionData session : parsed) {
                if (session != null && session.authKey != null) {
                    Arrays.fill(session.authKey, (byte) 0);
                }
            }
            ProtocolPaths.deleteRecursive(convertedDirectory);
        }
    }

    private static File createOne(Context context, InputAccount input, int sequence) throws Exception {
        ProtocolParser.SessionData session = input.session;
        if (session == null || session.authKey == null || session.authKey.length != ProtocolAccount.KEY_SIZE
                || session.dcId < 1 || session.dcId > 5) {
            throw new IOException("授权密钥或数据中心无效");
        }
        String address = session.address == null || session.address.trim().isEmpty()
                ? SessionCodec.addressForDc(session.dcId) : session.address.trim();
        int port = session.port > 0 && session.port <= 65535
                ? session.port : SessionCodec.portForDc(session.dcId);
        int accountNum = reserveFreeSlot();
        if (accountNum < 0) {
            throw new IOException("没有可用的临时账号槽位。请退出一个账号后再试；不会覆盖已登录账号。");
        }

        File archive = null;
        File passkeyFile = null;
        boolean requestWasSent = false;
        try {
            clearSlot(accountNum);
            ConnectionsManager.native_importAuthKey(accountNum, session.dcId, address, port, session.authKey);
            ConnectionsManager manager = ConnectionsManager.getInstance(accountNum);
            manager.resumeNetworkMaybe();

            TLRPC.TL_users_getUsers selfRequest = new TLRPC.TL_users_getUsers();
            selfRequest.id.add(new TLRPC.TL_inputUserSelf());
            RpcResult selfResult = send(manager, selfRequest, session.dcId);
            if (selfResult.error != null || !(selfResult.response instanceof Vector)) {
                throw rpcFailure("验证 session", selfResult);
            }
            TLRPC.User self = findSelf((Vector) selfResult.response);
            if (self == null || self.id <= 0) {
                throw new IOException("该 session 未能验证为有效账号");
            }

            TL_account.initPasskeyRegistration init = new TL_account.initPasskeyRegistration();
            RpcResult optionsResult = send(manager, init, session.dcId);
            if (optionsResult.error != null || !(optionsResult.response instanceof TL_account.passkeyRegistrationOptions)) {
                throw rpcFailure("获取 Passkey 注册参数", optionsResult);
            }
            TL_account.passkeyRegistrationOptions options = (TL_account.passkeyRegistrationOptions) optionsResult.response;
            if (options.options == null || options.options.data == null) {
                throw new IOException("Telegram 返回的 Passkey 注册参数为空");
            }
            JSONObject root = new JSONObject(options.options.data);
            JSONObject publicKey = root.optJSONObject("publicKey");
            if (publicKey == null) {
                throw new IOException("Telegram 返回的 WebAuthn 参数无效");
            }
            String challenge = publicKey.optString("challenge", "");
            JSONObject rp = publicKey.optJSONObject("rp");
            String rpId = rp == null ? "" : rp.optString("id", "");
            if (challenge.isEmpty() || rpId.isEmpty()) {
                throw new IOException("Telegram 返回的 challenge 或 RP ID 缺失");
            }
            if (!"telegram.org".equalsIgnoreCase(rpId)) {
                throw new IOException("Telegram 当前仅允许 telegram.org RP ID，服务器返回了不支持的值");
            }

            KeyPair keyPair = generateP256KeyPair();
            byte[] credentialId = new byte[32];
            RANDOM.nextBytes(credentialId);
            String credentialIdB64 = base64UrlEncode(credentialId);
            String userHandle = session.dcId + ":" + self.id;
            JSONObject webUser = publicKey.optJSONObject("user");
            if (webUser != null) {
                String encodedUserId = webUser.optString("id", "");
                String decodedUserId = decodeBase64UrlUtf8(encodedUserId);
                if (decodedUserId != null && decodedUserId.matches("[1-5]:[1-9][0-9]*")) {
                    String[] parts = decodedUserId.split(":", 2);
                    if (Integer.parseInt(parts[0]) == session.dcId && Long.parseLong(parts[1]) == self.id) {
                        userHandle = decodedUserId;
                    }
                }
            }

            String privateKeyHex = fixedHex(((ECPrivateKey) keyPair.getPrivate()).getS(), 32);
            JSONObject payload = new JSONObject();
            payload.put("Phone", self.phone == null || self.phone.isEmpty()
                    ? (input.phone.isEmpty() ? String.valueOf(self.id) : input.phone) : self.phone);
            payload.put("TwoFA", "");
            payload.put("CredentialId", credentialIdB64);
            payload.put("PrivateKeyHex", privateKeyHex);
            payload.put("UserHandle", userHandle);
            payload.put("UserHandleEncoding", "plain");
            payload.put("SignCount", 0);
            payload.put("RpId", rpId);
            payload.put("Origin", PASSKEY_ORIGIN);
            payload.put("SigFormat", "der");

            JSONObject clientData = new JSONObject();
            clientData.put("type", "webauthn.create");
            clientData.put("challenge", challenge);
            clientData.put("origin", PASSKEY_ORIGIN);
            clientData.put("crossOrigin", false);

            byte[] coseKey = encodeCosePublicKey((ECPublicKey) keyPair.getPublic());
            byte[] authenticatorData = createAuthenticatorData(rpId, credentialId, coseKey);
            byte[] attestationObject = createNoneAttestation(authenticatorData);

            ProtocolPaths.Resolved output = ProtocolPaths.resolveOutputDirectory(context);
            String fileBase = safeBaseName(self.phone, self.id, sequence);
            String internalName = fileBase + ".Passkey";
            passkeyFile = ProtocolPaths.createTemporaryFile(context, "passkey_credential_", ".Passkey");
            try (FileOutputStream stream = new FileOutputStream(passkeyFile)) {
                stream.write(payload.toString(2).getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }
            PasskeyParser.parse(passkeyFile);
            archive = ProtocolPaths.uniqueTarget(output.directory, fileBase + "_Passkey", ".zip");
            writeZip(archive, passkeyFile, internalName);

            TL_account.inputPasskeyResponseRegister response = new TL_account.inputPasskeyResponseRegister();
            response.client_data = new TLRPC.TL_dataJSON();
            response.client_data.data = clientData.toString();
            response.attestation_object = attestationObject;
            TL_account.inputPasskeyCredentialPublicKey credential = new TL_account.inputPasskeyCredentialPublicKey();
            credential.id = credentialIdB64;
            credential.raw_id = credentialIdB64;
            credential.response = response;
            TL_account.registerPasskey register = new TL_account.registerPasskey();
            register.credential = credential;
            requestWasSent = true;
            RpcResult registerResult;
            try {
                registerResult = send(manager, register, session.dcId);
            } catch (Exception networkError) {
                throw new PendingRegistrationException(archive,
                        "无法确认 Telegram 是否完成注册（" + safeError(networkError)
                                + "）。已保留 Passkey ZIP，请先在 Telegram Passkey 设置中核对注册状态，再决定是否使用该文件。");
            }
            if (registerResult.timedOut || (registerResult.error == null
                    && !(registerResult.response instanceof TL_account.Passkey))) {
                throw new PendingRegistrationException(archive,
                        "注册请求结果未确认；服务器可能已经收到请求。已保留 Passkey ZIP，请先在 Telegram Passkey 设置中核对注册状态，再决定是否使用该文件。");
            }
            if (registerResult.error != null) {
                throw rpcFailure("注册 Passkey", registerResult);
            }
            return archive;
        } catch (Throwable error) {
            // Keep the pre-written archive if the network request timed out after submission:
            // the server may have committed registration even though the response was lost.
            if (archive != null && !requestWasSent) {
                //noinspection ResultOfMethodCallIgnored
                archive.delete();
            }
            if (archive != null && requestWasSent && !(error instanceof PendingRegistrationException)) {
                // A definitive RPC rejection means no successful registration was reported.
                // Delete the credential artifact rather than present it as ready to use.
                // Ambiguous transport outcomes are represented by PendingRegistrationException.
                //noinspection ResultOfMethodCallIgnored
                archive.delete();
            }
            if (error instanceof Exception) {
                throw (Exception) error;
            }
            throw new Exception(error);
        } finally {
            if (passkeyFile != null) {
                //noinspection ResultOfMethodCallIgnored
                passkeyFile.delete();
            }
            clearSlot(accountNum);
        }
    }

    private static int reserveFreeSlot() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (!UserConfig.getInstance(i).isClientActivated()) {
                return i;
            }
        }
        return -1;
    }

    private static void clearSlot(int accountNum) {
        try {
            ConnectionsManager.getInstance(accountNum).cleanup(true);
        } catch (Throwable ignore) {
        }
        try {
            UserConfig.getInstance(accountNum).clearConfig();
        } catch (Throwable ignore) {
        }
    }

    private static RpcResult send(ConnectionsManager manager, org.telegram.tgnet.TLObject request, int dcId) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean done = new AtomicBoolean(false);
        AtomicReference<Object> response = new AtomicReference<>();
        AtomicReference<TLRPC.TL_error> error = new AtomicReference<>();
        int requestId = manager.sendRequest(request, (res, err) -> {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            response.set(res);
            error.set(err);
            latch.countDown();
        }, null, null, REQUEST_FLAGS, dcId, ConnectionsManager.ConnectionTypeGeneric, true);
        RpcResult result = new RpcResult();
        if (!latch.await(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            result.timedOut = true;
            manager.cancelRequest(requestId, true);
            return result;
        }
        result.response = response.get();
        result.error = error.get();
        return result;
    }

    private static TLRPC.User findSelf(Vector users) {
        if (users == null || users.objects == null) {
            return null;
        }
        TLRPC.User fallback = null;
        for (Object object : users.objects) {
            if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                if (user.id > 0 && (user.self || fallback == null)) {
                    fallback = user;
                }
                if (user.self && user.id > 0) {
                    return user;
                }
            }
        }
        return fallback;
    }

    private static Exception rpcFailure(String stage, RpcResult result) {
        if (result != null && result.timedOut) {
            return new IOException(stage + "超时，请检查网络后重试");
        }
        if (result != null && result.error != null) {
            String text = result.error.text == null ? "Telegram RPC 错误" : result.error.text;
            return new IOException(stage + "失败：" + text);
        }
        return new IOException(stage + "失败：服务器返回内容无效");
    }

    private static KeyPair generateP256KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return generator.generateKeyPair();
    }

    private static byte[] createAuthenticatorData(String rpId, byte[] credentialId, byte[] coseKey) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(java.security.MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8)));
        output.write(0x45); // UP + UV + AT, matching GAFBot's none-attestation software authenticator.
        output.write(new byte[]{0, 0, 0, 0}); // sign counter
        output.write(new byte[16]); // zero AAGUID for fmt=none
        output.write((credentialId.length >>> 8) & 0xff);
        output.write(credentialId.length & 0xff);
        output.write(credentialId);
        output.write(coseKey);
        return output.toByteArray();
    }

    private static byte[] encodeCosePublicKey(ECPublicKey key) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0xa5); // map(5)
        writeCborInt(output, 1); writeCborInt(output, 2); // kty = EC2
        writeCborInt(output, 3); writeCborInt(output, -7); // alg = ES256
        writeCborInt(output, -1); writeCborInt(output, 1); // crv = P-256
        writeCborInt(output, -2); writeCborBytes(output, fixedUnsigned(key.getW().getAffineX(), 32));
        writeCborInt(output, -3); writeCborBytes(output, fixedUnsigned(key.getW().getAffineY(), 32));
        return output.toByteArray();
    }

    private static byte[] createNoneAttestation(byte[] authData) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeCborLength(output, 5, 3);
        writeCborText(output, "fmt"); writeCborText(output, "none");
        writeCborText(output, "attStmt"); writeCborLength(output, 5, 0);
        writeCborText(output, "authData"); writeCborBytes(output, authData);
        return output.toByteArray();
    }

    private static void writeCborInt(ByteArrayOutputStream output, long value) throws IOException {
        if (value >= 0) {
            writeCborLength(output, 0, value);
        } else {
            writeCborLength(output, 1, -1L - value);
        }
    }

    private static void writeCborText(ByteArrayOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeCborLength(output, 3, bytes.length);
        output.write(bytes);
    }

    private static void writeCborBytes(ByteArrayOutputStream output, byte[] bytes) throws IOException {
        writeCborLength(output, 2, bytes.length);
        output.write(bytes);
    }

    private static void writeCborLength(ByteArrayOutputStream output, int major, long value) throws IOException {
        if (value < 24) {
            output.write((major << 5) | (int) value);
        } else if (value <= 0xff) {
            output.write((major << 5) | 24);
            output.write((int) value);
        } else if (value <= 0xffff) {
            output.write((major << 5) | 25);
            output.write((int) (value >>> 8));
            output.write((int) value);
        } else if (value <= 0xffffffffL) {
            output.write((major << 5) | 26);
            for (int shift = 24; shift >= 0; shift -= 8) output.write((int) (value >>> shift));
        } else {
            output.write((major << 5) | 27);
            for (int shift = 56; shift >= 0; shift -= 8) output.write((int) (value >>> shift));
        }
    }

    private static byte[] fixedUnsigned(BigInteger value, int size) {
        byte[] raw = value.toByteArray();
        int offset = raw.length > size && raw[0] == 0 ? 1 : 0;
        int length = raw.length - offset;
        if (length > size) {
            return Arrays.copyOfRange(raw, raw.length - size, raw.length);
        }
        byte[] result = new byte[size];
        System.arraycopy(raw, offset, result, size - length, length);
        return result;
    }

    private static String fixedHex(BigInteger value, int size) {
        byte[] bytes = fixedUnsigned(value, size);
        StringBuilder result = new StringBuilder(size * 2);
        for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static String decodeBase64UrlUtf8(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            byte[] decoded = Base64.decode(value, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static String safeBaseName(String phone, long userId, int sequence) {
        String value = phone == null ? "" : phone;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
        }
        if (digits.length() > 0) return digits.toString();
        if (userId > 0) return String.valueOf(userId);
        return "account_" + sequence;
    }

    private static void writeZip(File archive, File passkey, String entryName) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(entryName));
            try (java.io.FileInputStream input = new java.io.FileInputStream(passkey)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
            }
            zip.closeEntry();
        }
    }

    private static void zeroAccounts(List<ProtocolAccount> accounts) {
        if (accounts == null) return;
        for (ProtocolAccount account : accounts) {
            if (account != null && account.authKey != null) Arrays.fill(account.authKey, (byte) 0);
        }
    }

    private static String failureDetails(String source, Throwable error) {
        return source + "：" + safeError(error)
                + "\n如失败原因提示授权未满 24 小时，请等待一天后重新尝试。";
    }

    private static String safeError(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return "未知错误";
        }
        String message = error.getMessage().replace('\n', ' ').replace('\r', ' ');
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
