# iOS 液态毛玻璃统一改造（Huanghun）

## 一、目标

把客户端里所有"液态玻璃"表面统一成社交平台上常见的 **iOS 液态透明毛玻璃**观感：

- 背景先重度柔化（frosted），而不是"清晰壁纸 + 一层 8% 蒙层"；
- 表面上叠乳白（浅色）/深灰（深色）body tint，让文字有对比、面板有实体感；
- 边缘一圈 1px 上亮下暗的高光（rim light），角落有柔和环境阴影；
- API 33+ 额外走 AGSL 折射/色散/Fresnel，低版本退化为"模糊位图 + tint + rim"，**观感一致、都不透明**；
- 全程不允许出现卡屏、黑屏、闪退、黑闪。

## 二、结构

新增包 `tw.nekomimi.nekogram.ui.glass`：

| 文件 | 职责 |
| --- | --- |
| `HuanghunGlassPalette` | 玻璃令牌：tint / 上高光 / 下暗边 / 阴影 / 描边宽 / 液态厚度 / 折射强度。随深色主题与壁纸亮度自适应，实现 `BlurredBackgroundProvider`，可直接交给 blur3 |
| `HuanghunGlassSurface` | 每页一个玻璃视口：把壁纸**一次性**栅格化 + 高斯柔化成小位图，作为所有玻璃表面的共享 backplate；API 29+ 上传到 RenderNode（33+ 自动挂 AGSL shader），27–28 直接用位图源 |
| `HuanghunGlassBackdropDrawable` | 页面根背景：绘制柔化背板；任何异常/空位图都退回主题色，保证根视图永不完全透明 |
| `HuanghunGlassPainter` | Canvas 级玻璃：给 RecyclerView 分组卡片、标题条画 tint + 顶部渐变 sheen + 上下 rim，零分配 |
| `HuanghunGlassPage` | 页面级门面：`applyActionBar()` / `applyBackdrop()` / `palette()`，各页面一行接入，同一 ActionBar 只创建一次玻璃 |

统一后的层次：

```
壁纸 → 柔化背板（整页，一次生成） → 分组卡片(tint+rim) → ActionBar/键盘/浮层(玻璃 drawable + shader)
```

因为整页背板本身就是柔化的，列表里任何半透明行、空隙、分隔都自动属于同一块毛玻璃材质，滚动时也不会出现"卡片透明但背景清晰"的割裂感。

## 三、改动清单

### 1. 公共入口

- `ActionBar/BaseFragment.java`
  - `applySettingsGlassWallpaper()`：根背景改为 `HuanghunGlassBackdropDrawable`；ActionBar 改为 `HuanghunGlassSurface.createBackground(VARIANT_ACTION_BAR)`（真玻璃，含 rim/阴影/shader），失败才退回主题色；
  - 新增 `glassSurfaces` / `glassActionBarBackgrounds` 弱表，`onResume` 不重复建 drawable；
  - 新增 `ensureGlassLayoutListener()`：容器首次测量后补传视口尺寸与壁纸（避免首帧只有纯色）；
  - `applyTransparentSettingsSections()`：分组卡片改用 `HuanghunGlassPainter`；
  - `onFragmentDestroy()` 清理本页玻璃登记。
- `Components/LoginBackgroundLayout.java`
  - 登录页背景从"清晰照片 + 0x18 遮罩"改为柔化背板；
  - 控件玻璃的 color provider 换成 `HuanghunGlassPalette(VARIANT_OVERLAY)`，自动带上 1px rim 与阴影；
  - 数字键盘与输入框仍走原有 bitmap 玻璃源（不新增 shader 实例，避免 12 个按键同时编译 shader）。
- `settings/HuanghunWallpaperLayout.java`
  - 静态壁纸：`drawChild(backgroundView)` 之后叠柔化背板；`onSizeChanged` 补壁纸；
  - 动态/视频壁纸：保持原始画面，不做逐帧模糊（避免卡顿），ActionBar 仍为玻璃。

### 2. 各独立页面

以下页面把 `setDrawBlurBackground(root)` + `setBackgroundColor(0x20FFFFFF)`（12% 白的伪玻璃）统一替换为 `HuanghunGlassPage.applyActionBar(actionBar, root)`：

`ProxyListActivity`、`ProxySettingsActivity`、`FiltersSetupActivity`、`HuanghunServiceActivity`、`HuanghunDeviceDetailsActivity`、`HuanghunBubbleCatalogActivity`、`HuanghunCallVideoLibraryActivity`、`RegexFilterEditActivity`。

其中 `ProxyListActivity` / `FiltersSetupActivity` 的列表分组也换成玻璃卡片。

### 3. Neko 设置基类

`BaseNekoSettingsActivity`、`BaseNekoXSettingsActivity`：

- `applyHuanghunSettingsActionBarGlass()` 优先走 `HuanghunGlassPage.applyActionBar`；
- `setSections(...)` 从"纯色 12%~20% 半透明"改为 `HuanghunGlassPainter.drawPanel`（tint + sheen + 双 rim）。

### 4. blur3 全局材质参数

所有聊天顶栏、底部面板、弹层、表情面板等共用 `BlurredBackgroundDrawableRenderNode` 的唯一参数入口：

```
specular 1.30 → 1.55      （高光更亮）
dispersion 0.90 → 0.55    （色散更克制）
frost 0.42 → 0.58         （真正的毛玻璃柔化）
fresnel 1.20 → 1.45
reflection 1.15 → 1.25
grain 0.12 → 0.06         （减少噪点）
```

`liquid_glass_shader.agsl`：

- bodyAlpha `0.028 + frost*0.12`（≤0.16）→ `0.050 + frost*0.26`（≤0.34）：面板从"几乎透明"变成"乳白玻璃"；
- rim `0.075 + 0.16*top + 0.20*fresnel + 0.18*specular` → `0.065 + 0.24*top + 0.24*fresnel + 0.20*specular`：上边缘更亮；
- sheen 0.032/0.045 → 0.045/0.060；
- highlight 上限 0.42 → 0.55。

`LiquidGlassEffect` 的默认值同步调整，保证不传参的调用点也拿到新材质。

### 5. 默认开启（重要）

`LiteMode` 之前只在旧偏好里偶然打开 `FLAG_LIQUID_GLASS`，`PRESET_MEDIUM` / `PRESET_HIGH` 都不含该位，所以大多数设备跑的是"只有半透明色、没有 shader"的降级路径——这正是"液态玻璃看着不对劲"的主因之一。现在：

- `PRESET_MEDIUM`、`PRESET_HIGH` 默认包含 `FLAG_LIQUID_GLASS`；
- 升级迁移不再主动清掉该位（只有省电模式才保持关闭）；
- `PRESET_LOW` 保持关闭，低端机只走"模糊位图 + tint + rim"，不会因为 shader 掉帧。

省电模式（`PRESET_POWER_SAVER = 0`）仍然整体关闭，符合预期。


## 四、稳定性设计（对应"不要卡/黑/闪退/黑闪"）

1. **不逐帧模糊**：背板只在壁纸或尺寸变化时生成一次；同一次运行内按壁纸对象复用（`obtainBlurredBackdrop` 弱引用缓存），连续打开多页只算一次。
2. **工作尺寸封顶**：源图长边先缩到 ≤1440 再 `Utilities.blurWallpaper`，内存与耗时可控。
3. **降级链**：AGSL shader（33+）→ RenderNode 位图（29–32）→ 位图源（27–28）→ 纯色 tint；任一层异常都被 `catch (Throwable)` 吃掉并落到下一层。
4. **软件绘制兜底**：`BlurredBackgroundDrawableRenderNode.draw()` 在非硬件加速画布上不再只画 source，而是补画 tint + rim。以前这里直接 `return`，软件绘制时 ActionBar 会变全透明、露出黑窗口——这是历史黑闪的来源之一。
5. **永不透明的根与栏**：`HuanghunGlassBackdropDrawable` 与 `HuanghunGlassPage.applyActionBar()` 在拿不到位图时都画主题色，绝不留下透明。
6. **零 onDraw 分配**：`HuanghunGlassPainter` 的 Paint/Path/RectF 全部静态复用；Surface 的 Matrix/Paint 也是字段复用。
7. **不重复建 drawable**：ActionBar 玻璃按 ActionBar 实例缓存，`onResume` 只刷新颜色。
8. **视频壁纸不模糊**：动态壁纸保持原画，避免每帧 RenderEffect 造成的卡顿。

## 五、验证状态

已完成（本机，无 Android SDK）：

- 全部 19 个改动 Java 文件通过 `javalang` 语法解析（`BaseNekoXSettingsActivity` 因使用 Java 21 `switch` 模式匹配，javalang 无法解析整文件；已单独把模式匹配中和后验证，改动区语法正确）；
- 改动中调用的第三方/内部 API（`setRadius`/`setThickness`/`setBackdropAlpha`/`setColorProvider`/`invalidateDisplayList`/`beginRecording`/`setParentSize`/`setWallpaperBackground`/`blurWallpaper` 等）逐项确认在源码中存在；
- `Theme` 关键键与 `isCurrentThemeDark()` / `getCachedWallpaperNonBlocking()` 存在性确认。

**未完成**：本机（Android + proot Ubuntu arm64）不具备 APK 编译条件，未做 `compileDebugJavaWithJavac`，也未构建 APK / 真机回归。上机前请按第六节检查。

编译环境实测记录（供复现/CI 参考）：

| 项 | 结果 |
| --- | --- |
| JDK 21 | `apt install openjdk-21-jdk-headless` 的 postinst 失败，但文件已解包，`/usr/lib/jvm/java-21-openjdk-arm64/bin/javac` 可用 |
| JDK 信任库 | 包安装失败导致 `lib/security/cacerts` 为空，需用系统 CA 重建；否则 Gradle 下载全部报 `trustAnchors parameter must be non-empty` |
| Gradle 9.6.1 | `services.gradle.org` 重定向到被拒 CDN，改用 `mirrors.cloud.tencent.com/gradle/` 手动下载可用 |
| Android SDK | `sdkmanager` 拉不到 manifest，改为解析 `repository2-3.xml` 手动下载 `platform-36_r02.zip` 与 `build-tools_r36_linux.zip` |
| aapt2 | 构建工具与 SDK 只有 x86_64 版本；本机 arm64 需要 qemu-user + x86_64 glibc/libstdc++，proot 内 qemu 无法启动该二进制 → **这是无法完成 APK 编译的根因** |

因此编译请在具备 x86_64 或原生 aarch64 构建工具的机器上执行：

```bash
# 本机已验证 Gradle/JDK 可用，CI 上直接：
./gradlew :TMessagesProj:compileDebugJavaWithJavac -PnativeTarget=SKIP --no-daemon --console=plain
```

若 CI 的 SDK 只有 android-36，需要临时把根 `build.gradle` 的 `compileSdk = 37` 改为 36（仓库既有文档同样记录过这一点，改完请还原）。

## 六、回归检查清单

1. 设置 → 逐页进入（聊天设置 / 贴纸 / 隐私 / 通知 / 数据与存储 / 聊天文件夹 / 设备 / 省电）：顶部应为毛玻璃条（上边缘 1px 亮线），分组卡片为玻璃圆角，滚动无白块/黑块/闪烁。
2. 代理设置、新建代理、黄昏服务、本机详情、气泡目录、通话视频库、正则过滤编辑：进入瞬间不黑屏，返回不残留。
3. 登录页：号码输入、国家选择、继续按钮、数字键盘均为玻璃；后台切回不闪。
4. 浅色/深色主题各过一遍；切换主题后停在设置页，标题栏不应变纯黑或纯白。
5. 壁纸切换：照片、图案、渐变动图、动态视频各试一次；视频壁纸应保持清晰且不卡。
6. 低端机/API 27–30：确认玻璃为"模糊 + tint"且不透明，滚动流畅。
7. 从设置页快速连续返回多级，确认没有残留玻璃层或重复阴影。

## 七、可调项

- 想更透：`HuanghunGlassPalette.update()` 里各 variant 的 `alpha`（`VARIANT_ACTION_BAR 0x66`、`VARIANT_PANEL 0x73`、`VARIANT_CARD 0x59`、`VARIANT_OVERLAY 0x40`）；
- 想更糊：`Utilities.blurWallpaper()` 内部 radius（当前 12）或 `buildBlurredBackdrop` 的 1440 上限；
- 想更亮的高光：shader 的 `rimAlpha` 系数与 `highlightAlpha` 上限。
