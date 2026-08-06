# Kinny 听 · 歌词与音乐同步原理

本文档说明**歌词如何与音乐播放进度保持同步**的通用原理，以及 Kinny 听（`com.kinny.localmusicplayer`）中的具体实现方式。

> 歌词文件的加载与 SAF 目录配对问题，请参阅 [lyrics-loading-saf.md](./lyrics-loading-saf.md)。

---

## 1. 核心结论

Kinny 听的歌词同步**不是**通过分析音频波形、语音识别或节拍检测实现的，而是：

> **读取 LRC 文件中预先标注的时间戳，用播放器当前的播放时间（毫秒）去查找「此刻应该显示哪一行歌词」。**

这是一种 **基于时间轴的查表匹配**，也是 LRC 格式的行业标准做法。

---

## 2. 通用原理

### 2.1 LRC 时间轴

LRC（Lyrics）文件在每一行歌词前标注了时间标签，表示该行应从音频的哪个时刻开始显示：

```lrc
[ti:花妖]
[ar:刀郎]
[00:12.50]第一句歌词
[00:17.30]第二句歌词
[00:22.00]第三句歌词
```

时间标签 `[mm:ss.xx]` 会被转换为**毫秒时间戳**：

```
[00:12.50] → 0×60000 + 12×1000 + 500 = 12500 ms
[00:17.30] → 17300 ms
```

解析后得到按时间排序的结构化列表：

```
[
  { timestampMs: 12500, text: "第一句歌词" },
  { timestampMs: 17300, text: "第二句歌词" },
  ...
]
```

### 2.2 同步算法

给定当前播放位置 `positionMs`（播放器报告的已播放毫秒数），同步算法的目标是：

> **找到所有 `timestampMs ≤ positionMs` 的歌词行，取其中最后一行的索引。**

即：**当前行 = 最后一个「已经开始」的歌词行**。

#### 示例

当前播放 `15000 ms`（15 秒）：

| 行号 | 时间戳 (ms) | ≤ 15000? |
|----|----------|----------|
| 0  | 12500    | ✅        |
| 1  | 17300    | ❌ → 停止   |

→ 当前行索引 = **0**，显示「第一句歌词」

当前播放 `18000 ms`：

| 行号 | 时间戳 (ms) | ≤ 18000? |
|----|----------|----------|
| 0  | 12500    | ✅        |
| 1  | 17300    | ✅        |
| 2  | 22000    | ❌ → 停止   |

→ 当前行索引 = **1**，显示「第二句歌词」

#### 一行多个时间戳

LRC 允许同一行歌词对应多个时间点（重复段落）：

```lrc
[00:30.00][01:30.00]副歌歌词
```

解析器会为每个时间戳各生成一条记录，文本相同、时间戳不同。

### 2.3 播放进度从哪来

播放器（ExoPlayer / Media3）在解码音频时维护一个**播放时钟**，对外暴露：

| 属性                | 含义         |
|-------------------|------------|
| `currentPosition` | 当前播放位置（毫秒） |
| `duration`        | 总时长（毫秒）    |
| `isPlaying`       | 是否正在播放     |

UI 层定期或在状态变化时读取 `currentPosition`，再调用同步算法更新歌词显示。

### 2.4 与音频分析方案的对比

| 方案            | 原理          | Kinny 听 |
|---------------|-------------|---------|
| **LRC 时间轴查表** | 预先标注时间，查表匹配 | ✅ 采用    |
| 音频波形/节拍分析     | 实时分析 PCM 数据 | ❌ 未采用   |
| 语音识别 (ASR)    | 识别唱词对齐      | ❌ 未采用   |
| 在线歌词 API      | 服务端返回时间轴    | ❌ 未采用   |

LRC 方案的优点是实现简单、CPU 占用低、不依赖网络；缺点是**完全依赖 LRC 文件的打轴质量**——时间不准则歌词就会「词不对板」。

---

## 3. Kinny 听整体数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                        MusicPlaybackService                      │
│                     ExoPlayer (Media3) 解码 MP3                   │
└───────────────────────────────┬─────────────────────────────────┘
                                │ currentPosition (ms)
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      MusicPlayerManager                          │
│              MediaController → PlayerState Flow                  │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PlaylistViewModel                           │
│  ① 切歌时加载歌词 (LyricsLoader)                                  │
│  ② 接收播放进度 → Lyrics.indexAt(positionMs)                     │
│  ③ 更新 currentLyricIndex / currentLyricLine                     │
└───────────────────────────────┬─────────────────────────────────┘
                                │ PlaylistUiState
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              NowPlayingScreen / ScrollableLyrics                 │
│              高亮当前行 + animateScrollToItem 自动滚动             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 项目实现详解

### 4.1 第一步：LRC 解析（`LrcParser`）

**文件：** `data/lyrics/LrcParser.kt`

职责：将 LRC 纯文本解析为 `List<LrcLine>`。

支持的时间格式：

| 格式       | 示例              |
|----------|-----------------|
| 分:秒.毫秒   | `[00:12.50]`    |
| 分:秒      | `[00:12]`       |
| 时:分:秒.毫秒 | `[00:01:30.00]` |

处理规则：

- 去除 UTF-8 BOM 头
- 跳过元数据行（`[ti:]`、`[ar:]`、`[by:]` 等）
- 一行内多个时间标签 → 展开为多条 `LrcLine`
- 最终按 `timestampMs` 升序排列

毫秒换算（分:秒.毫秒格式）：

```kotlin
timestampMs = min × 60_000 + sec × 1_000 + frac
```

### 4.2 第二步：歌词数据模型（`Lyrics` / `LrcLine`）

**文件：** `data/lyrics/LyricsModels.kt`

```kotlin
data class LrcLine(
    val timestampMs: Long,  // 毫秒时间戳，-1 表示无时间轴
    val text: String,
)

data class Lyrics(
    val lines: List<LrcLine>,
    val source: LyricsSource,
) {
    val isSynced: Boolean = lines.any { it.timestampMs >= 0 }
}
```

`LyricsSource` 枚举：

| 值              | 含义             | 能否同步        |
|----------------|----------------|-------------|
| `LRC_FILE`     | 同目录 `.lrc` 文件  | ✅           |
| `ID3_EMBEDDED` | MP3 内嵌 USLT 歌词 | ❌（纯文本，无时间轴） |
| `NONE`         | 未找到歌词          | —           |

### 4.3 第三步：同步核心算法（`Lyrics.indexAt`）

**文件：** `data/lyrics/LyricsModels.kt`

```kotlin
fun indexAt(positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    if (!isSynced) return 0          // 无时间轴：始终显示第一行
    var result = -1
    for (i in lines.indices) {
        if (lines[i].timestampMs <= positionMs) {
            result = i
        } else {
            break                    // 列表已排序，可提前终止
        }
    }
    return result
}
```

返回值语义：

| 返回值   | 含义                    |
|-------|-----------------------|
| `-1`  | 尚无歌词行到达（播放位置早于第一行时间戳） |
| `≥ 0` | 当前应显示的行索引             |

配套方法 `lineAt(positionMs)` 在 `indexAt` 基础上返回对应文本。

> 当前实现为线性扫描。歌词行数通常在几百行以内，性能足够；若需优化可改为二分查找（`lines` 已按时间排序）。

### 4.4 第四步：获取播放进度

**文件：** `player/MusicPlayerManager.kt`

通过 Media3 的 `MediaController` 连接后台 `MusicPlaybackService`，将 ExoPlayer 状态映射为 `PlayerState`：

```kotlin
data class PlayerState(
    val currentMediaId: String?,
    val isPlaying: Boolean,
    val currentPositionMs: Long,   // ← 歌词同步的核心输入
    val durationMs: Long,
    val currentIndex: Int,
)

private fun Player.toPlayerState() = PlayerState(
    currentPositionMs = currentPosition,  // ExoPlayer API
    ...
)
```

### 4.5 第五步：ViewModel 驱动同步（`PlaylistViewModel`）

**文件：** `ui/playlist/PlaylistViewModel.kt`

ViewModel 通过**两个渠道**获取播放进度并更新歌词：

#### 渠道 A：播放器事件回调

`observePlayerState()` 订阅 `MusicPlayerManager.observePlayerState()`：

- 切歌、暂停、播放、seek 等事件触发时立即更新
- 每次更新调用 `lyrics.indexAt(state.currentPositionMs)`

```kotlin
playerManager.observePlayerState().collect { state ->
    _uiState.update {
        val index = it.currentLyrics.indexAt(state.currentPositionMs)
        it.copy(
            currentPositionMs = state.currentPositionMs,
            currentLyricLine = it.currentLyrics.lineAt(state.currentPositionMs),
            currentLyricIndex = index,
        )
    }
    if (songChanged) loadLyricsForSong(state.currentMediaId)
}
```

#### 渠道 B：定时轮询（播放中）

Media3 的 `Player.Listener` **不会在播放过程中每帧回调进度**，因此播放中额外每 **250ms** 轮询一次：

```kotlin
private const val PROGRESS_UPDATE_INTERVAL_MS = 250L

private fun startProgressTicker() {
    while (isActive) {
        delay(PROGRESS_UPDATE_INTERVAL_MS)
        if (_uiState.value.isPlaying) {
            val state = playerManager.currentState()
            updateLyricLine(state.currentPositionMs)
        }
    }
}
```

#### 特殊场景：用户拖动进度条

`seekTo()` 后立即用新位置刷新歌词，无需等待下一次轮询：

```kotlin
fun seekTo(positionMs: Long) {
    playerManager.seekTo(positionMs)
    updateLyricLine(positionMs)
}
```

#### 切歌时加载歌词

```kotlin
private fun loadLyricsForSong(songId: String) {
    val lyrics = lyricsLoader.loadLyrics(
        songUri = ...,
        lrcUri = song.lrcUri,       // 导入时已配对
        sourceTreeUri = ...,
        title = song.title,
    )
    _uiState.update {
        it.copy(
            currentLyrics = lyrics,
            currentLyricIndex = lyrics.indexAt(it.currentPositionMs),
        )
    }
}
```

### 4.6 第六步：UI 呈现（`ScrollableLyrics`）

**文件：** `ui/components/ScrollableLyrics.kt`

接收 `PlaylistUiState` 中的 `currentLyrics` 和 `currentLyricIndex`：

#### 高亮当前行

```kotlin
LyricLineItem(
    text = line.text,
    isActive = lyrics.isSynced && index == currentLineIndex,
)
```

当前行：主题色、20sp、加粗；其余行：42% 透明度、16sp。

#### 自动滚动

```kotlin
LaunchedEffect(currentLineIndex, lyrics.isSynced) {
    if (lyrics.isSynced && currentLineIndex >= 0) {
        listState.animateScrollToItem(
            index = currentLineIndex,
            scrollOffset = -180,    // 使当前行偏向屏幕中央
        )
    }
}
```

列表上下各有 `220dp` 留白（`LYRICS_VERTICAL_PADDING`），确保首尾行也能滚到中央。

#### 无时间轴歌词

当 `isSynced == false`（ID3 内嵌纯文本）时，静态展示全部歌词，不进行高亮和滚动。

---

## 5. 完整同步时序

以用户点击播放、歌词正常加载为例：

```
T+0ms     用户点击歌曲
          └─ playSong() → ExoPlayer 开始解码
          └─ loadLyricsForSong() → 异步读取 .lrc → LrcParser.parse()

T+50ms    歌词加载完成，currentLyrics 更新
          └─ indexAt(0) = -1（第一行时间戳尚未到达）
          └─ UI 显示歌词列表，尚无高亮行

T+12500ms 播放进度到达第一行时间戳
          └─ progressTicker / playerState 触发
          └─ indexAt(12500) = 0
          └─ UI 高亮第 0 行，滚动到中央

T+17300ms 播放进度到达第二行时间戳
          └─ indexAt(17300) = 1
          └─ UI 切换到第 1 行

T+30000ms 用户拖动进度条到 60 秒
          └─ seekTo(60000) → updateLyricLine(60000)
          └─ indexAt(60000) 立即重算 → UI 瞬间跳转
```

---

## 6. UI 状态字段

**文件：** `model/PlaylistUiState.kt`

| 字段                  | 类型        | 用途                |
|---------------------|-----------|-------------------|
| `currentLyrics`     | `Lyrics`  | 当前歌曲的完整歌词数据       |
| `currentLyricIndex` | `Int`     | 当前应高亮的行索引（-1 表示无） |
| `currentLyricLine`  | `String?` | 当前行文本（便于底部栏等简要展示） |
| `currentPositionMs` | `Long`    | 当前播放位置            |
| `isPlaying`         | `Boolean` | 控制轮询是否运行          |

---

## 7. 精度与局限

### 7.1 同步精度

| 因素           | 影响                 |
|--------------|--------------------|
| 250ms 轮询间隔   | 歌词切换最多约 1/4 秒视觉延迟  |
| LRC 打轴质量     | 时间戳不准则「词不对板」，与代码无关 |
| ExoPlayer 缓冲 | seek 后首帧定位可能有微小偏差  |

### 7.2 功能局限

| 局限       | 说明                         |
|----------|----------------------------|
| 仅逐行同步    | 不支持逐字滚动（KRC / 增强 LRC 逐字标签） |
| 依赖本地 LRC | 不支持在线歌词自动下载                |
| ID3 内嵌歌词 | 无时间轴，只能静态展示                |
| 无自动打轴    | 不会分析音频自动生成时间戳              |

### 7.3 前提条件

歌词能同步显示的前提：

1. 存在带 `[mm:ss.xx]` 时间轴的 `.lrc` 文件
2. LRC 时间轴与音频对齐（制作时打准）
3. LRC 已被 App 成功加载（参见 [lyrics-loading-saf.md](./lyrics-loading-saf.md)）

---

## 8. 关键源码索引

| 模块     | 文件                                  | 职责                              |
|--------|-------------------------------------|---------------------------------|
| LRC 解析 | `data/lyrics/LrcParser.kt`          | 文本 → 时间戳列表                      |
| 同步算法   | `data/lyrics/LyricsModels.kt`       | `indexAt()` / `lineAt()`        |
| 歌词加载   | `data/lyrics/LyricsLoader.kt`       | 读取 .lrc 文件                      |
| 播放进度   | `player/MusicPlayerManager.kt`      | ExoPlayer → `currentPositionMs` |
| 同步调度   | `ui/playlist/PlaylistViewModel.kt`  | 轮询 + 事件 + seek 处理               |
| UI 展示  | `ui/components/ScrollableLyrics.kt` | 高亮 + 自动滚动                       |
| 全屏播放   | `ui/nowplaying/NowPlayingScreen.kt` | 集成 ScrollableLyrics             |
| UI 状态  | `model/PlaylistUiState.kt`          | 状态字段定义                          |

---

## 9. 调试

Debug 构建下可通过 Logcat 观察歌词加载与同步：

```bash
adb logcat -s KinnyLyrics
```

关注以下日志：

- `PlaylistViewModel.loadLyricsForSong.done` — 歌词是否加载成功、`lines` 数量
- `LrcParser.parse` — 解析出多少行、是否有跳过的时间戳
- 播放过程中 `currentLyricIndex` 随进度变化（可在 IDE Layout Inspector 或自行加日志）

---

## 10. 修订历史

| 日期         | 说明                          |
|------------|-----------------------------|
| 2026-08-05 | 初版：LRC 时间轴同步原理与 Kinny 听实现说明 |
