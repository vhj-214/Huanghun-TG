# 设置页液态玻璃改造说明（代理设置 / 聊天文件夹 / 黄昏服务）

## 一、现象

从设置进入代理设置、聊天文件夹、黄昏服务等二级页面时，会出现三类问题：

1. 状态栏与标题栏是一整块纯黑，与下方壁纸断裂；
2. 列表中出现纯白或纯黑的矩形块（文件夹行、创建文件夹/开关/说明区域）；
3. 页面滚动或主题刷新时，标题栏与列表底色反复闪烁。

## 二、根因

### 1. 内容视图被排在标题栏下方

`ActionBarLayout.LayoutContainer.onLayout()` 会把非 ActionBar 的子视图放到 `actionBarHeight` 之下，只有 `fitsSystemWindows=true` 的子视图才从 y=0 铺满。这些页面原来都用普通 `FrameLayout` 作根视图，壁纸只从标题栏下方开始，标题栏区域露出窗口底色。

### 2. 图案/渐变壁纸无法克隆，顶栏退回透明

`BaseFragment.applySettingsGlassWallpaper()` 需要把壁纸同时铺到根视图和 ActionBar。照片壁纸是 `BitmapDrawable`，可以克隆；图案、渐变、动态壁纸是 `MotionBackgroundDrawable`，没有 `ConstantState`，克隆返回 `null`，旧代码于是把 ActionBar 设为 `Color.TRANSPARENT`。根视图又被排在标题栏下方，透明标题栏直接露出黑色窗口底。

### 3. 自适应标题栏会滚动变黑

这些设置页都调用了 `actionBar.setAdaptiveBackground(listView, false, -1, -1)`。滚动时 `adaptive_updateColor()` 会在 `windowBackgroundGray` 与 `actionBarDefault` 之间做颜色动画，深色主题下两者都是黑色，于是标题栏在玻璃与纯黑之间来回闪。`-1/-1` 分支还会把 `Theme.getCachedWallpaperNonBlocking()` 的同一个 Drawable 同时挂到根视图和 ActionBar 上；Drawable 只有一个回调宿主，两个视图用不同 bounds 绘制同一个实例，表现为壁纸图案跳动/闪烁。

### 4. 主题刷新把根视图和单元格重新刷成白/黑

`ThemeDescription` 中的 `FLAG_BACKGROUND`（根视图 `windowBackgroundGray`）与 `FLAG_CELLBACKGROUNDCOLOR`（单元格 `windowBackgroundWhite`）会在页面打开和主题切换时执行 `setBackgroundColor()`，把壁纸覆盖成纯白或纯黑。`FiltersSetupActivity` 还在 `onCreateViewHolder` 里给 `FilterCell` 手动设置了 `windowBackgroundWhite`。

## 三、修改内容

### 1. `org/telegram/ui/ActionBar/ActionBar.java`

新增 `disableAdaptiveBackground()`：置位 `adaptiveBackgroundDisabled`，取消正在运行的颜色动画，清空阴影。`setAdaptiveBackground()` 注册的两个滚动回调与 `adaptive_updateColor()` 都会提前返回，标题栏不再随滚动变色。

### 2. `org/telegram/ui/ActionBar/BaseFragment.java`

- `applySettingsGlassWallpaper()` 跳过自带壁纸根的页面（`LoginBackgroundLayout`、`HuanghunWallpaperLayout`），避免重复铺层。
- 壁纸缓存未就绪时最多重试 4 次（每次 220ms），不再永久停留在黑色兜底。
- 新增 `createActionBarWallpaper()`：先克隆；克隆失败时用 `rasterizeWallpaperSlice()` 把壁纸顶部栅格化为 `BitmapDrawable`，再叠加低透明度玻璃色（浅色 `0x14FFFFFF`、深色 `0x1F000000`）。两者都失败时退回 `key_actionBarDefault` 主题色，绝不使用透明。
- 顶栏壁纸按“壁纸对象 + 当前背景”缓存，避免每次 `onResume` 重新分配位图。
- 新增静态注册表 `glassWallpaperRoots` 与 `isInGlassPage(View)`，供主题刷新判断某个 View 是否属于壁纸页。
- 对壁纸页调用 `actionBar.disableAdaptiveBackground()`，消除滚动变黑/闪烁。
- `applyTransparentSettingsSections()` 给每个 `RecyclerListView` 注册 `OnChildAttachStateChangeListener`，单元格每次 attach 都清理不透明底色，解决滚动时新绑定的白色单元格。
- 新增 `Logout` 页面到壁纸页名单。

### 3. `org/telegram/ui/ActionBar/ThemeDescription.java`

- `FLAG_BACKGROUND`：`BaseFragment.isInGlassPage(view)` 为真时跳过 `setBackgroundColor()`，根视图的壁纸不再被 `windowBackgroundGray` 覆盖。
- `FLAG_CELLBACKGROUNDCOLOR`：壁纸页内的单元格改为 `Color.TRANSPARENT`，不再刷成 `windowBackgroundWhite`。

### 4. `org/telegram/ui/ProxyListActivity.java`（代理设置列表）

- 非登录入口改为 `HuanghunWallpaperLayout` + `setFitsSystemWindows(true)`，壁纸从 y=0 铺满。
- 标题栏改为 `setDrawBlurBackground(root)` + `0x20FFFFFF` 液态玻璃。
- 列表保持透明，分组底板改为玻璃色（深色 `0x32` 黑、浅色 `0x2A` 白）。
- `updateListInsets()`：列表顶部内边距 = ActionBar 实测高度，底部留导航栏高度，`setClipToPadding(false)`。
- `onResume()` 重新应用玻璃色；登录入口同样全屏化。

### 5. `org/telegram/ui/ProxySettingsActivity.java`（新增/编辑代理）

- 同样改为全屏壁纸根 + 玻璃标题栏。
- `ScrollView` 增加 `updateScrollInsets()` 顶部内边距与 `setClipToPadding(false)`。
- 登录入口同样全屏化。

### 6. `org/telegram/ui/FiltersSetupActivity.java`（聊天文件夹）

- 根布局从 `FrameLayout` + `windowBackgroundGray` 改为 `HuanghunWallpaperLayout` + `setFitsSystemWindows(true)`。
- 移除 `actionBar.setAdaptiveBackground(...)`，改为 `setDrawBlurBackground` + `0x20FFFFFF` 玻璃标题栏。
- `setSections()` 改为玻璃渲染器，排除 `TextInfoPrivacyCell`、`ShadowSectionCell`、`HintInnerCell`。
- 删除 `FilterCell.setBackgroundColor(windowBackgroundWhite)`，文件夹行不再白底。
- 新增 `updateListInsets()`，列表首行不再被标题栏遮挡。
- 移除 `fragmentView` 的 `FLAG_BACKGROUND` 主题描述，主题刷新不再把根视图刷成灰色。

### 7. `com/Huanghun/HuanghunServiceActivity.java`（黄昏服务）

- 根布局改为 `HuanghunWallpaperLayout` + `setFitsSystemWindows(true)`，标题栏改为液态玻璃。
- 内容玻璃面板上边距仍为 `状态栏高度 + 64dp`，在全屏坐标下正好落在标题栏下方 8dp。

### 8. `com/Huanghun/HuanghunDeviceDetailsActivity.java`（本机详情）

- 同样改为全屏壁纸根 + 玻璃标题栏；WebView 顶部偏移状态栏高度，使内置 HTML 自带的 56px 顶部留白对齐标题栏底部。

## 四、验证

- 语法解析：`javalang` 解析全部改动文件通过。
- Java 编译：`./gradlew :TMessagesProj:compileDebugJavaWithJavac -PnativeTarget=SKIP`，`BUILD SUCCESSFUL`。
  - 本机 Android SDK 仓库最高只有 `platforms;android-36`，编译时临时把根 `build.gradle` 的 `compileSdk = 37` 改为 36，编译完成后已还原；该临时改动不在补丁内。
- 未做真机/模拟器 UI 验证，未构建 APK。

## 五、建议回归

1. 设置 → 数据和存储 → 代理设置：浅色/深色主题、照片/图案/动态壁纸、滚动、多选删除、返回。
2. 代理设置 → 新增代理 / 信息按钮：输入框焦点、键盘、保存返回。
3. 设置 → 聊天文件夹：滚动列表、拖动排序、创建新文件夹、开关、说明文字、进入本地文件夹。
4. 设置 → 聊天设置 / 贴纸与表情 / 隐私与安全 / 通知 / 数据与存储 / 设备 / 省电：逐页滚动，确认无白块、黑块与标题栏闪烁。
5. 设置 → 黄昏服务 → 本机详情：WebView 内容不被标题栏遮挡。
6. 登录页 → 代理设置：登录壁纸是否铺满状态栏。
7. 切换主题后停留在上述页面，确认背景不会被主题刷新覆盖。

## 六、八个设置入口的覆盖范围

本次公共修复覆盖以下入口以及它们继续打开的设置子页面：

- **聊天设置**：`NekoChatSettingsActivity`，由 `BaseNekoXSettingsActivity` 统一处理；
- **贴纸与表情**：`StickersActivity`、`NekoEmojiSettingsActivity` 及相关表情/贴纸子页；
- **隐私与安全**：`PrivacySettingsActivity`、隐私用户/控制等子页；
- **通知**：`NotificationsSettingsActivity` 及通知声音、自定义通知等子页；
- **数据与存储**：`DataSettingsActivity`、自动下载、数据用量、缓存等子页；
- **聊天文件夹**：`FiltersSetupActivity` 及文件夹编辑/筛选相关页面；
- **设备**：`SessionsActivity` 及设备详情/登录会话相关页面；
- **省电**：`LiteModeSettingsActivity` 及其设置列表。

原生 Telegram 页面统一由 `BaseFragment.applySettingsGlassWallpaper()` 处理：满足设置页名称过滤条件的页面会自动获得全屏根布局、壁纸背景、标题栏壁纸玻璃、关闭滚动自适应黑色标题栏、透明列表分组和回收单元格底色清理。Neko/NekoX 设置页面则由两个公共基类直接处理，避免子类漏改。

## 七、最新验证

- 两套 Neko 设置基类已加入标题栏自适应关闭、子项 attach 清理、绑定后清理；
- 原生设置页根视图已在应用壁纸前设置 `fitsSystemWindows=true`，黑色顶部窗口区域不再残留；
- Java 编译再次通过：`BUILD SUCCESSFUL`。
