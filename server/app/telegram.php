<?php
require_once __DIR__ . '/db.php';

function hh_tg(string $method, array $params = []): array
{
    $url = 'https://api.telegram.org/bot' . hh_config()['bot_token'] . '/' . $method;
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => $params,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 8,
        CURLOPT_TIMEOUT => 20,
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_HTTPHEADER => ['Expect:'],
    ]);
    $body = curl_exec($ch);
    $error = curl_error($ch);
    $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($body === false || $status < 200 || $status >= 300) {
        hh_log("Telegram API {$method} failed HTTP {$status}: {$error}");
        return ['ok' => false, 'description' => $error ?: 'HTTP ' . $status];
    }
    $decoded = json_decode($body, true);
    return is_array($decoded) ? $decoded : ['ok' => false, 'description' => 'Invalid Telegram response'];
}

function hh_tg_send(int $chatId, string $text, ?array $keyboard = null): void
{
    $params = [
        'chat_id' => $chatId,
        'text' => $text,
        'parse_mode' => 'HTML',
        'disable_web_page_preview' => 'true',
    ];
    if ($keyboard !== null) {
        $params['reply_markup'] = json_encode($keyboard, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }
    hh_tg('sendMessage', $params);
}

function hh_admin_keyboard(): array
{
    return [
        'keyboard' => [
            [['text' => '代理列表'], ['text' => '添加代理']],
            [['text' => '删除代理'], ['text' => '更新配置']],
            [['text' => '上传最新版客户端'], ['text' => '配置更新链接']],
            [['text' => '系统状态'], ['text' => '取消操作']],
        ],
        'resize_keyboard' => true,
        'is_persistent' => true,
    ];
}

function hh_safe_html(string $text): string
{
    return htmlspecialchars($text, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function hh_valid_proxy(array $proxy): bool
{
    $host = trim((string) ($proxy['address'] ?? ''));
    $port = filter_var($proxy['port'] ?? null, FILTER_VALIDATE_INT);
    if ($host === '' || strlen($host) > 253 || preg_match('/[\s\x00-\x1f<>"\']/', $host)) {
        return false;
    }
    if (!$port || $port < 1 || $port > 65535) {
        return false;
    }
    foreach (['username', 'password', 'secret', 'label'] as $key) {
        if (strlen((string) ($proxy[$key] ?? '')) > 512) {
            return false;
        }
    }
    return true;
}

function hh_parse_proxy(string $value): ?array
{
    $value = trim($value);
    if ($value === '') {
        return null;
    }
    $proxy = ['address' => '', 'port' => 0, 'username' => '', 'password' => '', 'secret' => '', 'label' => ''];

    if (preg_match('~^(?:https?://)?(?:t\.me|telegram\.me)/(?:proxy|socks)\?(.+)$~i', $value, $m)) {
        parse_str($m[1], $query);
        $proxy['address'] = (string) ($query['server'] ?? '');
        $proxy['port'] = (int) ($query['port'] ?? 0);
        $proxy['username'] = (string) ($query['user'] ?? '');
        $proxy['password'] = (string) ($query['pass'] ?? '');
        $proxy['secret'] = (string) ($query['secret'] ?? '');
    } elseif (preg_match('~^tg://proxy\?(.+)$~i', $value, $m)) {
        parse_str($m[1], $query);
        $proxy['address'] = (string) ($query['server'] ?? '');
        $proxy['port'] = (int) ($query['port'] ?? 0);
        $proxy['username'] = (string) ($query['user'] ?? '');
        $proxy['password'] = (string) ($query['pass'] ?? '');
        $proxy['secret'] = (string) ($query['secret'] ?? '');
    } elseif (preg_match('~^socks5://(.+)$~i', $value, $m)) {
        $parts = parse_url($value);
        if (!is_array($parts) || empty($parts['host'])) {
            return null;
        }
        $proxy['address'] = (string) $parts['host'];
        $proxy['port'] = (int) ($parts['port'] ?? 1080);
        $proxy['username'] = (string) ($parts['user'] ?? '');
        $proxy['password'] = (string) ($parts['pass'] ?? '');
    } elseif (preg_match('/^([^:\s]+):(\d+):([^:\s]*):([^\s]*)$/', $value, $m)) {
        $proxy['address'] = $m[1];
        $proxy['port'] = (int) $m[2];
        $proxy['username'] = $m[3];
        $proxy['password'] = $m[4];
    } elseif (preg_match('/^([^:\s]+):(\d+)(?::([^:\s]*):([^\s]*))?$/', $value, $m)) {
        $proxy['address'] = $m[1];
        $proxy['port'] = (int) $m[2];
        $proxy['username'] = (string) ($m[3] ?? '');
        $proxy['password'] = (string) ($m[4] ?? '');
    } else {
        return null;
    }
    return hh_valid_proxy($proxy) ? $proxy : null;
}

function hh_add_proxy(array $proxy): bool
{
    $now = time();
    $stmt = hh_db()->prepare(
        'INSERT INTO proxies (address, port, username, password, secret, label, enabled, sort_order, created_at, updated_at)
         VALUES (:address, :port, :username, :password, :secret, :label, 1,
                 COALESCE((SELECT MAX(sort_order) + 1 FROM proxies), 0), :created, :updated)
         ON CONFLICT(address, port, username, password, secret) DO UPDATE SET
             label = excluded.label, enabled = 1, updated_at = excluded.updated_at'
    );
    return $stmt->execute([
        ':address' => $proxy['address'], ':port' => (int) $proxy['port'],
        ':username' => $proxy['username'] ?? '', ':password' => $proxy['password'] ?? '',
        ':secret' => $proxy['secret'] ?? '', ':label' => $proxy['label'] ?? '',
        ':created' => $now, ':updated' => $now,
    ]);
}

function hh_update_version_valid(string $version): bool
{
    return preg_match('/^[0-9]+(?:\\.[0-9]+){0,4}(?:[-+][A-Za-z0-9._-]+)?$/', $version) === 1;
}

function hh_public_url(string $filename): string
{
    $base = preg_replace('~/api\\.php(?:\\?.*)?$~', '', hh_config()['api_base']);
    return rtrim((string) $base, '/') . '/' . ltrim($filename, '/');
}

function hh_issue_upload_link(int $chatId, string $version): string
{
    $token = bin2hex(random_bytes(32));
    hh_set_setting('upload_version', $version);
    hh_set_setting('upload_token_hash', hash('sha256', $token));
    hh_set_setting('upload_token_expiry', (string) (time() + 900));
    hh_state_set($chatId, 'upload_file', $version);
    return hh_public_url('upload.php') . '#token=' . $token;
}

function hh_finalize_apk(string $sourcePath, string $version, int $chatId): bool
{
    $size = is_file($sourcePath) ? filesize($sourcePath) : false;
    if ($size === false || $size < 1 || $size > 256 * 1024 * 1024 || !hh_update_version_valid($version)) {
        return false;
    }
    $handle = fopen($sourcePath, 'rb');
    $magic = $handle ? fread($handle, 4) : '';
    if ($handle) fclose($handle);
    if ($magic !== "PK\x03\x04" && $magic !== "PK\x05\x06" && $magic !== "PK\x07\x08") {
        return false;
    }
    if (class_exists(ZipArchive::class)) {
        $zip = new ZipArchive();
        if ($zip->open($sourcePath) !== true) return false;
        $hasManifest = $zip->locateName('AndroidManifest.xml') !== false;
        $zip->close();
        if (!$hasManifest) return false;
    }
    $path = hh_release_path();
    $directory = dirname($path);
    if (!is_dir($directory) && !mkdir($directory, 0700, true) && !is_dir($directory)) return false;
    @chmod($directory, 0700);
    $temporary = $directory . '/latest-' . bin2hex(random_bytes(8)) . '.part';
    if (!copy($sourcePath, $temporary)) return false;
    @chmod($temporary, 0600);
    $sha = hash_file('sha256', $temporary);
    if ($sha === false) {
        @unlink($temporary);
        return false;
    }
    hh_set_setting('latest_apk_version', '');
    hh_set_setting('latest_apk_sha256', '');
    hh_set_setting('latest_apk_size', '0');
    if (!rename($temporary, $path)) {
        @unlink($temporary);
        return false;
    }
    @chmod($path, 0600);
    hh_set_setting('latest_version', $version);
    hh_set_setting('latest_apk_version', $version);
    hh_set_setting('latest_apk_sha256', $sha);
    hh_set_setting('latest_apk_size', (string) $size);
    hh_set_setting('upload_token_hash', '');
    hh_set_setting('upload_token_expiry', '0');
    hh_set_setting('upload_version', '');
    hh_bump('config_rev');
    hh_state_clear($chatId);
    hh_event('apk_uploaded', 'version=' . $version . ', size=' . $size . ', sha256=' . $sha, $chatId);
    return true;
}

function hh_download_telegram_document(array $document, string $version, int $chatId): bool
{
    $fileSize = (int) ($document['file_size'] ?? 0);
    if ($fileSize < 1 || $fileSize > 20 * 1024 * 1024) return false;
    $response = hh_tg('getFile', ['file_id' => (string) ($document['file_id'] ?? '')]);
    $filePath = (string) ($response['result']['file_path'] ?? '');
    if (empty($response['ok']) || $filePath === '') return false;
    $temp = tempnam(sys_get_temp_dir(), 'hh-apk-');
    if ($temp === false) return false;
    $handle = fopen($temp, 'wb');
    $url = 'https://api.telegram.org/file/bot' . hh_config()['bot_token'] . '/' . ltrim($filePath, '/');
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_FILE => $handle,
        CURLOPT_FOLLOWLOCATION => false,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 120,
        CURLOPT_SSL_VERIFYPEER => true,
    ]);
    $ok = curl_exec($ch) !== false && (int) curl_getinfo($ch, CURLINFO_HTTP_CODE) === 200;
    curl_close($ch);
    fclose($handle);
    $saved = $ok && filesize($temp) <= 20 * 1024 * 1024 && hh_finalize_apk($temp, $version, $chatId);
    @unlink($temp);
    return $saved;
}

function hh_render_admin_menu(int $chatId, string $notice = ''): void
{
    $text = "<b>黄昏服务机器人管理面板</b>\n请选择操作：";
    if ($notice !== '') {
        $text = hh_safe_html($notice) . "\n\n" . $text;
    }
    hh_tg_send($chatId, $text, hh_admin_keyboard());
}

function hh_bot_start(int $chatId, int $userId, string $chatType): void
{
    $adminId = (int) hh_setting('admin_id', (string) hh_config()['admin_id']);
    if ($userId !== $adminId || $chatType !== 'private') {
        hh_event('unauthorized_start', 'user=' . $userId . ', chat=' . $chatType, $chatId);
        hh_tg_send($chatId, '权限不足，无法访问。');
        return;
    }
    hh_state_clear($chatId);
    hh_tg_send($chatId, "欢迎，管理员。\n可在此维护内置代理和客户端配置。", hh_admin_keyboard());
}

function hh_bot_handle_message(array $message): void
{
    $chatId = (int) ($message['chat']['id'] ?? 0);
    $userId = (int) ($message['from']['id'] ?? 0);
    $chatType = (string) ($message['chat']['type'] ?? '');
    $text = trim((string) ($message['text'] ?? ''));
    $document = $message['document'] ?? null;
    if ($chatId === 0 || $userId === 0 || ($text === '' && !is_array($document))) {
        return;
    }
    if (preg_match('~^/start(?:@\w+)?(?:\s|$)~i', $text)) {
        hh_bot_start($chatId, $userId, $chatType);
        return;
    }
    if ($userId !== (int) hh_setting('admin_id', (string) hh_config()['admin_id']) || $chatType !== 'private') {
        hh_tg_send($chatId, '权限不足，无法访问。');
        hh_event('unauthorized_message', 'user=' . $userId . ', chat=' . $chatType, $chatId);
        return;
    }

    $stateRow = hh_state_get($chatId);
    $state = (string) $stateRow['state'];
    if ($state === 'upload_file' && is_array($document)) {
        $version = (string) ($stateRow['payload'] ?: hh_setting('upload_version', ''));
        $size = (int) ($document['file_size'] ?? 0);
        if (strtolower(pathinfo((string) ($document['file_name'] ?? ''), PATHINFO_EXTENSION)) !== 'apk') {
            hh_tg_send($chatId, '请发送 .apk 文件；或打开机器人此前提供的一次性网页上传链接。');
            return;
        }
        if ($size > 20 * 1024 * 1024) {
            $link = hh_issue_upload_link($chatId, $version);
            hh_tg_send($chatId, "此 APK 超过 Telegram Bot API 的 20 MiB 下载上限，请使用安全网页上传（链接 15 分钟内一次有效）：\n<a href=\"" . hh_safe_html($link) . "\">打开 APK 上传页</a>");
            return;
        }
        if (hh_download_telegram_document($document, $version, $chatId)) {
            hh_render_admin_menu($chatId, '客户端 ' . $version . ' 上传并发布成功。');
        } else {
            hh_tg_send($chatId, 'APK 下载或校验失败，请重试，或使用一次性网页上传链接。');
        }
        return;
    }
    if ($state === 'upload_file' && $text !== '' && $text !== '取消操作' && !preg_match('~^/cancel(?:@\\w+)?$~i', $text)) {
        hh_tg_send($chatId, '请发送 APK 文档，或打开机器人提供的一次性网页上传链接；发送“取消操作”退出。');
        return;
    }
    if ($text === '取消操作' || preg_match('~^/cancel(?:@\w+)?$~i', $text)) {
        hh_state_clear($chatId);
        hh_render_admin_menu($chatId, '已取消当前操作。');
        return;
    }

    if ($state === 'add_proxies') {
        $lines = preg_split('/\R/u', $text) ?: [];
        $added = 0;
        $invalid = [];
        $pdo = hh_db();
        $pdo->beginTransaction();
        try {
            foreach ($lines as $index => $line) {
                $line = trim($line);
                if ($line === '') continue;
                $proxy = hh_parse_proxy($line);
                if ($proxy === null) {
                    $invalid[] = (string) ($index + 1);
                    continue;
                }
                hh_add_proxy($proxy);
                $added++;
            }
            if ($added > 0) hh_bump('proxy_rev');
            $pdo->commit();
        } catch (Throwable $e) {
            $pdo->rollBack();
            hh_log('add proxy transaction failed: ' . $e->getMessage());
            hh_tg_send($chatId, '保存失败，请稍后重试。', hh_admin_keyboard());
            hh_state_clear($chatId);
            return;
        }
        hh_state_clear($chatId);
        hh_event('proxy_added', 'count=' . $added, $chatId);
        $reply = "已保存 {$added} 条代理。当前有效代理共 " . count(hh_proxy_rows(true)) . ' 条。';
        if ($invalid) $reply .= "\n无法识别的行号：" . implode(', ', $invalid);
        hh_render_admin_menu($chatId, $reply);
        return;
    }

    if ($state === 'delete_proxy') {
        if (preg_match('/^all$/i', $text)) {
            hh_db()->exec('DELETE FROM proxies');
            hh_bump('proxy_rev');
            hh_state_clear($chatId);
            hh_event('proxy_deleted_all', '', $chatId);
            hh_render_admin_menu($chatId, '已删除全部内置代理。');
            return;
        }
        $id = filter_var($text, FILTER_VALIDATE_INT);
        if (!$id || $id < 1) {
            hh_tg_send($chatId, '请输入代理编号，或输入 all 删除全部；发送“取消操作”可退出。');
            return;
        }
        $stmt = hh_db()->prepare('DELETE FROM proxies WHERE id = ?');
        $stmt->execute([$id]);
        if ($stmt->rowCount()) {
            hh_bump('proxy_rev');
            hh_event('proxy_deleted', 'id=' . $id, $chatId);
            hh_render_admin_menu($chatId, '已删除代理 #' . $id . '。');
        } else {
            hh_tg_send($chatId, '没有找到该编号，请输入有效代理编号。');
        }
        hh_state_clear($chatId);
        return;
    }

    if ($state === 'config_version') {
        if (!hh_update_version_valid($text)) {
            hh_tg_send($chatId, '版本号过长，请重新输入；或发送“取消操作”。');
            return;
        }
        hh_set_setting('latest_version', $text);
        hh_state_set($chatId, 'config_url');
        hh_tg_send($chatId, '请输入更新包下载链接；没有链接请发送 - 。');
        return;
    }
    if ($state === 'config_url') {
        if ($text !== '-' && (!filter_var($text, FILTER_VALIDATE_URL) || parse_url($text, PHP_URL_SCHEME) !== 'https')) {
            hh_tg_send($chatId, '链接格式无效，请输入完整的 https:// 链接，或发送 - 。');
            return;
        }
        hh_set_setting('update_url', $text === '-' ? '' : $text);
        hh_state_set($chatId, 'config_announcement');
        hh_tg_send($chatId, '请输入客户端公告；不修改公告请发送 - 。');
        return;
    }
    if ($state === 'config_announcement') {
        if (mb_strlen($text) > 1000) {
            hh_tg_send($chatId, '公告最多 1000 个字符，请缩短后重试。');
            return;
        }
        if ($text !== '-') hh_set_setting('announcement', $text);
        hh_state_set($chatId, 'config_force');
        hh_tg_send($chatId, '是否要求客户端强制更新？发送 1 是，0 否。');
        return;
    }
    if ($state === 'config_force') {
        if (!in_array($text, ['0', '1'], true)) {
            hh_tg_send($chatId, '请输入 1 或 0。');
            return;
        }
        hh_set_setting('force_update', $text);
        hh_bump('config_rev');
        hh_state_clear($chatId);
        hh_event('config_updated', 'version=' . hh_setting('latest_version'), $chatId);
        hh_render_admin_menu($chatId, '配置已更新；客户端点击“更新配置”即可获取。');
        return;
    }
    if ($state === 'upload_version') {
        if (!hh_update_version_valid($text)) {
            hh_tg_send($chatId, '版本号格式无效，请输入类似 16.1 的版本号。');
            return;
        }
        $link = hh_issue_upload_link($chatId, $text);
        hh_event('apk_upload_started', 'version=' . $text, $chatId);
        hh_tg_send($chatId, '准备发布版本 <b>' . hh_safe_html($text) . "</b>。可直接发送小于等于 20 MiB 的 APK 文档；较大的 APK 请使用 15 分钟内有效的一次性网页链接：\n<a href=\"" . hh_safe_html($link) . "\">打开 APK 上传页</a>\n上传前请确认 APK 与已安装客户端使用相同签名。", hh_admin_keyboard());
        return;
    }
    if ($state === 'cloud_url') {
        if ($text !== '-' && (!filter_var($text, FILTER_VALIDATE_URL) || parse_url($text, PHP_URL_SCHEME) !== 'https')) {
            hh_tg_send($chatId, '请输入完整的 https:// 网盘下载链接，或发送 - 清空链接。');
            return;
        }
        hh_set_setting('update_url', $text === '-' ? '' : $text);
        hh_bump('config_rev');
        hh_state_clear($chatId);
        hh_event('cloud_update_url_changed', $text === '-' ? 'cleared' : 'set', $chatId);
        hh_render_admin_menu($chatId, '网盘更新链接已保存。');
        return;
    }

    switch ($text) {
        case '代理列表':
        case '/proxies':
            $rows = hh_proxy_rows(false);
            if (!$rows) {
                hh_tg_send($chatId, '当前没有内置代理。');
                return;
            }
            $lines = ['<b>内置代理（按客户端获取顺序）</b>'];
            foreach ($rows as $row) {
                $status = (int) $row['enabled'] ? '启用' : '停用';
                $lines[] = '#' . (int) $row['id'] . ' · ' . $status . ' · ' . hh_safe_html($row['address'] . ':' . $row['port']);
            }
            hh_tg_send($chatId, implode("\n", $lines));
            return;
        case '添加代理':
        case '/addproxy':
            hh_state_set($chatId, 'add_proxies');
            hh_tg_send($chatId, "请发送一条或多条代理，每行一条。支持：Telegram t.me/proxy 链接、SOCKS5 URL（socks5://user:pass@host:port）、host:port 或 host:port:user:pass。\n发送“取消操作”退出。", hh_admin_keyboard());
            return;
        case '删除代理':
        case '/delproxy':
            $rows = hh_proxy_rows(false);
            $lines = ['请输入代理编号删除，或输入 all 删除全部：'];
            foreach ($rows as $row) {
                $lines[] = '#' . (int) $row['id'] . ' · ' . hh_safe_html($row['address'] . ':' . $row['port']);
            }
            hh_state_set($chatId, 'delete_proxy');
            hh_tg_send($chatId, implode("\n", $lines), hh_admin_keyboard());
            return;
        case '更新配置':
        case '/config':
            hh_state_set($chatId, 'config_version');
            hh_tg_send($chatId, "当前版本：<b>" . hh_safe_html(hh_setting('latest_version')) . "</b>\n请输入客户端最新版本号，例如 16.1。", hh_admin_keyboard());
            return;
        case '上传最新版客户端':
            hh_state_set($chatId, 'upload_version');
            hh_tg_send($chatId, '请输入 APK 版本号，例如 16.1。随后可直接发送不超过 20 MiB 的 APK，或使用一次性网页上传链接上传更大的文件。', hh_admin_keyboard());
            return;
        case '配置更新链接':
            hh_state_set($chatId, 'cloud_url');
            hh_tg_send($chatId, '请输入网盘下载链接（必须为 https://），发送 - 可清空当前链接。', hh_admin_keyboard());
            return;
        case '系统状态':
        case '/status':
            $rows = hh_proxy_rows(true);
            $db = hh_db();
            $events = (int) $db->query('SELECT COUNT(*) FROM events')->fetchColumn();
            hh_tg_send($chatId,
                '<b>黄昏服务状态</b>' . "\n活动代理：" . count($rows)
                . "\n代理版本：" . hh_safe_html(hh_setting('proxy_rev'))
                . "\n配置版本：" . hh_safe_html(hh_setting('config_rev'))
                . "\n最近事件累计：" . $events
                . "\n服务器时间：" . date('Y-m-d H:i:s'), hh_admin_keyboard());
            return;
        default:
            hh_render_admin_menu($chatId, '命令未识别。');
    }
}
