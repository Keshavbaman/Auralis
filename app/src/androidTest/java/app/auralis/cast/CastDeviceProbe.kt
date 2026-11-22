@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.cast

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.session.*
import androidx.mediarouter.media.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.playback.PlaybackService
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit opt-in only: tests the owner's real Mi Smart Speaker. Never part of unattended tests.
 */
@RunWith(AndroidJUnit4::class)
class CastDeviceProbe {
    @Test
    fun liveSpeakerHandoff() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveCast") == "true")
        val activity =
            androidx.test.core.app.ActivityScenario.launch(app.auralis.MainActivity::class.java)
        val chapterIndex = requireNotNull(args.getString("chapterIndex")).toInt()
        val originalPosition = requireNotNull(args.getString("positionMs")).toLong()
        val fileStart = requireNotNull(args.getString("fileStartMs")).toLong()
        val chapterCount = requireNotNull(args.getString("chapterCount")).toInt()
        val context = ins.targetContext
        fun main(action: () -> Unit) = ins.runOnMainSync(action)
        fun until(seconds: Int, condition: () -> Boolean) {
            val end = System.currentTimeMillis() + seconds * 1000
            while (System.currentTimeMillis() < end) {
                var good = false
                main { good = condition() }
                if (good) return
                Thread.sleep(250)
            }
            fail("Timed out waiting for receiver state")
        }
        lateinit var controllerFuture:
            com.google.common.util.concurrent.ListenableFuture<MediaController>
        main {
            controllerFuture =
                MediaController.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                    )
                    .buildAsync()
        }
        val player = controllerFuture.get(15, TimeUnit.SECONDS)
        lateinit var load: com.google.common.util.concurrent.ListenableFuture<SessionResult>
        main {
            load =
                player.sendCustomCommand(
                    SessionCommand("load", Bundle.EMPTY),
                    Bundle().apply {
                        putString("id", args.getString("bookId"))
                        putBoolean("play", false)
                    },
                )
        }
        assertEquals(SessionResult.RESULT_SUCCESS, load.get(15, TimeUnit.SECONDS).resultCode)
        main {
            player.pause()
            player.seekTo(chapterIndex, originalPosition)
            player.setPlaybackSpeed(1.2f)
        }
        until(10) {
            player.currentMediaItemIndex == chapterIndex &&
                kotlin.math.abs(player.currentPosition - originalPosition) < 2000
        }
        lateinit var router: MediaRouter
        lateinit var cast: CastContext
        val callback = object : MediaRouter.Callback() {}
        main {
            CastActivation.requested.value = true
            cast = CastContext.getSharedInstance(context)
            router = MediaRouter.getInstance(context)
            router.addCallback(
                MediaRouteSelector.Builder()
                    .addControlCategory(
                        CastMediaControlIntent.categoryForCast(
                            CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                        )
                    )
                    .build(),
                callback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
            )
        }
        var chosen: MediaRouter.RouteInfo? = null
        try {
            until(20) {
                chosen =
                    router.routes.firstOrNull { r ->
                        val d = CastDevice.getFromBundle(r.extras)
                        d != null && (d.modelName.equals("Mi Smart Speaker", true))
                    }
                chosen != null
            }
            main {
                println(
                    "CAST_DEVICE name=${chosen!!.name} model=${CastDevice.getFromBundle(chosen!!.extras)?.modelName}"
                )
                chosen!!.select()
            }
            until(30) { cast.sessionManager.currentCastSession?.isConnected == true }
            until(65) {
                val r = cast.sessionManager.currentCastSession?.remoteMediaClient
                if (System.currentTimeMillis() % 3000 < 270)
                    println(
                        "CAST_WAIT index=${player.currentMediaItemIndex} count=${player.mediaItemCount} position=${player.currentPosition} state=${player.playbackState} receiver=${r?.playerState} receiverPosition=${r?.approximateStreamPosition}"
                    )
                r?.mediaStatus != null &&
                    player.currentMediaItemIndex == chapterIndex &&
                    player.deviceInfo.playbackType ==
                        androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE
            }
            main {
                println(
                    "CAST_LOADED index=${player.currentMediaItemIndex} position=${player.currentPosition} duration=${player.duration}"
                )
                assertTrue(kotlin.math.abs(player.currentPosition - originalPosition) < 3000)
                player.play()
            }
            until(10) { kotlin.math.abs(player.playbackParameters.speed - 1.2f) < .01f }
            main {
                context.startActivity(
                    android.content
                        .Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_HOME)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            var start = 0L
            main { start = player.currentPosition }
            until(25) {
                player.isPlaying &&
                    player.currentPosition >= start + 5000 &&
                    cast.sessionManager.currentCastSession?.remoteMediaClient?.isPlaying == true
            }
            main {
                player.pause()
                println(
                    "CAST_PLAYED position=${player.currentPosition} speed=${player.playbackParameters.speed}"
                )
            }
            until(8) {
                !player.isPlaying &&
                    cast.sessionManager.currentCastSession?.remoteMediaClient?.isPaused == true
            }
            var expected = 0L
            main {
                expected = player.currentPosition + 10000
                player.seekTo(expected)
            }
            until(10) {
                val receiver =
                    cast.sessionManager.currentCastSession
                        ?.remoteMediaClient
                        ?.approximateStreamPosition ?: 0
                kotlin.math.abs(player.currentPosition - expected) < 2000 &&
                    kotlin.math.abs(receiver - fileStart - expected) < 2000
            }
            Thread.sleep(
                1500
            ) // Let the background service checkpoint the receiver's confirmed position.
            main {
                println("CAST_SEEK position=${player.currentPosition}")
                cast.sessionManager.endCurrentSession(true)
            }
            until(15) { cast.sessionManager.currentCastSession == null }
            until(10) {
                player.mediaItemCount == chapterCount &&
                    player.currentMediaItemIndex == chapterIndex &&
                    kotlin.math.abs(player.currentPosition - expected) < 3000
            }
            main {
                player.pause()
                println(
                    "CAST_RETURN index=${player.currentMediaItemIndex} position=${player.currentPosition}"
                )
            }
        } finally {
            main {
                router.removeCallback(callback)
                cast.sessionManager.endCurrentSession(true)
                player.pause()
                player.release()
            }
            activity.close()
        }
    }
}
