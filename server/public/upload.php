<?php
// 上传功能已停用。保留此地址以明确告知旧链接的访问者。
http_response_code(410);
header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'");
?><!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>上传功能已关闭</title><style>body{font:16px/1.6 system-ui,sans-serif;background:#f4f7fb;color:#202630;margin:8vh auto;max-width:600px;padding:20px}main{background:#fff;border-radius:16px;padding:28px;box-shadow:0 8px 30px #1232}h1{margin-top:0}</style></head>
<body><main><h1>上传功能已关闭</h1><p>请在管理员机器人中配置客户端更新链接。</p></main></body></html>
