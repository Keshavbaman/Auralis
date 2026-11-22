package app.auralis.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.domain.*

@Composable
fun ProfileScreen(vm: AuralisViewModel, navigate: (String) -> Unit) {
    val rows by vm.segments.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val unlocked by vm.achievements.collectAsStateWithLifecycle()
    val challenges by vm.challenges.collectAsStateWithLifecycle()
    val today = rememberListeningDate()
    val stats = rememberListeningStats(rows, prefs.streakMinutes, today)
    val books by vm.catalogue.collectAsStateWithLifecycle()
    ProfileWorldContent(
        stats,
        prefs.goalMinutes,
        unlocked.map { it.key }.toSet(),
        challenges.size,
        books.count { it.isFinished },
        navigate,
    )
}
