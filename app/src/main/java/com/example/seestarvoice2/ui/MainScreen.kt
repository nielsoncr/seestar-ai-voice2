package com.example.seestarvoice2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.TextButton
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
    var liveViewHeight by remember { mutableStateOf(350.dp) }
    var assistantHeight by remember { mutableStateOf(200.dp) }

    var isLiveViewVisible by remember { mutableStateOf(true) }
    var isAssistantVisible by remember { mutableStateOf(true) }
    var isLogPanelVisible by remember { mutableStateOf(true) }

    var showSettings by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Welcome to SeeStar Voice",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { isLiveViewVisible = !isLiveViewVisible }) {
                        Icon(
                            imageVector = if (isLiveViewVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Live View",
                            tint = if (isLiveViewVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { isAssistantVisible = !isAssistantVisible }) {
                        Icon(
                            imageVector = if (isAssistantVisible) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Toggle Assistant",
                            tint = if (isAssistantVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { isLogPanelVisible = !isLogPanelVisible }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Toggle Logs",
                            tint = if (isLogPanelVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val totalHeight = maxHeight
            
            Column(modifier = Modifier.fillMaxSize()) {
                // Top area: Shared by Live View and Assistant Status (takes remaining space)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Section 1: Live View
                    CollapsibleResizablePanel(
                        title = "Live View",
                        icon = Icons.Default.Visibility,
                        height = liveViewHeight,
                        isVisible = isLiveViewVisible,
                        onHeightChange = { delta: androidx.compose.ui.unit.Dp -> liveViewHeight = (liveViewHeight + delta).coerceIn(100.dp, totalHeight * 0.7f) },
                        onToggleVisibility = { isLiveViewVisible = !isLiveViewVisible }
                    ) {
                        LiveViewContent(
                            capturedImageUrl = viewModel.capturedImageUrl,
                            isLoading = viewModel.isModelLoading
                        )
                    }

                    // Section 2: Assistant Status
                    CollapsibleResizablePanel(
                        title = "Voice Assistant Status",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        height = assistantHeight,
                        isVisible = isAssistantVisible,
                        onHeightChange = { delta: androidx.compose.ui.unit.Dp -> assistantHeight = (assistantHeight + delta).coerceIn(100.dp, totalHeight * 0.7f) },
                        onToggleVisibility = { isAssistantVisible = !isAssistantVisible }
                    ) {
                        AssistantStatusContent(
                            isLoading = viewModel.isModelLoading,
                            requireWakeWord = viewModel.requireWakeWord,
                            assistantResponses = viewModel.assistantResponses,
                            onMockSpeechInput = { viewModel.onUserSpeechInput(it) }
                        )
                    }
                }

                // Section 3: System Logs - Anchored to bottom, 25% of screen height
                val logsDesiredHeight = if (isLogPanelVisible) totalHeight * 0.25f else 48.dp
                
                CollapsibleResizablePanel(
                    title = "System Logs",
                    icon = Icons.Default.Info,
                    height = if (isLogPanelVisible) ( (totalHeight * 0.25f) - 48.dp).coerceAtLeast(100.dp) else 0.dp,
                    isVisible = isLogPanelVisible,
                    onHeightChange = { /* Fixed at 25% as requested */ },
                    onToggleVisibility = { isLogPanelVisible = !isLogPanelVisible },
                    isBottomPanel = true,
                    modifier = Modifier.height(logsDesiredHeight)
                ) {
                    LogContent(logs = viewModel.logs)
                }
            }

            if (viewModel.enableActionButtons) {
                ActionButtonsBar(
                    onOpenArm = { viewModel.handleIntent(com.example.seestarvoice2.intelligence.TelescopeIntent.OpenArm) },
                    onCloseArm = { viewModel.handleIntent(com.example.seestarvoice2.intelligence.TelescopeIntent.CloseArm) },
                    onCapture = { viewModel.handleIntent(com.example.seestarvoice2.intelligence.TelescopeIntent.QuickCapture) },
                    onGoto = { target -> viewModel.handleIntent(com.example.seestarvoice2.intelligence.TelescopeIntent.GOTO(target)) }
                )
            }
        }
    }
}

@Composable
fun ActionButtonsBar(
    onOpenArm: () -> Unit,
    onCloseArm: () -> Unit,
    onCapture: () -> Unit,
    onGoto: (String) -> Unit
) {
    var gotoTarget by remember { mutableStateOf("") }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onOpenArm) { Text("Open Arm") }
            TextButton(onClick = onCloseArm) { Text("Close Arm") }
            TextButton(onClick = onCapture) { Text("Capture") }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            TextField(
                value = gotoTarget,
                onValueChange = { gotoTarget = it },
                placeholder = { Text("M31, Moon...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(
                onClick = { if (gotoTarget.isNotBlank()) onGoto(gotoTarget) },
                enabled = gotoTarget.isNotBlank()
            ) {
                Text("Goto")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
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
                    Text(text = "Require wake word", style = MaterialTheme.typography.bodyLarge)
                }

                if (viewModel.requireWakeWord) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 32.dp)) {
                        Text(text = "Manage Wake Words", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.wakeWords.forEach { word ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text(word) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            modifier = Modifier
                                                .size(AssistChipDefaults.IconSize)
                                                .clickable { viewModel.removeWakeWord(word) }
                                        )
                                    }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var newWakeWord by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = newWakeWord,
                                onValueChange = { newWakeWord = it },
                                placeholder = { Text("Add wake word...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (newWakeWord.isNotBlank()) {
                                    viewModel.addWakeWord(newWakeWord)
                                    newWakeWord = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Debug Logging Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateDebugLogging(!viewModel.debugLogging) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = viewModel.debugLogging,
                        onCheckedChange = { viewModel.updateDebugLogging(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Debug Logging", style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Voice Response Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateSpeakResponses(!viewModel.speakResponses) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = viewModel.speakResponses,
                        onCheckedChange = { viewModel.updateSpeakResponses(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Enable voice response", style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateEnableActionButtons(!viewModel.enableActionButtons) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = viewModel.enableActionButtons,
                        onCheckedChange = { viewModel.updateEnableActionButtons(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Enable Action Buttons", style = MaterialTheme.typography.bodyLarge)
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

                // Telescope Port Option
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Telescope Port", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = viewModel.telescopePort.toString(),
                        onValueChange = { viewModel.updateTelescopePort(it) },
                        placeholder = { Text("32323") },
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

                // Minimum Visibility Angle Option
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Minimum Visibility Angle (${viewModel.minVisibilityAngle}°)", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = viewModel.minVisibilityAngle.toFloat(),
                        onValueChange = { viewModel.updateMinVisibilityAngle(it.toInt()) },
                        valueRange = 0f..90f,
                        steps = 89,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                        Text("Horizon (0°)", style = MaterialTheme.typography.labelSmall)
                        Text("Zenith (90°)", style = MaterialTheme.typography.labelSmall)
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
                                text = { Text("qwen2.5-1.5b-instruct.litertlm") },
                                onClick = { 
                                    expanded = false 
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .width(120.dp) // Approximately double the default width of an IconButton
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CollapsibleResizablePanel(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    height: androidx.compose.ui.unit.Dp,
    isVisible: Boolean,
    onHeightChange: (androidx.compose.ui.unit.Dp) -> Unit,
    onToggleVisibility: () -> Unit,
    isBottomPanel: Boolean = false,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (isBottomPanel) {
            // For the bottom panel, the drag handle is the header itself (dragging up increases height)
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            PanelHeader(title, icon, isVisible, onToggleVisibility, onHeightChange)
        } else {
            PanelHeader(title, icon, isVisible, onToggleVisibility, null)
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    content()
                }
                
                if (!isBottomPanel) {
                    // For top/middle panels, the drag handle is a bar at the bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    onHeightChange(with(density) { dragAmount.toDp() })
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
        
        if (!isBottomPanel) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onHeightChange: ((androidx.compose.ui.unit.Dp) -> Unit)?
) {
    val density = LocalDensity.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (isVisible) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer)
            .clickable { onToggleVisibility() }
            .then(
                if (onHeightChange != null) {
                    Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            onHeightChange(with(density) { dragAmount.toDp() })
                        }
                    }
                } else Modifier
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleVisibility) {
            Icon(
                imageVector = if (isVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (isVisible) "Hide" else "Show"
            )
        }
    }
}

@Composable
fun LiveViewContent(
    capturedImageUrl: String?,
    isLoading: Boolean
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

    // Spiral Galaxy Welcome Image (Loaded from assets for offline support)
    val spiralGalaxyAsset = "file:///android_asset/welcome_galaxy.jpg"

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = capturedImageUrl ?: spiralGalaxyAsset,
            contentDescription = "Telescope View",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onLoading = {
                android.util.Log.d("MainScreen", "Image loading: ${capturedImageUrl ?: "Welcome"}")
            },
            onSuccess = {
                android.util.Log.d("MainScreen", "Image loaded successfully")
            },
            onError = { error ->
                android.util.Log.e("MainScreen", "Image load failed: ${error.result.throwable.message}")
            }
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

        // Tag in the top right - Removed per request
        /*
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (capturedImageUrl != null) "IMAGE CAPTURED" else "LIVE VIEW",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
        */
    }
}

@Composable
fun AssistantStatusContent(
    isLoading: Boolean,
    requireWakeWord: Boolean,
    assistantResponses: List<String>,
    onMockSpeechInput: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .clickable(enabled = !isLoading) { 
                onMockSpeechInput(if (requireWakeWord) "SeeStar Point to M31 and take a picture" else "Point to M31 and take a picture")
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(0.6f)) {
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
            
            // Assistant Responses Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = "Assistant Responses",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(assistantResponses) { response ->
                        Text(
                            text = "• $response",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
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

@Composable
fun LogContent(logs: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
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

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MainScreenPreview() {
    SeeStarVoice2Theme {
        Text("Main Screen Preview")
    }
}
