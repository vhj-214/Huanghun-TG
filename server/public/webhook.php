<?php
require_once __DIR__ . '/../app/telegram.php';

$cfg = hh_config();
$provided = (string) ($_SERVER['HTTP_X_TELEGRAM_BOT_API_SECRET_TOKEN'] ?? '');
if ($provided === '' || !hash_equals($cfg['webhook_secret'], $provided)) {
    http_response_code(403);
    exit;
}
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    header('Allow: POST');
    http_response_code(405);
    exit;
}
$raw = file_get_contents('php://input');
$update = json_decode($raw ?: '', true);
if (!is_array($update)) {
    http_response_code(400);
    exit;
}
http_response_code(200);
echo 'ok';
if (function_exists('fastcgi_finish_request')) {
    fastcgi_finish_request();
}
try {
    if (isset($update['message']) && is_array($update['message'])) {
        hh_bot_handle_message($update['message']);
    } elseif (isset($update['callback_query']) && is_array($update['callback_query'])) {
        hh_bot_handle_callback($update['callback_query']);
    }
} catch (Throwable $e) {
    hh_log('Webhook handler error: ' . $e->getMessage());
}
