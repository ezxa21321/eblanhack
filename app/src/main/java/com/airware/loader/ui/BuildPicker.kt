package com.airware.loader.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airware.loader.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val BUILD_DIR = "/data/local/tmp"

@Composable
fun FilesTab(
    selectedBuild: String?,
    onBuildSelected: (String) -> Unit,
    onLog: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            loading = true
            val found = withContext(Dispatchers.IO) {
                RootShell.listConfigsAndBuilds(BUILD_DIR)
            }
            files = found
            loading = false
            if (found.isEmpty()) onLog("Файлы (.cfg / .so) не найдены в $BUILD_DIR")
            else onLog("Найдено файлов в $BUILD_DIR: ${found.size}")
        }
    }

    // Лаунчер для выбора файла через системный проводник Android
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            loading = true
            val fileName = getFileNameFromUri(context, uri) ?: "imported_config.cfg"
            onLog("Импорт файла: $fileName...")

            val success = withContext(Dispatchers.IO) {
                try {
                    val tempFile = File(context.cacheDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val copied = RootShell.copyFileToDir(tempFile.absolutePath, BUILD_DIR, fileName)
                    tempFile.delete()
                    copied
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
                onLog("Файл $fileName успешно импортирован")
                refresh()
            } else {
                loading = false
                onLog("Ошибка импорта файла $fileName (проверьте права Root)")
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        floatingActionButton = {
            if (files.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Импорт .cfg") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (files.isEmpty()) {
                EmptyFilesState(
                    onImport = { filePickerLauncher.launch("*/*") },
                    onRefresh = { refresh() }
                )
            } else {
                BuildsList(
                    builds = files,
                    selected = selectedBuild,
                    onSelect = { file ->
                        onBuildSelected("$BUILD_DIR/$file")
                        onLog("Выбран файл: $file")
                    },
                    onRefresh = { refresh() }
                )
            }
        }
    }
}

/** Получение реального имени файла из Uri (включая расширение .cfg) */
private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = cursor.getString(index)
            }
        }
    }
    if (name == null) {
        name = uri.path?.substringAfterLast('/')
    }
    return name
}

@Composable
private fun EmptyFilesState(onImport: () -> Unit, onRefresh: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = cs.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = cs.onSecondaryContainer,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Конфигурации не найдены",
            color = cs.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Импортируйте файлы .cfg из памяти устройства — они будут скопированы в рабочую директорию.",
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = onRefresh,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Обновить")
            }

            Button(
                onClick = onImport,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Импорт .cfg")
            }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Доступные файлы (${builds.size})",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить", tint = cs.onSurfaceVariant)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            items(builds, key = { it }) { file ->
                val isSelected = selected?.substringAfterLast('/') == file
                BuildRow(file = file, selected = isSelected, onClick = { onSelect(file) })
            }
        }
    }
}

@Composable
private fun BuildRow(file: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isCfg = file.endsWith(".cfg", ignoreCase = true)

    OutlinedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) cs.secondaryContainer else cs.surfaceContainerLow
        ),
        border = CardDefaults.outlinedCardBorder(enabled = !selected)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = file,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = if (isCfg) "Файл конфигурации (.cfg)" else "Библиотека / Билд",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingContent = {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) cs.primary else cs.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCfg) Icons.Filled.SettingsApplications else Icons.Outlined.Description,
                            contentDescription = null,
                            tint = if (selected) cs.onPrimary else cs.onSurfaceVariant
                        )
                    }
                }
            },
            trailingContent = {
                RadioButton(selected = selected, onClick = onClick)
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}
