# 黄昏本地个性装扮：素材与动效研究记录

日期：2026-08-17

## 设计结论

首批个性装扮不复制 QQ 的界面或受保护素材。气泡、头像挂件、进群特效优先采用 Android Canvas、渐变、路径、粒子、旋转、缩放与透明度动画构成原创的本地动态效果，以避免大量外部二进制素材造成安装包膨胀，也保证可在真实界面中绘制与测试。

来电铃声使用原创合成的简短音色及明确授权的公开素材方向。Mixkit 的通知音效页面说明其通知音效可依据 Mixkit License 下载使用；后续若引入任何外部音频，会逐项记录来源和许可证，并将文件本地打包。[Mixkit Free Notification Sound Effects](https://mixkit.co/free-sound-effects/notification/)

## 视觉方向

首批 200 套模板统一采用当代潮流、鲜明、抽象的视觉语言：液态金属、赛博故障、Y2K 透明果冻、机能工业、反重力星云、梦核、暗黑漫画、潮玩涂鸦、像素霓虹、超现实拼贴、怪诞萌物与玻璃拟态。避免朴素、陈旧或仅更换颜色的重复设计。

## 功能表现原则

1. 聊天气泡通过消息视图的本地绘制层真实作用于聊天消息。
2. 头像挂件以头像边缘叠加层绘制，带动态光环、粒子、旋转纹理或呼吸光。
3. 来电方案使用全屏动态视觉预览与本地试听；系统原有接听、挂断、静音、扬声器、视频等功能和权限流程不变。
4. 进群效果由独立全屏动画承载，包含主题主体、轨迹拖尾、粒子、光晕、称号卡与进场音效，而不仅是文字。
5. 每个选择必须可保存、返回/重启后恢复，并在真实目标界面回归测试。

## 参考链接

- Mixkit Free Notification Sound Effects: https://mixkit.co/free-sound-effects/notification/
- OpenGameArt CC0 collection index: https://opengameart.org/content/good-cc0-art
- Pixabay sound effects catalog: https://pixabay.com/sound-effects/search/notification/
