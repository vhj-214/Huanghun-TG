# 号码登录页透明玻璃修复说明

## 基线与范围

仓库：`vhj-214/Huanghun-TG`。修复基于 `dev` 分支的 `e3524f50c`（Fix settings navigation and add protocol login button）。完整克隆保留了 Git 历史、远程分支和三个子模块。

本次对仓库文件建立了索引，并深入检查号码登录页、欢迎页、导航容器、系统栏、壁纸容器、玻璃渲染源、数字键盘与它们的调用关系。并非逐行人工阅读三万余个文件或所有第三方原生源码；完整 Java/Kotlin 编译用于补充检查项目级源码兼容性。

## 问题来源

原来的 `LoginActivity.createView()` 会调用 `restoreOfficialLoginTheme()`，关闭自动夜间主题并持久化应用默认主题。创建根视图、`updateColors()` 和预留入场动画又会写入不透明的 `windowBackgroundWhite`。只让表单或某个按钮透明并不能解决根背景与顶部白色区域。

数字键盘则使用原来的灰色实体选择器；没有针对登录页的壁纸玻璃背景入口。

## 修改内容

1. 新增 `Components/LoginBackgroundLayout.java`。登录页自行绘制完整壁纸层，延伸到顶部按钮和状态栏下方，而不是穿透到 Activity 的窗口底色。首次缓存未就绪时使用本地默认壁纸；默认壁纸创建失败时使用本地渐变兜底。
2. 删除登录页主动重置全局主题的逻辑，保留用户已有主题、壁纸和自动夜间设置。创建、主题刷新、缓存页面恢复与预留动画收尾不再回写实体白底。
3. 国家/地区、号码输入框、协议登录按钮、返回/菜单按钮与继续按钮复用现有 `WallpaperBitmapProvider → BlurredBackgroundSourceWrapped → BlurredBackgroundDrawableViewFactory`。输入框原有焦点、错误轮廓与标签缺口保留，标签上方区域不被玻璃背景填平。
4. 数字键盘新增可选 `setGlassBackground()` 接口。仅 `LoginActivity` 启用；其他键盘调用者默认仍使用原选择器。玻璃与原按压选择器组合绘制，每个按键独立持有 drawable，保留原来的四角形状和退格图标 padding。
5. 壁纸变化监听在片段创建/销毁时成对添加和移除。位置监听器在最终释放时 shutdown；主题刷新复用已有玻璃 drawable，避免反复订阅导致累积。
6. 不新增视频播放器、账号初始化、实时根视图抓取、RenderNode 或 AGSL 依赖。使用可在旧版 Android 和软件 Canvas 上绘制的位图/颜色渲染路径，并保留现有项目的玻璃开关行为。
7. 在安装壁纸前及背景绘制前校验空/已回收位图。模糊源准备遇到内存或运行时异常时退回安全颜色源，不主动回收 Theme 共享位图。
8. 根据当前壁纸与主题文字颜色局部调整雾化强度；不修改全局配色。深浅严重不匹配时会使用更强的雾化以优先保证可读性。继续按钮的加载环与箭头均使用主题蓝色，避免玻璃底上出现白色进度环看不见的情况。
9. 系统栏颜色根据实际壁纸/遮罩估算，并保留附着底部弹层的导航栏颜色覆盖。保留原有 Insets 和键盘布局模式，没有为了透明效果更改全局 edge-to-edge 行为。

## 保持不变的业务

源码回归脚本会对照基线检查全部原有登录业务内部类，以及协议登录选项、免责声明、导入结果处理、权限处理、登录状态保存/恢复、返回逻辑和自定义键盘显示逻辑。原号码格式化、国家选择、验证码、密码、通行密钥、注册、付款相关内部类保持不变。

数字键盘构造器中的数字输入回调、退格触摸与长按重复逻辑、焦点查找、测量布局、文字绘制保持原实现。未更改 `com/Huanghun/protocol`、Gradle 依赖、签名设置、AndroidManifest 或 JNI/第三方子模块。

## 验证与边界

交付包的 `verification/compile.log` 与 `verification/source-regression.log` 保存实际验证结果。验证命令：

```bash
python3 .github/test_login_glass.py
git diff --check
ANDROID_HOME=/home/ubuntu/android-sdk bash gradlew \
  :TMessagesProj:compileDebugJavaWithJavac \
  -PnativeTarget=SKIP --no-daemon --console=plain
```

源码回归脚本不等于 Android UI 自动化；`nativeTarget=SKIP` 只用于 Java/Kotlin 源码编译检查，不构建原生库，也不能用这种方式产出可承诺启动的安装包。不要将编译检查结果理解为已通过真机或完整 APK 测试。

建议安装完整正式构建后检查：冷启动→号码登录；浅/深色主题；本地图片/默认壁纸；后台恢复与返回；数字输入、长按删除、国家选择；系统键盘与无障碍模式；协议登录菜单及文件选择；验证码和密码页；横屏、平板与不同 Android 版本。本次没有发送验证码、导入用户账号或执行真实登录请求。

## 使用交付包

修改包包含三份完整 Java 文件、源码回归脚本、本文档、统一 diff 补丁和验证日志。它是修复包，不是整个仓库的重新打包，也不是 APK。

在基线仓库根目录，可先检查再应用补丁：

```bash
git apply --check /path/to/login-glass-fix.patch
git apply /path/to/login-glass-fix.patch
```

或者按包内相对路径覆盖/新增 `TMessagesProj`、`.github` 和 `docs` 下的五个文件；不要把修复包替换成整个项目。源码回归脚本需要 Git 历史中包含 `e3524f50c`。完整 APK 构建仍使用项目原有 Android Studio/GitHub Actions 流程与原签名配置。

当前修改保留在本地工作树，未创建提交、未推送 GitHub，也未触发远程 APK 构建。若远程 `dev` 已发生变化，应先检查补丁兼容性，不要直接覆盖后续业务修改。
