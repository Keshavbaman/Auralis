package app.auralis.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.auralis.branding.*

@Composable
fun SettingsOverview(back: () -> Unit, select: (String) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                RefinedHeader("Settings", back, "Make Auralis feel like you.")
            }
            LazyColumn(
                Modifier.fillMaxSize().testTag("settings-scroll"),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Text(
                        "YOUR EXPERIENCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column {
                            RefinedAction(
                                Icons.Rounded.Headphones,
                                "Playback",
                                "Speed, skips & smart resume",
                            ) {
                                select("Playback")
                            }
                            SettingsRule()
                            RefinedAction(
                                Icons.Rounded.Cast,
                                "Google Cast",
                                "Listen on your Wi-Fi speaker",
                            ) {
                                select("Google Cast")
                            }
                            SettingsRule()
                            RefinedAction(
                                Icons.Rounded.Palette,
                                "Appearance",
                                "Theme, colours & app icon",
                            ) {
                                select("Appearance")
                            }
                            SettingsRule()
                            RefinedAction(
                                Icons.Rounded.NotificationsNone,
                                "Notifications",
                                "Your playback controls",
                            ) {
                                select("Notifications")
                            }
                            SettingsRule()
                            RefinedAction(
                                Icons.Rounded.AccessibilityNew,
                                "Accessibility",
                                "Motion, touch & readability",
                            ) {
                                select("Accessibility")
                            }
                        }
                    }
                }
                item {
                    Text(
                        "YOUR LIBRARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column {
                            RefinedAction(
                                Icons.Rounded.FolderOpen,
                                "Storage and imports",
                                "Audio folders & rescanning",
                            ) {
                                select("Storage and imports")
                            }
                            SettingsRule()
                            RefinedAction(
                                Icons.Rounded.CloudDownload,
                                "Backup and restore",
                                "Keep your listening life safe",
                            ) {
                                select("Backup and restore")
                            }
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        RefinedAction(
                            Icons.Rounded.Info,
                            "About Auralis",
                            "Your books. Your world.",
                        ) {
                            select("About Auralis")
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BrandMark(AuralisIcon.Signature, Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Made for your own little world.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRule() {
    HorizontalDivider(
        Modifier.padding(start = 76.dp, end = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f),
    )
}
