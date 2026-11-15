<?php

function hh_config(): array
{
    static $config = null;
    if ($config === null) {
        $config = require __DIR__ . '/config.php';
        date_default_timezone_set($config['timezone'] ?? 'Asia/Shanghai');
    }
    return $config;
}

function hh_log(string $message): void
{
    $cfg = hh_config();
    $path = $cfg['log_path'];
    $dir = dirname($path);
    if (!is_dir($dir)) {
        @mkdir($dir, 0700, true);
    }
    if (is_file($path) && filesize($path) > 2 * 1024 * 1024) {
        @rename($path, $path . '.1');
    }
    @file_put_contents($path, '[' . date('Y-m-d H:i:s') . '] ' . $message . "\n", FILE_APPEND | LOCK_EX);
}

function hh_db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }
    $cfg = hh_config();
    $dir = dirname($cfg['db_path']);
    if (!is_dir($dir)) {
        mkdir($dir, 0700, true);
    }
    $pdo = new PDO('sqlite:' . $cfg['db_path'], null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    $pdo->exec('PRAGMA journal_mode = WAL');
    $pdo->exec('PRAGMA busy_timeout = 5000');
    $pdo->exec('PRAGMA foreign_keys = ON');
    hh_migrate($pdo);
    return $pdo;
}

function hh_migrate(PDO $pdo): void
{
    $pdo->exec(<<<'SQL'
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL DEFAULT '',
    updated_at INTEGER NOT NULL DEFAULT 0
)
SQL);
    $pdo->exec(<<<'SQL'
CREATE TABLE IF NOT EXISTS proxies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    address TEXT NOT NULL,
    port INTEGER NOT NULL,
    username TEXT NOT NULL DEFAULT '',
    password TEXT NOT NULL DEFAULT '',
    secret TEXT NOT NULL DEFAULT '',
    label TEXT NOT NULL DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0,
    UNIQUE (address, port, username, password, secret)
)
SQL);
    $pdo->exec(<<<'SQL'
CREATE TABLE IF NOT EXISTS bot_state (
    chat_id INTEGER PRIMARY KEY,
    state TEXT NOT NULL DEFAULT '',
    payload TEXT NOT NULL DEFAULT '',
    updated_at INTEGER NOT NULL DEFAULT 0
)
SQL);
    $pdo->exec(<<<'SQL'
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL DEFAULT 0,
    kind TEXT NOT NULL DEFAULT '',
    detail TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL DEFAULT 0
)
SQL);

    $defaults = [
        'admin_id'         => (string) hh_config()['admin_id'],
        'announcement'     => '欢迎使用黄昏服务，代理与配置均由管理员统一维护。',
        'latest_version'   => '16.0',
        'update_url'       => '',
        'force_update'     => '0',
        'proxy_auto_apply' => '0',
        'api_base'         => hh_config()['api_base'],
        'proxy_rev'        => '1',
        'config_rev'       => '1',
    ];
    $now = time();
    $stmt = $pdo->prepare('INSERT OR IGNORE INTO settings (key, value, updated_at) VALUES (?, ?, ?)');
    foreach ($defaults as $key => $value) {
        $stmt->execute([$key, $value, $now]);
    }
}

function hh_proxy_rows(bool $enabledOnly = false): array
{
    $sql = 'SELECT * FROM proxies' . ($enabledOnly ? ' WHERE enabled = 1' : '') . ' ORDER BY sort_order ASC, id ASC';
    return hh_db()->query($sql)->fetchAll();
}

function hh_setting(string $key, ?string $default = null): string
{
    $stmt = hh_db()->prepare('SELECT value FROM settings WHERE key = ?');
    $stmt->execute([$key]);
    $value = $stmt->fetchColumn();
    if ($value === false || $value === null) {
        return $default ?? '';
    }
    return (string) $value;
}

function hh_set_setting(string $key, string $value): void
{
    $stmt = hh_db()->prepare(
        'INSERT INTO settings (key, value, updated_at) VALUES (?, ?, ?)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at'
    );
    $stmt->execute([$key, $value, time()]);
}

function hh_bump(string $counter): int
{
    $value = (int) hh_setting($counter, '0') + 1;
    hh_set_setting($counter, (string) $value);
    return $value;
}

function hh_event(string $kind, string $detail = '', int $chatId = 0): void
{
    try {
        $stmt = hh_db()->prepare('INSERT INTO events (chat_id, kind, detail, created_at) VALUES (?, ?, ?, ?)');
        $stmt->execute([$chatId, $kind, mb_substr($detail, 0, 500), time()]);
    } catch (Throwable $e) {
        hh_log('event insert failed: ' . $e->getMessage());
    }
}

function hh_state_set(int $chatId, string $state, string $payload = ''): void
{
    $stmt = hh_db()->prepare(
        'INSERT INTO bot_state (chat_id, state, payload, updated_at) VALUES (?, ?, ?, ?)
         ON CONFLICT(chat_id) DO UPDATE SET state = excluded.state, payload = excluded.payload, updated_at = excluded.updated_at'
    );
    $stmt->execute([$chatId, $state, $payload, time()]);
}

function hh_state_get(int $chatId): array
{
    $stmt = hh_db()->prepare('SELECT state, payload, updated_at FROM bot_state WHERE chat_id = ?');
    $stmt->execute([$chatId]);
    $row = $stmt->fetch();
    return $row ?: ['state' => '', 'payload' => '', 'updated_at' => 0];
}

function hh_state_clear(int $chatId): void
{
    $stmt = hh_db()->prepare('DELETE FROM bot_state WHERE chat_id = ?');
    $stmt->execute([$chatId]);
}
