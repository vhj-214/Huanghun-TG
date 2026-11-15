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
            [['text' => '代理列表'], ['text' => '代理检测']],
            [['text' => '添加代理'], ['text' => '删除代理']],
            [['text' => '更新配置'], ['text' => '配置更新链接']],
            [['text' => '查看更新链接'], ['text' => '系统状态']],
            [['text' => '删除更新链接'], ['text' => '取消操作']],
        ],
        'resize_keyboard' => true,
        'is_persistent' => true,
    ];
}

function hh_safe_html(string $text): string
{
    return htmlspecialchars($text, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function hh_proxy_menu_key(int $chatId): string
{
    return 'bot_proxy_menu_nonce_' . $chatId;
}

function hh_proxy_fingerprint(array $row): string
{
    $snapshot = [
        (int) ($row['id'] ?? 0), (string) ($row['address'] ?? ''), (int) ($row['port'] ?? 0),
        (string) ($row['username'] ?? ''), (string) ($row['password'] ?? ''),
        (string) ($row['secret'] ?? ''), (string) ($row['label'] ?? ''), (int) ($row['enabled'] ?? 0),
        (int) ($row['sort_order'] ?? 0), (int) ($row['created_at'] ?? 0), (int) ($row['updated_at'] ?? 0),
    ];
    return hash('sha256', json_encode($snapshot, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
}

function hh_proxy_scan_is_fresh(array $scan): bool
{
    return isset($scan['timestamp']) && is_int($scan['timestamp'])
        && $scan['timestamp'] > 0 && $scan['timestamp'] >= time() - 600;
}

function hh_proxy_scan_summary(array $scan): string
{
    $time = date('Y-m-d H:i:s', (int) ($scan['timestamp'] ?? 0));
    $total = (int) ($scan['total'] ?? 0);
    $reachable = (int) ($scan['reachable'] ?? 0);
    $unreachable = (int) ($scan['unreachable'] ?? 0);
    $fresh = hh_proxy_scan_is_fresh($scan) ? '（可用于删除确认）' : '（已过期，请重新检测）';
    return "最近检测：{$time}{$fresh}\n共 {$total} 条：可连接 {$reachable}，不可连接 {$unreachable}。\n检测仅验证 TCP host:port 可达性，不验证代理认证或 Telegram 转发。";
}

function hh_bot_render_proxy_menu(int $chatId, string $notice = ''): void
{
    $nonce = bin2hex(random_bytes(8));
    hh_set_setting(hh_proxy_menu_key($chatId), $nonce);
    $text = '<b>代理检测</b>';
    if ($notice !== '') $text = hh_safe_html($notice) . "\n\n" . $text;
    $raw = hh_setting('proxy_scan_latest', '');
    $scan = json_decode($raw, true);
    if (is_array($scan) && isset($scan['timestamp'])) $text .= "\n\n" . hh_safe_html(hh_proxy_scan_summary($scan));
    else $text .= "\n\n尚无检测记录。";
    $button = static fn(string $label, string $action): array => [
        'text' => $label, 'callback_data' => 'p:' . $nonce . ':' . $action,
    ];
    hh_tg_send($chatId, $text, ['inline_keyboard' => [
        [$button('检测代理', 'scan'), $button('重新检测', 'rescan')],
        [$button('删除不可用代理', 'delete')],
        [$button('返回主菜单', 'back')],
    ]]);
}

function hh_proxy_tcp_uri(array $row): string
{
    $host = trim((string) ($row['address'] ?? ''));
    if (strlen($host) >= 2 && $host[0] === '[' && substr($host, -1) === ']') {
        $host = substr($host, 1, -1);
    }
    if (strpos($host, ':') !== false) $host = '[' . $host . ']';
    return 'tcp://' . $host . ':' . (int) ($row['port'] ?? 0);
}

function hh_bot_scan_proxies(int $chatId): void
{
    $rows = hh_proxy_rows(false);
    $total = count($rows);
    if ($total > 200) {
        hh_bot_render_proxy_menu($chatId, '当前有 ' . $total . ' 条代理，超过单次检测上限 200；请分批清理或限制列表后重试。');
        return;
    }
    $results = [];
    foreach (array_chunk($rows, 32) as $batch) {
        $pending = [];
        foreach ($batch as $row) {
            $errno = 0;
            $errstr = '';
            $stream = @stream_socket_client(hh_proxy_tcp_uri($row), $errno, $errstr, 1.2,
                STREAM_CLIENT_CONNECT | STREAM_CLIENT_ASYNC_CONNECT);
            $id = (int) $row['id'];
            $pending[(int) $id] = ['row' => $row, 'stream' => $stream, 'started' => time()];
            if (!is_resource($stream)) $pending[(int) $id]['reachable'] = false;
        }
        $streams = [];
        foreach ($pending as $item) if (is_resource($item['stream'])) $streams[] = $item['stream'];
        if ($streams) {
            $write = $streams;
            $except = $streams;
            $read = [];
            @stream_select($read, $write, $except, 1, 300000);
            $ready = [];
            foreach (array_merge($write, $except) as $stream) $ready[(int) $stream] = true;
            foreach ($pending as &$item) {
                $stream = $item['stream'];
                if (!is_resource($stream)) continue;
                $item['reachable'] = isset($ready[(int) $stream]) && @stream_socket_get_name($stream, true) !== false;
                @fclose($stream);
                $item['stream'] = null;
            }
            unset($item);
        }
        foreach ($pending as $id => $item) {
            $results[$id] = [
                'id' => (int) $id,
                'fingerprint' => hh_proxy_fingerprint($item['row']),
                'reachable' => (bool) ($item['reachable'] ?? false),
                'checked_at' => time(),
            ];
        }
    }
    $reachable = count(array_filter($results, static fn(array $r): bool => $r['reachable']));
    $scan = [
        'timestamp' => time(), 'total' => $total, 'reachable' => $reachable,
        'unreachable' => $total - $reachable, 'rows' => array_values($results),
    ];
    hh_set_setting('proxy_scan_latest', json_encode($scan, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
    hh_event('proxy_scan_completed', 'total=' . $total . ', reachable=' . $reachable . ', unreachable=' . ($total - $reachable), $chatId);
    hh_bot_render_proxy_menu($chatId, '检测完成。');
}

function hh_bot_begin_proxy_delete(int $chatId): void
{
    $scan = json_decode(hh_setting('proxy_scan_latest', ''), true);
    if (!is_array($scan) || !hh_proxy_scan_is_fresh($scan)) {
        hh_bot_render_proxy_menu($chatId, '没有 10 分钟内的检测记录；请先检测代理，再删除不可用代理。');
        return;
    }
    $failed = [];
    foreach (($scan['rows'] ?? []) as $item) {
        if (is_array($item) && empty($item['reachable']) && isset($item['id'], $item['fingerprint'])) {
            $failed[] = ['id' => (int) $item['id'], 'fingerprint' => (string) $item['fingerprint']];
        }
    }
    if (!$failed) {
        hh_bot_render_proxy_menu($chatId, '最近检测没有不可连接的代理，无需删除。');
        return;
    }
    hh_bot_wait($chatId, 'proxy_delete_confirm', ['timestamp' => (int) $scan['timestamp'], 'failed' => $failed]);
    $row = hh_state_get($chatId);
    $text = '<b>确认删除检测为不可连接的代理？</b>' . "\n本次检测发现 " . count($failed)
        . ' 条不可连接代理。只删除与检测时完全相同的记录；检测后新增或修改的代理会保留。'
        . "\n" . hh_safe_html(hh_proxy_scan_summary($scan));
    hh_prompt($chatId, $text, [[['确认删除', 'confirm']]], $row);
}

function hh_bot_confirm_proxy_delete(int $chatId, array $row): void
{
    $value = hh_bot_state_data($row)['value'] ?? null;
    if (!is_array($value) || !isset($value['timestamp']) || !is_array($value['failed'])) {
        hh_bot_render_proxy_menu($chatId, '确认信息无效，请重新检测。');
        return;
    }
    $db = hh_db();
    $deleted = 0;
    $skipped = 0;
    try {
        $db->beginTransaction();
        $consume = $db->prepare('DELETE FROM bot_state WHERE chat_id = ? AND state = ? AND payload = ?');
        $consume->execute([$chatId, 'proxy_delete_confirm', (string) $row['payload']]);
        if ($consume->rowCount() !== 1) {
            $db->rollBack();
            hh_bot_render_proxy_menu($chatId, '操作已变化，请重新检测。');
            return;
        }
        $scan = json_decode(hh_setting('proxy_scan_latest', ''), true);
        if (!is_array($scan) || (int) ($scan['timestamp'] ?? 0) !== (int) $value['timestamp'] || !hh_proxy_scan_is_fresh($scan)) {
            $db->commit();
            hh_bot_render_proxy_menu($chatId, '检测记录已过期或变化；未删除任何代理，请重新检测。');
            return;
        }
        $select = $db->prepare('SELECT * FROM proxies WHERE id = ?');
        $delete = $db->prepare('DELETE FROM proxies WHERE id = ? AND address = ? AND port = ? AND username = ? AND password = ? AND secret = ? AND label = ? AND enabled = ? AND sort_order = ? AND created_at = ? AND updated_at = ?');
        foreach ($value['failed'] as $item) {
            if (!is_array($item) || !isset($item['id'], $item['fingerprint'])) { $skipped++; continue; }
            $select->execute([(int) $item['id']]);
            $current = $select->fetch();
            if (!$current || !hash_equals((string) $item['fingerprint'], hh_proxy_fingerprint($current))) { $skipped++; continue; }
            $delete->execute([
                (int) $current['id'], (string) $current['address'], (int) $current['port'],
                (string) $current['username'], (string) $current['password'], (string) $current['secret'],
                (string) $current['label'], (int) $current['enabled'], (int) $current['sort_order'],
                (int) $current['created_at'], (int) $current['updated_at'],
            ]);
            $deleted += $delete->rowCount();
            if ($delete->rowCount() === 0) $skipped++;
        }
        if ($deleted > 0) hh_bump('proxy_rev');
        hh_event('proxy_unreachable_deleted', 'scan=' . (int) $value['timestamp'] . ', deleted=' . $deleted . ', skipped_changed=' . $skipped, $chatId);
        $db->commit();
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        hh_log('Unreachable proxy delete transaction failed.');
        hh_bot_render_proxy_menu($chatId, '删除失败，未确认完成；请重新检测后再试。');
        return;
    }
    hh_bot_render_proxy_menu($chatId, '已删除 ' . $deleted . ' 条不可连接代理；因检测后新增、修改或已移除而跳过 ' . $skipped . ' 条。');
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

// Each prompt receives a fresh nonce so buttons from earlier steps cannot act
// on the current step, even when the same state is entered more than once.
function hh_bot_wait(int $chatId, string $state, $value = ''): void
{
    $payload = json_encode(['nonce' => bin2hex(random_bytes(8)), 'value' => $value], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($payload === false) throw new RuntimeException('Cannot encode bot state');
    hh_state_set($chatId, $state, $payload);
}

function hh_bot_state_data(array $row): array
{
    $payload = (string) ($row['payload'] ?? '');
    $decoded = json_decode($payload, true);
    if (is_array($decoded) && isset($decoded['nonce']) && is_string($decoded['nonce'])) return $decoded;
    // Compatibility with state payloads created before nonce-based prompts.
    return ['nonce' => '', 'value' => $payload];
}

function hh_prompt(int $chatId, string $text, array $choices = [], ?array $expectedRow = null): void
{
    $row = $expectedRow ?? hh_state_get($chatId);
    if ($expectedRow !== null) {
        $current = hh_state_get($chatId);
        if (($current['state'] ?? '') !== ($row['state'] ?? '')
            || ($current['payload'] ?? '') !== ($row['payload'] ?? '')) return;
    }
    $data = hh_bot_state_data($row);
    if (($row['state'] ?? '') === '') {
        hh_render_admin_menu($chatId, '当前没有待处理的操作。');
        return;
    }
    if (!preg_match('/^[a-f0-9]{16}$/', (string) $data['nonce'])) {
        $payload = json_encode(['nonce' => bin2hex(random_bytes(8)), 'value' => $data['value'] ?? ''], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        if ($payload === false) throw new RuntimeException('Cannot encode bot state');
        $stmt = hh_db()->prepare('UPDATE bot_state SET payload = ?, updated_at = ? WHERE chat_id = ? AND state = ? AND payload = ?');
        $stmt->execute([$payload, time(), $chatId, (string) $row['state'], (string) $row['payload']]);
        if ($stmt->rowCount() !== 1) return;
        $row['payload'] = $payload;
        $data = hh_bot_state_data($row);
    }
    $rows = [];
    foreach ($choices as $choiceRow) {
        $buttons = [];
        foreach ($choiceRow as $choice) {
            $buttons[] = ['text' => $choice[0], 'callback_data' => 's:' . $data['nonce'] . ':' . $choice[1]];
        }
        $rows[] = $buttons;
    }
    $rows[] = [['text' => '取消操作', 'callback_data' => 's:' . $data['nonce'] . ':cancel']];
    hh_tg_send($chatId, $text, ['inline_keyboard' => $rows]);
}

function hh_menu_key(int $chatId): string
{
    return 'bot_menu_nonce_' . $chatId;
}

function hh_render_admin_menu(int $chatId, string $notice = ''): void
{
    hh_set_setting(hh_proxy_menu_key($chatId), '');
    $nonce = bin2hex(random_bytes(8));
    hh_set_setting(hh_menu_key($chatId), $nonce);
    $text = "<b>黄昏服务机器人管理面板</b>\n请选择操作：";
    if ($notice !== '') $text = hh_safe_html($notice) . "\n\n" . $text;
    $button = static fn(string $label, string $action): array => [
        'text' => $label, 'callback_data' => 'm:' . $nonce . ':' . $action,
    ];
    hh_tg_send($chatId, $text, ['inline_keyboard' => [
        [$button('代理列表', 'proxies'), $button('添加代理', 'addproxy')],
        [$button('代理检测', 'proxycheck')],
        [$button('删除代理', 'delproxy'), $button('更新配置', 'config')],
        [$button('配置更新链接', 'cloud'), $button('查看更新链接', 'viewurl')],
        [$button('删除更新链接', 'deleteurl')],
        [$button('系统状态', 'status')],
    ]]);
}

function hh_bot_start(int $chatId, int $userId, string $chatType): void
{
    $adminId = (int) hh_setting('admin_id', (string) hh_config()['admin_id']);
    if ($userId !== $adminId || $chatType !== 'private' || $chatId !== $userId) {
        hh_event('unauthorized_start', 'user=' . $userId . ', chat=' . $chatType, $chatId);
        hh_tg_send($chatId, '权限不足，无法访问。');
        return;
    }
    $retired = hh_bot_clear_retired_state($chatId);
    hh_state_clear($chatId);
    // Telegram allows only one reply_markup per message; retain the old reply
    // keyboard on the welcome message and send the inline menu separately.
    hh_tg_send($chatId, "欢迎，管理员。\n可在此维护内置代理和客户端配置。", hh_admin_keyboard());
    hh_render_admin_menu($chatId, $retired);
}

// Old, pre-deployment workflows must not publish or delete files. Remove only
// the exact observed state; do not touch legacy APK files or release metadata.
function hh_bot_clear_retired_state(int $chatId): string
{
    $row = hh_state_get($chatId);
    $state = (string) ($row['state'] ?? '');
    if (!in_array($state, ['upload_version', 'upload_file', 'delete_client'], true)) return '';
    $stmt = hh_db()->prepare('DELETE FROM bot_state WHERE chat_id = ? AND state = ? AND payload = ?');
    $stmt->execute([$chatId, $state, (string) ($row['payload'] ?? '')]);
    if ($stmt->rowCount() !== 1) return '';
    return $state === 'delete_client'
        ? '客户端文件管理已停用，旧操作已取消。'
        : '客户端文件上传已停用，旧上传操作已清理；请配置更新链接。';
}

function hh_bot_url_snapshot(): array
{
    $stmt = hh_db()->prepare('SELECT value, updated_at FROM settings WHERE key = ?');
    $stmt->execute(['update_url']);
    $row = $stmt->fetch();
    return ['url' => (string) ($row['value'] ?? ''),
        'updated_at' => (int) ($row['updated_at'] ?? 0),
        'config_rev' => hh_setting('config_rev', '0')];
}

function hh_bot_cloud_prompt(int $chatId, string $notice = '', ?array $expectedRow = null): void
{
    $row = $expectedRow ?? hh_state_get($chatId);
    if (($row['state'] ?? '') !== 'cloud_url') return;
    $url = hh_setting('update_url', '');
    $text = ($notice === '' ? '' : hh_safe_html($notice) . "\n\n")
        . '<b>当前更新链接</b>：' . ($url === '' ? '未配置' : '<code>' . hh_safe_html($url) . '</code>')
        . "\n请输入新的 https:// 更新链接；发送 - 可申请清空。";
    hh_prompt($chatId, $text, [[['查看当前链接', 'view'], ['清空链接', 'clear']]], $row);
}

function hh_bot_refresh_cloud_prompt(int $chatId, array $row): void
{
    $payload = json_encode(['nonce' => bin2hex(random_bytes(8)), 'value' => ''], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($payload === false) throw new RuntimeException('Cannot encode bot state');
    $stmt = hh_db()->prepare('UPDATE bot_state SET payload = ?, updated_at = ? WHERE chat_id = ? AND state = ? AND payload = ?');
    $stmt->execute([$payload, time(), $chatId, 'cloud_url', (string) $row['payload']]);
    if ($stmt->rowCount() !== 1) {
        hh_tg_send($chatId, '操作已变化，请使用当前步骤的按钮。');
        return;
    }
    hh_bot_cloud_prompt($chatId, '', ['state' => 'cloud_url', 'payload' => $payload]);
}

function hh_bot_clear_prompt(int $chatId, array $row, string $notice = ''): void
{
    $value = hh_bot_state_data($row)['value'] ?? [];
    $snapshot = is_array($value) ? ($value['snapshot'] ?? []) : [];
    $url = is_array($snapshot) ? (string) ($snapshot['url'] ?? '') : '';
    $text = ($notice === '' ? '' : hh_safe_html($notice) . "\n\n")
        . '<b>确认清空更新链接？</b>' . "\n当前链接："
        . ($url === '' ? '未配置' : '<code>' . hh_safe_html($url) . '</code>');
    if (($row['state'] ?? '') === 'config_clear_confirm') {
        $text .= "\n确认后将清空链接，并一并保存本次配置向导中的版本、公告和强制更新设置。";
    } else {
        $text .= "\n只会清空更新链接，不会更改其他配置。";
    }
    // Build buttons from this exact state, never from a newer concurrent prompt.
    hh_prompt($chatId, $text, [[['确认清空链接', 'confirm']]], $row);
}

// Enter a new state with a fresh nonce, retaining a snapshot so that an old
// confirmation cannot clear a link/configuration changed since the prompt.
function hh_bot_begin_clear_url(int $chatId, array $row, ?array $draft = null): void
{
    $snapshot = hh_bot_url_snapshot();
    if ($draft === null && $snapshot['url'] === '') {
        $stmt = hh_db()->prepare('DELETE FROM bot_state WHERE chat_id = ? AND state = ? AND payload = ?');
        $stmt->execute([$chatId, (string) $row['state'], (string) $row['payload']]);
        if ($stmt->rowCount() !== 1) {
            hh_tg_send($chatId, '操作已变化，请使用当前步骤的按钮。');
            return;
        }
        hh_render_admin_menu($chatId, '当前未配置更新链接，无需清空。');
        return;
    }
    $value = ['snapshot' => $snapshot];
    if ($draft !== null) $value['draft'] = $draft;
    $next = $draft === null ? 'clear_url_confirm' : 'config_clear_confirm';
    $payload = json_encode(['nonce' => bin2hex(random_bytes(8)), 'value' => $value], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($payload === false) throw new RuntimeException('Cannot encode bot state');
    $stmt = hh_db()->prepare('UPDATE bot_state SET state = ?, payload = ?, updated_at = ? WHERE chat_id = ? AND state = ? AND payload = ?');
    $stmt->execute([$next, $payload, time(), $chatId, (string) $row['state'], (string) $row['payload']]);
    if ($stmt->rowCount() !== 1) {
        hh_tg_send($chatId, '操作已变化，请使用当前步骤的按钮。');
        return;
    }
    $current = hh_state_get($chatId);
    if ($current['payload'] === $payload) hh_bot_clear_prompt($chatId, $current);
}

function hh_bot_set_update_url(int $chatId, array $row, string $url): void
{
    $db = hh_db();
    try {
        $db->beginTransaction();
        $stmt = $db->prepare('DELETE FROM bot_state WHERE chat_id = ? AND state = ? AND payload = ?');
        $stmt->execute([$chatId, 'cloud_url', (string) $row['payload']]);
        if ($stmt->rowCount() !== 1) {
            $db->rollBack();
            hh_tg_send($chatId, '操作已变化，请使用当前步骤的按钮。');
            return;
        }
        hh_set_setting('update_url', $url);
        hh_bump('config_rev');
        $db->commit();
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        hh_log('Update URL save failed: ' . $e->getMessage());
        hh_bot_cloud_prompt($chatId, '保存失败，请重试或取消。', $row);
        return;
    }
    hh_event('cloud_update_url_changed', 'set', $chatId);
    hh_render_admin_menu($chatId, '更新链接已保存。');
}

// Both wizard completion paths consume the exact state in the same transaction
// as all config changes. Snapshot checks cover wizard-driven link clearing too.
function hh_bot_commit_config(int $chatId, array $row, array $draft, string $force, ?array $snapshot = null): void
{
    $db = hh_db();
    try {
        $db->beginTransaction();
        $stmt = $db->prepare('DELETE FROM bot_state WHERE chat_id = ? AND state = ? AND payload = ?');
        $stmt->execute([$chatId, (string) $row['state'], (string) $row['payload']]);
        if ($stmt->rowCount() !== 1) {
            $db->rollBack();
            hh_tg_send($chatId, '操作已变化，请使用当前步骤的按钮。');
            return;
        }
        if ($snapshot !== null && hh_bot_url_snapshot() !== $snapshot) {
            $db->commit();
            hh_render_admin_menu($chatId, '更新链接或配置已变化，未保存；请重新操作。');
            return;
        }
        hh_set_setting('latest_version', (string) ($draft['version'] ?? hh_setting('latest_version')));
        hh_set_setting('update_url', (string) ($draft['url'] ?? hh_setting('update_url')));
        if (($draft['announcement'] ?? null) !== null) hh_set_setting('announcement', (string) $draft['announcement']);
        hh_set_setting('force_update', $force);
        hh_bump('config_rev');
        $db->commit();
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        hh_log('Config transaction failed: ' . $e->getMessage());
        if (($row['state'] ?? '') === 'config_clear_confirm') hh_bot_clear_prompt($chatId, $row, '保存失败，请重试或取消。');
        else hh_prompt($chatId, '配置保存失败，请重试或取消。', [[['是', 'yes'], ['否', 'no']]]);
        return;
    }
    hh_event('config_updated', 'version=' . hh_setting('latest_version'), $chatId);
    hh_render_admin_menu($chatId, '配置已更新；客户端点击“更新配置”即可获取。');
}

function hh_bot_confirm_clear_url(int $chatId, array $row): void
{
    $value = hh_bot_state_data($row)['value'] ?? null;
    $snapshot = is_array($value) ? ($value['snapshot'] ?? null) : null;
    if (!is_array($snapshot) || !isset($snapshot['url'], $snapshot['updated_at'], $snapshot['config_rev'])) {
        hh_tg_send($chatId, '确认信息无效，请取消并重新操作。');
        return;
    }
    if (($row['state'] ?? '') === 'config_clear_confirm') {
        $draft = $value['draft'] ?? null;
        if (!is_array($draft) || ($draft['url'] ?? null) !== '' || !in_array($draft['force'] ?? null, ['0', '1'], true)) {
            hh_tg_send($chatId, '确认信息无效，请取消并重新操作。');
            return;
        }
        hh_bot_commit_config($chatId, $row, $draft, $draft['force'], $snapshot);
        return;
    }
    $db = hh_db();
    try {
        $db->beginTransaction();
        $stmt = $db->prepare('DELETE FROM bot_state WHERE chat_id = ? AND state = ? AND payload = ?');
        $stmt->execute([$chatId, 'clear_url_confirm', (string) $row['payload']]);
        if ($stmt->rowCount() !== 1) {
            $db->rollBack();
            hh_tg_send($chatId, '操作已变化，请使用当前步骤的按钮。');
            return;
        }
        if (hh_bot_url_snapshot() !== $snapshot) {
            $db->commit();
            hh_render_admin_menu($chatId, '更新链接或配置已变化，未清空；请重新操作。');
            return;
        }
        hh_set_setting('update_url', '');
        hh_bump('config_rev');
        $db->commit();
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        hh_log('Update URL clearing failed: ' . $e->getMessage());
        hh_bot_clear_prompt($chatId, $row, '清空失败，请重试或取消。');
        return;
    }
    hh_event('cloud_update_url_changed', 'cleared', $chatId);
    hh_render_admin_menu($chatId, '更新链接已清空；其他配置未更改。');
}

function hh_bot_route_menu(int $chatId, string $text): void
{
    // Starting any menu operation invalidates previously sent menu buttons.
    hh_set_setting(hh_menu_key($chatId), '');
    hh_set_setting(hh_proxy_menu_key($chatId), '');
    switch ($text) {
        case '代理检测':
        case '/proxycheck':
            hh_state_clear($chatId);
            hh_bot_render_proxy_menu($chatId);
            return;
        case '代理列表':
        case '/proxies':
            $rows = hh_proxy_rows(false);
            $lines = ['<b>内置代理（按客户端获取顺序）</b>'];
            foreach ($rows as $row) {
                $status = (int) $row['enabled'] ? '启用' : '停用';
                $lines[] = '#' . (int) $row['id'] . ' · ' . $status . ' · ' . hh_safe_html($row['address'] . ':' . $row['port']);
            }
            hh_tg_send($chatId, $rows ? implode("\n", $lines) : '当前没有内置代理。');
            hh_render_admin_menu($chatId);
            return;
        case '添加代理':
        case '/addproxy':
            hh_bot_wait($chatId, 'add_proxies');
            hh_prompt($chatId, "请发送一条或多条代理，每行一条。支持：Telegram t.me/proxy 链接、SOCKS5 URL（socks5://user:pass@host:port）、host:port 或 host:port:user:pass。\n发送“取消操作”退出。");
            return;
        case '删除代理':
        case '/delproxy':
            $rows = hh_proxy_rows(false);
            $lines = ['请输入代理编号删除，或输入 all 删除全部：'];
            foreach ($rows as $row) $lines[] = '#' . (int) $row['id'] . ' · ' . hh_safe_html($row['address'] . ':' . $row['port']);
            hh_bot_wait($chatId, 'delete_proxy');
            hh_prompt($chatId, implode("\n", $lines));
            return;
        case '更新配置':
        case '/config':
            hh_bot_wait($chatId, 'config_version');
            hh_prompt($chatId, '当前版本：<b>' . hh_safe_html(hh_setting('latest_version')) . "</b>\n请输入客户端最新版本号，例如 16.1。");
            return;
        case '配置更新链接':
        case '/cloud':
        case '查看更新链接':
        case '查看当前更新链接':
        case '/viewurl':
            hh_bot_wait($chatId, 'cloud_url');
            hh_bot_cloud_prompt($chatId);
            return;
        case '删除更新链接':
        case '清空更新链接':
        case '/deleteurl':
            hh_bot_wait($chatId, 'cloud_url');
            hh_bot_begin_clear_url($chatId, hh_state_get($chatId));
            return;
        case '系统状态':
        case '/status':
            $rows = hh_proxy_rows(true);
            $events = (int) hh_db()->query('SELECT COUNT(*) FROM events')->fetchColumn();
            hh_tg_send($chatId, '<b>黄昏服务状态</b>' . "\n活动代理：" . count($rows)
                . "\n代理版本：" . hh_safe_html(hh_setting('proxy_rev'))
                . "\n配置版本：" . hh_safe_html(hh_setting('config_rev'))
                . "\n最近事件累计：" . $events . "\n服务器时间：" . date('Y-m-d H:i:s'));
            hh_render_admin_menu($chatId);
            return;
        default:
            hh_render_admin_menu($chatId, '命令未识别。');
    }
}

function hh_bot_apply_choice(int $chatId, string $state, string $action, array $row): void
{
    $draft = hh_bot_state_data($row)['value'] ?? null;
    if (!is_array($draft)) $draft = ['version' => hh_setting('latest_version'), 'url' => hh_setting('update_url')];
    $db = hh_db();
    if ($state === 'config_url' || $state === 'config_announcement') {
        if ($state === 'config_url') {
            $draft['url'] = '';
            $next = 'config_announcement';
        } else {
            $draft['announcement'] = null;
            $next = 'config_force';
        }
        $payload = json_encode(['nonce' => bin2hex(random_bytes(8)), 'value' => $draft], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        if ($payload === false) throw new RuntimeException('Cannot encode bot state');
        $stmt = $db->prepare('UPDATE bot_state SET state = ?, payload = ?, updated_at = ? WHERE chat_id = ? AND state = ? AND payload = ?');
        $stmt->execute([$next, $payload, time(), $chatId, $state, (string) $row['payload']]);
        if ($stmt->rowCount() !== 1) {
            hh_tg_send($chatId, '操作已变化，请使用当前步骤的按钮。');
            return;
        }
        if (hh_state_get($chatId)['payload'] !== $payload) return;
        if ($next === 'config_announcement') {
            hh_prompt($chatId, '请输入客户端公告；不修改公告请发送 - 。', [[['保持公告', 'skip']]]);
        } else {
            hh_prompt($chatId, '是否要求客户端强制更新？发送 1 是，0 否。', [[['是', 'yes'], ['否', 'no']]]);
        }
        return;
    }
    $force = $action === 'yes' ? '1' : '0';
    if (($draft['url'] ?? '') === '') {
        $draft['force'] = $force;
        hh_bot_begin_clear_url($chatId, $row, $draft);
    } else {
        hh_bot_commit_config($chatId, $row, $draft, $force);
    }

}

function hh_bot_handle_callback(array $callback): void
{
    $id = (string) ($callback['id'] ?? '');
    if ($id === '') return;
    $userId = (int) ($callback['from']['id'] ?? 0);
    $message = $callback['message'] ?? [];
    $chatId = (int) ($message['chat']['id'] ?? 0);
    $chatType = (string) ($message['chat']['type'] ?? '');
    $answered = false;
    $answer = static function (string $text = '') use ($id, &$answered): void {
        $answered = true;
        $params = ['callback_query_id' => $id];
        if ($text !== '') {
            $params['text'] = $text;
            $params['show_alert'] = 'true';
        }
        hh_tg('answerCallbackQuery', $params);
    };
    $clearSource = static function () use ($message, $chatId): void {
        $messageId = (int) ($message['message_id'] ?? 0);
        if ($chatId === 0 || $messageId === 0) return;
        $deleted = hh_tg('deleteMessage', ['chat_id' => $chatId, 'message_id' => $messageId]);
        if (!empty($deleted['ok'])) return;
        hh_tg('editMessageReplyMarkup', [
            'chat_id' => $chatId,
            'message_id' => $messageId,
            'reply_markup' => json_encode(['inline_keyboard' => []], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
        ]);
    };
    try {
    if ($userId === 0 || $userId !== (int) hh_setting('admin_id', (string) hh_config()['admin_id'])
        || $chatType !== 'private' || $chatId !== $userId) {
        $answer('权限不足。');
        hh_event('unauthorized_callback', 'user=' . $userId . ', chat=' . $chatType, $chatId);
        return;
    }
    // Delete the clicked screen first, falling back to clearing its inline
    // keyboard so stale menus and prompts cannot accumulate.
    $clearSource();
    $retired = hh_bot_clear_retired_state($chatId);
    if ($retired !== '') {
        $answer($retired);
        hh_render_admin_menu($chatId, $retired);
        return;
    }
    $value = (string) ($callback['data'] ?? '');
    if (!preg_match('/^([msp]):([a-f0-9]{16}):([a-z0-9_]+)$/', $value, $matches)) {
        $answer('按钮无效或已过期，请使用最新菜单。');
        return;
    }
    [, $kind, $nonce, $action] = $matches;
    if ($kind === 'm') {
        $actions = ['proxies' => '代理列表', 'addproxy' => '添加代理', 'delproxy' => '删除代理',
            'config' => '更新配置', 'cloud' => '配置更新链接', 'viewurl' => '查看更新链接',
            'deleteurl' => '删除更新链接', 'status' => '系统状态', 'proxycheck' => '代理检测'];
        if (!isset($actions[$action])) {
            $answer('按钮无效。');
            return;
        }
        $stmt = hh_db()->prepare('UPDATE settings SET value = ?, updated_at = ? WHERE key = ? AND value = ?');
        $stmt->execute(['', time(), hh_menu_key($chatId), $nonce]);
        if ($stmt->rowCount() !== 1) {
            $answer('菜单已过期，请使用最新菜单。');
            return;
        }
        $answer();
        // An explicit new menu action supersedes any previous operation.
        hh_state_clear($chatId);
        hh_bot_route_menu($chatId, $actions[$action]);
        return;
    }
    if ($kind === 'p') {
        $stmt = hh_db()->prepare('UPDATE settings SET value = ?, updated_at = ? WHERE key = ? AND value = ?');
        $stmt->execute(['', time(), hh_proxy_menu_key($chatId), $nonce]);
        if ($stmt->rowCount() !== 1) {
            $answer('代理检测菜单已过期，请使用最新菜单。');
            return;
        }
        if (!in_array($action, ['scan', 'rescan', 'delete', 'back'], true)) {
            $answer('按钮无效。');
            return;
        }
        $answer();
        if ($action === 'back') {
            hh_state_clear($chatId);
            hh_set_setting(hh_proxy_menu_key($chatId), '');
            hh_render_admin_menu($chatId);
        } elseif ($action === 'delete') {
            hh_bot_begin_proxy_delete($chatId);
        } else {
            hh_state_clear($chatId);
            hh_bot_scan_proxies($chatId);
        }
        return;
    }
    $row = hh_state_get($chatId);
    $data = hh_bot_state_data($row);
    if (($row['state'] ?? '') === '' || !hash_equals((string) $data['nonce'], $nonce)) {
        $answer('操作已结束或按钮已过期，请使用当前步骤的按钮。');
        return;
    }
    $state = (string) $row['state'];
    $options = [
        'config_url' => ['skip'], 'config_announcement' => ['skip'],
        'cloud_url' => ['view', 'clear'], 'config_force' => ['yes', 'no'],
        'clear_url_confirm' => ['confirm'], 'config_clear_confirm' => ['confirm'],
        'proxy_delete_confirm' => ['confirm'],
    ];
    if ($action !== 'cancel' && !in_array($action, $options[$state] ?? [], true)) {
        $answer('按钮不适用于当前步骤。');
        return;
    }
    if ($action === 'cancel') {
        $stmt = hh_db()->prepare('DELETE FROM bot_state WHERE chat_id = ? AND state = ? AND payload = ?');
        $stmt->execute([$chatId, $state, (string) $row['payload']]);
        if ($stmt->rowCount() !== 1) {
            $answer('操作已变化，请使用当前步骤的按钮。');
            return;
        }
        $answer();
        if ($state === 'proxy_delete_confirm') {
            hh_state_clear($chatId);
            hh_bot_render_proxy_menu($chatId, '已取消删除操作。');
            return;
        }
        hh_render_admin_menu($chatId, '已取消当前操作。');
        return;
    }
    $answer();
    if ($state === 'proxy_delete_confirm') {
        hh_bot_confirm_proxy_delete($chatId, $row);
    } elseif ($state === 'cloud_url') {
        if ($action === 'clear') hh_bot_begin_clear_url($chatId, $row);
        else hh_bot_refresh_cloud_prompt($chatId, $row);
    } elseif ($state === 'clear_url_confirm' || $state === 'config_clear_confirm') {
        hh_bot_confirm_clear_url($chatId, $row);
    } else {
        hh_bot_apply_choice($chatId, $state, $action, $row);
    }
    } finally {
        if (!$answered) $answer();
    }
}

function hh_bot_handle_message(array $message): void
{
    $chatId = (int) ($message['chat']['id'] ?? 0);
    $userId = (int) ($message['from']['id'] ?? 0);
    $chatType = (string) ($message['chat']['type'] ?? '');
    $text = trim((string) ($message['text'] ?? ''));
    $document = $message['document'] ?? null;
    if ($chatId === 0 || $userId === 0 || ($text === '' && !is_array($document))) return;
    if (!is_array($document) && preg_match('~^/start(?:@\w+)?(?:\s|$)~i', $text)) {
        hh_bot_start($chatId, $userId, $chatType);
        return;
    }
    if ($userId !== (int) hh_setting('admin_id', (string) hh_config()['admin_id']) || $chatType !== 'private' || $chatId !== $userId) {
        hh_tg_send($chatId, '权限不足，无法访问。');
        hh_event('unauthorized_message', 'user=' . $userId . ', chat=' . $chatType, $chatId);
        return;
    }
    $retired = hh_bot_clear_retired_state($chatId);
    if ($retired !== '') {
        hh_render_admin_menu($chatId, $retired);
        return;
    }
    if (is_array($document)) {
        $row = hh_state_get($chatId);
        if (($row['state'] ?? '') === 'cloud_url') hh_bot_cloud_prompt($chatId, '不接收文件，请输入更新链接或取消。', $row);
        elseif (in_array(($row['state'] ?? ''), ['clear_url_confirm', 'config_clear_confirm'], true)) hh_bot_clear_prompt($chatId, $row);
        elseif (($row['state'] ?? '') !== '') hh_prompt($chatId, '当前步骤需要文字输入，请输入文字或取消操作。');
        else hh_render_admin_menu($chatId, '不接收文件，请使用更新链接。');
        return;
    }
    if ($text === '取消操作' || preg_match('~^/cancel(?:@\w+)?$~i', $text)) {
        hh_state_clear($chatId);
        hh_render_admin_menu($chatId, '已取消当前操作。');
        return;
    }
    if ($text === '主菜单' || preg_match('~^/menu(?:@\w+)?$~i', $text)) {
        hh_state_clear($chatId);
        hh_render_admin_menu($chatId);
        return;
    }
    $stateRow = hh_state_get($chatId);
    $state = (string) $stateRow['state'];
    if ($state === 'clear_url_confirm' || $state === 'config_clear_confirm') {
        hh_bot_clear_prompt($chatId, $stateRow);
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
            hh_state_clear($chatId);
            hh_render_admin_menu($chatId, '保存失败，请稍后重试。');
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
            hh_prompt($chatId, '请输入代理编号，或输入 all 删除全部。');
            return;
        }
        $stmt = hh_db()->prepare('DELETE FROM proxies WHERE id = ?');
        $stmt->execute([$id]);
        if ($stmt->rowCount()) {
            hh_bump('proxy_rev');
            hh_event('proxy_deleted', 'id=' . $id, $chatId);
            hh_state_clear($chatId);
            hh_render_admin_menu($chatId, '已删除代理 #' . $id . '。');
        } else {
            hh_prompt($chatId, '没有找到该编号，请输入有效代理编号。');
        }
        return;
    }
    if ($state === 'config_version') {
        if (!hh_update_version_valid($text)) {
            hh_prompt($chatId, '版本号格式无效，请重新输入。');
            return;
        }
        hh_bot_wait($chatId, 'config_url', ['version' => $text]);
        hh_prompt($chatId, '请输入更新包下载链接；没有链接请发送 - 。', [[['无链接', 'skip']]]);
        return;
    }
    if ($state === 'config_url') {
        if ($text !== '-' && (!filter_var($text, FILTER_VALIDATE_URL) || parse_url($text, PHP_URL_SCHEME) !== 'https')) {
            hh_prompt($chatId, '链接格式无效，请输入完整的 https:// 链接，或发送 - 。', [[['无链接', 'skip']]]);
            return;
        }
        $draft = hh_bot_state_data($stateRow)['value'] ?? null;
        if (!is_array($draft)) $draft = ['version' => hh_setting('latest_version')];
        $draft['url'] = $text === '-' ? '' : $text;
        hh_bot_wait($chatId, 'config_announcement', $draft);
        hh_prompt($chatId, '请输入客户端公告；不修改公告请发送 - 。', [[['保持公告', 'skip']]]);
        return;
    }
    if ($state === 'config_announcement') {
        if (mb_strlen($text) > 1000) {
            hh_prompt($chatId, '公告最多 1000 个字符，请缩短后重试。', [[['保持公告', 'skip']]]);
            return;
        }
        $draft = hh_bot_state_data($stateRow)['value'] ?? null;
        if (!is_array($draft)) $draft = ['version' => hh_setting('latest_version'), 'url' => hh_setting('update_url')];
        $draft['announcement'] = $text === '-' ? null : $text;
        hh_bot_wait($chatId, 'config_force', $draft);
        hh_prompt($chatId, '是否要求客户端强制更新？发送 1 是，0 否。', [[['是', 'yes'], ['否', 'no']]]);
        return;
    }
    if ($state === 'config_force') {
        if (!in_array($text, ['0', '1'], true)) {
            hh_prompt($chatId, '请输入 1 或 0。', [[['是', 'yes'], ['否', 'no']]]);
            return;
        }
        $draft = hh_bot_state_data($stateRow)['value'] ?? null;
        if (!is_array($draft)) $draft = ['version' => hh_setting('latest_version'), 'url' => hh_setting('update_url'), 'announcement' => null];
        if (($draft['url'] ?? '') === '') {
            $draft['force'] = $text;
            hh_bot_begin_clear_url($chatId, $stateRow, $draft);
        } else {
            hh_bot_commit_config($chatId, $stateRow, $draft, $text);
        }
        return;
    }
    if ($state === 'cloud_url') {
        if ($text === '-') {
            hh_bot_begin_clear_url($chatId, $stateRow);
        } elseif (!filter_var($text, FILTER_VALIDATE_URL) || parse_url($text, PHP_URL_SCHEME) !== 'https') {
            hh_bot_cloud_prompt($chatId, '请输入完整的 https:// 更新链接，或发送 - 清空链接。', $stateRow);
        } else {
            hh_bot_set_update_url($chatId, $stateRow, $text);
        }
        return;
    }
    hh_bot_route_menu($chatId, $text);
}
