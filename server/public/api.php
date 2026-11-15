<?php
require_once __DIR__ . '/../app/db.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('X-Content-Type-Options: nosniff');

function api_fail(int $status, string $message): never
{
    http_response_code($status);
    echo json_encode(['ok' => false, 'error' => $message], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

$cfg = hh_config();
$provided = (string) ($_SERVER['HTTP_X_HUANGHUN_KEY'] ?? '');
if ($provided === '' || !hash_equals($cfg['api_key'], $provided)) {
    api_fail(401, 'unauthorized');
}
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    header('Allow: GET');
    api_fail(405, 'method_not_allowed');
}

$resource = (string) ($_GET['resource'] ?? '');
try {
    if ($resource === 'proxies') {
        $rows = hh_proxy_rows(true);
        $proxies = [];
        foreach ($rows as $row) {
            $proxies[] = [
                'address' => (string) $row['address'],
                'port' => (int) $row['port'],
                'username' => (string) $row['username'],
                'password' => (string) $row['password'],
                'secret' => (string) $row['secret'],
            ];
        }
        echo json_encode([
            'ok' => true,
            'revision' => (int) hh_setting('proxy_rev', '0'),
            'count' => count($proxies),
            'proxies' => $proxies,
            'server_time' => time(),
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }
    if ($resource === 'config') {
        $releasePath = hh_release_path();
        $latestVersion = hh_setting('latest_version', '');
        $releaseVersion = hh_setting('latest_apk_version', '');
        $releaseSha = hh_setting('latest_apk_sha256', '');
        $releaseSize = (int) hh_setting('latest_apk_size', '0');
        $releaseAvailable = is_file($releasePath) && is_readable($releasePath)
            && $releaseVersion !== '' && hash_equals($latestVersion, $releaseVersion)
            && preg_match('/^[a-f0-9]{64}$/i', $releaseSha) === 1
            && $releaseSize > 0 && filesize($releasePath) === $releaseSize;
        $publicBase = preg_replace('~/api\\.php(?:\\?.*)?$~', '', $cfg['api_base']);
        echo json_encode([
            'ok' => true,
            'revision' => (int) hh_setting('config_rev', '0'),
            'latest_version' => $latestVersion,
            'update_url' => hh_setting('update_url', ''),
            'server_download_url' => rtrim((string) $publicBase, '/') . '/download.php',
            'server_download_available' => $releaseAvailable,
            'server_apk_sha256' => $releaseAvailable ? $releaseSha : '',
            'server_apk_size' => $releaseAvailable ? $releaseSize : 0,
            'force_update' => hh_setting('force_update', '0') === '1',
            'announcement' => hh_setting('announcement', ''),
            'proxy_count' => count(hh_proxy_rows(true)),
            'proxy_revision' => (int) hh_setting('proxy_rev', '0'),
            'server_time' => time(),
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }
    api_fail(404, 'not_found');
} catch (Throwable $e) {
    hh_log('API error: ' . $e->getMessage());
    api_fail(500, 'server_error');
}
