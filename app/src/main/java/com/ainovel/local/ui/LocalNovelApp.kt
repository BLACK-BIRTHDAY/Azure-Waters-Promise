package com.ainovel.local.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PsychologyAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainovel.local.ui.screens.AssistantScreen
import com.ainovel.local.ui.screens.ContextScreen
import com.ainovel.local.ui.screens.HistoryScreen
import com.ainovel.local.ui.screens.LibraryScreen
import com.ainovel.local.ui.screens.PreparedRequestSheet
import com.ainovel.local.ui.screens.SettingsScreen
import com.ainovel.local.ui.screens.WorkspaceScreen
import com.ainovel.local.ui.localization.AppLanguage
import com.ainovel.local.ui.localization.AppLanguageRuntime
import com.ainovel.local.ui.localization.AppLanguageStore
import com.ainovel.local.ui.localization.LocalAppLanguage
import com.ainovel.local.ui.localization.Text
import com.ainovel.local.ui.localization.localized

private data class DestinationItem(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector,
)

private data class AppSurface(
    val novelId: String?,
    val destination: AppDestination,
)

private val mainDestinations = listOf(
    DestinationItem(AppDestination.WORKSPACE, "写作", Icons.Outlined.AutoStories),
    DestinationItem(AppDestination.CONTEXT, "记忆", Icons.Outlined.Tune),
    DestinationItem(AppDestination.ASSISTANT, "灵感", Icons.Outlined.PsychologyAlt),
    DestinationItem(AppDestination.HISTORY, "足迹", Icons.Outlined.History),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocalNovelApp(viewModel: AppViewModel) {
    val context = LocalContext.current.applicationContext
    var selectedLanguage by remember { mutableStateOf(AppLanguageStore.read(context)) }

    if (selectedLanguage == null) {
        LanguageSelectionScreen { language ->
            AppLanguageRuntime.current = language
            AppLanguageStore.save(context, language)
            selectedLanguage = language
        }
        return
    }

    val language = requireNotNull(selectedLanguage)
    AppLanguageRuntime.current = language
    CompositionLocalProvider(LocalAppLanguage provides language) {
        LocalNovelContent(
            viewModel = viewModel,
            language = language,
            onLanguageChange = { newLanguage ->
                AppLanguageRuntime.current = newLanguage
                AppLanguageStore.save(context, newLanguage)
                selectedLanguage = newLanguage
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LocalNovelContent(
    viewModel: AppViewModel,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    val prepared by viewModel.preparedRequest.collectAsStateWithLifecycle()
    val candidatePanelExpanded by viewModel.candidatePanelExpanded.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val windowSize = LocalWindowInfo.current.containerSize
    val windowWidth = with(density) { windowSize.width.toDp() }
    val isExpanded = windowWidth >= 840.dp
    val imeVisible = WindowInsets.isImeVisible
    val surface = AppSurface(workspace.novel?.id, destination)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    is UiMessage.Info -> localized(message.text, language)
                    is UiMessage.Error -> localized(message.text, language)
                },
            )
        }
    }

    LaunchedEffect(destination) {
        if (destination != AppDestination.WORKSPACE) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    BackHandler(
        enabled = prepared != null ||
            (destination == AppDestination.WORKSPACE && workspace.candidates.isNotEmpty() && candidatePanelExpanded) ||
            destination == AppDestination.SETTINGS ||
            workspace.novel != null,
    ) {
        when {
            prepared != null -> viewModel.closePreparedRequest()
            destination == AppDestination.SETTINGS -> viewModel.closeSettings()
            destination == AppDestination.WORKSPACE && workspace.candidates.isNotEmpty() && candidatePanelExpanded -> viewModel.hideCandidatePanel()
            workspace.novel != null -> viewModel.returnToLibrary()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (workspace.novel != null || destination == AppDestination.SETTINGS) {
                        IconButton(
                            onClick = {
                                if (destination == AppDestination.SETTINGS) {
                                    viewModel.closeSettings()
                                } else {
                                    viewModel.returnToLibrary()
                                }
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = if (destination == AppDestination.SETTINGS) "返回上一页" else "返回作品列表",
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = workspace.novel?.name ?: "绀海之约",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (workspace.novel != null) {
                            Text(
                                text = destinationLabel(destination),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openDestination(AppDestination.SETTINGS) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        bottomBar = {
            if (!isExpanded && !imeVisible && workspace.novel != null && destination != AppDestination.SETTINGS) {
                NavigationBar {
                    mainDestinations.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item.destination,
                            onClick = { viewModel.openDestination(item.destination) },
                            icon = { Icon(item.icon, contentDescription = localized(item.label, language)) },
                            label = { Text(localized(item.label, language)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (isExpanded && workspace.novel != null && destination != AppDestination.SETTINGS) {
                NavigationRail(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    mainDestinations.forEach { item ->
                        NavigationRailItem(
                            selected = destination == item.destination,
                            onClick = { viewModel.openDestination(item.destination) },
                            icon = { Icon(item.icon, contentDescription = localized(item.label, language)) },
                            label = { Text(localized(item.label, language)) },
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (workspace.novel != null && destination == AppDestination.WORKSPACE) Modifier.imePadding() else Modifier),
            ) {
                AnimatedContent(
                    targetState = surface,
                    transitionSpec = {
                        when {
                            initialState.novelId == null && targetState.novelId != null -> {
                                (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(tween(150)) { -it / 10 } + fadeOut(tween(140)))
                            }
                            initialState.novelId != null && targetState.novelId == null -> {
                                (slideInHorizontally(tween(230)) { -it / 5 } + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(150)) { it / 10 } + fadeOut(tween(140)))
                            }
                            else -> fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "app-surface-transition",
                ) { target ->
                    if (target.novelId == null && target.destination != AppDestination.SETTINGS) {
                        LibraryScreen(
                            novels = novels,
                            onCreate = viewModel::createNovel,
                            onSelect = viewModel::selectNovel,
                            coverFile = viewModel::coverFile,
                            onDecodeCover = viewModel::decodeCoverForEditing,
                            onSaveCover = { novelId, bitmap, onSaved -> viewModel.saveNovelCover(novelId, bitmap, onSaved) },
                        )
                    } else {
                        when (target.destination) {
                            AppDestination.WORKSPACE -> WorkspaceScreen(viewModel, imeVisible)
                            AppDestination.CONTEXT -> ContextScreen(viewModel)
                            AppDestination.ASSISTANT -> AssistantScreen(viewModel)
                            AppDestination.HISTORY -> HistoryScreen(viewModel)
                            AppDestination.SETTINGS -> SettingsScreen(viewModel, language, onLanguageChange)
                        }
                    }
                }
            }
        }
    }

    if (prepared != null) {
        PreparedRequestSheet(
            prepared = requireNotNull(prepared),
            onDismiss = viewModel::closePreparedRequest,
            onSend = viewModel::sendPreparedRequest,
        )
    }
}

@Composable
private fun LanguageSelectionScreen(onSelect: (AppLanguage) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text("选择语言 / Choose your language", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "选择后仍可在设置中随时更改 / You can change this anytime in Settings",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { onSelect(AppLanguage.SIMPLIFIED_CHINESE) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            ) {
                androidx.compose.material3.Text("简体中文")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onSelect(AppLanguage.ENGLISH) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            ) {
                androidx.compose.material3.Text("English")
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun destinationLabel(destination: AppDestination): String = localized(when (destination) {
    AppDestination.WORKSPACE -> "故事正在生长"
    AppDestination.CONTEXT -> "故事记忆"
    AppDestination.ASSISTANT -> "灵感对谈"
    AppDestination.HISTORY -> "创作足迹"
    AppDestination.SETTINGS -> "设置"
})
