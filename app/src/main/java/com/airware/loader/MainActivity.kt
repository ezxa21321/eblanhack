package com.airware.loader

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.airware.loader.ui.*
import com.airware.loader.ui.theme.AirwareLoaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirwareLoaderTheme(darkTheme = true) {
                LoaderScreen()
            }
        }
    }
}

/**
 * Отслеживание состояния интернета в реальном времени через ConnectivityManager
 */
@Composable
fun rememberNetworkState(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = isNetworkAvailable(context)) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                value = true
            }
            override fun onLost(network: Network) {
                value = isNetworkAvailable(context)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        awaitDispose {
            cm.unregisterNetworkCallback(callback)
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * Пример сетевого запроса (проверка пинга / получение данных с сервера)
 */
suspend fun checkServerConnection(urlStr: String = "https://clients3.google.com/generate_204"): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            val code = connection.responseCode
            connection.disconnect()
            code in 200..399
        } catch (e: Exception) {
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoaderScreen() {
    var status by remember { mutableStateOf(LoaderStatus.IDLE) }
    var selectedBuild by remember { mutableStateOf<String?>(null) }
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    val isOnline by rememberNetworkState()
    val log = rememberLogState()
    val scope = rememberCoroutineScope()

    // Логирование изменений состояния интернета
    LaunchedEffect(isOnline) {
        if (isOnline) {
            log.info("Подключение к сети активно, проверка доступа в интернет...")
            val reachable = checkServerConnection()
            if (reachable) log.success("Интернет подключен и работает")
            else log.warn("Сеть подключена, но доступ к интернету ограничен")
        } else {
            log.error("Отсутствует подключение к интернету")
        }
    }

    LaunchedEffect(selectedBuild, isOnline) {
        if (status == LoaderStatus.SUCCESS || status == LoaderStatus.ERROR) {
            status = LoaderStatus.IDLE
        }
    }

    val canStart = isOnline && selectedBuild != null && status == LoaderStatus.IDLE
    val busy = status == LoaderStatus.CHECKING || status == LoaderStatus.INJECTING

    fun doStart() {
        val build = selectedBuild ?: return
        scope.launch {
            status = LoaderStatus.CHECKING
            log.info("Проверка подключения и конфигурации...")
            delay(300)

            if (!isOnline || !checkServerConnection()) {
                log.error("Ошибка: нет доступа к интернету")
                status = LoaderStatus.ERROR
                return@launch
            }

            status = LoaderStatus.INJECTING
            log.info("Применение конфигурации '${build.substringAfterLast('/')}'...")
            delay(600)
            log.success("Конфигурация успешно загружена")
            status = LoaderStatus.SUCCESS
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
                            isOnline = isOnline,
                            selectedBuild = selectedBuild?.substringAfterLast('/'),
                            status = status,
                            canStart = canStart,
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
    isOnline: Boolean,
    selectedBuild: String?,
    status: LoaderStatus,
    canStart: Boolean,
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
        // Плашка "Root-доступ" удалена. Вместо неё статус интернет-соединения и выбранного файла:
        StatusCard(
            icon = if (isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
            title = "Интернет-соединение",
            subtitle = if (isOnline) "Подключено" else "Нет подключения",
            accent = if (isOnline) StatusGreen else StatusRed
        )

        StatusCard(
            icon = Icons.Filled.Description,
            title = "Выбранный файл",
            subtitle = selectedBuild ?: "Не выбран",
            accent = if (selectedBuild != null) StatusGreen else MaterialTheme.colorScheme.secondary
        )

        // Окно логов в таком же стиле карточки и без тега [INFO]
        LogAccordion(state = log, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.weight(1f))

        ActionButtons(
            status = status,
            canStart = canStart,
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
    canStart: Boolean,
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
            enabled = canStart,
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
                    Text(if (status == LoaderStatus.CHECKING) "Проверка..." else "Загрузка...")
                }
                LoaderStatus.SUCCESS -> {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Готово")
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = animatedAccent.copy(alpha = 0.16f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = animatedAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = animatedAccent,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
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
