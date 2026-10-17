package com.airware.loader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airware.loader.RootShell

const val BUILD_DIR = "/data/local/tmp"

@Composable
fun FilesTab(
    selectedBuild: String?,
    onBuildSelected: (String) -> Unit,
    onLog: (String) -> Unit,
) {
    var builds by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        loading = true
        val found = RootShell.listFiles(BUILD_DIR, "airware")
        builds = found
        loading = false
        if (found.isEmpty()) onLog("no files containing 'airware' found in $BUILD_DIR")
        else onLog("found ${found.size} build(s) in $BUILD_DIR")
    }

    LaunchedEffect(Unit) { refresh() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (builds.isEmpty()) {
            EmptyFilesState(loading = loading, onImport = { refresh() })
        } else {
            BuildsList(
                builds = builds,
                selected = selectedBuild,
                onSelect = { file ->
                    onBuildSelected("$BUILD_DIR/$file")
                    onLog("selected build: $file")
                },
                onRefresh = { refresh() }
            )
        }
    }
}

@Composable
private fun EmptyFilesState(loading: Boolean, onImport: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(320)) + scaleIn(
                initialScale = 0.7f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        ) {
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .background(cs.primaryContainer, ExpressiveShapeTokens.sunny),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    ExpressiveActivityIndicator(diameter = 64.dp, color = cs.onPrimaryContainer)
                } else {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = cs.onPrimaryContainer,
                        modifier = Modifier.size(62.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (loading) "Сканирование..." else "Пусто",
            color = cs.onBackground,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Импортируйте конфигурации — они появятся здесь.",
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 40.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onImport,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
            modifier = Modifier.height(52.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Импорт конфигурации", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BuildsList(
    builds: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Конфигурации",
                color = cs.onBackground,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(builds) { file ->
                val isSelected = selected?.substringAfterLast('/') == file
                BuildRow(file = file, selected = isSelected, onClick = { onSelect(file) })
            }
        }
    }
}

@Composable
private fun BuildRow(file: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "rowPress"
    )
    val container by animateColorAsState(
        targetValue = if (selected) cs.primaryContainer else cs.surfaceContainer,
        animationSpec = tween(220), label = "rowBg"
    )
    val onContainer = if (selected) cs.onPrimaryContainer else cs.onSurface

    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (selected) cs.primary.copy(alpha = 0.22f) else cs.surfaceContainerHighest,
                        ExpressiveShapeTokens.cookie
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = if (selected) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                file,
                color = onContainer,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Выбрано", tint = cs.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}
