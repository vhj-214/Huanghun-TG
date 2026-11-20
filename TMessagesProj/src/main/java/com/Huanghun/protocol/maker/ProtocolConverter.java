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
 * <p>接受的输入：单个 .session，或包含 Session 的 ZIP；JSON 仅接受含 auth_key 的本机提取数据；
 * tdata 输入要求 ZIP 内包含完整 tdata 目录。
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
        return load(context, input, "");
    }

    public static Loaded load(Context context, File input, String localPasscode) {
        return load(context, input, localPasscode, "all");
    }

    public static Loaded load(Context context, File input, String localPasscode, String inputKind) {
        Loaded loaded = new Loaded();
        File workDir = null;
        String scanKind = inputKind == null || inputKind.isEmpty() ? "all" : inputKind;
        try {
            File root = input;
            if (input != null && input.isFile() && ZipUtil.isZip(input)) {
                workDir = ProtocolPaths.createTemporaryDirectory(context, "import_");
                ZipUtil.unzip(input, workDir);
                root = workDir;
            }
            collect(root, loaded, 0, context, localPasscode, scanKind);
        } catch (Exception e) {
            loaded.failures.add(describe(e));
        } finally {
            if (workDir != null) {
                ProtocolPaths.deleteRecursive(workDir);
            }
        }
        deduplicateAccounts(loaded.accounts);
        return loaded;
    }

    private static void deduplicateAccounts(List<ProtocolAccount> accounts) {
        ArrayList<ProtocolAccount> unique = new ArrayList<>();
        for (ProtocolAccount candidate : accounts) {
            ProtocolAccount existing = null;
            for (ProtocolAccount account : unique) {
                if (account.authKey != null && candidate.authKey != null
                        && java.util.Arrays.equals(account.authKey, candidate.authKey)) {
                    existing = account;
                    break;
                }
            }
            if (existing == null) {
                unique.add(candidate);
            } else {
                if ((existing.phone == null || existing.phone.isEmpty()) && candidate.phone != null) {
                    existing.phone = candidate.phone;
                }
                if (existing.userId == 0) {
                    existing.userId = candidate.userId;
                }
                if ((existing.country == null || existing.country.isEmpty()) && candidate.country != null) {
                    existing.country = candidate.country;
                }
                if ((existing.username == null || existing.username.isEmpty()) && candidate.username != null) {
                    existing.username = candidate.username;
                }
                if (existing.premium == null) {
                    existing.premium = candidate.premium;
                }
                if (candidate.authKey != null) {
                    java.util.Arrays.fill(candidate.authKey, (byte) 0);
                }
            }
        }
        accounts.clear();
        accounts.addAll(unique);
    }

    private static void collect(File node, Loaded loaded, int depth, Context context,
                                String localPasscode, String inputKind) {
        if (node == null) {
            return;
        }
        if (depth > 6) {
            loaded.failures.add(node.getName() + "：压缩包嵌套层级超过 6 层，已停止扫描");
            return;
        }
        if (node.isDirectory()) {
            if (new File(node, "key_datas").isFile()) {
                if (!"all".equals(inputKind) && !"tdata".equalsIgnoreCase(inputKind)) {
                    return;
                }
                try {
                    List<ProtocolParser.SessionData> sessions = TdataParser.parse(node, localPasscode);
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
                    collect(child, loaded, depth + 1, context, localPasscode, inputKind);
                }
            }
            return;
        }
        if (node.isFile() && ZipUtil.isZip(node)) {
            File unpacked = null;
            try {
                unpacked = ProtocolPaths.createTemporaryDirectory(context, "inner_zip_");
                ZipUtil.unzip(node, unpacked);
                collect(unpacked, loaded, depth + 1, context, localPasscode, inputKind);
            } catch (Exception e) {
                loaded.failures.add(node.getName() + "：" + describe(e));
            } finally {
                ProtocolPaths.deleteRecursive(unpacked);
            }
            return;
        }
        String name = node.getName().toLowerCase(java.util.Locale.ROOT);
        boolean allowSession = "all".equals(inputKind) || "session".equalsIgnoreCase(inputKind);
        boolean allowJson = "all".equals(inputKind) || "json".equalsIgnoreCase(inputKind);
        try {
            if (allowSession && (name.endsWith(".session") || SessionCodec.isSqliteDatabase(node))) {
                loaded.accounts.addAll(SessionCodec.readSession(node, SessionCodec.KIND_AUTO));
            } else if (allowJson && (name.endsWith(".json") || looksLikeJson(node))) {
                loaded.accounts.addAll(SessionCodec.readJson(node));
            }
        } catch (Exception e) {
            loaded.failures.add(node.getName() + "：" + describe(e));
        }
    }

    private static boolean looksLikeJson(File file) {
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            int value;
            while ((value = input.read()) != -1) {
                if (value == ' ' || value == '\n' || value == '\r' || value == '\t') {
                    continue;
                }
                return value == '{' || value == '[';
            }
        } catch (Exception ignore) {
        }
        return false;
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
        return convert(context, input, formats, "");
    }

    public static Result convert(Context context, File input, int formats, String localPasscode) {
        return convert(context, input, formats, "all", localPasscode);
    }

    public static Result convert(Context context, File input, int formats,
                                 String inputKind, String localPasscode) {
        Result result = new Result(ProtocolPaths.resolveOutputDirectory(context,
                ProtocolPaths.FOLDER_CONVERT).isPublic);
        Loaded loaded = load(context, input, localPasscode, inputKind);
        result.failures.addAll(loaded.failures);
        if (loaded.accounts.isEmpty()) {
            return result;
        }
        try {
            ArrayList<ProtocolAccount> verifiedAccounts = new ArrayList<>();
            for (ProtocolAccount account : loaded.accounts) {
                account.apiId = BuildVars.APP_ID;
                account.apiHash = BuildVars.APP_HASH;
                String validationFailure = ProtocolSessionVerifier.verify(account);
                if (validationFailure != null) {
                    result.failures.add(account.displayName() + "：" + validationFailure);
                } else {
                    verifiedAccounts.add(account);
                }
            }
            for (ProtocolAccount account : verifiedAccounts) {
                try {
                    if (account.country == null || account.country.isEmpty()) {
                        account.country = CountryCodes.resolve(account.phone);
                    }
                    ProtocolExporter.Result exported = ProtocolExporter.exportAccount(context, account, formats,
                            ProtocolPaths.FOLDER_CONVERT);
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
