package com.Huanghun.protocol.maker;

import android.content.Context;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 功能一：把本机已登录账号导出成协议文件（session / tdata / json），打成压缩包。
 */
public final class ProtocolExporter {

    public static final int FORMAT_TELETHON = 1;
    /** Deprecated: the exported bundle intentionally does not include Pyrogram's duplicate session. */
    public static final int FORMAT_PYROGRAM = 1 << 1;
    public static final int FORMAT_JSON = 1 << 2;
    public static final int FORMAT_TDATA = 1 << 3;
    /** The stable bundle contains only the verified session, JSON metadata and tdata. */
    public static final int FORMAT_ALL =
            FORMAT_TELETHON | FORMAT_JSON | FORMAT_TDATA;

    private ProtocolExporter() {
    }

    public static final class Result {
        public final File zipFile;
        public final boolean publicDirectory;

        Result(File zipFile, boolean publicDirectory) {
            this.zipFile = zipFile;
            this.publicDirectory = publicDirectory;
        }
    }

    /** 读取账号在内存中的授权信息，不落盘。 */
    public static ProtocolAccount readAccount(Context context, int accountNum) throws Exception {
        UserConfig config = UserConfig.getInstance(accountNum);
        if (config == null || !config.isClientActivated()) {
            throw new Exception("这个账号还没有登录完成，取不到授权数据");
        }
        TLRPC.User user = config.getCurrentUser();
        if (user == null || user.id == 0) {
            throw new Exception("账号信息还没准备好，稍后再试");
        }
        ConnectionsManager manager = ConnectionsManager.getInstance(accountNum);
        byte[] authKey = manager.getCurrentAuthKey();
        if (authKey == null || authKey.length != ProtocolAccount.KEY_SIZE) {
            if (authKey != null) {
                Arrays.fill(authKey, (byte) 0);
            }
            throw new Exception("读取授权密钥失败（native 未就绪或账号未完成握手）");
        }
        int dcId = manager.getCurrentDatacenterId();
        if (dcId < 1 || dcId > 5) {
            Arrays.fill(authKey, (byte) 0);
            throw new Exception("数据中心编号异常：" + dcId);
        }
        ProtocolAccount account = new ProtocolAccount(authKey, dcId);
        account.userId = user.id;
        account.phone = user.phone == null ? "" : user.phone;
        account.country = CountryCodes.resolve(account.phone);
        account.apiId = BuildVars.APP_ID;
        account.apiHash = BuildVars.APP_HASH;
        account.isBot = user.bot;
        account.source = "本机账号 " + accountNum;
        return account;
    }

    /** 当前客户端里所有已登录的账号槽位。 */
    public static List<Integer> activeAccounts() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            try {
                if (UserConfig.getInstance(i).isClientActivated()) {
                    result.add(i);
                }
            } catch (Throwable ignore) {
            }
        }
        return result;
    }

    /**
     * 导出单个账号：生成临时目录 → 打包 → 落到「黄昏/协议制作」。
     */
    public static Result export(Context context, int accountNum, int formats) throws Exception {
        ProtocolAccount account = readAccount(context, accountNum);
        return exportAccount(context, account, formats);
    }

    public static Result exportAccount(Context context, ProtocolAccount account, int formats) throws Exception {
        if (account == null || !account.isValid()) {
            if (account != null && account.authKey != null) {
                Arrays.fill(account.authKey, (byte) 0);
            }
            throw new Exception("授权数据不完整，无法生成协议文件");
        }
        if (formats == 0) {
            formats = FORMAT_ALL;
        }
        if ((formats & FORMAT_ALL) == 0) {
            Arrays.fill(account.authKey, (byte) 0);
            throw new Exception("没有选择有效的协议格式");
        }
        formats &= FORMAT_ALL;
        String baseName = account.displayName();
        File workDir = null;
        try {
            workDir = ProtocolPaths.createTemporaryDirectory(context, "export_");
            if ((formats & FORMAT_TELETHON) != 0) {
                SessionCodec.writeTelethonSession(account, new File(workDir, baseName + ".session"));
            }
            if ((formats & FORMAT_JSON) != 0) {
                SessionCodec.writeJson(account, new File(workDir, baseName + ".json"));
            }
            if ((formats & FORMAT_TDATA) != 0) {
                File tdataDir = new File(workDir, "tdata");
                TdataCodec.writeTdata(java.util.Collections.singletonList(account), tdataDir);
                // 写完立刻回读校验，防止把坏结构打进包。
                List<ProtocolAccount> verified = TdataCodec.readBack(tdataDir);
                for (ProtocolAccount verifiedAccount : verified) {
                    if (verifiedAccount.authKey != null) {
                        Arrays.fill(verifiedAccount.authKey, (byte) 0);
                    }
                }
            }

            ProtocolPaths.Resolved resolved = ProtocolPaths.resolveOutputDirectory(context);
            File zipFile = ProtocolPaths.uniqueTarget(resolved.directory, baseName, ".zip");
            ZipUtil.zipDirectory(workDir, zipFile);
            if (!zipFile.isFile() || zipFile.length() <= 0) {
                throw new Exception("压缩包写入失败");
            }
            return new Result(zipFile, resolved.isPublic);
        } finally {
            if (workDir != null) {
                ProtocolPaths.deleteRecursive(workDir);
            }
            Arrays.fill(account.authKey, (byte) 0);
        }
    }
}
