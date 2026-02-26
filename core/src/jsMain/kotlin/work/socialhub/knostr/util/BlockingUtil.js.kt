package work.socialhub.knostr.util

import kotlinx.coroutines.CoroutineScope

actual fun <T> toBlocking(block: suspend CoroutineScope.() -> T): T {
    throw UnsupportedOperationException("Blocking calls are not supported on JavaScript platform.")
}
