package com.example.seestarvoice2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.seestarvoice2.ui.theme.SeeStarVoice2Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var logPanelHeight by remember { mutableStateOf(200.dp) }
    var isLogPanelVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SeeStar Voice 2",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(onClick = { isLogPanelVisible = !isLogPanelVisible }) {
                        Icon(
                            imageVector = if (isLogPanelVisible) Icons.Default.Info else Icons.Default.Info,
                            contentDescription = if (isLogPanelVisible) "Hide Logs" else "Show Logs",
                            tint = if (isLogPanelVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (showSettings) {
            SettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSettings = false }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Feedback Area
            FeedbackArea(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onMockSpeechInput = { viewModel.onUserSpeechInput(it) },
                isLoading = viewModel.isModelLoading,
                requireWakeWord = viewModel.requireWakeWord,
                capturedImageUrl = viewModel.capturedImageUrl
            )

            // Log Panel
            LogPanel(
                height = logPanelHeight,
                isVisible = isLogPanelVisible,
                logs = viewModel.logs,
                onHeightChange = { delta ->
                    val newHeight = logPanelHeight - delta
                    if (newHeight > 100.dp && newHeight < 600.dp) {
                        logPanelHeight = newHeight
                    }
                },
                onToggleVisibility = { isLogPanelVisible = !isLogPanelVisible }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Application Options",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Wake Word Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateRequireWakeWord(!viewModel.requireWakeWord) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = viewModel.requireWakeWord,
                        onCheckedChange = { viewModel.updateRequireWakeWord(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Require SeeStar wake word", style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SeeStar IP Option
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "SeeStar IP Address", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = viewModel.seestarIp,
                        onValueChange = { viewModel.updateSeestarIp(it) },
                        placeholder = { Text("10.0.0.1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Light Pollution Option
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Light Pollution (Bortle ${viewModel.bortleScale})", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = viewModel.bortleScale.toFloat(),
                        onValueChange = { viewModel.updateBortleScale(it.toInt()) },
                        valueRange = 1f..9f,
                        steps = 7,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                        Text("Dark (1)", style = MaterialTheme.typography.labelSmall)
                        Text("Urban (9)", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // LLM Engine Option
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "LLM Engine", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { /* Disabled for now */ }
                    ) {
                        TextField(
                            value = viewModel.currentLlmEngine,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("gemma-2b-it-cpu-int4.bin") },
                                onClick = { 
                                    expanded = false 
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FeedbackArea(
    modifier: Modifier = Modifier,
    onMockSpeechInput: (String) -> Unit = {},
    isLoading: Boolean = false,
    requireWakeWord: Boolean = true,
    capturedImageUrl: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loadingTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loadingAlpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loadingScale"
    )

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = capturedImageUrl ?: "https://via.placeholder.com/800x600.png?text=Telescope+View",
                        contentDescription = "Telescope View",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.HourglassEmpty,
                                    contentDescription = "Loading",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            alpha = alpha
                                        )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "System Initializing",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.alpha(alpha)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (capturedImageUrl != null) "IMAGE CAPTURED" else "LIVE VIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f)
                    .clickable(enabled = !isLoading) { 
                        onMockSpeechInput(if (requireWakeWord) "SeeStar Point to M31 and take a picture" else "Point to M31 and take a picture")
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Assistant Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                isLoading -> "Initializing models..."
                                requireWakeWord -> "Listening for 'SeeStar'..."
                                else -> "Listening for commands..."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (requireWakeWord) "(Say 'SeeStar' + command)" else "(Say command directly)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogPanel(
    height: androidx.compose.ui.unit.Dp,
    isVisible: Boolean,
    logs: List<String>,
    onHeightChange: (androidx.compose.ui.unit.Dp) -> Unit,
    onToggleVisibility: () -> Unit
) {
    val animatedHeight by animateDpAsState(targetValue = if (isVisible) height else 0.dp, label = "logPanelHeight")
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(animatedHeight + 48.dp)
    ) {
        // Drag handle and Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(if (isVisible) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer)
                .clickable { if (!isVisible) onToggleVisibility() }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        onHeightChange(with(density) { dragAmount.toDp() })
                    }
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "System Logs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (isVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isVisible) "Hide Logs" else "Show Logs"
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        AnimatedVisibility(
            visible = isVisible,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(8.dp)
            ) {
                LazyColumn {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = Color.Green,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MainScreenPreview() {
    SeeStarVoice2Theme {
        Text("Main Screen Preview")
    }
}
