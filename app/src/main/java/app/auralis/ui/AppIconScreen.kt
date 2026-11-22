package app.auralis.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.branding.*

@Composable
fun AppIconScreen(vm: AuralisViewModel, back: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.changingIcon.collectAsStateWithLifecycle()
    AppIconGallery(AuralisIcon.fromKey(prefs.appIcon), busy, back, vm::changeIcon)
}

@Composable
fun AppIconGallery(
    selected: AuralisIcon,
    busy: Boolean,
    back: () -> Unit,
    select: (AuralisIcon) -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = back)
    LazyVerticalGrid(
        GridCells.Adaptive(156.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                BackTitle("App icon", back)
                Text(
                    "A small signature. Entirely yours.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Choose the artwork that welcomes you home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
        items(AuralisIcon.entries, key = { it.key }) { icon ->
            val active = icon == selected
            Surface(
                onClick = { select(icon) },
                enabled = !busy,
                shape = AuralisShape.panel,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border =
                    BorderStroke(
                        if (active) 1.5.dp else .5.dp,
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                modifier =
                    Modifier.semantics {
                        this.selected = active
                        role = Role.RadioButton
                        contentDescription =
                            "${icon.title}. ${icon.description}${if(active) ". Selected" else ""}"
                    },
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(112.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(BrandObsidian),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon == AuralisIcon.Signature) {
                            BrandMark(icon, Modifier.size(92.dp))
                        } else {
                            Image(
                                painterResource(
                                    when (icon) {
                                        AuralisIcon.Classic -> app.auralis.R.drawable.ic_classic_foreground
                                        AuralisIcon.Horizon -> app.auralis.R.drawable.ic_horizon_foreground
                                        AuralisIcon.Pulse -> app.auralis.R.drawable.ic_pulse_foreground
                                        AuralisIcon.Quill -> app.auralis.R.drawable.ic_quill_foreground
                                        AuralisIcon.Signature -> icon.artwork
                                    }
                                ),
                                null,
                                Modifier.size(168.dp).requiredSize(168.dp),
                            )
                        }
                    }
                    Text(icon.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        icon.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Selected",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else
                            Text(
                                "Choose icon",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (busy) "Updating your home screen…"
                else
                    "Your home screen may take a moment to show the new icon. Your listening continues.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
