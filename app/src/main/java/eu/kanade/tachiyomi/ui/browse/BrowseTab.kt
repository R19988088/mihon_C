package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.SourcesViewModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.rounded.ExpandMore
import mihon.icons.materialsymbols.rounded.Search
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Serializable
data class BrowseTag(
    val key: String,
    val name: String,
)

@Serializable
data class BrowseTagGroup(
    val namespace: String,
    val tags: List<BrowseTag>,
)

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalSearchScreen())
    }

    private val switchToExtensionTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToExtensionTabChannel.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        
        val sourcePreferences = remember { context.appGraph.sourcePreferences }
        val sourcesViewModel = metroViewModel<SourcesViewModel>()
        val sourcesState by sourcesViewModel.state.collectAsStateWithLifecycle()
        val selectorSources by sourcesViewModel.selectorSources.collectAsStateWithLifecycle()
        val reorderableSources = remember { selectorSources.toMutableStateList() }
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState, PaddingValues()) { from, to ->
            val item = reorderableSources.removeAt(from.index)
            reorderableSources.add(to.index, item)
            sourcesViewModel.reorderSources(reorderableSources.map { it.id })
        }
        var selectedSourceId by rememberSaveable {
            mutableStateOf(sourcePreferences.browseSelectedSource.get().takeIf { it >= 0L })
        }
        val snackbarHostState = remember { SnackbarHostState() }
        var showTagDialog by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(selectorSources) {
            if (!reorderableState.isAnyItemDragging) {
                reorderableSources.clear()
                reorderableSources.addAll(selectorSources)
            }
            if (selectorSources.isEmpty()) return@LaunchedEffect

            val sourceIds = selectorSources.map { it.id }
            if (selectedSourceId !in sourceIds) {
                val restoredSourceId = sourcePreferences.browseSelectedSource.get()
                    .takeIf { it in sourceIds }
                val nextSourceId = restoredSourceId ?: sourceIds.first()
                selectedSourceId = nextSourceId
                sourcePreferences.browseSelectedSource.set(nextSourceId)
            }
        }

        Scaffold(
            topBar = {
                Column {
                    AppBar(
                        title = stringResource(MR.strings.browse),
                        actions = {
                            AppBarActions(
                                actions = listOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_search),
                                        icon = MaterialSymbols.Rounded.Search,
                                        onClick = { navigator.push(GlobalSearchScreen()) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.label_extensions),
                                        onClick = { },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.label_migration),
                                        onClick = { },
                                    ),
                                ),
                            )
                        },
                    )

                    if (!sourcesState.isLoading && reorderableSources.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 8.dp),
                            state = lazyListState,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            items(
                                items = reorderableSources,
                                key = { it.id },
                            ) { source ->
                                ReorderableItem(reorderableState, key = source.id) {
                                    SourceLogoItem(
                                        source = source,
                                        isSelected = source.id == selectedSourceId,
                                        dragModifier = Modifier.longPressDraggableHandle(),
                                        onClick = {
                                            selectedSourceId = source.id
                                            sourcePreferences.browseSelectedSource.set(source.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            if (sourcesState.isLoading) {
                LoadingScreen()
            } else if (selectedSourceId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    key(selectedSourceId) {
                        BrowseSourceContentWithMap(
                            sourceId = selectedSourceId!!,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

@Composable
private fun SourceLogoItem(
    source: Source,
    isSelected: Boolean,
    dragModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()

    Row(
        modifier = dragModifier
            .height(50.dp)
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(28.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (isSelected) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SourceIcon(source = source, circular = true)
        }
        if (isSelected) {
            Text(
                text = source.name,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 160.dp),
                color = if (isDarkTheme) Color.Black else Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun quoteBrowseTag(value: String): String {
    return "\"${value.trim().replace("\"", "\\\\\"")}\""
}

@Composable
private fun BrowseTagDialog(
    history: Set<String>,
    initialSelected: Set<String>,
    onDismissRequest: () -> Unit,
    onApply: (List<String>) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var allTags by remember { mutableStateOf<List<BrowseTagGroup>>(emptyList()) }
    var input by rememberSaveable { mutableStateOf("") }
    var languageMode by rememberSaveable { mutableStateOf("en") }
    val selected = remember(initialSelected) { initialSelected.toMutableStateList() }

    LaunchedEffect(Unit) {
        allTags = withContext(Dispatchers.IO) {
            context.assets.open("ehtag_tags.json").bufferedReader().use { reader ->
                Json.decodeFromString<List<BrowseTagGroup>>(reader.readText())
            }
        }
    }

    val query = input.trim()
    val tagByKey = remember(allTags) { allTags.flatMap { it.tags }.associateBy { it.key } }
    val matches = remember(allTags, query, languageMode) {
        if (query.isEmpty()) {
            emptyList()
        } else {
            allTags.flatMap { group ->
                group.tags.filter { tag ->
                    when (languageMode) {
                        "zh" -> tag.name.contains(query, true)
                        "ja" -> tag.key.contains(query, true)
                        else -> tag.key.contains(query, true)
                    }
                }.take(100).map { group.namespace to it }
            }.take(200)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("分类 / 标签") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "English", "zh" to "中文", "ja" to "日本語").forEach { (mode, label) ->
                        FilterChip(
                            selected = languageMode == mode,
                            onClick = { languageMode = mode },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("输入${if (languageMode == "en") "英文" else if (languageMode == "zh") "中文" else "日文"}标签") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                if (selected.isNotEmpty()) {
                    Text(
                        text = "已选：${selected.joinToString(", ")}",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    if (query.isEmpty() && history.isNotEmpty()) {
                        items(history.toList(), key = { "history-$it" }) { tag ->
                            TagHistoryRow(
                                tag = tag,
                                selected = tag in selected,
                                onClick = {
                                    if (tag in selected) selected.remove(tag) else selected.add(tag)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDelete(tag)
                                    selected.remove(tag)
                                },
                            )
                        }
                    }
                    if (query.isNotEmpty()) {
                        items(matches, key = { "${it.first}:${it.second.key}" }) { (namespace, tag) ->
                        TagHistoryRow(
                            tag = "${tag.name} · ${tag.key}",
                            selected = tag.key in selected,
                            onClick = {
                                if (tag.key in selected) selected.remove(tag.key) else selected.add(tag.key)
                            },
                            onLongClick = {},
                        )
                    }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawTags = buildList {
                        addAll(selected)
                        if (query.isNotEmpty()) add(query)
                    }.distinct()
                    val outputTags = rawTags.map { raw ->
                        val matched = tagByKey[raw] ?: allTags.asSequence()
                            .flatMap { it.tags.asSequence() }
                            .firstOrNull { it.name.equals(raw, true) || it.key.equals(raw, true) }
                        when (languageMode) {
                            "zh" -> matched?.name ?: raw
                            "ja" -> matched?.key ?: raw
                            else -> matched?.key ?: raw
                        }
                    }.distinct()
                    onApply(outputTags)
                },
            ) { Text("应用") }
        },
        dismissButton = {
            Button(onClick = onDismissRequest) { Text("取消") }
        },
    )
}

@Composable
private fun TagHistoryRow(
    tag: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = selected, onClick = onClick, label = { Text(tag) })
    }
}

@Composable
private fun BrowseListingFilterRow(
    listing: BrowseSourceViewModel.Listing,
    supportsLatest: Boolean,
    activeTags: Set<String>,
    onListingSelected: (BrowseSourceViewModel.Listing) -> Unit,
    onTagsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = listing == BrowseSourceViewModel.Listing.Popular,
            onClick = { onListingSelected(BrowseSourceViewModel.Listing.Popular) },
            label = { Text(stringResource(MR.strings.popular)) },
        )
        if (supportsLatest) {
            FilterChip(
                selected = listing == BrowseSourceViewModel.Listing.Latest,
                onClick = { onListingSelected(BrowseSourceViewModel.Listing.Latest) },
                label = { Text(stringResource(MR.strings.latest)) },
            )
        }
        FilterChip(
            modifier = Modifier
                .widthIn(min = 72.dp)
                .weight(1f, fill = true),
            selected = activeTags.isNotEmpty(),
            onClick = onTagsClick,
            label = {
                Text(
                    text = activeTags.joinToString(", ").ifEmpty { "-" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = MaterialSymbols.Rounded.ExpandMore,
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
private fun BrowseSourceContentWithMap(
    sourceId: Long,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    
    val viewModel = assistedMetroViewModel<BrowseSourceViewModel, BrowseSourceViewModel.Factory>(
        key = "BrowseSourceViewModel:$sourceId",
    ) {
        create(sourceId = sourceId, listingQuery = BrowseSourceViewModel.Listing.Popular.query)
    }
    
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val source = state.source
    if (source == null) {
        LoadingScreen()
        return
    }
    
    Column(modifier = modifier) {
        var showTagDialog by rememberSaveable(sourceId) { mutableStateOf(false) }
        val tagHistory by viewModel.browseTagHistory.collectAsStateWithLifecycle()
        val activeTags by viewModel.activeBrowseTags.collectAsStateWithLifecycle()
        BrowseListingFilterRow(
            listing = state.listing,
            supportsLatest = source.supportsLatest,
            activeTags = activeTags,
            onListingSelected = viewModel::setListing,
            onTagsClick = { showTagDialog = true },
        )
        if (showTagDialog) {
            BrowseTagDialog(
                history = tagHistory,
                initialSelected = activeTags,
                onDismissRequest = { showTagDialog = false },
                onApply = { tags ->
                    viewModel.applyBrowseTags(tags.toSet())
                    tags.forEach(viewModel::addBrowseTagHistory)
                    showTagDialog = false
                },
                onDelete = viewModel::removeBrowseTagHistory,
            )
        }

        HorizontalDivider()

        // Display source content
        BrowseSourceContent(
            source = source,
            mangaList = viewModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
            columns = viewModel.getColumnsPreference(configuration.orientation),
            displayMode = viewModel.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = PaddingValues(0.dp),
            onWebViewClick = {},
            onHelpClick = {},
            onLocalSourceHelpClick = {},
            onMangaClick = { manga ->
                navigator.push(MangaScreen(manga.id, true))
            },
            onMangaLongClick = { manga ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }
}

@Composable
private fun BrowseSourceContentForSource(
    sourceId: Long,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    
    // Create ViewModel - this will only be created once per sourceId
    val viewModel = assistedMetroViewModel<BrowseSourceViewModel, BrowseSourceViewModel.Factory> {
        create(sourceId = sourceId, listingQuery = BrowseSourceViewModel.Listing.Popular.query)
    }
    
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val source = state.source
    if (source == null) {
        LoadingScreen()
        return
    }
    
    Column(modifier = modifier) {
        var showTagDialog by rememberSaveable(sourceId) { mutableStateOf(false) }
        val tagHistory by viewModel.browseTagHistory.collectAsStateWithLifecycle()
        val activeTags by viewModel.activeBrowseTags.collectAsStateWithLifecycle()
        BrowseListingFilterRow(
            listing = state.listing,
            supportsLatest = source.supportsLatest,
            activeTags = activeTags,
            onListingSelected = viewModel::setListing,
            onTagsClick = { showTagDialog = true },
        )
        if (showTagDialog) {
            BrowseTagDialog(
                history = tagHistory,
                initialSelected = activeTags,
                onDismissRequest = { showTagDialog = false },
                onApply = { tags ->
                    viewModel.applyBrowseTags(tags.toSet())
                    tags.forEach(viewModel::addBrowseTagHistory)
                    showTagDialog = false
                },
                onDelete = viewModel::removeBrowseTagHistory,
            )
        }

        HorizontalDivider()

        // Display source content
        BrowseSourceContent(
            source = source,
            mangaList = viewModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
            columns = viewModel.getColumnsPreference(configuration.orientation),
            displayMode = viewModel.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = PaddingValues(0.dp),
            onWebViewClick = {},
            onHelpClick = {},
            onLocalSourceHelpClick = {},
            onMangaClick = { manga ->
                navigator.push(MangaScreen(manga.id, true))
            },
            onMangaLongClick = { manga ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }
}

