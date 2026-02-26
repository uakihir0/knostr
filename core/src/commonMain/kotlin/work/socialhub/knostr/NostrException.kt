package work.socialhub.knostr

class NostrException : Exception {
    var relayUrl: String? = null
    var reason: String? = null

    constructor(m: String) : super(m)
    constructor(e: Exception) : super(e)
    constructor(relayUrl: String, reason: String) : super("relay: $relayUrl, reason: $reason") {
        this.relayUrl = relayUrl
        this.reason = reason
    }
}
