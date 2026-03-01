package work.socialhub.knostr.social.internal

import work.socialhub.knostr.EventKind
import work.socialhub.knostr.Nostr
import work.socialhub.knostr.NostrException
import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.entity.UnsignedEvent
import work.socialhub.knostr.social.api.PollResource
import work.socialhub.knostr.social.model.NostrPoll
import work.socialhub.knostr.social.model.NostrPollOption
import work.socialhub.knostr.util.toBlocking
import kotlin.time.Clock

class PollResourceImpl(
    private val nostr: Nostr,
) : PollResource {

    override suspend fun createPoll(content: String, options: List<String>, closedAt: Long?): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to create poll")

        val tags = mutableListOf<List<String>>()
        for ((index, option) in options.withIndex()) {
            tags.add(listOf("poll_option", index.toString(), option))
        }
        if (closedAt != null) {
            tags.add(listOf("closed_at", closedAt.toString()))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.POLL,
            tags = tags,
            content = content,
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun vote(pollEventId: String, optionIndexes: List<Int>): Response<NostrEvent> {
        val signer = nostr.signer()
            ?: throw NostrException("Signer is required to vote")

        val tags = mutableListOf<List<String>>()
        tags.add(listOf("e", pollEventId))
        for (index in optionIndexes) {
            tags.add(listOf("response", index.toString()))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.getPublicKey(),
            createdAt = Clock.System.now().epochSeconds,
            kind = EventKind.POLL_RESPONSE,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        nostr.events().publishEvent(signed)
        return Response(signed)
    }

    override suspend fun getPoll(pollEventId: String): Response<NostrPoll> {
        val filter = NostrFilter(
            ids = listOf(pollEventId),
            kinds = listOf(EventKind.POLL),
            limit = 1,
        )
        val response = nostr.events().queryEvents(listOf(filter))
        val event = response.data.firstOrNull()
            ?: throw NostrException("Poll not found: $pollEventId")

        return Response(toPoll(event))
    }

    override suspend fun getPollVotes(pollEventId: String): Response<Map<Int, Int>> {
        val filter = NostrFilter(
            kinds = listOf(EventKind.POLL_RESPONSE),
            eTags = listOf(pollEventId),
        )
        val response = nostr.events().queryEvents(listOf(filter))

        // Count votes per option, deduplicate by author (latest vote wins)
        val latestVotes = response.data
            .sortedByDescending { it.createdAt }
            .distinctBy { it.pubkey }

        val counts = mutableMapOf<Int, Int>()
        for (vote in latestVotes) {
            for (tag in vote.tags) {
                if (tag.size >= 2 && tag[0] == "response") {
                    val index = tag[1].toIntOrNull() ?: continue
                    counts[index] = (counts[index] ?: 0) + 1
                }
            }
        }

        return Response(counts)
    }

    private fun toPoll(event: NostrEvent): NostrPoll {
        val poll = NostrPoll()
        poll.event = event
        poll.content = event.content
        poll.createdAt = event.createdAt

        val options = mutableListOf<NostrPollOption>()
        for (tag in event.tags) {
            if (tag.size >= 3 && tag[0] == "poll_option") {
                val index = tag[1].toIntOrNull() ?: continue
                options.add(NostrPollOption(index, tag[2]))
            }
            if (tag.size >= 2 && tag[0] == "closed_at") {
                poll.closedAt = tag[1].toLongOrNull()
            }
        }
        poll.options = options.sortedBy { it.index }

        return poll
    }

    override fun createPollBlocking(content: String, options: List<String>, closedAt: Long?): Response<NostrEvent> {
        return toBlocking { createPoll(content, options, closedAt) }
    }

    override fun voteBlocking(pollEventId: String, optionIndexes: List<Int>): Response<NostrEvent> {
        return toBlocking { vote(pollEventId, optionIndexes) }
    }

    override fun getPollBlocking(pollEventId: String): Response<NostrPoll> {
        return toBlocking { getPoll(pollEventId) }
    }

    override fun getPollVotesBlocking(pollEventId: String): Response<Map<Int, Int>> {
        return toBlocking { getPollVotes(pollEventId) }
    }
}
