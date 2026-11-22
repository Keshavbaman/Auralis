package app.auralis.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.*

class AuralisCastOptions : OptionsProvider {
    override fun getCastOptions(context: Context) =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setResumeSavedSession(false)
            .setEnableReconnectionService(false)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setRemoteToLocalEnabled(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> =
        emptyList()
}
