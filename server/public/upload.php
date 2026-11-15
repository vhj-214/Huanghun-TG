<?php
require_once __DIR__ . '/../app/db.php';

header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');

function upload_page(string $title, string $message, int $status = 200): never
{
    http_response_code($status);
    $safeTitle = htmlspecialchars($title, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    $safeMessage = htmlspecialchars($message, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    echo '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
        . '<title>' . $safeTitle . '</title><style>body{font:16px/1.6 system-ui,sans-serif;background:#f4f7fb;color:#202630;margin:8vh auto;max-width:600px;padding:20px}main{background:#fff;border-radius:16px;padding:28px;box-shadow:0 8px 30px #1232}h1{margin-top:0}input,button{font:inherit}button{background:#1688e8;color:#fff;border:0;border-radius:8px;padding:10px 16px}</style>'
        . '<main><h1>' . $safeTitle . '</h1><p>' . nl2br($safeMessage) . '</p></main></html>';
    exit;
}

$method = (string) ($_SERVER['REQUEST_METHOD'] ?? 'GET');
$token = (string) ($method === 'POST' ? ($_POST['token'] ?? '') : ($_GET['token'] ?? ''));
$expectedHash = hh_setting('upload_token_hash', '');
$expires = (int) hh_setting('upload_token_expiry', '0');
if (!(($method === 'GET' && $token === '')
    || (strlen($token) >= 32 && strlen($token) <= 128 && $expectedHash !== '' && $expires >= time() && hash_equals($expectedHash, hash('sha256', $token))))) {
    upload_page('上传链接已失效', '该链接已使用或已过期。请回到管理员机器人重新生成上传链接。', 403);
}

if ($method === 'GET') {
    $version = htmlspecialchars(hh_setting('upload_version', ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    $safeToken = htmlspecialchars($token, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    echo '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
        . '<title>上传黄昏最新版客户端</title><style>body{font:16px/1.6 system-ui,sans-serif;background:#f4f7fb;color:#202630;margin:8vh auto;max-width:600px;padding:20px}main{background:#fff;border-radius:16px;padding:28px;box-shadow:0 8px 30px #1232}input,button{font:inherit;margin:10px 0}button{background:#1688e8;color:#fff;border:0;border-radius:8px;padding:10px 16px}</style>'
        . '<main><h1>上传黄昏最新版客户端</h1><p>目标版本：<b>' . $version . '</b>。仅上传由你构建并使用正确签名的 APK；最大 256 MiB。此链接一次有效，15 分钟后过期。</p>'
        . '<form method="post" enctype="multipart/form-data"><input id="upload-token" type="hidden" name="token" value="' . $safeToken . '"><input type="file" name="apk" accept=".apk,application/vnd.android.package-archive" required><br><button type="submit">上传并发布</button></form></main>'
        . '<script>(function(){var m=location.hash.match(/^#token=([a-f0-9]{64})$/i);if(m){document.getElementById("upload-token").value=m[1];history.replaceState(null,"",location.pathname);}})();</script></html>';
    exit;
}
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    header('Allow: GET, POST');
    upload_page('请求不支持', '请重新打开机器人提供的上传链接。', 405);
}

$file = $_FILES['apk'] ?? null;
if (!is_array($file) || (int) ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK || !is_uploaded_file((string) ($file['tmp_name'] ?? ''))) {
    upload_page('上传失败', '没有收到完整文件，请重新选择 APK。', 400);
}
$size = (int) ($file['size'] ?? 0);
$name = (string) ($file['name'] ?? '');
if ($size < 1 || $size > 256 * 1024 * 1024 || strtolower(pathinfo($name, PATHINFO_EXTENSION)) !== 'apk') {
    upload_page('文件不符合要求', '请上传 1 字节至 256 MiB 的 .apk 文件。', 400);
}
$handle = fopen((string) $file['tmp_name'], 'rb');
$magic = $handle ? fread($handle, 4) : '';
if ($handle) fclose($handle);
if ($magic !== "PK\x03\x04" && $magic !== "PK\x05\x06" && $magic !== "PK\x07\x08") {
    upload_page('APK 文件无效', '文件不是有效的 ZIP/APK 容器。', 400);
}
if (class_exists(ZipArchive::class)) {
    $zip = new ZipArchive();
    if ($zip->open((string) $file['tmp_name']) !== true || $zip->locateName('AndroidManifest.xml') === false) {
        if ($zip->status === ZipArchive::ER_OK) $zip->close();
        upload_page('APK 文件无效', 'APK 中没有 AndroidManifest.xml。', 400);
    }
    $zip->close();
}
$version = hh_setting('upload_version', '');
if (!preg_match('/^[0-9]+(?:\.[0-9]+){0,4}(?:[-+][A-Za-z0-9._-]+)?$/', $version)) {
    upload_page('版本信息无效', '请回到机器人重新开始上传，并输入数字版本号，例如 16.1。', 400);
}
$now = time();
$claim = hh_db()->prepare(
    "UPDATE settings SET value = '', updated_at = ? WHERE key = 'upload_token_hash' AND value = ?
     AND EXISTS (SELECT 1 FROM settings WHERE key = 'upload_token_expiry' AND CAST(value AS INTEGER) >= ?)"
);
$claim->execute([$now, hash('sha256', $token), $now]);
if ($claim->rowCount() !== 1) {
    upload_page('上传链接已使用', '该一次性链接已被使用。请回到管理员机器人重新生成上传链接。', 403);
}

$directory = dirname(hh_release_path());
if (!is_dir($directory) && !mkdir($directory, 0700, true) && !is_dir($directory)) {
    upload_page('服务器错误', '无法创建发布目录，请稍后重试。', 500);
}
@chmod($directory, 0700);
$temporary = $directory . '/latest-' . bin2hex(random_bytes(8)) . '.part';
if (!move_uploaded_file((string) $file['tmp_name'], $temporary)) {
    upload_page('上传失败', '服务器未能保存 APK，请重试。', 500);
}
@chmod($temporary, 0600);
$sha = hash_file('sha256', $temporary);
if ($sha === false) {
    @unlink($temporary);
    upload_page('发布失败', '服务器无法完成文件校验或原子替换。', 500);
}
hh_set_setting('latest_apk_version', '');
hh_set_setting('latest_apk_sha256', '');
hh_set_setting('latest_apk_size', '0');
if (!rename($temporary, hh_release_path())) {
    @unlink($temporary);
    upload_page('发布失败', '服务器无法完成文件校验或原子替换。', 500);
}
@chmod(hh_release_path(), 0600);
hh_set_setting('latest_version', $version);
hh_set_setting('latest_apk_version', $version);
hh_set_setting('latest_apk_sha256', $sha);
hh_set_setting('latest_apk_size', (string) $size);
hh_set_setting('upload_token_hash', '');
hh_set_setting('upload_token_expiry', '0');
hh_set_setting('upload_version', '');
hh_bump('config_rev');
try {
    $stmt = hh_db()->prepare('DELETE FROM bot_state WHERE chat_id = ?');
    $stmt->execute([(int) hh_setting('admin_id', (string) hh_config()['admin_id'])]);
} catch (Throwable $ignored) {}
hh_event('apk_uploaded', 'version=' . $version . ', size=' . $size . ', sha256=' . $sha, (int) hh_setting('admin_id', (string) hh_config()['admin_id']));
upload_page('上传成功', '黄昏客户端 ' . $version . ' 已发布。客户端点击“检查更新”即可比较版本并下载。');
