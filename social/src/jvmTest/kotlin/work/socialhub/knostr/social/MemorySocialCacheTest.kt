package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.internal.MemorySocialCache
import work.socialhub.knostr.social.internal.SocialMapper
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class MemorySocialCacheTest {
    @Test
    fun largeBatchIsEvictedToConfiguredBound() = runBlocking {
        val ids = (0 until 6_001).map { it.toString(16).padStart(64, '0') }
        val notes = ids.map { id ->
            SocialMapper.toNote(
                NostrEvent(
                    id = id,
                    pubkey = "1".repeat(64),
                    createdAt = 1,
                    kind = EventKind.TEXT_NOTE,
                    content = "",
                    sig = "",
                )
            )
        }
        val cache = MemorySocialCache(NostrSocialConfig())

        cache.put(SocialDataBatch(notes = notes))
        val cached = cache.get(SocialDataRequest(noteIds = ids))

        assertEquals(5_000, cached.notes.size)
    }
}
