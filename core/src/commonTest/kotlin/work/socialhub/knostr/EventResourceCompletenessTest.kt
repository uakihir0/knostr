package work.socialhub.knostr

import kotlinx.coroutines.test.runTest
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.internal.EventResourceImpl
import work.socialhub.knostr.relay.RelayPool
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventResourceCompletenessTest {
    @Test
    fun responseIsCompleteByDefault() {
        assertTrue(Response<List<NostrEvent>>(listOf()).isComplete)
    }

    @Test
    fun queryIsIncompleteWhenEoseTimesOut() = runTest {
        val config = NostrConfig().apply { queryTimeoutMs = 1 }
        val resource = EventResourceImpl(config, RelayPool())

        val response = resource.queryEvents(listOf(NostrFilter(kinds = listOf(EventKind.TEXT_NOTE))))

        assertFalse(response.isComplete)
    }
}
