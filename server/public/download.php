<?php
require_once __DIR__ . '/../app/db.php';

$cfg = hh_config();
$provided = (string) ($_SERVER['HTTP_X_HUANGHUN_KEY'] ?? '');
if ($provided === '' || !hash_equals($cfg['api_key'], $provided)) {
    http_response_code(401);
    exit('unauthorized');
}
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    header('Allow: GET');
    http_response_code(405);
    exit('method_not_allowed');
}
$path = hh_release_path();
if (!is_file($path) || !is_readable($path)
    || hh_setting('latest_apk_version', '') === ''
    || !hash_equals(hh_setting('latest_version', ''), hh_setting('latest_apk_version', ''))
    || preg_match('/^[a-f0-9]{64}$/i', hh_setting('latest_apk_sha256', '')) !== 1) {
    http_response_code(404);
    exit('release_not_found');
}
$size = filesize($path);
if ($size === false || $size <= 0 || $size > 256 * 1024 * 1024 || (int) hh_setting('latest_apk_size', '0') !== $size) {
    http_response_code(500);
    exit('invalid_release');
}
$sha256 = hash_file('sha256', $path);
if ($sha256 === false) {
    http_response_code(500);
    exit('checksum_failed');
}
header('Content-Type: application/vnd.android.package-archive');
header('Content-Length: ' . $size);
header('Content-Disposition: attachment; filename="huanghun-latest.apk"');
header('X-APK-SHA256: ' . $sha256);
header('Cache-Control: no-store, no-cache, must-revalidate');
header('X-Content-Type-Options: nosniff');
readfile($path);
