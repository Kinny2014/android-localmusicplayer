# Kinny 听 · 本地 LRC 歌词加载技术文档

本文档记录 Kinny 听（`com.kinny.localmusicplayer`）在 SAF 目录导入场景下，同目录 `.lrc` 歌词无法显示的问题分析、根因与最终解决方案。

---

## 1. 背景

Kinny 听通过 **Storage Access Framework（SAF）** 导入本地 MP3，并在播放时加载同目录同名 `.lrc` 歌词文件，实现时间轴同步滚动。

典型目录结构：

```
Download/kinny_music/
├── Sia - Rainbow.mp3
├── Sia - Rainbow.lrc
├── 刀郎 - 花妖.mp3
└── 花妖.lrc
```

用户操作：在 App 内点击「导入文件夹」，选择 `kinny_music` 目录。

---

## 2. 歌词加载架构

```
导入阶段                          播放阶段
─────────                        ─────────
LocalMusicScanner.scanFolder     PlaylistViewModel.loadLyricsForSong()
        │                                    │
        ▼                                    ▼
配对 MP3 ↔ LRC，写入 Song.lrcUri    LyricsLoader.loadLyrics()
        │                                    │
        ▼                                    ├─ 1. 已知 lrcUri 直接读取
Song.sourceTreeUri 持久化                     ├─ 2. SafSiblingFinder 运行时查找
                                             ├─ 3. ID3 USLT 内嵌歌词
                                             └─ 4. 无歌词
                                                    │
                                                    ▼
                                             LrcParser.parse() → UI 高亮滚动
```

### 2.1 歌词匹配规则

| 优先级 | 来源                   | 是否支持时间轴同步 |
|-----|----------------------|-----------|
| 1   | 导入时配对的 `Song.lrcUri` | ✅         |
| 2   | 运行时同目录 `.lrc` 查找     | ✅         |
| 3   | MP3 ID3 USLT 内嵌      | ❌（纯文本）    |

文件名匹配候选（忽略大小写）：

- MP3 完整主文件名，如 `刀郎 - 花妖`
- 去掉序号前缀，如 `01 - 花妖` → `花妖`
- 从 `艺术家 - 歌名` 提取歌名，如 `刀郎 - 花妖` → `花妖`
- ID3 标题（如 `花妖`）

---

## 3. 问题现象

### 3.1 现象描述

- MP3 可正常导入和播放
- 全屏播放页显示「暂无歌词」
- Logcat 标签 `KinnyLyrics` 出现 `LyricsLoader.loadLyrics.failed`

### 3.2 典型日志（修复前）

```
PlaylistViewModel.loadLyricsForSong.start | title=花妖, lrcUri=null, sourceTreeUri=null
LyricsLoader.loadFromLrcFile | displayName=刀郎 - 花妖.mp3, baseName=刀郎 - 花妖
SafSiblingFinder.DocumentsContract | invalid parentId, documentId=msf:1000106457, parentId=
SafSiblingFinder.DocumentFile | parentFile is null
LyricsLoader.loadLyrics.failed | no lyrics found
```

---

## 4. 根因分析

### 4.1 根因一：Downloads Provider 使用 opaque Document ID

文件位于 `Download/` 目录时，Android 常通过以下 Authority 暴露文档：

```
com.android.providers.downloads.documents
```

此时 Document ID 为 **opaque 形式**，例如：

| 类型         | 示例               |
|------------|------------------|
| 目录（tree 根） | `msd:1000106454` |
| 文件         | `msf:1000106457` |

**而非** External Storage 的路径型 ID：

```
primary:Download/kinny_music/刀郎 - 花妖.mp3
```

旧代码通过 `documentId.substringBeforeLast('/')` 推导父目录：

```kotlin
// 对 msf:1000106457 执行后 parentId 为空字符串 → 查找失败
val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
```

同时 `DocumentFile.fromSingleUri().parentFile` 在 tree/document Uri 上通常返回 `null`。

**结论：** 路径型父目录推导对 Downloads Provider **完全无效**。

### 4.2 根因二：导入数据未携带歌词 Uri

日志中 `lrcUri=null, sourceTreeUri=null` 表明：

- 播放列表来自**旧版导入数据**（修复前写入 DataStore）
- 或重新导入时因去重逻辑未更新已有歌曲的 `lrcUri`

### 4.3 根因三：文件名不完全一致

| MP3 文件名             | LRC 文件名             | 旧逻辑   |
|---------------------|---------------------|-------|
| `刀郎 - 花妖.mp3`       | `花妖.lrc`            | ❌ 不匹配 |
| `Sia - Rainbow.mp3` | `Sia - Rainbow.lrc` | ✅ 匹配  |

旧逻辑仅比较完整主文件名，未从 `艺术家 - 歌名` 格式中提取歌名。

### 4.4 根因四：LRC 编码与格式

部分 `.lrc` 使用 GBK/GB18030 编码，或包含 `[ti:]` 元数据行、无标准 `[mm:ss.xx]` 时间轴，会导致 `LrcParser.parse()` 返回空列表。

---

## 5. 解决方案

### 5.1 导入阶段配对（主路径）

`LocalMusicScanner.scanFolder()` 递归收集目录内**所有文件**（含 `.lrc`），在同一 `parentDocumentId` 下建立索引并配对：

```kotlin
// opaque ID 场景：parentDocumentId 回退为当前 walk 的目录 documentId
parentDocumentId = childDocId.substringBeforeLast('/', missingDelimiterValue = documentId)
```

配对结果写入：

```kotlin
data class Song(
    val lrcUri: String? = null,        // 配对到的 .lrc Uri
    val sourceTreeUri: String? = null, // 用户授权的 SAF tree Uri
)
```

### 5.2 Downloads Provider 运行时回退

新增 `SafDocumentHelper.parentFolderIds()`：当路径型 parent 无效时，使用 **tree 根目录 ID** 列出同级文件：

```kotlin
fun parentFolderIds(context: Context, songUri: Uri): List<String> {
    val documentId = DocumentsContract.getDocumentId(songUri)
    val treeDocumentId = DocumentsContract.getTreeDocumentId(songUri)
    return buildList {
        val pathParent = documentId.substringBeforeLast('/', missingDelimiterValue = "")
        if (pathParent.isNotEmpty() && pathParent != documentId) add(pathParent)
        if (treeDocumentId.isNotBlank() && treeDocumentId != documentId) add(treeDocumentId)
    }.distinct()
}
```

### 5.3 自动推断 sourceTreeUri

从 `song.uri`（tree/document 形式）还原 tree Uri，兼容旧数据：

```kotlin
DocumentsContract.buildTreeDocumentUri(
    uri.authority,
    DocumentsContract.getTreeDocumentId(uri),
)
```

### 5.4 增强文件名匹配

`SafDocumentHelper.lrcNameCandidates()` 生成多种候选名，支持 `艺术家 - 歌名` ↔ `歌名.lrc`。

### 5.5 重新导入时更新已有歌曲

`PlaylistRepository.addSongs()` 对已存在的 `Song` 更新 `lrcUri` / `sourceTreeUri`，而非跳过。

### 5.6 LRC 解析增强

- UTF-8 BOM 去除
- GBK / GB18030 编码回退
- 跳过 `[ti:]`、`[ar:]` 等元数据行
- 支持 `[hh:mm:ss.xx]` 格式

---

## 6. 关键源码位置

| 模块     | 文件                                 | 职责                      |
|--------|------------------------------------|-------------------------|
| 扫描配对   | `data/LocalMusicScanner.kt`        | 目录导入、MP3/LRC 配对         |
| SAF 工具 | `data/lyrics/SafDocumentHelper.kt` | tree Uri 解析、parent 推断   |
| 兄弟查找   | `data/lyrics/SafSiblingFinder.kt`  | 运行时同目录 .lrc 查找          |
| 歌词加载   | `data/lyrics/LyricsLoader.kt`      | 读取、编码检测、加载编排            |
| LRC 解析 | `data/lyrics/LrcParser.kt`         | 时间轴解析                   |
| UI 同步  | `ui/playlist/PlaylistViewModel.kt` | 播放进度驱动歌词索引              |
| 调试日志   | `data/lyrics/LyricsDebugLog.kt`    | Logcat 标签 `KinnyLyrics` |

---

## 7. 调试指南

### 7.1 开启日志

日志仅在 **Debug 构建**下输出：

```bash
adb logcat -s KinnyLyrics
```

### 7.2 正常流程日志示例

```
LocalMusicScanner.scanFolder.scanned | totalFiles=4, mp3=2, lrc=2
LocalMusicScanner.matchLrc | mp3=刀郎 - 花妖.mp3, matched=花妖.lrc
PlaylistViewModel.loadLyricsForSong.start | lrcUri=content://..., sourceTreeUri=content://...
LyricsLoader.loadLyrics.success | source=LRC_FILE, lines=42, synced=true
```

### 7.3 常见问题对照

| 日志特征                     | 可能原因                    | 处理建议                         |
|--------------------------|-------------------------|------------------------------|
| `lrc=0`                  | 目录内无 .lrc 或未扫到          | 确认 LRC 与 MP3 同目录，重新导入        |
| `matched=NONE`           | 文件名不匹配                  | 对齐主文件名或使用 `歌名.lrc`           |
| `invalid parentId`       | Downloads Provider（已修复） | 升级至含 `SafDocumentHelper` 的版本 |
| `parse returned 0 lines` | LRC 无时间轴或编码异常           | 检查 LRC 是否含 `[00:00.00]` 行    |
| `lrcUri=null`            | 旧播放列表数据                 | 重新导入文件夹                      |

---

## 8. 用户使用建议

1. 使用「**导入文件夹**」选择 `kinny_music`，不要单选 MP3 文件
2. MP3 与 LRC **放在同一目录**
3. 优先保证主文件名一致；或使用 `歌名.lrc` 配 `艺术家 - 歌名.mp3`
4. LRC 文件需包含标准时间轴行，例如：

   ```lrc
   [00:12.50]第一句歌词
   [00:17.30]第二句歌词
   ```

5. 修改 LRC 或升级 App 后，**重新导入**对应文件夹

---

## 9. 已知限制

- 仅支持本地 `.lrc` 与 ID3 USLT，不支持在线歌词
- 歌词同步精度依赖 LRC 制作质量与 250ms 进度轮询
- 逐行同步，不支持逐字 KRC 格式
- 单文件导入（非文件夹）无法读取同目录兄弟 `.lrc`

---

## 10. 修订历史

| 日期         | 说明                                           |
|------------|----------------------------------------------|
| 2026-08-05 | 初版：记录 Downloads Provider opaque ID 问题及完整修复方案 |
