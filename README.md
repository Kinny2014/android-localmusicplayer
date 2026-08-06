# LocalMusicPlayer

基于 Kotlin + Jetpack Compose 的本地 MP3 音乐播放器，遵循 Google 推荐架构与 Media3 最佳实践。

## 功能

- 通过 SAF（Storage Access Framework）导入本地 MP3 文件或整个文件夹
- 后台播放：应用退到后台或锁屏后音乐不中断
- 顺序播放 + 列表循环（播完最后一首后从第一首重新开始）
- 点击歌曲立即播放，当前歌曲播完后自动切到下一首
- 播放列表持久化（DataStore）

## 技术栈

| 类别 | 技术 |
|------|------|
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM + Repository |
| DI | Hilt |
| 播放 | Media3 (ExoPlayer + MediaSessionService) |
| 持久化 | DataStore Preferences + kotlinx.serialization |
| 异步 | Kotlin Coroutines + Flow |

## 运行

1. 用 Android Studio 打开本项目目录
2. 等待 Gradle Sync 完成
3. 连接设备或启动模拟器（API 26+）
4. 运行 `app` 模块

## 使用说明

1. 首次启动会请求存储与通知权限
2. 点击右下角 **文件夹图标** 导入单个或多个 MP3 文件
3. 点击 **新建文件夹图标** 导入整个文件夹（递归扫描 MP3）
4. 点击列表中的歌曲开始播放
5. 底部控制栏可暂停/播放、切歌
