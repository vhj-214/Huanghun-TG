package com.Huanghun.protocol.maker;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies imported authorization keys against Telegram without touching active accounts. */
final class ProtocolSessionVerifier {
    private static final long TIMEOUT_SECONDS = 30;

    private ProtocolSessionVerifier() {
    }

    /** Returns null when valid, otherwise a user-readable reason. Call from a worker thread. */
    static String verify(ProtocolAccount account) {
        if (account == null || !account.isValid()) {
            return "协议授权密钥或数据中心信息无效";
        }
        int slot = reserveFreeSlot();
        if (slot < 0) {
            return "没有空闲账号槽位用于验证；请退出一个账号后重试，不会覆盖已登录账号";
        }
        try {
            clearSlot(slot);
            String address = SessionCodec.addressForDc(account.dcId);
            int port = SessionCodec.portForDc(account.dcId);
            ConnectionsManager.native_importAuthKey(slot, account.dcId, address, port, account.authKey);
            ConnectionsManager manager = ConnectionsManager.getInstance(slot);
            manager.resumeNetworkMaybe();

            TLRPC.TL_users_getUsers request = new TLRPC.TL_users_getUsers();
            request.id.add(new TLRPC.TL_inputUserSelf());
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<Object> response = new AtomicReference<>();
            AtomicReference<TLRPC.TL_error> rpcError = new AtomicReference<>();
            int requestId = manager.sendRequest(request, (result, error) -> {
                if (completed.compareAndSet(false, true)) {
                    response.set(result);
                    rpcError.set(error);
                    latch.countDown();
                }
            }, null, null,
                    ConnectionsManager.RequestFlagWithoutLogin
                            | ConnectionsManager.RequestFlagEnableUnauthorized,
                    account.dcId, ConnectionsManager.ConnectionTypeGeneric, true);

            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                if (completed.compareAndSet(false, true)) {
                    manager.cancelRequest(requestId, true);
                }
                return "Telegram 授权验证超时；请检查网络后重试，本次不会生成协议文件";
            }
            TLRPC.TL_error error = rpcError.get();
            if (error != null) {
                String message = error.text == null ? "Telegram 拒绝了该授权" : error.text;
                return "Telegram 授权验证失败：" + message;
            }
            Object result = response.get();
            if (!(result instanceof Vector)) {
                return "Telegram 未返回有效的账号信息";
            }
            for (Object item : ((Vector) result).objects) {
                if (item instanceof TLRPC.User && ((TLRPC.User) item).id > 0) {
                    TLRPC.User user = (TLRPC.User) item;
                    account.userId = user.id;
                    account.phone = user.phone == null ? "" : user.phone;
                    account.username = user.username == null ? "" : user.username;
                    account.isBot = user.bot;
                    account.premium = user.premium;
                    return null;
                }
            }
            return "该协议未能验证为有效 Telegram 账号";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "授权验证被中断";
        } catch (Throwable error) {
            String message = error.getMessage();
            return "授权验证失败：" + (message == null || message.trim().isEmpty()
                    ? error.getClass().getSimpleName() : message);
        } finally {
            clearSlot(slot);
        }
    }

    private static int reserveFreeSlot() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            try {
                if (!UserConfig.getInstance(i).isClientActivated()) {
                    return i;
                }
            } catch (Throwable ignore) {
            }
        }
        return -1;
    }

    private static void clearSlot(int slot) {
        try {
            ConnectionsManager manager = ConnectionsManager.getInstance(slot);
            if (manager != null) {
                manager.cleanup(true);
            }
        } catch (Throwable ignore) {
        }
        try {
            UserConfig.getInstance(slot).clearConfig();
        } catch (Throwable ignore) {
        }
    }
}
