# 聊天顶部玻璃透明度调整

## 现象

聊天页（联系人、非联系人、群组、频道）顶部区域由三层玻璃叠加而成：

1. `ActionBar` 的玻璃胶囊（返回按钮、头像+标题、通话/菜单按钮）
2. `ChatActivityTopPanelLayout` 的玻璃卡片（“添加为联系人”、翻译、置顶消息等）
3. 这两层背后的模糊壁纸快照 + 液态玻璃乳白高光

此前玻璃填充（模糊壁纸快照 + 乳白液态玻璃层）以 100% 不透明度合成，只有一层很淡的主题色着色，因此无论聊天类型如何，顶部看起来都像一块近乎实心的磨砂面板，壁纸几乎透不出来。

## 相关源码

| 作用 | 文件 |
| --- | --- |
| 聊天顶部玻璃的颜色/透明度提供器 | `TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java` |
| 玻璃提供器构建器 | `TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/color/BlurredBackgroundProviderBuilder.java` |
| 玻璃颜色提供器接口 | `TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/color/BlurredBackgroundColorProvider.java` |
| 玻璃 Drawable（软件绘制路径） | `TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java` |
| 玻璃 Drawable（RenderNode / 液态玻璃路径） | `TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawableRenderNode.java` |
| 顶部面板布局 | `TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityTopPanelLayout.java` |
| 聊天页 ActionBar 玻璃初始化 | `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`（`actionBar.setupGlass(...)`） |

`ChatActivity` 在创建 ActionBar 和顶部面板时都使用
`BlurredBackgroundProviderImpl.topPanelChatActivity(...)`，因此只修改这一个提供器即可同时覆盖：

- 联系人聊天
- 非联系人聊天
- 群组聊天
- 频道聊天
- 论坛话题的顶部标签、搜索标签等使用同一提供器的顶部玻璃

## 改动内容

### 1. 新增整体透明度参数 `backdropAlpha`

`BlurredBackgroundColorProvider` 增加默认方法 `getBackdropAlpha()`（默认 `1f`，不影响其他页面）。
`BlurredBackgroundProviderBuilder#setBackdropAlpha(float)` 用于为某个提供器指定整体透明度。

`BlurredBackgroundDrawable#setColorProvider(...)` 会自动读取该值并保存到 `backdropAlpha` 字段。

### 2. 绘制时真正让壁纸透出

- **RenderNode 路径**（Android 10+）：`BlurredBackgroundDrawableRenderNode#updateDisplayList()` 对包含模糊源、乳白液态玻璃层和 AGSL 折射效果的 `renderNodeFill` 调用 `setAlpha(backdropAlpha)`，主题色着色也按同一比例缩放。
- **软件绘制路径**：`BlurredBackgroundDrawable#drawSourceAny/drawSourceColorImpl/drawSourceBitmap` 将模糊源、着色和液态玻璃高光统一乘以 `backdropAlpha`，避免动画透明度（`alpha`）与背景透明度叠加时出现双重衰减。

### 3. 聊天顶部使用 50% 玻璃

`BlurredBackgroundProviderImpl.topPanelChatActivity(...)`：

```java
.setBackdropAlpha(0.50f)
```

同时把主题色着色从 `0.34f / 0.27f / 0.24f` 降到 `0.20f / 0.16f / 0.14f`。
`topPanelChatActivityTags(...)` 同步调整，保证搜索标签栏与顶部玻璃一致。

## 如何继续微调

- 想让壁纸更明显：把 `topPanelChatActivity(...)` 中的 `.setBackdropAlpha(0.50f)` 调低，例如 `0.35f`。
- 想让玻璃更“实”：调高到 `0.65f`～`0.75f`。
- 想只调整着色、不改变模糊层：修改 `topPanelChatActivity(...)` 里的 `0.20f / 0.16f / 0.14f`。
- 该参数只影响使用 `topPanelChatActivity` 的聊天顶部区域；设置页、登录页、输入框等其他玻璃表面保持原样。

## 验证

- `./gradlew :TMessagesProj:compileStagingJavaWithJavac -PnativeTarget=SKIP` 编译通过。
- 在联系人、非联系人、群组、频道四种聊天中，顶部 ActionBar 胶囊与顶部面板卡片应能明显透出壁纸纹理，同时保留磨砂与边缘高光。
