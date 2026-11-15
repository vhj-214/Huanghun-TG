# 黄昏服务端（AlwaysData PHP）

本目录包含客户端「获取黄昏定制代理」「更新配置」所需的 PHP API 和 Telegram Bot webhook。运行要求：PHP 8+、PDO SQLite、cURL、HTTPS。AlwaysData 的 PHP 站点可直接运行，无需常驻进程。

## 部署结构

将 `public/` 中的 PHP 文件放入 AlwaysData 站点的 `www/`，将 `app/` 放到站点根目录以外（例如 `/home/<account>/huanghun-app/`），避免源码被直接访问。修改 `public/api.php`、`public/webhook.php`、`public/health.php` 开头的 `require_once` 路径以指向该 `app/` 目录。把 `app/config.example.php` 复制为 `app/config.php` 并填写真实值；`config.php` 不可放入 `www/`、不可提交到 Git。

示例权限：`config.php` 设为 `0600`，SQLite 数据目录设为 `0700`。`db_path` 和 `log_path` 必须位于站点文档根目录之外。

## Telegram Bot

Webhook 地址：`https://<域名>/webhook.php`。调用 Bot API 的 `setWebhook` 时，设置 `secret_token` 为配置里的随机 `webhook_secret`，并确保 Telegram 请求头 `X-Telegram-Bot-Api-Secret-Token` 校验成功。服务端使用 webhook，不需要后台轮询进程。

只有配置中的管理员 Telegram 数字 ID 可在私聊里使用管理面板；其他用户收到“权限不足，无法访问”。管理面板支持：

- **添加代理**：每行一个，支持 `t.me/proxy?...`、`tg://proxy?...`、`socks5://user:pass@host:port`、`host:port`、`host:port:user:pass`。
- **代理列表 / 删除代理**：按服务端保存顺序查看，按编号删除，输入 `all` 删除全部。
- **更新配置**：维护最新版本号、公告和强制更新标记。
- **上传最新版客户端**：输入版本号后可直接发送 APK（Telegram Bot API 下载上限 20 MiB），或打开机器人生成的 15 分钟一次性 HTTPS 上传链接（网页上传上限 256 MiB）。网页链接令牌放在 URL 片段、不随初始 HTTP 请求发送；服务端以原子方式消费令牌，避免重放。APK 存在文档根目录外 `data/releases/latest.apk`，服务端校验 ZIP/APK 容器并保存 SHA-256 和绑定版本。
- **配置更新链接**：单独设置或清空网盘 HTTPS 链接。
- **系统状态**：查看代理、配置修订号与事件计数。

## API

客户端 API 请求携带 `X-Huanghun-Key` 请求头；该值编译在 APK 中，只能作为公开客户端标识/降低随意探测的门槛，不能作为秘密凭据或访问控制边界。代理 API 会向持有客户端的人返回代理信息，这是此功能的预期行为。

- `GET /api.php?resource=proxies`：返回启用代理及排序；密码/代理 secret 仅经 HTTPS 传给客户端。
- `GET /api.php?resource=config`：返回当前/最新版本、网盘链接、服务器 APK 是否可用、SHA-256、公告、强制更新状态和代理数量。
- `GET /download.php`：需 `X-Huanghun-Key` 请求头；仅在服务端已上传 APK 时提供下载。
- `GET/POST /upload.php?token=...`：由机器人生成的一次性、15 分钟有效的上传链接；上传成功后立即失效。
- `GET /health.php`：不返回机密的服务健康状态。

客户端在「黄昏服务 → 检查客户端更新」读取本机已安装的 `versionName` 并与服务器版本号按数字版本段比较。服务器版本更高时弹出「服务器端更新」和「网盘下载」选项；服务器下载会校验 SHA-256，再交给 Android 系统安装器。安装是否成功仍取决于 APK 包名、签名证书与当前安装版本匹配，以及用户确认系统安装提示。服务器当前没有 APK 时，服务器下载选项会提示管理员尚未上传；网盘链接为空时相应选项也会提示。

代理获取会以服务器当前列表**完整替换**客户端本地代理列表。客户端先并行测试现有代理可用性，再删除旧列表并按服务器顺序添加新列表；服务端列表为空或接口失败时不改本地数据。内置代理在代理列表中统一显示为“黄昏🌇定制代理”，详情编辑入口隐藏，分享操作不会导出其真实连接地址。此 UI 隐藏不是密码学保护；运行设备或 APK 分析者仍可检查网络配置。

## 安全事项

不要把真实 Bot token、管理员/SSH 密码或 webhook secret 写入版本库或截图。若这些凭据曾在不可信位置公开，应及时轮换。客户端 API key 会随 APK 分发，不应被当成秘密；服务端 APK 只有在其记录的版本与当前最新版本一致、文件长度及 SHA-256 元数据有效时才对客户端提供。
