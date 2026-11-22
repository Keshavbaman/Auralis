package app.auralis.cast

import kotlinx.coroutines.flow.MutableStateFlow

/** Load Google's optional discovery SDK only after the listener explicitly opens Cast. */
object CastActivation {
    val requested = MutableStateFlow(false)
}
