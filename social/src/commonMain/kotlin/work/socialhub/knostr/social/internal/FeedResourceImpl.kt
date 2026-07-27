package work.socialhub.knostr.social.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.NostrSocialConfig
import work.socialhub.knostr.social.api.FeedResource
import work.socialhub.knostr.social.api.SocialCache
import work.socialhub.knostr.social.model.NostrNote
import work.socialhub.knostr.social.model.NostrNoteStats
import work.socialhub.knostr.social.model.NostrThread
import work.socialhub.knostr.social.model.NostrUser
import work.socialhub.knostr.social.model.SocialDataBatch
import work.socialhub.knostr.social.model.SocialDataRequest
import work.socialhub.knostr.util.Bech32
import work.socialhub.knostr.util.Hex
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class FeedResourceImpl(
    private val nostr: Nostr,
    private val config: NostrSocialConfig = NostrSocialConfig(),
    private val socialCache: SocialCache = MemorySocialCache(config),
    private val enrichment: EnrichmentResourceImpl = EnrichmentResourceImpl(nostr, socialCache, config),
) : FeedResource {
    private enum class InteractionKind {
        REPLY,
        REPOST,
    }

    private data class LocalInteraction(
        val targetEventId: String,
        val kind: InteractionKind,
    )

    private var cachedFollowList: List<String>? = null
    private var followListCachedAt: Long = 0
    private val localInteractions = LinkedHashMap<String, LocalInteraction>()
    private val localInteractionsMutex = Mutex()

    private suspend fun getFollowPubkeys(): Response<List<String>> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to get home feed")

        if (config.cacheFollowList) {
            val now = Clock.System.now().toEpochMilliseconds()
            val cached = cachedFollowList
            if (cached != null && (now - followListCachedAt) < config.followListCacheTtlMs) {
                return Response(cached)
            }
        }

        val followFilter = NostrFilter(
            authors = listOf(signer.getPublicKey()),
            kinds = listOf(EventKind.FOLLOW_LIST),
            limit = 1,
        )
        val followResponse = nostr.events().queryEvents(listOf(followFilter))
        val pubkeys = followResponse.data
            .firstOrNull()
            ?.let { SocialMapper.toFollowList(it) }
            ?: listOf()

        if (config.cacheFollowList && followResponse.isComplete && pubkeys.isNotEmpty()) {
            cachedFollowList = pubkeys
            followListCachedAt = Clock.System.now().toEpochMilliseconds()
        }

        return followResponse.withData(pubkeys)
    }

    fun invalidateFollowListCache() {
        cachedFollowList = null
        followListCachedAt = 0
    }

    override suspend fun getHomeFeed(since: Long?, until: Long?, limit: Int, excludeSensitive: Boolean): Response<List<NostrNote>> {
        val followResponse = getFollowPubkeys()
        val followPubkeys = followResponse.data

        if (followPubkeys.isEmpty()) {
            return followResponse.withData(listOf())
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
        cacheNotes(notes)
        if (excludeSensitive) {
            notes = notes.filterNot { it.isSensitive }
        }
        if (notes.isNotEmpty()) {
            populateQuotedNotes(notes)
            populateAuthors(notes)
            populateLikeCounts(notes)
        }

        return responseOf(notes, followResponse, feedResponse)
    }

    override suspend fun getNote(eventId: String): Response<NostrNote> {
        val response = getNoteInternal(eventId, visited = mutableSetOf())
        populateAuthors(listOf(response.data))
        populateLikeCounts(listOf(response.data))
        return response
    }

    private suspend fun getNoteInternal(eventId: String, visited: MutableSet<String>): Response<NostrNote> {
        if (eventId in visited) {
            throw NostrException("Circular quote reference detected: $eventId")
        }
        visited.add(eventId)

        val cached = cacheGet(SocialDataRequest(noteIds = listOf(eventId)))
            .notes.firstOrNull { it.event.id == eventId }
        if (cached != null) {
            resolveQuotedNote(cached, visited)
            return Response(cached)
        }

        val filter = NostrFilter(
            ids = listOf(eventId),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull()
            ?: throw NostrException("Note not found: $eventId")
        val note = SocialMapper.toNote(event)
        cachePut(SocialDataBatch(notes = listOf(note)))

        resolveQuotedNote(note, visited)

        return response.withData(note)
    }

    private suspend fun resolveQuotedNote(note: NostrNote, visited: MutableSet<String>) {
        val quotedEventId = note.quotedEventId ?: return
        if (note.quotedNote != null) return
        try {
            note.quotedNote = getNoteInternal(quotedEventId, visited).data
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            enrichment.requestMissing(SocialDataRequest(noteIds = listOf(quotedEventId)))
        }
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
        cacheNotes(notes)
        if (excludeSensitive) {
            notes = notes.filterNot { it.isSensitive }
        }
        if (notes.isNotEmpty()) {
            populateQuotedNotes(notes)
            populateAuthors(notes)
            populateLikeCounts(notes)
        }
        return response.withData(notes)
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
        cacheNotes(notes)
        if (excludeSensitive) {
            notes = notes.filterNot { it.isSensitive }
        }
        if (notes.isNotEmpty()) {
            populateQuotedNotes(notes)
            populateAuthors(notes)
            populateLikeCounts(notes)
        }
        return response.withData(notes)
    }

    override suspend fun getThread(eventId: String): Response<NostrThread> {
        val thread = NostrThread()
        val sourceResponses = mutableListOf<Response<*>>()

        // Fetch the target note
        val targetFilter = NostrFilter(
            ids = listOf(eventId),
            kinds = listOf(EventKind.TEXT_NOTE),
            limit = 1,
        )
        val targetResponse = nostr.events().queryEvents(listOf(targetFilter))
        sourceResponses.add(targetResponse)
        val targetEvent = targetResponse.data.firstOrNull()
            ?: throw NostrException("Note not found: $eventId")

        thread.rootNote = SocialMapper.toNote(targetEvent)
        cacheNotes(listOfNotNull(thread.rootNote))

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
            sourceResponses.add(parentResponse)
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
        sourceResponses.add(replyResponse)
        val descendants = replyResponse.data
            .filter { it.id != eventId }
            .map { SocialMapper.toNote(it) }
            .sortedBy { it.createdAt }
        cacheNotes(ancestors + descendants)

        thread.replies = ancestors + descendants
        thread.rootNote?.let {
            val allNotes = listOf(it) + thread.replies
            populateQuotedNotes(allNotes)
            populateAuthors(allNotes)
            populateLikeCounts(allNotes)
        }
        return responseOf(thread, *sourceResponses.toTypedArray())
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

    private suspend fun populateAuthors(notes: List<NostrNote>) {
        if (notes.isEmpty()) return

        val targets = mutableListOf<NostrNote>()
        fun collect(note: NostrNote?) {
            if (note == null || note in targets) return
            targets.add(note)
            collect(note.quotedNote)
        }
        notes.forEach { collect(it) }
        val allPubkeys = targets.map { it.event.pubkey }.distinct()
        if (allPubkeys.isEmpty()) return

        val resolved = cacheGet(SocialDataRequest(userPubkeys = allPubkeys))
            .users.associateByTo(mutableMapOf()) { it.pubkey }
        val uncached = mutableListOf<String>()

        for (pk in allPubkeys) {
            val cached = resolved[pk]
            if (cached != null) {
                resolved[pk] = cached
            } else {
                uncached.add(pk)
            }
        }

        if (uncached.isNotEmpty()) {
            val batchSize = 50
            for (batch in uncached.chunked(batchSize)) {
                val fetched = fetchProfileBatch(batch)
                resolved.putAll(fetched)
            }
        }

        val missing = allPubkeys.filter { it !in resolved }
        if (socialCache is MemorySocialCache && missing.isNotEmpty()) {
            socialCache.getStaleUsers(missing).forEach { resolved[it.pubkey] = it }
        }

        for (note in targets) {
            val author = resolved[note.event.pubkey]
            if (author != null) {
                note.author = author
            } else {
                note.author = NostrUser().apply {
                    pubkey = note.event.pubkey
                    npub = Bech32.encode("npub", Hex.decode(note.event.pubkey))
                    name = note.event.pubkey.take(8) + "..."
                }
            }
        }

        if (missing.isNotEmpty()) {
            enrichment.requestMissing(SocialDataRequest(userPubkeys = missing))
        }
    }

    private fun processMetadataEvents(events: List<NostrEvent>): Map<String, NostrUser> {
        return events
            .sortedByDescending { it.createdAt }
            .distinctBy { it.pubkey }
            .associate { it.pubkey to SocialMapper.toUser(it) }
    }

    /**
     * Fetch kind:0 metadata for a batch of pubkeys with one retry for missing profiles.
     * Relays sometimes return incomplete results; a single retry resolves most transient gaps.
     */
    private suspend fun fetchProfileBatch(pubkeys: List<String>): Map<String, NostrUser> {
        val filter = NostrFilter(
            authors = pubkeys,
            kinds = listOf(EventKind.METADATA),
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val fetched = processMetadataEvents(response.data).toMutableMap()
        val cacheable = mutableMapOf<String, NostrUser>()
        if (response.isComplete) {
            cacheable.putAll(fetched)
        }

        // Retry missing profiles, or the full batch when the first result was incomplete.
        val retryPubkeys = if (response.isComplete) {
            pubkeys.filter { it !in fetched }
        } else {
            pubkeys
        }
        if (retryPubkeys.isNotEmpty()) {
            val retryFilter = NostrFilter(
                authors = retryPubkeys,
                kinds = listOf(EventKind.METADATA),
            )
            try {
                val retryResponse = nostr.events().queryEvents(listOf(retryFilter))
                val retryFetched = processMetadataEvents(retryResponse.data)
                fetched.putAll(retryFetched)
                if (retryResponse.isComplete) {
                    cacheable.putAll(retryFetched)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Retry failed — proceed with what we have
            }
        }

        cachePut(SocialDataBatch(users = cacheable.values.toList()))
        val unresolved = pubkeys.filter { it !in cacheable }
        if (unresolved.isNotEmpty()) {
            enrichment.request(
                SocialDataRequest(userPubkeys = unresolved),
                forceRefresh = true,
            )
        }
        return fetched
    }

    /** Populate likeCount for a list of notes by fetching reactions */
    private suspend fun populateLikeCounts(notes: List<NostrNote>) {
        if (notes.isEmpty()) return

        val eventIds = notes.map { it.event.id }
        val cachedStats = cacheGet(SocialDataRequest(noteStatsEventIds = eventIds))
            .noteStats.associateBy { it.eventId }
        notes.forEach { note ->
            cachedStats[note.event.id]?.let { applyStats(note, it) }
        }

        val uncachedEventIds = eventIds.filter { it !in cachedStats }
        if (uncachedEventIds.isEmpty()) return

        val statsResponse = nostr.events().queryEvents(SocialStats.filters(uncachedEventIds))
        if (statsResponse.isComplete) {
            val stats = notes.filter { it.event.id in uncachedEventIds }.map { note ->
                SocialStats.calculate(note.event.id, statsResponse.data).also {
                    applyStats(note, it)
                }
            }
            cachePut(SocialDataBatch(noteStats = stats))
        } else {
            enrichment.requestMissing(SocialDataRequest(noteStatsEventIds = uncachedEventIds))
        }
    }

    private suspend fun populateQuotedNotes(notes: List<NostrNote>) {
        val unresolved = notes.filter { it.quotedEventId != null && it.quotedNote == null }
        val targets = unresolved.mapNotNull { it.quotedEventId }.distinct()
        if (targets.isEmpty()) return
        val cached = cacheGet(SocialDataRequest(noteIds = targets))
            .notes.associateBy { it.event.id }
        unresolved.forEach { note ->
            note.quotedEventId?.let { cached[it] }?.let { note.quotedNote = it }
        }
        val missing = targets.filter { it !in cached }
        if (missing.isNotEmpty()) {
            enrichment.requestMissing(SocialDataRequest(noteIds = missing))
        }
    }

    private fun applyStats(note: NostrNote, stats: NostrNoteStats) {
        note.likeCount = stats.likeCount
        note.replyCount = stats.replyCount
        note.repostCount = stats.repostCount
    }

    private suspend fun cacheNotes(notes: List<NostrNote>) {
        if (notes.isNotEmpty()) {
            cachePut(SocialDataBatch(notes = notes))
        }
    }

    private suspend fun cacheGet(request: SocialDataRequest): SocialDataBatch {
        return try {
            socialCache.get(request)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SocialDataBatch()
        }
    }

    private suspend fun cachePut(batch: SocialDataBatch) {
        if (batch.isEmpty()) return
        try {
            socialCache.put(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Cache failures must not fail the API request.
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

    override suspend fun reply(
        content: String,
        tags: List<List<String>>,
        replyToEventId: String,
        rootEventId: String?,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to reply")

        // NIP-10: build e-tags with root/reply markers
        val allTags = mutableListOf<List<String>>()
        val effectiveRootId = rootEventId ?: replyToEventId
        allTags.add(listOf("e", effectiveRootId, "", "root"))
        if (effectiveRootId != replyToEventId) {
            allTags.add(listOf("e", replyToEventId, "", "reply"))
        }
        allTags.addAll(tags)
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
        val published = nostr.events().publishEvent(signed)
        if (published.data) {
            rememberInteraction(signed.id, replyToEventId, InteractionKind.REPLY)
            SocialStats.adjustCached(socialCache, enrichment, replyToEventId) {
                NostrNoteStats(
                    eventId = it.eventId,
                    likeCount = it.likeCount,
                    replyCount = it.replyCount + 1,
                    repostCount = it.repostCount,
                )
            }
        }
        return Response(signed)
    }

    @Deprecated(
        message = "Use the overload that accepts tags",
        replaceWith = ReplaceWith(
            "reply(content, emptyList(), replyToEventId, rootEventId, contentWarning, expiry, sensitive)",
        ),
    )
    override suspend fun reply(
        content: String,
        replyToEventId: String,
        rootEventId: String?,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrEvent> {
        return reply(
            content = content,
            tags = emptyList(),
            replyToEventId = replyToEventId,
            rootEventId = rootEventId,
            contentWarning = contentWarning,
            expiry = expiry,
            sensitive = sensitive,
        )
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
        val published = nostr.events().publishEvent(signed)
        if (published.data) {
            rememberInteraction(signed.id, eventId, InteractionKind.REPOST)
            SocialStats.adjustCached(socialCache, enrichment, eventId) {
                NostrNoteStats(
                    eventId = it.eventId,
                    likeCount = it.likeCount,
                    replyCount = it.replyCount,
                    repostCount = it.repostCount + 1,
                )
            }
        }
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
        val response = nostr.events().deleteEvent(eventId, reason)
        if (response.data) {
            val interaction = takeInteraction(eventId)
            try {
                socialCache.remove(
                    SocialDataRequest(
                        noteIds = listOf(eventId),
                        noteStatsEventIds = listOf(eventId),
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Cache failures must not change the successful relay result.
            }
            when (interaction?.kind) {
                InteractionKind.REPLY -> {
                    SocialStats.adjustCached(socialCache, enrichment, interaction.targetEventId) {
                        NostrNoteStats(
                            eventId = it.eventId,
                            likeCount = it.likeCount,
                            replyCount = (it.replyCount - 1).coerceAtLeast(0),
                            repostCount = it.repostCount,
                        )
                    }
                }

                InteractionKind.REPOST -> {
                    SocialStats.adjustCached(socialCache, enrichment, interaction.targetEventId) {
                        NostrNoteStats(
                            eventId = it.eventId,
                            likeCount = it.likeCount,
                            replyCount = it.replyCount,
                            repostCount = (it.repostCount - 1).coerceAtLeast(0),
                        )
                    }
                }

                null -> Unit
            }
        }
        return response
    }

    private suspend fun rememberInteraction(
        eventId: String,
        targetEventId: String,
        kind: InteractionKind,
    ) {
        localInteractionsMutex.withLock {
            if (localInteractions.size >= MAX_LOCAL_INTERACTIONS) {
                val iterator = localInteractions.iterator()
                repeat(LOCAL_INTERACTION_EVICT_COUNT) {
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
            }
            localInteractions[eventId] = LocalInteraction(targetEventId, kind)
        }
    }

    private suspend fun takeInteraction(eventId: String): LocalInteraction? {
        return localInteractionsMutex.withLock {
            localInteractions.remove(eventId)
        }
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

        // Filter only NIP-25 likes and extract target event IDs
        val targetEventIds = reactionResponse.data
            .filter { event -> SocialMapper.isLike(event.content) }
            .mapNotNull { event ->
                event.tags.lastOrNull { it.size >= 2 && it[0] == "e" }?.get(1)
            }
            .distinct()
            .take(limit)

        if (targetEventIds.isEmpty()) {
            return reactionResponse.withData(listOf())
        }

        // Fetch the actual notes
        val noteFilter = NostrFilter(
            ids = targetEventIds,
            kinds = listOf(EventKind.TEXT_NOTE),
            limit = targetEventIds.size,
        )
        val noteResponse = nostr.events().queryEvents(listOf(noteFilter))
        var notes = noteResponse.data.map { SocialMapper.toNote(it) }
        cacheNotes(notes)
        if (excludeSensitive) {
            notes = notes.filterNot { it.isSensitive }
        }
        if (notes.isNotEmpty()) {
            populateQuotedNotes(notes)
            populateAuthors(notes)
            populateLikeCounts(notes)
        }

        return responseOf(notes, reactionResponse, noteResponse)
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
        cacheNotes(mediaNotes)

        if (excludeSensitive) {
            mediaNotes = mediaNotes.filterNot { it.isSensitive }
        }
        mediaNotes = mediaNotes.take(limit)
        if (mediaNotes.isNotEmpty()) {
            populateQuotedNotes(mediaNotes)
            populateAuthors(mediaNotes)
            populateLikeCounts(mediaNotes)
        }

        return response.withData(mediaNotes)
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

    override fun replyBlocking(
        content: String,
        tags: List<List<String>>,
        replyToEventId: String,
        rootEventId: String?,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrEvent> {
        return toBlocking {
            reply(content, tags, replyToEventId, rootEventId, contentWarning, expiry, sensitive)
        }
    }

    @Deprecated(
        message = "Use the overload that accepts tags",
        replaceWith = ReplaceWith(
            "replyBlocking(content, emptyList(), replyToEventId, rootEventId, contentWarning, expiry, sensitive)",
        ),
    )
    override fun replyBlocking(
        content: String,
        replyToEventId: String,
        rootEventId: String?,
        contentWarning: String?,
        expiry: Long?,
        sensitive: Boolean,
    ): Response<NostrEvent> {
        return replyBlocking(
            content = content,
            tags = emptyList(),
            replyToEventId = replyToEventId,
            rootEventId = rootEventId,
            contentWarning = contentWarning,
            expiry = expiry,
            sensitive = sensitive,
        )
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

    private companion object {
        const val MAX_LOCAL_INTERACTIONS = 5_000
        const val LOCAL_INTERACTION_EVICT_COUNT = 500
    }
}
