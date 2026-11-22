package app.auralis.cast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CastGold = Color(0xFFF0BD70)
private val CastDeep = Color(0xFF111216)
private val CastSurface = Color(0xFF1B1B20)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastRouteButton(modifier: Modifier = Modifier) {
    val activated by CastActivation.requested.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            CastActivation.requested.value = true
            showPicker = true
        },
        modifier = modifier.size(48.dp).testTag("cast-route-button"),
    ) {
        Icon(Icons.Rounded.Cast, "Cast to a speaker")
    }
    if (showPicker) CastDeviceSheet(
        activated = activated,
        onDismiss = { showPicker = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastDeviceSheet(activated: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val router = remember(context) { MediaRouter.getInstance(context) }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }
    val routes = remember { mutableStateListOf<MediaRouter.RouteInfo>() }
    var selected by remember { mutableStateOf(router.selectedRoute.takeIf { it.matchesSelector(selector) }) }
    var connecting by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun refresh() {
        routes.clear()
        routes.addAll(router.routes.filter { it.matchesSelector(selector) && it.isEnabled }.distinctBy { it.id })
        selected = router.selectedRoute.takeIf { it.matchesSelector(selector) }
        connecting = selected?.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
    }

    DisposableEffect(router, selector, activated, refreshToken) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                selected = route
                connecting = true
                refresh()
            }
            override fun onRouteConnected(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
                requestedRoute: MediaRouter.RouteInfo,
            ) {
                selected = route
                connecting = false
                onDismiss()
            }
        }
        val flags = MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or
            if (refreshToken > 0) MediaRouter.CALLBACK_FLAG_FORCE_DISCOVERY else 0
        router.addCallback(selector, callback, flags)
        refresh()
        onDispose { router.removeCallback(callback) }
    }

    LaunchedEffect(activated) {
        if (activated) {
            // Google Cast discovery can take a moment to publish the first receiver route.
            repeat(8) {
                refresh()
                if (routes.isNotEmpty()) return@LaunchedEffect
                delay(500)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = CastDeep,
        contentColor = Color(0xFFF6F1E8),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF24211E), CastDeep, CastDeep)))
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(3.dp).background(CastGold.copy(alpha = .75f), CircleShape))
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, "Close speaker picker", tint = Color(0xFFD7D0C4))
                }
            }
            Spacer(Modifier.height(8.dp))
            CastHero(isSearching = routes.isEmpty() && !connecting)
            Spacer(Modifier.height(12.dp))
            Text("Your listening room", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                if (connecting) "Connecting your audiobook to ${selected?.name ?: "your speaker"}…"
                else "Choose a speaker on this Wi-Fi network.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB7B2AA),
            )
            Spacer(Modifier.height(20.dp))
            when {
                connecting -> {
                    Surface(color = CastSurface, shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = CastGold, strokeWidth = 2.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selected?.name ?: "Cast speaker", fontWeight = FontWeight.SemiBold)
                                Text("Getting the story ready…", color = Color(0xFFB7B2AA), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                routes.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(routes, key = { it.id }) { route ->
                            val isSelected = route.isSelected || selected?.id == route.id
                            Surface(
                                onClick = {
                                    if (!isSelected) {
                                        selected = route
                                        connecting = true
                                        router.selectRoute(route)
                                    }
                                },
                                enabled = route.isEnabled,
                                color = if (isSelected) Color(0xFF29251F) else CastSurface,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(44.dp).background(CastGold.copy(alpha = .13f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Rounded.Speaker, null, tint = CastGold) }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(route.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (isSelected) "Listening here" else route.description?.takeIf(String::isNotBlank) ?: "Available on your network",
                                            color = Color(0xFFB7B2AA),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                        )
                                    }
                                    if (isSelected) Icon(Icons.Rounded.CheckCircle, "Connected", tint = CastGold)
                                    else Icon(Icons.Rounded.Wifi, "Available", tint = Color(0xFFAAA49A))
                                }
                            }
                        }
                    }
                    selected?.takeIf { it.isSelected }?.let { route ->
                        Spacer(Modifier.height(18.dp))
                        CastVolumeControl(route)
                    }
                }
                else -> {
                    Surface(color = CastSurface, shape = RoundedCornerShape(20.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(17.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = CastGold, strokeWidth = 2.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Finding nearby speakers", fontWeight = FontWeight.SemiBold)
                                Text("Keep your phone and speaker on the same Wi-Fi.", color = Color(0xFFB7B2AA), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { refreshToken++ },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Search again")
                    }
                }
            }
        }
    }
}

@Composable
private fun CastHero(isSearching: Boolean) {
    val transition = rememberInfiniteTransition(label = "cast discovery")
    val pulse by transition.animateFloat(
        initialValue = .82f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "speaker pulse",
    )
    Box(Modifier.fillMaxWidth().height(116.dp), contentAlignment = Alignment.Center) {
        repeat(2) { ring ->
            val scale = (pulse + ring * .25f).coerceAtMost(1.5f)
            val alpha = (1.15f - scale).coerceIn(.08f, .4f)
            Box(
                Modifier.size(72.dp).scale(scale)
                    .alpha(alpha)
                    .background(CastGold.copy(alpha = .2f), CircleShape)
            )
        }
        Box(
            Modifier.size(64.dp).background(
                Brush.linearGradient(listOf(Color(0xFFFFD99E), CastGold, Color(0xFFB77D38))),
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(if (isSearching) Icons.Rounded.Wifi else Icons.Rounded.Cast, null, tint = Color(0xFF24190B), modifier = Modifier.size(29.dp)) }
    }
}

@Composable
private fun CastVolumeControl(route: MediaRouter.RouteInfo) {
    val scope = rememberCoroutineScope()
    val maxVolume = route.volumeMax.coerceAtLeast(1)
    var volume by remember(route.id) { mutableFloatStateOf(route.volume.coerceIn(0, maxVolume).toFloat()) }
    var dragging by remember(route.id) { mutableStateOf(false) }
    var pendingUpdate by remember(route.id) { mutableStateOf<Job?>(null) }

    LaunchedEffect(route.id, route.volume, route.volumeMax) {
        if (!dragging) volume = route.volume.coerceIn(0, maxVolume).toFloat()
    }
    DisposableEffect(route.id) {
        onDispose { pendingUpdate?.cancel() }
    }

    Surface(color = Color(0xFF222126), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speaker volume", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${(volume / maxVolume * 100).roundToInt()}%",
                    color = CastGold,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            if (route.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeDown, "Quiet", tint = Color(0xFFB7B2AA), modifier = Modifier.size(20.dp))
                    Slider(
                        value = volume.coerceIn(0f, maxVolume.toFloat()),
                        onValueChange = { next ->
                            dragging = true
                            volume = next
                            pendingUpdate?.cancel()
                            pendingUpdate = scope.launch {
                                delay(90)
                                route.requestSetVolume(next.roundToInt())
                            }
                        },
                        onValueChangeFinished = {
                            pendingUpdate?.cancel()
                            route.requestSetVolume(volume.roundToInt())
                            dragging = false
                        },
                        valueRange = 0f..maxVolume.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = CastGold,
                            activeTrackColor = CastGold,
                            inactiveTrackColor = Color.White.copy(alpha = .16f),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Loud", tint = CastGold, modifier = Modifier.size(20.dp))
                }
            } else {
                Text(
                    "Adjust volume on the speaker.",
                    color = Color(0xFFB7B2AA),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
