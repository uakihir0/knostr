package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.api.FeedResource
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrThread
import work.socialhub.knostr.util.Bech32
import work.socialhub.knostr.util.Hex
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class FeedResourceImpl(
    private val nostr: Nostr,
) : FeedResource {

    override suspend fun getHomeFeed(since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get home feed")

        // First, get the follow list (kind:3)
        val followFilter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.FOLLOW_LIST),
            limit = 1,
        )
        val followResponse = nostr.events().queryEvents(listOf(followFilter))
        val followPubkeys = followResponse.data
            .firstOrNull()
            ?.let { SocialMapper.toFollowList(it) }
            ?: listOf()

        if (followPubkeys.isEmpty()) {
            return Response(listOf())
        }

        // Then, get posts from followed users
        val feedFilter = NostrFilter(
            authors = followPubkeys,
            kinds = listOf(EventKind.TEXT_NOTE),
            since = since,
            until = until,
            limit = limit,
        )
        val feedResponse = nostr.events().queryEvents(listOf(feedFilter))
        var notes = feedResponse.data.map { SocialMapper.toNote(it) }
        if (excludeSensitive) {
            notes = notes.filterNot { it.isSensitive }
        }
        if (notes.isNotEmpty()) {
            populateLikeCounts(notes)
        }

        return Response(notes)
    }

    override suspend fun getNote(eventId: String): Response<NostrNote> {
        return getNoteInternal(eventId, visited = mutableSetOf())
    }

    private suspend fun getNoteInternal(eventId: String, visited: MutableSet<String>): Response<NostrNote> {
        if (eventId in visited) {
            throw NostrException("Circular quote reference detected: $eventId")
        }
        visited.add(eventId)

        val filter = NostrFilter(
            ids = listOf(eventId),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull()
            ?: throw NostrException("Note not found: $eventId")
        val note = SocialMapper.toNote(event)

        // Fetch reactions to populate likeCount
        val reactionFilter = NostrFilter(
            eTags = listOf(eventId),
            kinds = listOf(EventKind.REACTION),
            limit = 1000,
        )
        val reactionResponse = nostr.events().queryEvents(listOf(reactionFilter))
        note.likeCount = SocialMapper.countLikes(reactionResponse.data)

        // Resolve quoted note if q-tag present
        if (note.quotedEventId != null) {
            try {
                val quotedResponse = getNoteInternal(note.quotedEventId!!, visited)
                note.quotedNote = quotedResponse.data
            } catch (_: Exception) {
                // Ignore if quoted note not found or circular reference
            }
        }

        return Response(note)
    }

    override suspend fun getUserFeed(pubkey: String, since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.TEXT_NOTE),
            since = since,
            until = until,
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        var notes = response.data.map { SocialMapper.toNote(it) }
        if (excludeSensitive) {
            notes = notes.filterNot { it.isSensitive }
        }
        if (notes.isNotEmpty()) {
            populateLikeCounts(notes)
        }
        return Response(notes)
    }

    override suspend fun getMentions(since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get mentions")

        val filter = NostrFilter(
            pTags = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.TEXT_NOTE),
            since = since,
            until = until,
            limit = limit,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        var notes = response.data.map { SocialMapper.toNote(it) }
        if (excludeSensitive) {
            notes = notes.filterNot { it.isSensitive }
        }
        if (notes.isNotEmpty()) {
            populateLikeCounts(notes)
        }
        return Response(notes)
    }

    override suspend fun getThread(eventId: String): Response<NostrThread> {
        val thread = NostrThread()

        // Fetch the target note
        val targetFilter = NostrFilter(
            ids = listOf(eventId),
            kinds = listOf(EventKind.TEXT_NOTE),
            limit = 1,
        )
        val targetResponse = nostr.events().queryEvents(listOf(targetFilter))
        val targetEvent = targetResponse.data.firstOrNull()
            ?: throw NostrException("Note not found: $eventId")

        thread.rootNote = SocialMapper.toNote(targetEvent)

        // Walk ancestors (NIP-10 e-tags: root and reply markers)
        val ancestors = mutableListOf<NostrNote>()
        val visited = mutableSetOf(eventId)
        var currentEvent = targetEvent
        for (i in 0 until 25) { // max depth
            val parentId = findReplyParent(currentEvent) ?: break
            if (parentId in visited) break
            visited.add(parentId)

            val parentFilter = NostrFilter(
                ids = listOf(parentId),
                kinds = listOf(EventKind.TEXT_NOTE),
                limit = 1,
            )
            val parentResponse = nostr.events().queryEvents(listOf(parentFilter))
            val parentEvent = parentResponse.data.firstOrNull() ?: break
            ancestors.add(0, SocialMapper.toNote(parentEvent))
            currentEvent = parentEvent
        }

        // Fetch descendants (replies to this note)
        val replyFilter = NostrFilter(
            eTags = listOf(eventId),
            kinds = listOf(EventKind.TEXT_NOTE),
            limit = 100,
        )
        val replyResponse = nostr.events().queryEvents(listOf(replyFilter))
        val descendants = replyResponse.data
            .filter { it.id != eventId }
            .map { SocialMapper.toNote(it) }
            .sortedBy { it.createdAt }

        thread.replies = ancestors + descendants
        return Response(thread)
    }

    /** Extract the parent event ID from NIP-10 e-tags */
    private fun findReplyParent(event: NostrEvent): String? {
        val eTags = event.tags.filter { it.size >= 2 && it[0] == "e" }
        if (eTags.isEmpty()) return null

        // Prefer marked tags (NIP-10)
        val replyTag = eTags.find { it.size >= 4 && it[3] == "reply" }
        if (replyTag != null) return replyTag[1]

        val rootTag = eTags.find { it.size >= 4 && it[3] == "root" }
        if (rootTag != null) return rootTag[1]

        // Fallback: positional (last e-tag is reply target if multiple, only e-tag is root)
        return if (eTags.size == 1) eTags[0][1] else eTags.last()[1]
    }

    /** Populate likeCount for a list of notes by fetching reactions */
    private suspend fun populateLikeCounts(notes: List<NostrNote>) {
        if (notes.isEmpty()) return

        // Collect all event IDs
        val eventIds = notes.map { it.event.id }

        // Fetch reactions with a limit proportional to note count
        // (cap at 5000 to avoid overwhelming relays)
        val reactionLimit = minOf(eventIds.size * 200, 5000)
        val reactionFilter = NostrFilter(
            eTags = eventIds,
            kinds = listOf(EventKind.REACTION),
            limit = reactionLimit,
        )
        val reactionResponse = nostr.events().queryEvents(listOf(reactionFilter))

        // Group reactions by target event ID
        val reactionsByEvent = reactionResponse.data
            .groupBy { SocialMapper.getReactionTarget(it) ?: "" }

        // Populate likeCount for each note
        for (note in notes) {
            val reactions = reactionsByEvent[note.event.id] ?: listOf()
            note.likeCount = SocialMapper.countLikes(reactions)
        }
    }

    override suspend fun post(content: String, tags: List<List<String>>, contentWarning: String?, expiry: Long?, sensitive: Boolean): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to post")

        val allTags = tags.toMutableList()
        if (contentWarning != null) {
            allTags.add(listOf("content-warning", contentWarning))
        }
        if (expiry != null) {
            allTags.add(listOf("expiration", expiry.toString()))
        }
        if (sensitive && contentWarning == null) {
            allTags.add(listOf("content-warning"))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.TEXT_NOTE,
            tags = allTags,
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun reply(content: String, replyToEventId: String, rootEventId: String?, contentWarning: String?, expiry: Long?, sensitive: Boolean): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to reply")

        // NIP-10: build e-tags with root/reply markers
        val tags = mutableListOf<List<String>>()
        val effectiveRootId = rootEventId ?: replyToEventId
        tags.add(listOf("e", effectiveRootId, "", "root"))
        if (effectiveRootId != replyToEventId) {
            tags.add(listOf("e", replyToEventId, "", "reply"))
        }
        if (contentWarning != null) {
            tags.add(listOf("content-warning", contentWarning))
        }
        if (expiry != null) {
            tags.add(listOf("expiration", expiry.toString()))
        }
        if (sensitive && contentWarning == null) {
            tags.add(listOf("content-warning"))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.TEXT_NOTE,
            tags = tags,
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun repost(eventId: String): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to repost")

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.REPOST,
            tags = listOf(listOf("e", eventId)),
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun quoteRepost(eventId: String, comment: String, contentWarning: String?, expiry: Long?, sensitive: Boolean): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to quote repost")

        val tags = mutableListOf<List<String>>()
        tags.add(listOf("q", eventId))
        if (contentWarning != null) {
            tags.add(listOf("content-warning", contentWarning))
        }
        if (expiry != null) {
            tags.add(listOf("expiration", expiry.toString()))
        }
        if (sensitive && contentWarning == null) {
            tags.add(listOf("content-warning"))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.TEXT_NOTE,
            tags = tags,
            content = comment,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun delete(eventId: String, reason: String): Response<Boolean> {
        return nostr.events().deleteEvent(eventId, reason)
    }

    override suspend fun getUserLikesFeed(pubkey: String, since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        // Query kind:7 (reaction) events by the user
        val reactionFilter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.REACTION),
            since = since,
            until = until,
            limit = limit * 3, // Fetch more to account for non-like reactions
        )
        val reactionResponse = nostr.events().queryEvents(listOf(reactionFilter))

        // Filter only likes (empty content, "+", or heart) and extract target event IDs
        val targetEventIds = reactionResponse.data
            .filter { event ->
                val content = event.content.trim()
                content.isEmpty() || content == "+" || content == "\u2764\ufe0f" || content == "\u2764"
            }
            .mapNotNull { event ->
                event.tags.lastOrNull { it.size >= 2 && it[0] == "e" }?.get(1)
            }
            .distinct()
            .take(limit)

        if (targetEventIds.isEmpty()) {
            return Response(listOf())
        }

        // Fetch the actual notes
        val noteFilter = NostrFilter(
            ids = targetEventIds,
            kinds = listOf(EventKind.TEXT_NOTE),
            limit = targetEventIds.size,
        )
        val noteResponse = nostr.events().queryEvents(listOf(noteFilter))
        var notes = noteResponse.data.map { SocialMapper.toNote(it) }
        if (excludeSensitive) {
            notes = notes.filterNot { it.isSensitive }
        }

        return Response(notes)
    }

    override suspend fun getUserMediaFeed(pubkey: String, since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        // Query kind:1 events by the user
        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(EventKind.TEXT_NOTE),
            since = since,
            until = until,
            limit = limit * 2, // Fetch more to filter for media
        )
        val response = nostr.events().queryEvents(listOf(filter))

        // Filter for notes with imeta tags (NIP-94) or image URLs in content
        var mediaNotes = response.data
            .filter { event ->
                event.tags.any { it.size >= 2 && it[0] == "imeta" } ||
                    event.content.contains(Regex("https?://\\S+\\.(jpg|jpeg|png|gif|webp|mp4|webm)", RegexOption.IGNORE_CASE))
            }
            .map { SocialMapper.toNote(it) }

        if (excludeSensitive) {
            mediaNotes = mediaNotes.filterNot { it.isSensitive }
        }
        mediaNotes = mediaNotes.take(limit)

        return Response(mediaNotes)
    }

    override suspend fun getNoteByNpub(noteId: String): Response<NostrNote> {
        // Decode note1... bech32 to get event ID
        val (hrp, data) = Bech32.decode(noteId)
        if (hrp != "note") {
            throw NostrException("Invalid note bech32: $noteId")
        }
        val eventId = Hex.encode(data)
        return getNote(eventId)
    }

    override fun getHomeFeedBlocking(since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        return toBlocking { getHomeFeed(since, until, limit, excludeSensitive) }
    }

    override fun getNoteBlocking(eventId: String): Response<NostrNote> {
        return toBlocking { getNote(eventId) }
    }

    override fun getUserFeedBlocking(pubkey: String, since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        return toBlocking { getUserFeed(pubkey, since, until, limit, excludeSensitive) }
    }

    override fun getMentionsBlocking(since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        return toBlocking { getMentions(since, until, limit, excludeSensitive) }
    }

    override fun getThreadBlocking(eventId: String): Response<NostrThread> {
        return toBlocking { getThread(eventId) }
    }

    override fun postBlocking(content: String, tags: List<List<String>>, contentWarning: String?, expiry: Long?, sensitive: Boolean): Response<NostrEvent> {
        return toBlocking { post(content, tags, contentWarning, expiry, sensitive) }
    }

    override fun replyBlocking(content: String, replyToEventId: String, rootEventId: String?, contentWarning: String?, expiry: Long?, sensitive: Boolean): Response<NostrEvent> {
        return toBlocking { reply(content, replyToEventId, rootEventId, contentWarning, expiry, sensitive) }
    }

    override fun repostBlocking(eventId: String): Response<NostrEvent> {
        return toBlocking { repost(eventId) }
    }

    override fun quoteRepostBlocking(eventId: String, comment: String, contentWarning: String?, expiry: Long?, sensitive: Boolean): Response<NostrEvent> {
        return toBlocking { quoteRepost(eventId, comment, contentWarning, expiry, sensitive) }
    }

    override fun deleteBlocking(eventId: String, reason: String): Response<Boolean> {
        return toBlocking { delete(eventId, reason) }
    }

    override fun getUserLikesFeedBlocking(pubkey: String, since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        return toBlocking { getUserLikesFeed(pubkey, since, until, limit, excludeSensitive) }
    }

    override fun getUserMediaFeedBlocking(pubkey: String, since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        return toBlocking { getUserMediaFeed(pubkey, since, until, limit, excludeSensitive) }
    }

    override fun getNoteByNpubBlocking(noteId: String): Response<NostrNote> {
        return toBlocking { getNoteByNpub(noteId) }
    }
}
