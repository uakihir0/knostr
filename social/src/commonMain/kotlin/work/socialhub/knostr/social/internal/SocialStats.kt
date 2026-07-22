package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.social.model.NostrNoteStats

internal object SocialStats {
    fun filters(eventIds: List<String>): List<NostrFilter> {
        return listOf(
            NostrFilter(
                kinds = listOf(EventKind.REACTION),
                eTags = eventIds,
            ),
            NostrFilter(
                kinds = listOf(EventKind.TEXT_NOTE),
                eTags = eventIds,
            ),
            NostrFilter(
                kinds = listOf(EventKind.REPOST, EventKind.GENERIC_REPOST),
                eTags = eventIds,
            ),
        )
    }

    fun calculate(eventId: String, events: Collection<NostrEvent>): NostrNoteStats {
        return NostrNoteStats(
            eventId = eventId,
            likeCount = SocialMapper.countLikes(
                events.filter {
                    it.kind == EventKind.REACTION && SocialMapper.getReactionTarget(it) == eventId
                }
            ),
            replyCount = events.count {
                it.kind == EventKind.TEXT_NOTE && findReplyParent(it) == eventId
            },
            repostCount = events.count {
                (it.kind == EventKind.REPOST || it.kind == EventKind.GENERIC_REPOST) &&
                    it.tags.any { tag -> tag.size >= 2 && tag[0] == "e" && tag[1] == eventId }
            },
        )
    }

    private fun findReplyParent(event: NostrEvent): String? {
        val eTags = event.tags.filter { it.size >= 2 && it[0] == "e" }
        if (eTags.isEmpty()) return null
        eTags.firstOrNull { it.size >= 4 && it[3] == "reply" }?.let { return it[1] }
        val unmarkedTags = eTags.filter { it.size < 4 || it[3].isEmpty() }
        if (unmarkedTags.isNotEmpty()) {
            return if (unmarkedTags.size == 1) unmarkedTags[0][1] else unmarkedTags.last()[1]
        }
        return eTags.firstOrNull { it.size >= 4 && it[3] == "root" }?.get(1)
    }
}
