package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Search
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

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
        var selectedSourceId by remember { mutableStateOf<Long?>(null) }
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(selectorSources) {
            if (!reorderableState.isAnyItemDragging) {
                reorderableSources.clear()
                reorderableSources.addAll(selectorSources)
            }
            if (selectedSourceId !in selectorSources.map { it.id }) {
                selectedSourceId = selectorSources.firstOrNull()?.id
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
                                        onClick = { selectedSourceId = source.id },
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
        SourceIcon(
            source = source,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
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
        create(sourceId = sourceId, listingQuery = null)
    }
    
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val source = state.source
    if (source == null) {
        LoadingScreen()
        return
    }
    
    Column(modifier = modifier) {
        // Listing tabs (Popular, Latest)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.listing == eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing.Popular,
                onClick = { 
                    viewModel.setListing(eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing.Popular)
                },
                label = { Text(stringResource(MR.strings.popular)) },
            )
            
            if (source.supportsLatest) {
                FilterChip(
                    selected = state.listing == eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing.Latest,
                    onClick = { 
                        viewModel.setListing(eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing.Latest)
                    },
                    label = { Text(stringResource(MR.strings.latest)) },
                )
            }
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
        create(sourceId = sourceId, listingQuery = null)
    }
    
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val source = state.source
    if (source == null) {
        LoadingScreen()
        return
    }
    
    Column(modifier = modifier) {
        // Listing tabs (Popular, Latest)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.listing == eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing.Popular,
                onClick = { 
                    viewModel.setListing(eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing.Popular)
                },
                label = { Text(stringResource(MR.strings.popular)) },
            )
            
            if (source.supportsLatest) {
                FilterChip(
                    selected = state.listing == eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing.Latest,
                    onClick = { 
                        viewModel.setListing(eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing.Latest)
                    },
                    label = { Text(stringResource(MR.strings.latest)) },
                )
            }
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

