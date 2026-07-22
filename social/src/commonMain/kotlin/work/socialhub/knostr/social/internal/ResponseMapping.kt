package work.socialhub.knostr.social.internal

import work.socialhub.knostr.api.response.Response

internal fun <T, R> Response<T>.withData(data: R): Response<R> {
    return Response(data).also {
        it.json = json
        it.isComplete = isComplete
    }
}

internal fun <T> responseOf(data: T, vararg sources: Response<*>): Response<T> {
    return Response(data).also {
        it.isComplete = sources.all { source -> source.isComplete }
    }
}
