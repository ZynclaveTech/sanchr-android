package com.sanchr.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SanchrTopBar(
                title = "Appearance",
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(SanchrTheme.spacing.default),
        ) {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            // Theme picker with preview cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
            ) {
                ThemePreviewCard(
                    icon = Icons.Filled.LightMode,
                    label = "Light",
                    isSelected = uiState.themeMode == "light",
                    onClick = { viewModel.setThemeMode("light") },
                    modifier = Modifier.weight(1f),
                )
                ThemePreviewCard(
                    icon = Icons.Filled.DarkMode,
                    label = "Dark",
                    isSelected = uiState.themeMode == "dark",
                    onClick = { viewModel.setThemeMode("dark") },
                    modifier = Modifier.weight(1f),
                )
                ThemePreviewCard(
                    icon = Icons.Filled.SettingsBrightness,
                    label = "System",
                    isSelected = uiState.themeMode == "system",
                    onClick = { viewModel.setThemeMode("system") },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // Dynamic Color toggle (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic Color",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Use wallpaper-based Material You colors (Android 12+)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiState.dynamicColorEnabled,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))
            }

            // Font size slider
            Text(
                text = "Font Size",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            val fontSizeOptions = listOf("small", "medium", "large", "extra_large")
            val fontSizeLabels = listOf("Small", "Medium", "Large", "Extra Large")
            val currentIndex = fontSizeOptions.indexOf(uiState.fontSize).coerceAtLeast(0)

            Column {
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { value ->
                        val index = value.toInt().coerceIn(0, fontSizeOptions.lastIndex)
                        viewModel.setFontSize(fontSizeOptions[index])
                    },
                    valueRange = 0f..fontSizeOptions.lastIndex.toFloat(),
                    steps = fontSizeOptions.size - 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    fontSizeLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))

            // Chat wallpaper picker
            Text(
                text = "Chat Wallpaper",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
            ) {
                listOf(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer,
                ).forEachIndexed { index, color ->
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(color)
                                .then(
                                    if (index == 0) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).clickable { /* select wallpaper */ },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == 0) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }
}

@Composable
private fun ThemePreviewCard(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(SanchrTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
