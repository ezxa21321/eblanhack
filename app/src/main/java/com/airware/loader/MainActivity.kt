package com.airware.loader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airware.loader.ui.*
import com.airware.loader.ui.theme.AirwareLoaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TARGET_PACKAGE = "com.axlebolt.standoff2"

// Семантические цвета статуса — остаются узнаваемыми в обеих темах
private val StatusGreen = Color(0xFF3FD07A)
private val StatusRed = Color(0xFFFF6B6B)

enum class LoaderStatus { IDLE, CHECKING, INJECTING, SUCCESS, ERROR }
enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Главная", Icons.Filled.Home),
    FEED("Лента", Icons.Filled.List),
    FILES("Файлы", Icons.Filled.Folder)
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
            // Тема теперь только тёмная — переключатель убран насовсем, светлая схема
            // в Theme.kt остаётся неиспользуемым резервом на будущее.
            AirwareLoaderTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LoaderScreen(
                        onInject = { pkg, libPath -> nativeInject(pkg, libPath) }
                    )
                }
            }
        }
    }
}

@Composable
fun LoaderScreen(onInject: (String, String) -> Boolean) {
    var status by remember { mutableStateOf(LoaderStatus.IDLE) }
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var gameRunning by remember { mutableStateOf<Boolean?>(null) }
    var selectedBuild by remember { mutableStateOf<String?>(null) }
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var previousTabIndex by remember { mutableStateOf(Tab.HOME.ordinal) }
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
                if (running) log.success("SO2 detected, pid=${pids.first()}")
                else log.warn("SO2 is not running")
            }
            gameRunning = running
            delay(2000)
        }
    }

    // Сброс завершённого статуса при смене билда/состояния среды — иначе "Запущено"/ошибка
    // виснет на кнопке бесконечно и перекрывает актуальное состояние готовности.
    LaunchedEffect(selectedBuild, gameRunning) {
        if (status == LoaderStatus.SUCCESS || status == LoaderStatus.ERROR) {
            status = LoaderStatus.IDLE
        }
    }

    val canInject = hasRoot == true && gameRunning == true && selectedBuild != null &&
            status == LoaderStatus.IDLE

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
                log.error("abort: SO2 process not found")
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

    val busy = status == LoaderStatus.CHECKING || status == LoaderStatus.INJECTING

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            TopBar(title = currentTab.label)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp)
            ) {
                val forward = currentTab.ordinal >= previousTabIndex
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        // Shared-axis-X: уходящий контент сдвигается и тает, новый въезжает с
                        // противоположной стороны — направление зависит от позиции таба в нав-баре,
                        // а не от голого fade, как было раньше.
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(animationSpec = tween(260)) { w -> dir * w / 4 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(220)) { w -> -dir * w / 4 } + fadeOut(tween(160)))
                    },
                    label = "tab"
                ) { tab ->
                    when (tab) {
                        Tab.HOME -> HomeTab(
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

            Spacer(modifier = Modifier.height(10.dp))
            BannerStrip()
            Spacer(modifier = Modifier.height(12.dp))
            BottomPillNav(
                current = currentTab,
                onSelect = { tab ->
                    previousTabIndex = currentTab.ordinal
                    currentTab = tab
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EmptyFeed() {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(cs.surfaceContainerHigh, ExpressiveShapeTokens.clover),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Inbox,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text("Лента пуста", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun TopBar(title: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = title,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (slideInHorizontally(tween(220)) { h -> h / 6 } + fadeIn(tween(220))) togetherWith
                        (fadeOut(tween(120)))
            },
            label = "title"
        ) { t ->
            Text(t, color = cs.onBackground, style = MaterialTheme.typography.headlineMedium)
        }
        DeveloperBadge()
    }
}

@Composable
fun DeveloperBadge() {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(cs.tertiaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = cs.onTertiaryContainer, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = "Developer", color = cs.onTertiaryContainer, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun HomeTab(
    gameRunning: Boolean?,
    selectedBuild: String?,
    status: LoaderStatus,
    canInject: Boolean,
    busy: Boolean,
    log: LogState,
    onStart: () -> Unit,
    onClearLog: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        StatusCard(
            icon = Icons.Filled.SportsEsports,
            title = "Standoff 2",
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
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard(
            icon = Icons.Filled.Description,
            title = "Выбранный билд",
            subtitle = selectedBuild ?: "Не выбран",
            accent = if (selectedBuild != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(12.dp))
        LogAccordion(state = log, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.weight(1f))

        ActionButtons(status = status, canInject = canInject, busy = busy, onStart = onStart, onClearLog = onClearLog)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * Логика раскладки кнопок (Expressive ButtonGroup):
 *  - Старт — главное, доминирующее действие на всю ширину (это единственное, что
 *    реально продвигает пользователя вперёд, поэтому оно получает весь приоритет);
 *  - Очистить логов — второстепенное действие, компактная icon-only кнопка сбоку,
 *    визуально явно подчинена и отключена во время активной операции (чтобы нельзя
 *    было стереть контекст лога, пока идёт проверка/инжект).
 *  Раньше обе кнопки были одного роста и спорили за внимание 1:1.6 — теперь
 *  соотношение явно читается: одно первичное действие, один вспомогательный значок.
 */
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquishButton(
            onClick = onClearLog,
            enabled = !busy,
            container = cs.surfaceContainerHigh,
            content = cs.onSurfaceVariant,
            shape = ExpressiveShapeTokens.cookie6,
            modifier = Modifier.size(60.dp)
        ) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = "Очистить логи", modifier = Modifier.size(22.dp))
        }

        SquishButton(
            onClick = onStart,
            enabled = canInject,
            container = when (status) {
                LoaderStatus.SUCCESS -> StatusGreen
                LoaderStatus.ERROR -> StatusRed
                else -> cs.primary
            },
            content = when (status) {
                LoaderStatus.SUCCESS, LoaderStatus.ERROR -> Color.White
                else -> cs.onPrimary
            },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.weight(1f)
        ) {
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.85f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(targetScale = 0.85f))
                },
                label = "startButtonContent"
            ) { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (s) {
                        LoaderStatus.CHECKING, LoaderStatus.INJECTING -> {
                            ExpressiveActivityIndicator(diameter = 22.dp, color = LocalContentColor.current)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (s == LoaderStatus.CHECKING) "Проверка..." else "Запуск...",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        LoaderStatus.SUCCESS -> {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Запущено", style = MaterialTheme.typography.labelLarge)
                        }
                        LoaderStatus.ERROR -> {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Повторить", style = MaterialTheme.typography.labelLarge)
                        }
                        LoaderStatus.IDLE -> {
                            Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Старт", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SquishButton(
    onClick: () -> Unit,
    enabled: Boolean,
    container: Color,
    content: Color,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    label: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "squish"
    )
    val bg by animateColorAsState(
        targetValue = if (enabled) container else container.copy(alpha = 0.35f),
        animationSpec = tween(200), label = "btnBg"
    )
    val fg by animateColorAsState(
        targetValue = if (enabled) content else content.copy(alpha = 0.5f),
        animationSpec = tween(200), label = "btnFg"
    )
    Row(
        modifier = modifier
            .scale(scale)
            .heightIn(min = 60.dp)
            .background(bg, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null
            ) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            label()
        }
    }
}

@Composable
fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
) {
    val cs = MaterialTheme.colorScheme
    val animatedAccent by animateColorAsState(targetValue = accent, animationSpec = tween(260), label = "cardAccent")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(animatedAccent.copy(alpha = 0.18f), ExpressiveShapeTokens.cookie),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = animatedAccent, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = cs.onSurface, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { w -> w / 8 }) togetherWith
                                fadeOut(tween(120))
                    },
                    label = "cardSubtitle"
                ) { sub ->
                    Text(sub, color = animatedAccent, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Было: три полноширинных пилюли 1:1:1 — тяжёлая строка, которая боролась за внимание
 * с контентом над ней. Теперь один компактный ряд: иконка-метка + каналы через точку-
 * разделитель внутри одной скруглённой капсулы, без лишнего веса на экране.
 */
@Composable
fun BannerStrip() {
    val cs = MaterialTheme.colorScheme
    val channels = listOf("timesplinter", "weeqz", "theftfelt")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .background(cs.surfaceContainerLow, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Send,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            channels.forEachIndexed { i, handle ->
                if (i > 0) {
                    Text(
                        text = "  •  ",
                        color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(text = handle, color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/**
 * Нижняя плавающая nav-панель, M3 Expressive.
 *
 * Было: вес сегмента переключался мгновенно (`if (selected) 1.5f else 1f` без анимации),
 * из-за чего при смене таба остальные элементы резко дёргались в сторону, а подпись
 * выбранного таба хлопком появлялась/исчезала без перехода. Иконка тоже визуально
 * "прыгала" при появлении текста рядом.
 *
 * Стало: вес, паддинг, фон и прозрачность подписи анимируются одним согласованным
 * набором пружин — сегменты плавно "перетекают" друг в друга, подпись проявляется
 * через fade+expand, а не возникает мгновенно.
 */
@Composable
fun BottomPillNav(current: Tab, onSelect: (Tab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = cs.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
                NavSegment(
                    tab = tab,
                    selected = tab == current,
                    onSelect = { onSelect(tab) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavSegment(tab: Tab, selected: Boolean, onSelect: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val weight by animateFloatAsState(
        targetValue = if (selected) 1.7f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "navWeight"
    )
    val bg by animateColorAsState(
        targetValue = if (selected) cs.primary else Color.Transparent,
        animationSpec = tween(260), label = "navBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) cs.onPrimary else cs.onSurfaceVariant,
        animationSpec = tween(260), label = "navFg"
    )
    val labelProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "navLabelExpand"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navPress"
    )
    val iconBump by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navIconBump"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(weight.coerceAtLeast(0.01f))
            .scale(pressScale)
            .background(bg, RoundedCornerShape(50))
            .clickable(
                indication = null,
                interactionSource = interaction,
            ) { onSelect() }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        // Было: голая Icon() под .scale(iconBump) — при смене tab.icon контент менялся
        // мгновенно, и соседняя геометрия (вес, паддинг) "доезжала" отдельно от иконки,
        // из-за чего казалось, что иконка дёргается влево и обратно. Теперь сама иконка
        // кросс-фейдится через AnimatedContent на том же месте — без смещения по X.
        AnimatedContent(
            targetState = tab.icon,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f, animationSpec = tween(160))) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f, animationSpec = tween(120)))
            },
            label = "navIcon"
        ) { icon ->
            Icon(
                icon,
                contentDescription = tab.label,
                tint = fg,
                modifier = Modifier.size(20.dp).scale(iconBump)
            )
        }
        // Подпись раскрывается по ширине и прозрачности вместе с весом сегмента —
        // не появляется разом, а "вытекает" синхронно с ростом контейнера.
        if (labelProgress > 0.01f) {
            Spacer(modifier = Modifier.width((8.dp * labelProgress)))
            Text(
                tab.label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.alpha(labelProgress)
            )
        }
    }
}
