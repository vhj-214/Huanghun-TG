# 黄昏服务端（AlwaysData PHP）

本目录包含客户端「获取黄昏定制代理」「更新配置」所需的 PHP API 和 Telegram Bot webhook。运行要求：PHP 8+、PDO SQLite、cURL、HTTPS。AlwaysData 的 PHP 站点可直接运行，无需常驻进程。

## 部署结构

将 `public/` 中的 PHP 文件放入 AlwaysData 站点的 `www/`，将 `app/` 放到站点根目录以外（例如 `/home/<account>/huanghun-app/`），避免源码被直接访问。修改 `public/api.php`、`public/webhook.php`、`public/health.php` 开头的 `require_once` 路径以指向该 `app/` 目录。把 `app/config.example.php` 复制为 `app/config.php` 并填写真实值；`config.php` 不可放入 `www/`、不可提交到 Git。

示例权限：`config.php` 设为 `0600`，SQLite 数据目录设为 `0700`。`db_path` 和 `log_path` 必须位于站点文档根目录之外。

## Telegram Bot

Webhook 地址：`https://<域名>/webhook.php`。调用 Bot API 的 `setWebhook` 时，设置 `secret_token` 为配置里的随机 `webhook_secret`，并确保 Telegram 请求头 `X-Telegram-Bot-Api-Secret-Token` 校验成功。服务端使用 webhook，不需要后台轮询进程。

只有配置中的管理员 Telegram 数字 ID 可在私聊里使用管理面板；其他用户收到“权限不足，无法访问”。管理面板支持：

- **添加代理**：每行一个，支持 `t.me/proxy?...`、`tg://proxy?...`、`socks5://user:pass@host:port`、`host:port`、`host:port:user:pass`。
- **代理列表 / 删除代理**：按服务端保存顺序查看、按编号删除，或输入 `all` 删除全部。
- **代理检测**：扫描全部已保存代理的 TCP 主机和端口可达性；可重新检测。删除不可用代理前必须再确认，并只删除与扫描时完全一致且未被修改的记录。此项只测 TCP 端口，不验证代理认证或 Telegram 实际转发。
- **更新配置**：维护最新版本号、公告和强制更新标记。
- **配置更新链接**：设置、查看或清空客户端更新用的 HTTPS 外链。
- **系统状态**：查看代理、配置修订号与事件计数。

点击内联菜单操作后，原菜单会收起，避免交互菜单重复堆叠。客户端 APK 上传及服务器本地 APK 下载已停用；客户端更新仅使用管理员设置的外链。

## API

客户端 API 请求携带 `X-Huanghun-Key` 请求头；该值编译在 APK 中，只能作为公开客户端标识/降低随意探测的门槛，不能作为秘密凭据或访问控制边界。代理 API 会向持有客户端的人返回代理信息，这是此功能的预期行为。

- `GET /api.php?resource=proxies`：返回启用代理及排序；密码/代理 secret 仅经 HTTPS 传给客户端。
- `GET /api.php?resource=config`：返回最新版本、更新外链、公告、强制更新状态和代理数量。为兼容旧客户端保留服务器 APK 字段，但始终返回不可用。
- `GET /download.php` 与 `GET/POST /upload.php`：已停用，返回 HTTP 410。
- `GET /health.php`：不返回机密的服务健康状态。

客户端在「黄昏服务 → 检查客户端更新」读取本机已安装的 `versionName` 并与服务器版本号比较。发现新版本且存在有效 HTTPS 更新链接时，只提供“打开更新链接”；没有可用链接时显示管理员未配置提示，不显示已停用的服务器 APK 下载按钮。强制更新也只有在有可用外链时才可执行。

代理获取时，客户端先检测当前代理：确认不可用的会移除，检测成功的保留，检测超时或结果未知的代理不会误删；随后将服务器代理与保留项去重合并。服务端接口错误时不改动本地代理列表。内置代理在代理列表中统一显示为“黄昏🌇定制代理”，详情编辑入口隐藏，分享操作不会导出其真实连接地址。此 UI 隐藏不是密码学保护；运行设备或 APK 分析者仍可检查网络配置。

## 安全事项

不要把真实 Bot token、管理员/SSH 密码或 webhook secret 写入版本库或截图。若这些凭据曾在不可信位置公开，应及时轮换。客户端 API key 会随 APK 分发，不应被当成秘密。
