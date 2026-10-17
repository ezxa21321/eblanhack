package com.airware.loader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.airware.loader.ui.*
import com.airware.loader.ui.theme.AirwareLoaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TARGET_PACKAGE = "com.axlebolt.standoff2"

private val StatusGreen = Color(0xFF3FD07A)
private val StatusRed = Color(0xFFFF6B6B)

enum class LoaderStatus { IDLE, CHECKING, INJECTING, SUCCESS, ERROR }

enum class Tab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Главная", Icons.Filled.Home, Icons.Outlined.Home),
    FEED("Лента", Icons.Filled.List, Icons.Outlined.List),
    FILES("Файлы", Icons.Filled.Folder, Icons.Outlined.Folder)
}

class MainActivity : ComponentActivity() {

    init {
        System.loadLibrary("airware_injector")
    }

    external fun nativeInject(targetPackage: String, libPath: String): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirwareLoaderTheme(darkTheme = true) {
                LoaderScreen(
                    onInject = { pkg, libPath -> nativeInject(pkg, libPath) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoaderScreen(onInject: (String, String) -> Boolean) {
    var status by remember { mutableStateOf(LoaderStatus.IDLE) }
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var gameRunning by remember { mutableStateOf<Boolean?>(null) }
    var selectedBuild by remember { mutableStateOf<String?>(null) }
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    val log = rememberLogState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        log.info("checking root access...")
        val root = withContext(Dispatchers.IO) { RootShell.hasRoot() }
        hasRoot = root
        if (root) log.success("root access granted") else log.error("root access denied — su not available")

        while (true) {
            val pids = withContext(Dispatchers.IO) { RootShell.pidOf(TARGET_PACKAGE) }
            val running = pids.isNotEmpty()
            if (gameRunning != running) {
                if (running) log.success("Process detected, pid=${pids.first()}")
                else log.warn("Target process is not running")
            }
            gameRunning = running
            delay(2000)
        }
    }

    LaunchedEffect(selectedBuild, gameRunning) {
        if (status == LoaderStatus.SUCCESS || status == LoaderStatus.ERROR) {
            status = LoaderStatus.IDLE
        }
    }

    val canInject = hasRoot == true && gameRunning == true && selectedBuild != null &&
            status == LoaderStatus.IDLE
    val busy = status == LoaderStatus.CHECKING || status == LoaderStatus.INJECTING

    fun doStart() {
        val build = selectedBuild ?: return
        scope.launch {
            status = LoaderStatus.CHECKING
            log.info("re-checking root and process before start...")
            delay(300)

            if (hasRoot != true) {
                log.error("abort: no root access")
                status = LoaderStatus.ERROR
                return@launch
            }
            val pids = withContext(Dispatchers.IO) { RootShell.pidOf(TARGET_PACKAGE) }
            if (pids.isEmpty()) {
                log.error("abort: process not found")
                status = LoaderStatus.ERROR
                return@launch
            }

            status = LoaderStatus.INJECTING
            log.info("starting '$build' on pid=${pids.first()}...")
            val ok = withContext(Dispatchers.IO) { onInject(TARGET_PACKAGE, build) }
            status = if (ok) {
                log.success("start complete")
                LoaderStatus.SUCCESS
            } else {
                log.error("start failed — see logcat for native errors")
                LoaderStatus.ERROR
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentTab.label,
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    AssistChip(
                        onClick = {},
                        label = { Text("Developer") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        border = null,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "tabContent"
                ) { tab ->
                    when (tab) {
                        Tab.HOME -> HomeTab(
                            hasRoot = hasRoot,
                            gameRunning = gameRunning,
                            selectedBuild = selectedBuild?.substringAfterLast('/'),
                            status = status,
                            canInject = canInject,
                            busy = busy,
                            log = log,
                            onStart = { doStart() },
                            onClearLog = { log.entries.clear() }
                        )
                        Tab.FEED -> EmptyFeed()
                        Tab.FILES -> FilesTab(
                            selectedBuild = selectedBuild,
                            onBuildSelected = { selectedBuild = it },
                            onLog = { log.info(it) }
                        )
                    }
                }
            }
            BannerStrip()
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun HomeTab(
    hasRoot: Boolean?,
    gameRunning: Boolean?,
    selectedBuild: String?,
    status: LoaderStatus,
    canInject: Boolean,
    busy: Boolean,
    log: LogState,
    onStart: () -> Unit,
    onClearLog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusCard(
            icon = Icons.Filled.Security,
            title = "Root-доступ",
            subtitle = when (hasRoot) {
                null -> "Проверка..."
                true -> "Предоставлен"
                false -> "Отсутствует"
            },
            accent = when (hasRoot) {
                true -> StatusGreen
                false -> StatusRed
                null -> MaterialTheme.colorScheme.secondary
            }
        )

        StatusCard(
            icon = Icons.Filled.SportsEsports,
            title = "Процесс игры",
            subtitle = when (gameRunning) {
                null -> "Проверка..."
                true -> "Запущена"
                false -> "Не запущена"
            },
            accent = when (gameRunning) {
                true -> StatusGreen
                false -> StatusRed
                null -> MaterialTheme.colorScheme.secondary
            }
        )

        StatusCard(
            icon = Icons.Filled.Description,
            title = "Выбранный файл",
            subtitle = selectedBuild ?: "Не выбран (перейдите во вкладку Файлы)",
            accent = if (selectedBuild != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        )

        LogAccordion(state = log, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.weight(1f))

        ActionButtons(
            status = status,
            canInject = canInject,
            busy = busy,
            onStart = onStart,
            onClearLog = onClearLog
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun ActionButtons(
    status: LoaderStatus,
    canInject: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    onClearLog: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onClearLog,
            enabled = !busy,
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = "Очистить логи")
        }

        Button(
            onClick = onStart,
            enabled = canInject,
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = when (status) {
                    LoaderStatus.SUCCESS -> StatusGreen
                    LoaderStatus.ERROR -> StatusRed
                    else -> cs.primary
                }
            ),
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
        ) {
            when (status) {
                LoaderStatus.CHECKING, LoaderStatus.INJECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (status == LoaderStatus.CHECKING) "Проверка..." else "Запуск...")
                }
                LoaderStatus.SUCCESS -> {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Запущено")
                }
                LoaderStatus.ERROR -> {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Повторить")
                }
                LoaderStatus.IDLE -> {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Старт")
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
) {
    val cs = MaterialTheme.colorScheme
    val animatedAccent by animateColorAsState(targetValue = accent, animationSpec = tween(250), label = "accent")

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleMedium)
            },
            supportingContent = {
                Text(subtitle, color = animatedAccent, style = MaterialTheme.typography.bodyMedium)
            },
            leadingContent = {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = animatedAccent.copy(alpha = 0.16f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = animatedAccent)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun EmptyFeed() {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = cs.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Inbox,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Лента пуста", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun BannerStrip() {
    val cs = MaterialTheme.colorScheme
    val channels = listOf("timesplinter", "weeqz", "theftfelt")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = cs.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = channels.joinToString("  •  "),
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
