package com.airware.loader.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Сворачиваемая панель "Логи" с шевроном, повёрнутым на 180° при раскрытии (пружинно).
@Composable
fun LogAccordion(state: LogState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chevron"
    )
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.entries.size, expanded) {
        if (expanded && state.entries.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.entries.size - 1) }
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(cs.tertiaryContainer, ExpressiveShapeTokens.cookie),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = cs.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Логи",
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (state.entries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(cs.secondaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${state.entries.size}",
                            color = cs.onSecondaryContainer,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(240)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceContainerLowest, MaterialTheme.shapes.medium)
                            .padding(14.dp)
                    ) {
                        if (state.entries.isEmpty()) {
                            Text(
                                text = "// waiting...",
                                color = cs.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            LazyColumn(state = listState) {
                                items(state.entries) { entry ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(
                                            text = "[${entry.level.tag}]",
                                            color = levelColor(entry.level),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = entry.message,
                                            color = cs.onSurface,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
