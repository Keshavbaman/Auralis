@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import app.auralis.data.*
import app.auralis.domain.withPlayback
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

@Composable
fun AuralisRoot(vm: AuralisViewModel) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    val library by vm.catalogue.collectAsStateWithLifecycle()
    val activeId by
        remember(vm) { vm.player.map { it.mediaId }.distinctUntilChanged() }
            .collectAsStateWithLifecycle(initialValue = "")
    val review by vm.review.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nav = rememberNavController()
    val backstack by nav.currentBackStackEntryAsState()
    val route = backstack?.destination?.route ?: "home"
    var importMenu by remember { mutableStateOf(false) }
    fun grant(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
            .onFailure {
                vm.message.value =
                    "This provider does not offer lasting access. Choose a document provider such as Files."
            }
    }
    val files =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach(::grant)
                vm.import(uris, false)
            }
        }
    val folder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                grant(uri)
                vm.import(listOf(uri), true)
            }
        }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snack.showSnackbar(message!!)
            vm.message.value = null
        }
    }
    val active = library.firstOrNull { b -> b.chapters.any { it.id == activeId } }
    val destinations =
        listOf(
            Triple("home", "Home", Icons.Rounded.Home),
            Triple("library", "Library", Icons.AutoMirrored.Rounded.LibraryBooks),
            Triple("statistics", "Listening", Icons.Rounded.Insights),
            Triple("settings", "You", Icons.Rounded.Person),
        )
    // Swiping between tabs: the screen follows the finger, then the next tab slides in from the
    // side it came from. Order matches the bottom bar: Home, Library, Listening, You.
    val tabPaths = destinations.map { it.first }
    // Written straight from the touch handler and read only in graphicsLayer: no per-move coroutine.
    var tabDrag by remember { mutableFloatStateOf(0f) }
    var tabJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var swipeStart by remember { mutableFloatStateOf(0f) }
    val gestureScope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun tabIndex(route: String?) = tabPaths.indexOf(route)
    fun tabOffset(width: Int, from: Int, to: Int): Int =
        ((if (to > from) 1 else -1) * width)

    val tabSettleMs = 150

    fun tabEnter(from: Int, to: Int, startOffset: Float = swipeStart): EnterTransition =
        slideInHorizontally(tween(tabSettleMs, easing = LinearOutSlowInEasing)) { width ->
            tabOffset(width, from, to) + (width * startOffset).toInt()
        }

    fun tabExit(from: Int, to: Int, startOffset: Float = swipeStart): ExitTransition =
        slideOutHorizontally(
            tween(tabSettleMs, easing = Easing { fraction ->
                val start = kotlin.math.abs(startOffset)
                start + (1f - start) * LinearOutSlowInEasing.transform(fraction)
            })
        ) { width -> -tabOffset(width, from, to) }
    AuralisTheme(p) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalArtworkTransition provides this) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BoxWithConstraints {
                        val wide = maxWidth >= 760.dp
                        val fullscreen = route == "player" || route == "pdf/{id}"
                        Row(
                            Modifier.fillMaxSize()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                                )
                        ) {
                            if (wide && !fullscreen)
                                NavigationRail(
                                    Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding(),
                                    header = {
                                        app.auralis.branding.BrandMark(
                                            app.auralis.branding.AuralisIcon.Signature,
                                            Modifier.padding(18.dp).size(36.dp),
                                            "Auralis",
                                        )
                                    },
                                ) {
                                    destinations.forEach { (path, label, icon) ->
                                        NavigationRailItem(
                                            route == path,
                                            { openTab(nav, vm, path) },
                                            icon = { Icon(icon, label) },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("nav-$path"),
                                        )
                                    }
                                }
                            Scaffold(
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.background,
                                contentWindowInsets =
                                    if (fullscreen) WindowInsets(0, 0, 0, 0)
                                    else ScaffoldDefaults.contentWindowInsets,
                                snackbarHost = { SnackbarHost(snack) },
                                bottomBar = {
                                    Column {
                                        AnimatedVisibility(
                                            visible = active != null && !fullscreen,
                                            enter =
                                                if (p.reducedMotion) EnterTransition.None
                                                else
                                                    fadeIn(tween(250)) +
                                                        expandVertically(tween(250)),
                                            exit =
                                                if (p.reducedMotion) ExitTransition.None
                                                else
                                                    fadeOut(tween(180)) +
                                                        shrinkVertically(tween(180)),
                                        ) {
                                            CompositionLocalProvider(
                                                LocalArtworkVisibility provides this
                                            ) {
                                                active?.let { book ->
                                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                                                            LiveMiniPlayer(
                                                                vm,
                                                                book,
                                                                p.reducedMotion,
                                                                { nav.navigate("player") },
                                                                vm::toggle,
                                                                { vm.skip(true) },
                                                                p.skipSeconds,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (!wide && !fullscreen)
                                            NavigationBar(
                                                containerColor =
                                                    MaterialTheme.colorScheme.background,
                                                tonalElevation = 0.dp,
                                            ) {
                                                destinations.forEach { (path, label, icon) ->
                                                    NavigationBarItem(
                                                        route == path,
                                                        { openTab(nav, vm, path) },
                                                        icon = { Icon(icon, label) },
                                                        label = { Text(label, fontSize = 10.sp) },
                                                        colors =
                                                            NavigationBarItemDefaults.colors(
                                                                indicatorColor =
                                                                    androidx.compose.ui.graphics
                                                                        .Color
                                                                        .Transparent,
                                                                selectedIconColor =
                                                                    MaterialTheme.colorScheme
                                                                        .primary,
                                                                selectedTextColor =
                                                                    MaterialTheme.colorScheme
                                                                        .primary,
                                                            ),
                                                        modifier = Modifier.testTag("nav-$path"),
                                                    )
                                                }
                                            }
                                        else if (wide && !fullscreen)
                                            Spacer(Modifier.navigationBarsPadding())
                                    }
                                },
                            ) { padding ->
                                val currentTab = tabIndex(route)
                                Box(
                                    Modifier.padding(padding)
                                        .then(
                                            if (currentTab < 0) Modifier
                                            else
                                                Modifier.liveSwipe(
                                                    route,
                                                    setOf(app.auralis.domain.SwipeAxis.Horizontal),
                                                    edge = 28.dp,
                                                    onDrag = { _, offset ->
                                                        val possible =
                                                            if (offset < 0) currentTab < tabPaths.lastIndex
                                                            else currentTab > 0
                                                        // Follows the finger; at the first or last tab it only stretches a little.
                                                        tabJob?.cancel()
                                                        tabDrag = if (p.reducedMotion) 0f else offset * if (possible) .5f else .12f
                                                    },
                                                    onRelease = { _, offset, velocity, width ->
                                                        val target =
                                                            currentTab +
                                                                app.auralis.domain.swipeCommit(offset, velocity, width)
                                                        tabJob =
                                                            gestureScope.launch {
                                                                if (target != currentTab && target in tabPaths.indices) {
                                                                    haptics.performHapticFeedback(
                                                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                                                    )
                                                                    swipeStart = (tabDrag / width.coerceAtLeast(1f)).coerceIn(-.5f, .5f)
                                                                    openTab(nav, vm, tabPaths[target])
                                                                    tabDrag = 0f
                                                                    kotlinx.coroutines.delay(450)
                                                                    swipeStart = 0f
                                                                } else {
                                                                    animate(
                                                                        tabDrag,
                                                                        0f,
                                                                        velocity * .5f,
                                                                        spring(dampingRatio = .8f, stiffness = Spring.StiffnessMediumLow),
                                                                    ) { value, _ -> tabDrag = value }
                                                                }
                                                            }
                                                    },
                                                )
                                        )
                                        .graphicsLayer {
                                            translationX = tabDrag
                                        }
                                ) {
                                    NavHost(
                                        nav,
                                        startDestination = "home",
                                        enterTransition = {
                                            val from = tabIndex(initialState.destination.route)
                                            val to = tabIndex(targetState.destination.route)
                                            if (p.reducedMotion) EnterTransition.None
                                            else if (from >= 0 && to >= 0 && from != to) tabEnter(from, to)
                                            else if (targetState.destination.route == "player")
                                                slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it }
                                            else fadeIn(tween(220))
                                        },
                                        exitTransition = {
                                            val from = tabIndex(initialState.destination.route)
                                            val to = tabIndex(targetState.destination.route)
                                            if (p.reducedMotion) ExitTransition.None
                                            else if (from >= 0 && to >= 0 && from != to) tabExit(from, to)
                                            else if (targetState.destination.route == "player") ExitTransition.KeepUntilTransitionsFinished
                                            else fadeOut(tween(180))
                                        },
                                        popEnterTransition = {
                                            val from = tabIndex(initialState.destination.route)
                                            val to = tabIndex(targetState.destination.route)
                                            if (p.reducedMotion) EnterTransition.None
                                            else if (from >= 0 && to >= 0 && from != to) tabEnter(from, to)
                                            else fadeIn(tween(240))
                                        },
                                        popExitTransition = {
                                            val from = tabIndex(initialState.destination.route)
                                            val to = tabIndex(targetState.destination.route)
                                            if (p.reducedMotion) ExitTransition.None
                                            else if (from >= 0 && to >= 0 && from != to) tabExit(from, to)
                                            else if (initialState.destination.route == "player")
                                                slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it }
                                            else fadeOut(tween(180))
                                        },
                                    ) {
                                        composable("home") {
                                            HomeScreen(
                                                vm,
                                                { nav.navigate("detail/$it") },
                                                { importMenu = true },
                                                { nav.navigate("player") },
                                                { nav.navigate("library") },
                                                { nav.navigate("goals") },
                                            )
                                        }
                                        composable("goals") {
                                            CenteredScreen(840.dp) {
                                                GoalsScreen(
                                                    vm,
                                                    back = { nav.popBackStack() },
                                                    changeGoal = { nav.navigate("preferences/Goals") },
                                                    keepListening = {
                                                        nav.navigate(if (active != null) "player" else "library")
                                                    },
                                                )
                                            }
                                        }
                                        composable("library") {
                                            LibraryScreen(
                                                vm,
                                                { nav.navigate("detail/$it") },
                                                { importMenu = true },
                                                { id ->
                                                    vm.load(id)
                                                    nav.navigate("player")
                                                },
                                            )
                                        }
                                        composable("statistics") {
                                            StatisticsScreen(
                                                vm,
                                                { nav.popBackStack() },
                                                { nav.navigate("detail/$it") },
                                            )
                                        }
                                        composable("settings") {
                                            ProfileScreen(vm) { nav.navigate(it) }
                                        }
                                        composable("preferences") {
                                            CenteredScreen(780.dp) {
                                                SettingsScreen(vm, back = { nav.popBackStack() })
                                            }
                                        }
                                        composable("preferences/{section}") { entry ->
                                            CenteredScreen(780.dp) {
                                                SettingsScreen(
                                                    vm,
                                                    entry.arguments?.getString("section") ?: "Overview",
                                                ) {
                                                    nav.popBackStack()
                                                }
                                            }
                                        }
                                        composable("achievements") {
                                            TrophyRoom(vm) { nav.popBackStack() }
                                        }
                                        composable("ranks") {
                                            CenteredScreen(900.dp) {
                                                RanksScreen(vm) { nav.popBackStack() }
                                            }
                                        }
                                        composable("history") {
                                            HistoryScreen(
                                                vm,
                                                { nav.popBackStack() },
                                                { nav.navigate("detail/$it") },
                                            )
                                        }
                                        composable("detail/{id}") { entry ->
                                            library
                                                .firstOrNull {
                                                    it.book.id == entry.arguments?.getString("id")
                                                }
                                                ?.let {
                                                    DetailScreen(
                                                        vm,
                                                        it,
                                                        { nav.popBackStack() },
                                                        {
                                                            vm.load(it.book.id)
                                                            nav.navigate("player")
                                                        },
                                                        library,
                                                        { id -> nav.navigate("detail/$id") },
                                                        { id -> nav.navigate("pdf/$id") },
                                                    )
                                                }
                                        }
                                        composable("pdf/{id}") { entry ->
                                            library.firstOrNull {
                                                it.book.id == entry.arguments?.getString("id")
                                            }?.let { pdfBook ->
                                                PdfReaderScreen(vm, pdfBook) { nav.popBackStack() }
                                            }
                                        }
                                        composable("player") {
                                            if (active != null)
                                                CompositionLocalProvider(
                                                    LocalArtworkVisibility provides this
                                                ) {
                                                    PlayerScreen(
                                                        vm,
                                                        active,
                                                        back = { nav.popBackStack() },
                                                        openDetails = {
                                                            nav.navigate("detail/${active.book.id}") {
                                                                popUpTo("player") { inclusive = true }
                                                            }
                                                        },
                                                        openPdf = { nav.navigate("pdf/${active.book.id}") },
                                                    )
                                                }
                                            else
                                                Empty(
                                                    "Choose your next chapter",
                                                    "Open a book from your library.",
                                                    "Library",
                                                ) {
                                                    nav.navigate("library")
                                                }
                                        }
                                    }
                                    if (busy)
                                        LinearProgressIndicator(
                                            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                                        )
                                }
                            }
                        }
                    }
                    if (importMenu)
                        AlertDialog(
                            onDismissRequest = { importMenu = false },
                            title = { Text("Bring your books") },
                            text = {
                                Column {
                                    Text(
                                        "Your audio stays where it is. Auralis remembers secure access to the files you choose."
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    FilledTonalButton(
                                        {
                                            importMenu = false
                                            files.launch(
                                                arrayOf(
                                                    "audio/*",
                                                    "application/ogg",
                                                    "application/octet-stream",
                                                )
                                            )
                                        },
                                        Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Choose audio files")
                                    }
                                    FilledTonalButton(
                                        {
                                            importMenu = false
                                            folder.launch(null)
                                        },
                                        Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Choose a folder")
                                    }
                                    TextButton({
                                        importMenu = false
                                        vm.rescan()
                                    }) {
                                        Text("Rescan remembered folders")
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton({ importMenu = false }) { Text("Close") }
                            },
                        )
                    if (review != null) ImportReviewDialog(review!!, vm)
                }
            }
        }
    }
}

@Composable
private fun ImportReviewDialog(review: app.auralis.importer.ImportReview, vm: AuralisViewModel) {
    var titles by remember(review) { mutableStateOf(review.groups.map { it.title }) }
    var split by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.review.value = null },
        title = { Text("Review your import") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${review.groups.sumOf { it.files.size }} audio files • ${review.duplicates} already imported"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(split, { split = it })
                    Text("Import each file as a separate book")
                }
                review.groups.forEachIndexed { i, g ->
                    OutlinedTextField(
                        titles[i],
                        { value -> titles = titles.toMutableList().also { it[i] = value } },
                        label = { Text("Book title • ${g.files.size} files") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        g.files.joinToString("\n") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (review.groups.isEmpty())
                    Text(
                        "No new supported audio files found. Choose MP3, M4B, M4A, AAC, FLAC, OGG, OPUS or WAV."
                    )
                review.errors.forEach {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    vm.commit(review.groups.mapIndexed { i, g -> g.copy(title = titles[i]) }, split)
                },
                enabled = review.groups.isNotEmpty(),
            ) {
                Text("Add to library")
            }
        },
        dismissButton = { TextButton({ vm.review.value = null }) { Text("Cancel") } },
    )
}

/** Collect the fast playback clock only where progress is actually drawn. */
@Composable
private fun LiveMiniPlayer(
    vm: AuralisViewModel,
    book: LibraryBook,
    reduced: Boolean,
    open: () -> Unit,
    toggle: () -> Unit,
    skip: () -> Unit,
    seconds: Int,
) {
    val playback by vm.player.collectAsStateWithLifecycle()
    val live =
        remember(book, playback) {
            book.withPlayback(
                playback.mediaId,
                playback.position,
                playback.speed,
                System.currentTimeMillis(),
            )
        }
    PremiumMiniPlayer(live, playback, reduced, open, toggle, skip, seconds, skipBack = { vm.skip(false) })
}

/**
 * A tab always opens on its own main screen. Its saved state (scroll, sort, shelf, filters) is
 * restored, but a book page opened from it is closed, and Library closes an open series or
 * collection. Back still steps through pages as before.
 */
private fun openTab(
    nav: androidx.navigation.NavHostController,
    vm: AuralisViewModel,
    path: String,
) {
    nav.navigate(path) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    nav.popBackStack(path, inclusive = false)
    if (path == "library") vm.requestLibraryRoot()
}
