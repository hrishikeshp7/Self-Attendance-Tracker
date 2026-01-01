package com.attendance.tracker.ui.screens.customizations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationsScreen(
    currentThemeMode: ThemeMode,
    currentPrimaryColor: Color?,
    currentSecondaryColor: Color?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onCustomColorsChange: (Long?, Long?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerTarget by remember { mutableStateOf<ColorTarget?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customizations") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Mode Section
            item {
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeModeOption(
                            title = "System Default",
                            description = "Follow system theme settings",
                            selected = currentThemeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                        )
                        Divider()
                        ThemeModeOption(
                            title = "Light",
                            description = "Light theme with bright colors",
                            selected = currentThemeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                        )
                        Divider()
                        ThemeModeOption(
                            title = "Dark",
                            description = "Dark theme for low-light environments",
                            selected = currentThemeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) }
                        )
                        Divider()
                        ThemeModeOption(
                            title = "AMOLED",
                            description = "Pure black theme for battery saving on AMOLED displays",
                            selected = currentThemeMode == ThemeMode.AMOLED,
                            onClick = { onThemeModeChange(ThemeMode.AMOLED) }
                        )
                    }
                }
            }

            // Color Customization Section
            item {
                Text(
                    text = "Color Customization",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Primary Color
                        ColorCustomizationRow(
                            label = "Primary Color",
                            currentColor = currentPrimaryColor ?: MaterialTheme.colorScheme.primary,
                            onClick = {
                                colorPickerTarget = ColorTarget.PRIMARY
                                showColorPicker = true
                            },
                            onReset = {
                                onCustomColorsChange(null, currentSecondaryColor?.toArgb()?.toLong())
                            }
                        )

                        Divider()

                        // Secondary Color
                        ColorCustomizationRow(
                            label = "Secondary Color",
                            currentColor = currentSecondaryColor ?: MaterialTheme.colorScheme.secondary,
                            onClick = {
                                colorPickerTarget = ColorTarget.SECONDARY
                                showColorPicker = true
                            },
                            onReset = {
                                onCustomColorsChange(currentPrimaryColor?.toArgb()?.toLong(), null)
                            }
                        )
                    }
                }
            }

            // Pre-built Color Schemes
            item {
                Text(
                    text = "Pre-built Color Schemes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PrebuiltSchemeOption(
                            title = "Ocean Blue",
                            primaryColor = Color(0xFF0277BD),
                            secondaryColor = Color(0xFF00ACC1),
                            onClick = {
                                onCustomColorsChange(0xFF0277BD, 0xFF00ACC1)
                            }
                        )
                        Divider()
                        PrebuiltSchemeOption(
                            title = "Forest Green",
                            primaryColor = Color(0xFF388E3C),
                            secondaryColor = Color(0xFF66BB6A),
                            onClick = {
                                onCustomColorsChange(0xFF388E3C, 0xFF66BB6A)
                            }
                        )
                        Divider()
                        PrebuiltSchemeOption(
                            title = "Purple Dream",
                            primaryColor = Color(0xFF7B1FA2),
                            secondaryColor = Color(0xFFAB47BC),
                            onClick = {
                                onCustomColorsChange(0xFF7B1FA2, 0xFFAB47BC)
                            }
                        )
                        Divider()
                        PrebuiltSchemeOption(
                            title = "Sunset Orange",
                            primaryColor = Color(0xFFE64A19),
                            secondaryColor = Color(0xFFFF6F00),
                            onClick = {
                                onCustomColorsChange(0xFFE64A19, 0xFFFF6F00)
                            }
                        )
                        Divider()
                        PrebuiltSchemeOption(
                            title = "Default",
                            primaryColor = Color(0xFF1976D2),
                            secondaryColor = Color(0xFF03DAC6),
                            onClick = {
                                onCustomColorsChange(null, null)
                            }
                        )
                    }
                }
            }

            // Info Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "💡 Tip",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AMOLED theme uses pure black backgrounds to save battery on OLED/AMOLED displays. Custom colors work with all themes including AMOLED mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    // Color Picker Dialog (simplified)
    if (showColorPicker && colorPickerTarget != null) {
        SimpleColorPickerDialog(
            currentColor = when (colorPickerTarget) {
                ColorTarget.PRIMARY -> currentPrimaryColor ?: MaterialTheme.colorScheme.primary
                ColorTarget.SECONDARY -> currentSecondaryColor ?: MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            },
            onColorSelected = { color ->
                when (colorPickerTarget) {
                    ColorTarget.PRIMARY -> {
                        onCustomColorsChange(color.toArgb().toLong(), currentSecondaryColor?.toArgb()?.toLong())
                    }
                    ColorTarget.SECONDARY -> {
                        onCustomColorsChange(currentPrimaryColor?.toArgb()?.toLong(), color.toArgb().toLong())
                    }
                    else -> {}
                }
                showColorPicker = false
            },
            onDismiss = {
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun ThemeModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ColorCustomizationRow(
    label: String,
    currentColor: Color,
    onClick: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onReset) {
                Text("Reset")
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onClick)
            )
        }
    }
}

@Composable
private fun PrebuiltSchemeOption(
    title: String,
    primaryColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(primaryColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(secondaryColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    }
}

@Composable
private fun SimpleColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF5E35B1),
        Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF039BE5), Color(0xFF00ACC1),
        Color(0xFF00897B), Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFC0CA33),
        Color(0xFFFDD835), Color(0xFFFFB300), Color(0xFFFB8C00), Color(0xFFF4511E),
        Color(0xFF6D4C41), Color(0xFF757575), Color(0xFF546E7A), Color(0xFF37474F)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color") },
        text = {
            Column {
                colors.chunked(5).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (color == currentColor) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .clickable { onColorSelected(color) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private enum class ColorTarget {
    PRIMARY, SECONDARY
}
