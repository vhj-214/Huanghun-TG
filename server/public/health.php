<?php
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
try {
    require_once __DIR__ . '/../app/db.php';
    $count = (int) hh_db()->query('SELECT COUNT(*) FROM proxies WHERE enabled = 1')->fetchColumn();
    echo json_encode(['ok' => true, 'service' => 'huanghun', 'active_proxies' => $count, 'time' => time()]);
} catch (Throwable $e) {
    http_response_code(503);
    echo json_encode(['ok' => false, 'service' => 'huanghun']);
}
