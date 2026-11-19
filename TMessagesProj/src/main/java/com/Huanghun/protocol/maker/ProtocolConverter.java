package com.Huanghun.protocol.maker;

import android.content.Context;

import org.telegram.messenger.BuildVars;
import com.Huanghun.protocol.ProtocolParser;
import com.Huanghun.protocol.TdataParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 功能二：session / tdata / json 三种协议格式互转。
 *
 * <p>接受的输入：单个 .session、单个 .json、一个 tdata 目录，或把它们装在一起的 zip。
 * 输出统一为 zip，命名规则与本机导出保持一致。</p>
 */
public final class ProtocolConverter {

    private ProtocolConverter() {
    }

    public static final class Result {
        public final List<File> zipFiles = new ArrayList<>();
        public final List<String> failures = new ArrayList<>();
        public final boolean publicDirectory;

        Result(boolean publicDirectory) {
            this.publicDirectory = publicDirectory;
        }
    }

    public static final class Loaded {
        public final List<ProtocolAccount> accounts = new ArrayList<>();
        public final List<String> failures = new ArrayList<>();
    }

    /**
     * 把任意输入展开成账号列表。
     */
    public static Loaded load(Context context, File input) {
        Loaded loaded = new Loaded();
        File workDir = null;
        try {
            File root = input;
            if (input != null && input.isFile() && ZipUtil.isZip(input)) {
                workDir = ProtocolPaths.createTemporaryDirectory(context, "import_");
                ZipUtil.unzip(input, workDir);
                root = workDir;
            }
            collect(root, loaded, 0, context);
        } catch (Exception e) {
            loaded.failures.add(describe(e));
        } finally {
            if (workDir != null) {
                ProtocolPaths.deleteRecursive(workDir);
            }
        }
        return loaded;
    }

    private static void collect(File node, Loaded loaded, int depth, Context context) {
        if (node == null || depth > 4) {
            return;
        }
        if (node.isDirectory()) {
            if (new File(node, "key_datas").isFile()) {
                try {
                    List<ProtocolParser.SessionData> sessions = TdataParser.parse(node);
                    if (sessions.isEmpty()) {
                        loaded.failures.add(node.getName() + "：tdata 里没有授权数据");
                    }
                    for (ProtocolParser.SessionData session : sessions) {
                        if (session.authKey != null && session.authKey.length == ProtocolAccount.KEY_SIZE) {
                            loaded.accounts.add(fromSessionData(session, node.getName()));
                        }
                    }
                } catch (Exception e) {
                    loaded.failures.add(node.getName() + "：" + describe(e));
                }
                return;
            }
            File[] children = node.listFiles();
            if (children != null) {
                java.util.Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File child : children) {
                    collect(child, loaded, depth + 1, context);
                }
            }
            return;
        }
        if (node.isFile() && ZipUtil.isZip(node)) {
            File unpacked = null;
            try {
                unpacked = ProtocolPaths.createTemporaryDirectory(context, "inner_zip_");
                ZipUtil.unzip(node, unpacked);
                collect(unpacked, loaded, depth + 1, context);
            } catch (Exception e) {
                loaded.failures.add(node.getName() + "：" + describe(e));
            } finally {
                ProtocolPaths.deleteRecursive(unpacked);
            }
            return;
        }
        String name = node.getName().toLowerCase(java.util.Locale.ROOT);
        try {
            if (name.endsWith(".session")) {
                loaded.accounts.addAll(SessionCodec.readSession(node, SessionCodec.KIND_AUTO));
            } else if (name.endsWith(".json")) {
                loaded.accounts.addAll(SessionCodec.readJson(node));
            }
        } catch (Exception e) {
            loaded.failures.add(node.getName() + "：" + describe(e));
        }
    }

    private static ProtocolAccount fromSessionData(ProtocolParser.SessionData data, String source) {
        ProtocolAccount account = new ProtocolAccount(data.authKey, data.dcId);
        account.userId = data.userId;
        account.source = source;
        account.apiId = BuildVars.APP_ID;
        account.apiHash = BuildVars.APP_HASH;
        return account;
    }

    /**
     * 转换并落盘。每个账号一个 zip，里面放本次要求的全部格式。
     */
    public static Result convert(Context context, File input, int formats) {
        Result result = new Result(ProtocolPaths.resolveOutputDirectory(context).isPublic);
        Loaded loaded = load(context, input);
        result.failures.addAll(loaded.failures);
        if (loaded.accounts.isEmpty()) {
            return result;
        }
        try {
            for (ProtocolAccount account : loaded.accounts) {
                try {
                    account.apiId = BuildVars.APP_ID;
                    account.apiHash = BuildVars.APP_HASH;
                    if (account.country == null || account.country.isEmpty()) {
                        account.country = CountryCodes.resolve(account.phone);
                    }
                    ProtocolExporter.Result exported = ProtocolExporter.exportAccount(context, account, formats);
                    result.zipFiles.add(exported.zipFile);
                } catch (Exception e) {
                    result.failures.add(account.displayName() + "：" + describe(e));
                }
            }
        } finally {
            for (ProtocolAccount account : loaded.accounts) {
                if (account != null && account.authKey != null) {
                    java.util.Arrays.fill(account.authKey, (byte) 0);
                }
            }
        }
        return result;
    }

    static String describe(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }
}
