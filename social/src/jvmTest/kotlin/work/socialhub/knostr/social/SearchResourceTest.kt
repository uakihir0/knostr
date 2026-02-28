package work.socialhub.knostr.social

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class SearchResourceTest : AbstractTest() {

    @Test
    fun testSearchNotes() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val response = social.search().searchNotes("nostr", limit = 10)
            val notes = response.data

            println("Search notes results: ${notes.size}")
            notes.forEach { note ->
                println("  [${note.noteId}] ${note.content.take(80)}")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }

    @Test
    fun testSearchUsers() = runBlocking {
        val social = social()
        val nostr = social.nostr()
        val scope = connectRelays(nostr)

        try {
            val response = social.search().searchUsers("nostr", limit = 10)
            val users = response.data

            println("Search users results: ${users.size}")
            users.forEach { user ->
                println("  ${user.name} (${user.npub.take(20)}...)")
            }
        } finally {
            disconnectRelays(nostr, scope)
        }
    }
}
