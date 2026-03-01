> [日本語](./docs/README_ja.md)

# knostr

![badge][badge-jvm]
![badge][badge-ios]
![badge][badge-mac]
![badge][badge-windows]
![badge][badge-linux]

**This library is a Nostr protocol client library compatible
with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html).**
It depends on [khttpclient] and uses Ktor Client internally. Therefore, this library can be used on any platform
supported by Kotlin Multiplatform and Ktor Client. The behavior on each platform depends on [khttpclient].

knostr provides three modules:
- **cipher** — Pure Kotlin secp256k1 / BIP-340 Schnorr implementation (zero external dependencies, all platforms)
- **core** — Low-level Nostr protocol operations (events, relay connections, signing, NIP utilities)
- **social** — High-level social abstraction layer (feeds, users, reactions, search, zaps, media upload, mutes, streaming)

## Usage

Below is how to use it with Kotlin on the supported platforms using Gradle.

### Snapshot

```kotlin:build.gradle.kts
repositories {
+   maven { url = uri("https://repo.repsy.io/mvn/uakihir0/public") }
}

dependencies {
+   implementation("work.socialhub.knostr:core:0.0.1-SNAPSHOT")
+   implementation("work.socialhub.knostr:social:0.0.1-SNAPSHOT")
}
```

### Using as part of a regular Java project

All of the above can be added to and used in regular Java projects, too. All you have to do is to use the suffix `-jvm` when listing the dependency.

Here is a sample Maven configuration:

```xml
<dependency>
    <groupId>work.socialhub.knostr</groupId>
    <artifactId>core-jvm</artifactId>
    <version>[VERSION]</version>
</dependency>
```

### Connecting to Relays (Core)

```kotlin
// Create with private key (JVM/Native only)
val nostr = NostrFactory.instance(
    privateKeyHex = "your-private-key-hex",
    relays = listOf("wss://relay.damus.io", "wss://nos.lol"),
)

// Create read-only (no signing)
val nostr = NostrFactory.instance(
    relays = listOf("wss://relay.damus.io"),
)

// Connect to relays
nostr.relays().connect()
```

### Querying Events (Core)

```kotlin
val filter = NostrFilter(
    kinds = listOf(EventKind.TEXT_NOTE),
    limit = 20,
)

val response = nostr.events().queryEvents(listOf(filter))
response.data.forEach { event ->
    println("${event.pubkey}: ${event.content}")
}
```

### Posting & Feed (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// Post a text note
social.feed().post("Hello Nostr!")

// Reply to a note
social.feed().reply(
    content = "This is a reply",
    replyToEventId = "target-event-id",
)

// Repost a note (kind:6)
social.feed().repost("target-event-id")

// Get a single note by ID
val note = social.feed().getNote("event-id").data

// Get a user's feed
val notes = social.feed().getUserFeed("pubkey-hex", limit = 20).data

// Get mentions (notes mentioning the authenticated user)
val mentions = social.feed().getMentions(limit = 20).data

// Get a thread (root note + ancestors + replies)
val thread = social.feed().getThread("event-id").data
println("Root: ${thread.rootNote?.content}")
println("Replies: ${thread.replies.size}")

// Delete a note
social.feed().delete("event-id", "reason")
```

### User Profile (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// Get user profile
val user = social.users().getProfile("pubkey-hex").data
println("${user.name}: ${user.about}")

// Get following list
val following = social.users().getFollowing("pubkey-hex").data

// Get followers (kind:3 reverse lookup)
val followers = social.users().getFollowers("pubkey-hex", limit = 100).data

// Get multiple profiles at once
val users = social.users().getProfiles(listOf("pubkey1", "pubkey2")).data

// Follow / unfollow
social.users().follow("pubkey-hex")
social.users().unfollow("pubkey-hex")

// NIP-05 verification
val verified = social.users().verifyNip05("user@example.com").data
```

### Reactions (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// Like a note
social.reactions().like(
    eventId = "target-event-id",
    authorPubkey = "target-author-pubkey",
)

// Custom reaction
social.reactions().react("event-id", "author-pubkey", "🤙")

// Unlike (find own like and delete via kind:5)
social.reactions().unlike("target-event-id")

// Get reactions for a note
val reactions = social.reactions().getReactions("event-id").data

// Get a user's reaction history
val userReactions = social.reactions().getUserReactions("pubkey-hex", limit = 20).data
```

### Mute (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// Mute a user (NIP-51 kind:10000)
social.mutes().mute("target-pubkey-hex")

// Unmute a user
social.mutes().unmute("target-pubkey-hex")

// Get mute list
val mutedPubkeys = social.mutes().getMuteList().data
```

### Real-time Timeline (Social)

```kotlin
val stream = TimelineStream(nostr)
stream.onNoteCallback = { note ->
    println("New note: ${note.content}")
}
stream.start(followingPubkeys)
```

### Zap / Lightning (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// Create a zap request (kind:9734)
val zapRequest = social.zaps().createZapRequest(
    recipientPubkey = "target-pubkey-hex",
    amountMilliSats = 21000,
    relays = listOf("wss://relay.damus.io"),
    message = "Great post!",
    eventId = "target-event-id", // optional, null for profile zap
)

// Get zap receipts for a user
val zaps = social.zaps().getZapsForUser("pubkey-hex", limit = 10).data

// Get LNURL pay info from Lightning address
val payInfo = social.zaps().getLnurlPayInfo("user@getalby.com").data
```

### Media Upload (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// Get NIP-96 server upload endpoint
val uploadUrl = social.media().getServerInfo("https://nostr.build").data

// Upload a file
val media = social.media().upload(
    serverUrl = "https://nostr.build",
    fileData = imageBytes,
    fileName = "photo.jpg",
    mimeType = "image/jpeg",
    description = "A photo",
).data
println("Uploaded: ${media.url}")
```

### Direct Messages (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// NIP-17: Send a private DM (Gift Wrap pattern)
social.messages().sendMessage(
    recipientPubkey = "recipient-pubkey-hex",
    content = "Hello via NIP-17!",
)

// NIP-17: Get received DMs
val messages = social.messages().getMessages(limit = 20).data
messages.forEach { msg ->
    println("${msg.senderPubkey}: ${msg.content}")
}

// NIP-17: Get conversation with a specific user
val conversation = social.messages().getConversation(
    pubkey = "other-user-pubkey-hex",
    limit = 50,
).data

// NIP-04 (legacy): Send an encrypted DM
social.messages().sendLegacyMessage(
    recipientPubkey = "recipient-pubkey-hex",
    content = "Hello via NIP-04!",
)

// NIP-04 (legacy): Get received legacy DMs
val legacyMessages = social.messages().getLegacyMessages(limit = 20).data
```

### NIP Utilities (Core)

```kotlin
// NIP-19: Bech32 encoding
val npub = nostr.nip().encodeNpub("pubkey-hex")
val entity = nostr.nip().decodeNip19("npub1...")

// NIP-05: DNS identity verification
val result = nostr.nip().resolveNip05("user@example.com")
```

## Social API Overview

| Resource | Methods | Description |
|----------|---------|-------------|
| `feed()` | `post`, `reply`, `repost`, `delete`, `getNote`, `getUserFeed`, `getHomeFeed`, `getMentions`, `getThread` | Feed & timeline management |
| `users()` | `getProfile`, `getProfiles`, `updateProfile`, `follow`, `unfollow`, `getFollowing`, `getFollowers`, `verifyNip05` | User profile management |
| `reactions()` | `like`, `unlike`, `react`, `unreact`, `getReactions`, `getUserReactions` | Reactions & likes |
| `search()` | `searchNotes`, `searchUsers` | Content search (NIP-50) |
| `zaps()` | `createZapRequest`, `getZapsForEvent`, `getZapsForUser`, `getLnurlPayInfo` | Lightning Zaps (NIP-57) |
| `media()` | `upload`, `getServerInfo` | File upload (NIP-96) |
| `mutes()` | `mute`, `unmute`, `getMuteList` | User muting (NIP-51) |
| `messages()` | `sendMessage`, `getMessages`, `getConversation`, `sendLegacyMessage`, `getLegacyMessages` | Direct messages (NIP-17 / NIP-04) |

All methods have both `suspend` (async) and `Blocking` (sync) variants.

## Supported NIPs

| NIP | Description | Status |
|-----|-------------|--------|
| NIP-01 | Basic protocol | Implemented |
| NIP-02 | Follow list (kind:3) | Implemented |
| NIP-04 | Encrypted DM (legacy, kind:4) | Implemented |
| NIP-05 | DNS identity verification | Implemented |
| NIP-09 | Event deletion (kind:5) | Implemented |
| NIP-10 | Reply threading (e-tag markers) | Implemented |
| NIP-17 | Private Direct Messages (Gift Wrap) | Implemented |
| NIP-18 | Reposts (kind:6) | Implemented |
| NIP-19 | Bech32 encoding (npub, nsec, note) | Implemented |
| NIP-25 | Reactions (kind:7) | Implemented |
| NIP-44 | Versioned Encryption | Implemented |
| NIP-50 | Search | Implemented |
| NIP-51 | Mute list (kind:10000) | Implemented |
| NIP-57 | Lightning Zaps | Implemented |
| NIP-59 | Gift Wrap | Implemented |
| NIP-96 | File upload | Implemented |
| NIP-98 | HTTP Auth (for NIP-96) | Implemented |

## Platform Support

| Platform | Cipher | Core | Social | Signing |
|----------|--------|------|--------|---------|
| JVM | Yes | Yes | Yes | Yes |
| iOS/macOS | Yes | Yes | Yes | Yes |
| Linux x64 | Yes | Yes | - | Yes |
| JS (Node/Browser) | Yes | Yes | Yes | Yes |
| Windows (mingwX64) | Yes | Yes | Yes | Yes |

> The cipher module provides a pure Kotlin implementation of secp256k1 / BIP-340 Schnorr signatures,
> enabling event signing on all platforms without native dependencies.

## License

MIT License

## Author

[Akihiro Urushihara](https://github.com/uakihir0)

[khttpclient]: https://github.com/uakihir0/khttpclient
[badge-android]: http://img.shields.io/badge/-android-6EDB8D.svg

[badge-android-native]: http://img.shields.io/badge/support-[AndroidNative]-6EDB8D.svg

[badge-wearos]: http://img.shields.io/badge/-wearos-8ECDA0.svg
[badge-jvm]: http://img.shields.io/badge/-jvm-DB413D.svg
[badge-js]: http://img.shields.io/badge/-js-F8DB5D.svg

[badge-js-ir]: https://img.shields.io/badge/support-[IR]-AAC4E0.svg

[badge-nodejs]: https://img.shields.io/badge/-nodejs-68a063.svg
[badge-linux]: http://img.shields.io/badge/-linux-2D3F6C.svg
[badge-windows]: http://img.shields.io/badge/-windows-4D76CD.svg
[badge-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg

[badge-apple-silicon]: http://img.shields.io/badge/support-[AppleSilicon]-43BBFF.svg

[badge-ios]: http://img.shields.io/badge/-ios-CDCDCD.svg
[badge-mac]: http://img.shields.io/badge/-macos-111111.svg
[badge-watchos]: http://img.shields.io/badge/-watchos-C0C0C0.svg
[badge-tvos]: http://img.shields.io/badge/-tvos-808080.svg
