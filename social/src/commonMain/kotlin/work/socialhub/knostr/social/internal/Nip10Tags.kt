package work.socialhub.knostr.social.internal

import work.socialhub.knostr.entity.NostrEvent

/**
 * NIP-10 tag construction for kind:1 replies.
 *
 * Only the marked scheme is emitted: the thread root carries the "root" marker
 * and the direct parent carries "reply". A reply straight to a root note gets
 * the "root" marker alone, as the NIP requires.
 *
 * The optional 5th element (the referenced author's pubkey) is filled in
 * whenever the parent event is known, so outbox-model clients can locate the
 * thread.
 */
internal object Nip10Tags {

    /**
     * Builds the "e"/"p" tags for a reply to [replyToEventId].
     *
     * [parent] is the event being replied to; pass null when it could not be
     * resolved, in which case the parent is treated as the thread root and no
     * participants can be carried over. [rootEventId] overrides the root
     * derived from [parent], and [relayHint] is the relay to suggest for the
     * referenced events ("" when there is nothing to suggest).
     */
    fun replyTags(
        replyToEventId: String,
        parent: NostrEvent?,
        rootEventId: String? = null,
        relayHint: String = "",
    ): List<List<String>> {
        val rootId = rootEventId?.takeIf { it.isNotBlank() }
            ?: parent?.let { rootIdOf(it) }
            ?: replyToEventId

        val tags = mutableListOf<List<String>>()
        tags.add(eventTag(rootId, "root", rootAuthorOf(parent, rootId, replyToEventId), relayHint))
        if (rootId != replyToEventId) {
            tags.add(eventTag(replyToEventId, "reply", parent?.pubkey, relayHint))
        }
        // NIP-10: the reply's "p" tags must contain all of the parent's "p" tags
        // plus the pubkey of the event being replied to, so every participant of
        // the thread keeps receiving notifications.
        participantsOf(parent).forEach { tags.add(listOf("p", it)) }
        return tags
    }

    /**
     * The thread root as seen from [parent]: its marked "root" tag, or the first
     * of its legacy positional "e" tags. Null when [parent] starts a thread, in
     * which case [parent] itself is the root.
     */
    private fun rootIdOf(parent: NostrEvent): String? {
        val eventTags = parent.tags.filter { it.size >= 2 && it[0] == "e" && it[1].isNotBlank() }
        eventTags.firstOrNull { it.size >= 4 && it[3] == "root" }?.let { return it[1] }
        // A "reply" marker without a "root" one means the parent's target is the
        // root of the thread.
        eventTags.firstOrNull { it.size >= 4 && it[3] == "reply" }?.let { return it[1] }
        // Legacy positional form: the first "e" tag is the root.
        return eventTags.firstOrNull { it.size < 4 || it[3].isBlank() }?.get(1)
    }

    /** Author of [rootId], as far as the parent event reveals it. */
    private fun rootAuthorOf(
        parent: NostrEvent?,
        rootId: String,
        replyToEventId: String,
    ): String? {
        if (parent == null) return null
        if (rootId == replyToEventId) return parent.pubkey
        return parent.tags
            .firstOrNull { it.size >= 5 && it[0] == "e" && it[1] == rootId && it[4].isNotBlank() }
            ?.get(4)
    }

    private fun participantsOf(parent: NostrEvent?): List<String> {
        if (parent == null) return listOf()
        val inherited = parent.tags
            .filter { it.size >= 2 && it[0] == "p" && it[1].isNotBlank() }
            .map { it[1] }
        return (inherited + parent.pubkey).distinct()
    }

    private fun eventTag(
        eventId: String,
        marker: String,
        authorPubkey: String?,
        relayHint: String,
    ): List<String> {
        return if (authorPubkey.isNullOrBlank()) {
            listOf("e", eventId, relayHint, marker)
        } else {
            listOf("e", eventId, relayHint, marker, authorPubkey)
        }
    }
}
