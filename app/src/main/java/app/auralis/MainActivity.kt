package app.auralis

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.auralis.branding.BrandLaunchHost
import app.auralis.data.*
import app.auralis.playback.PlaybackStatus
import app.auralis.ui.AuralisRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var preferenceStore: PreferenceStore
    @Inject lateinit var playbackStatus: PlaybackStatus

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val cold =
            (application as AuralisApp)
                .launchPolicy
                .claim(savedInstanceState != null, playbackStatus.serviceAlive.value)
        val started = SystemClock.uptimeMillis()
        var startupPreferences by mutableStateOf<Preferences?>(null)
        lifecycleScope.launch { startupPreferences = preferenceStore.flow.first() }
        splash.setKeepOnScreenCondition {
            startupPreferences == null && SystemClock.uptimeMillis() - started < 150
        }
        splash.setOnExitAnimationListener { it.remove() }
        val scale =
            if (ValueAnimator.areAnimatorsEnabled())
                Settings.Global.getFloat(
                    contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            else 0f
        setContent {
            BrandLaunchHost(
                cold,
                startupPreferences != null,
                startupPreferences?.reducedMotion ?: true,
                scale,
            ) {
                AuralisRoot(viewModel())
            }
        }
    }
}
