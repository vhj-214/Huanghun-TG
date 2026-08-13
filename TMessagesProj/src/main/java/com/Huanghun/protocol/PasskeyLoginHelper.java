package com.Huanghun.protocol;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import tw.nekomimi.nekogram.NekoXConfig;

/** Performs Telegram's WebAuthn Passkey login using an owner-provided .Passkey credential. */
public final class PasskeyLoginHelper {
    // Passkey login is two short server round trips. Do not block the batch for half a minute.
    private static final long VERIFY_TIMEOUT_MS = 18_000L;

    private PasskeyLoginHelper() {
    }

    public static void login(int accountNum, PasskeyParser.PasskeyData data, Callback callback) {
        if (data == null) {
            callback.onFailed();
            return;
        }
        final ConnectionsManager manager = ConnectionsManager.getInstance(accountNum);
        // Connect to the credential's home DC before asking for a challenge.
        if (manager.getCurrentDatacenterId() != data.datacenterId) {
            manager.setDefaultDatacenterId(data.datacenterId);
        }
        manager.resumeNetworkMaybe();
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicInteger requestToken = new AtomicInteger(0);

        TL_account.initPasskeyLogin init = new TL_account.initPasskeyLogin();
        init.api_id = NekoXConfig.currentAppId();
        init.api_hash = NekoXConfig.currentAppHash();
        requestToken.set(manager.sendRequest(init, (response, error) -> {
            if (completed.get()) {
                return;
            }
            if (error != null || !(response instanceof TL_account.passkeyLoginOptions)) {
                finishFailure(completed, callback);
                return;
            }
            try {
                TL_account.passkeyLoginOptions options = (TL_account.passkeyLoginOptions) response;
                JSONObject publicKey = new JSONObject(options.options.data).getJSONObject("publicKey");
                String challenge = publicKey.getString("challenge");
                String expectedRpId = publicKey.getString("rpId");
                if (!data.rpId.equalsIgnoreCase(expectedRpId)) {
                    finishFailure(completed, callback);
                    return;
                }

                String clientData = createClientData(challenge, data.origin);
                byte[] authenticatorData = createAuthenticatorData(data);
                byte[] signature = signAuthenticatorData(data.privateKeyHex, authenticatorData, clientData);

                TL_account.finishPasskeyLogin finish = new TL_account.finishPasskeyLogin();
                finish.credential = new TL_account.inputPasskeyCredentialPublicKey();
                finish.credential.id = data.credentialId;
                finish.credential.raw_id = data.credentialId;
                TL_account.inputPasskeyResponseLogin passkeyResponse = new TL_account.inputPasskeyResponseLogin();
                passkeyResponse.client_data = new TLRPC.TL_dataJSON();
                passkeyResponse.client_data.data = clientData;
                passkeyResponse.authenticator_data = authenticatorData;
                passkeyResponse.signature = signature;
                passkeyResponse.user_handle = data.userHandle;
                finish.credential.response = passkeyResponse;

                requestToken.set(manager.sendRequest(finish, (authorization, finishError) -> {
                    if (completed.get()) {
                        return;
                    }
                    if (finishError != null && containsPasswordNeeded(finishError.text)) {
                        finishWithTwoFactor(manager, data, completed, callback, requestToken);
                    } else if (finishError != null || !(authorization instanceof TLRPC.TL_auth_authorization)) {
                        finishFailure(completed, callback);
                    } else {
                        TLRPC.TL_auth_authorization auth = (TLRPC.TL_auth_authorization) authorization;
                        if (auth.user == null || auth.user.id == 0) {
                            finishFailure(completed, callback);
                        } else {
                            finishSuccess(completed, callback, auth);
                        }
                    }
                }, null, null,
                        ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagEnableUnauthorized,
                        data.datacenterId, ConnectionsManager.ConnectionTypeGeneric, true));
            } catch (Throwable ignore) {
                finishFailure(completed, callback);
            }
        }, null, null,
                ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagEnableUnauthorized,
                data.datacenterId, ConnectionsManager.ConnectionTypeGeneric, true));

        AndroidUtilities.runOnUIThread(() -> {
            if (completed.compareAndSet(false, true)) {
                int token = requestToken.get();
                if (token != 0) {
                    manager.cancelRequest(token, true);
                }
                callback.onFailed();
            }
        }, VERIFY_TIMEOUT_MS);
    }

    private static void finishWithTwoFactor(ConnectionsManager manager, PasskeyParser.PasskeyData data,
                                             AtomicBoolean completed, Callback callback, AtomicInteger requestToken) {
        if (data.twoFactorPassword == null || data.twoFactorPassword.isEmpty()) {
            finishFailure(completed, callback);
            return;
        }
        TL_account.getPassword getPassword = new TL_account.getPassword();
        requestToken.set(manager.sendRequest(getPassword, (passwordObject, passwordError) -> {
            if (completed.get() || passwordError != null || !(passwordObject instanceof TL_account.Password)) {
                finishFailure(completed, callback);
                return;
            }
            try {
                TL_account.Password password = (TL_account.Password) passwordObject;
                if (!(password.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow)) {
                    finishFailure(completed, callback);
                    return;
                }
                TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo =
                        (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) password.current_algo;
                byte[] passwordBytes = AndroidUtilities.getStringBytes(data.twoFactorPassword);
                byte[] x = SRPHelper.getX(passwordBytes, algo);
                TLRPC.TL_inputCheckPasswordSRP check = SRPHelper.startCheck(x, password.srp_id, password.srp_B, algo);
                if (check == null) {
                    finishFailure(completed, callback);
                    return;
                }
                TLRPC.TL_auth_checkPassword checkPassword = new TLRPC.TL_auth_checkPassword();
                checkPassword.password = check;
                requestToken.set(manager.sendRequest(checkPassword, (authorization, checkError) -> {
                    if (checkError != null || !(authorization instanceof TLRPC.TL_auth_authorization)) {
                        finishFailure(completed, callback);
                        return;
                    }
                    TLRPC.TL_auth_authorization auth = (TLRPC.TL_auth_authorization) authorization;
                    if (auth.user == null || auth.user.id == 0) {
                        finishFailure(completed, callback);
                    } else {
                        finishSuccess(completed, callback, auth);
                    }
                }, null, null, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagEnableUnauthorized,
                        data.datacenterId, ConnectionsManager.ConnectionTypeGeneric, true));
            } catch (Throwable ignore) {
                finishFailure(completed, callback);
            }
        }, null, null, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagEnableUnauthorized,
                data.datacenterId, ConnectionsManager.ConnectionTypeGeneric, true));
    }

    private static String createClientData(String challenge, String origin) throws Exception {
        JSONObject clientData = new JSONObject();
        clientData.put("type", "webauthn.get");
        clientData.put("challenge", challenge);
        clientData.put("origin", origin);
        clientData.put("crossOrigin", false);
        return clientData.toString();
    }

    private static byte[] createAuthenticatorData(PasskeyParser.PasskeyData data) {
        byte[] rpHash = Utilities.computeSHA256(data.rpId.getBytes(StandardCharsets.UTF_8));
        byte[] result = new byte[37];
        System.arraycopy(rpHash, 0, result, 0, rpHash.length);
        result[32] = (byte) Integer.parseInt(data.loginFlagsHex, 16);
        long count = ((long) data.signCount) + 1L;
        result[33] = (byte) (count >>> 24);
        result[34] = (byte) (count >>> 16);
        result[35] = (byte) (count >>> 8);
        result[36] = (byte) count;
        return result;
    }

    private static byte[] signAuthenticatorData(String privateKeyHex, byte[] authenticatorData, String clientData) throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(new BigInteger(privateKeyHex, 16), spec));
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(authenticatorData);
        signer.update(Utilities.computeSHA256(clientData.getBytes(StandardCharsets.UTF_8)));
        return signer.sign();
    }

    private static boolean containsPasswordNeeded(String errorText) {
        return errorText != null && errorText.contains("SESSION_PASSWORD_NEEDED");
    }

    private static void finishSuccess(AtomicBoolean completed, Callback callback, TLRPC.TL_auth_authorization authorization) {
        if (completed.compareAndSet(false, true)) {
            callback.onSuccess(authorization);
        }
    }

    private static void finishFailure(AtomicBoolean completed, Callback callback) {
        if (completed.compareAndSet(false, true)) {
            callback.onFailed();
        }
    }

    public interface Callback {
        void onSuccess(TLRPC.TL_auth_authorization authorization);

        void onFailed();
    }
}
