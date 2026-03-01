package work.socialhub.knostr.social.api

import work.socialhub.knostr.api.response.Response
import work.socialhub.knostr.entity.NostrEvent
import work.socialhub.knostr.social.model.NostrPoll
import kotlin.js.JsExport

@JsExport
interface PollResource {

    /** Create a poll (kind:1068) */
    suspend fun createPoll(content: String, options: List<String>, closedAt: Long? = null): Response<NostrEvent>

    /** Vote on a poll (kind:1018) */
    suspend fun vote(pollEventId: String, optionIndexes: List<Int>): Response<NostrEvent>

    /** Get a poll by event ID */
    suspend fun getPoll(pollEventId: String): Response<NostrPoll>

    /** Get votes for a poll */
    suspend fun getPollVotes(pollEventId: String): Response<Map<Int, Int>>

    @JsExport.Ignore
    fun createPollBlocking(content: String, options: List<String>, closedAt: Long? = null): Response<NostrEvent>

    @JsExport.Ignore
    fun voteBlocking(pollEventId: String, optionIndexes: List<Int>): Response<NostrEvent>

    @JsExport.Ignore
    fun getPollBlocking(pollEventId: String): Response<NostrPoll>

    @JsExport.Ignore
    fun getPollVotesBlocking(pollEventId: String): Response<Map<Int, Int>>
}
