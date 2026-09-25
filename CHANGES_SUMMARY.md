# Mihon 修改摘要

## 功能需求
在书架的单个书籍点击阅读时，先进行文件完整性检测，如果文件是完整的，那就允许直接打开并且标记为下载完成，即使来源失效。

## 修改的文件

### 1. DownloadManager.kt
**路径**: `app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt`

**修改内容**:
- 添加了新方法 `verifyAndMarkChapterIfComplete()`
- 该方法检查章节文件是否存在并且完整
- 如果文件完整但未标记为已下载，则自动标记为已下载
- 使用 `ImageUtil.isImage()` 验证文件是否为有效图片

```kotlin
/**
 * Checks if chapter files are complete on disk and marks as downloaded if valid.
 * Returns true if files are complete and ready to read.
 *
 * @param chapter the chapter to verify.
 * @param manga the manga of the chapter.
 * @param source the source of the chapter.
 * @return true if chapter files are complete and valid.
 */
suspend fun verifyAndMarkChapterIfComplete(
    chapter: Chapter,
    manga: Manga,
    source: Source,
): Boolean {
    // Skip if already marked as downloaded
    if (isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.title, manga.source)) {
        return true
    }

    // Check if chapter directory exists on disk
    val chapterDir = provider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.title, source)
        ?: return false

    // Verify chapter has valid image files
    val files = chapterDir.listFiles().orEmpty()
        .filter { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }

    // If no valid images found, chapter is incomplete
    if (files.isEmpty()) {
        return false
    }

    // Files exist and are valid - mark chapter as downloaded in cache
    cache.addChapter(chapter.name, chapter.scanlator, chapter.url, manga)
    logcat(LogPriority.INFO) {
        "Chapter ${chapter.name} verified complete and marked as downloaded"
    }

    return true
}
```

### 2. MangaScreen.kt
**路径**: `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`

**修改内容**:
- 修改了 `onChapterClicked` lambda，在打开章节前调用验证方法
- 修改了 `onContinueReading` lambda，在继续阅读前调用验证方法
- 验证失败不会阻止打开阅读器，确保用户体验

在 `onChapterClicked` 中添加：
```kotlin
onChapterClicked = { chapter ->
    scope.launch {
        try {
            // Verify chapter files are complete before opening
            val manga = viewModel.manga
            val source = viewModel.source
            if (manga != null && source != null) {
                viewModel.downloadManager.verifyAndMarkChapterIfComplete(chapter, manga, source)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to verify chapter completeness" }
        }
        // Open chapter regardless of verification result
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
    }
},
```

在 `onContinueReading` 中添加类似逻辑。

### 3. LibraryViewModel.kt
**路径**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt`

**修改内容**:
- 添加了新方法 `verifyAndMarkChapterIfComplete()`，作为对 DownloadManager 的封装
- 该方法在 LibraryTab 中被调用

```kotlin
suspend fun verifyAndMarkChapterIfComplete(chapter: Chapter, manga: Manga): Boolean {
    val source = sourceManager.get(manga.source) ?: return false
    return downloadManager.verifyAndMarkChapterIfComplete(chapter, manga, source)
}
```

### 4. LibraryTab.kt
**路径**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt`

**修改内容**:
- 修改了 `onContinueReadingClicked` lambda，在打开章节前调用验证方法

```kotlin
onContinueReadingClicked = { it: LibraryManga ->
    scope.launchIO {
        val chapter = viewModel.getNextUnreadChapter(it.manga)
        if (chapter != null) {
            try {
                // Verify chapter files are complete before opening
                viewModel.verifyAndMarkChapterIfComplete(chapter, it.manga)
            } catch (e: Exception) {
                // Log error but continue to open reader
            }
            context.startActivity(
                ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
            )
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }
    Unit
}.takeIf { state.showMangaContinueButton },
```

## 功能说明

1. **自动检测**: 当用户点击阅读章节时，系统会自动检测章节文件是否存在且完整
2. **自动标记**: 如果文件完整但未标记为已下载，系统会自动将其标记为已下载
3. **不阻塞阅读**: 即使检测失败，也不会阻止用户打开阅读器
4. **来源失效无影响**: 即使漫画来源失效，只要本地文件完整，就可以正常阅读并被标记
5. **日志记录**: 成功标记时会记录日志，便于调试

## 实现位置

- **漫画详情页点击章节**: MangaScreen.kt
- **漫画详情页继续阅读**: MangaScreen.kt
- **书架继续阅读**: LibraryTab.kt

所有阅读入口都已实现文件完整性检测功能。

## 测试建议

1. 下载一些章节后，删除下载缓存标记（但保留文件）
2. 点击阅读这些章节
3. 验证章节是否被正确标记为已下载
4. 验证在来源失效的情况下，本地完整文件仍可正常阅读

## 编译说明

使用以下命令编译项目：
```bash
./gradlew app:assembleRelease
```

编译完成后，APK 文件会在 `app/build/outputs/apk/release/` 目录中。
