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

knostr provides two modules:
- **core** — Low-level Nostr protocol operations (events, relay connections, signing, NIP utilities)
- **social** — High-level social abstraction layer (feeds, users, reactions, search, streaming)

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

### Posting a Note (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// Post a text note
social.feed().post("Hello Nostr!")

// Reply to a note
social.feed().reply(
    content = "This is a reply",
    replyToEventId = "target-event-id",
)

// Like a note
social.reactions().like(
    eventId = "target-event-id",
    authorPubkey = "target-author-pubkey",
)
```

### User Profile (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// Get user profile
val user = social.users().getProfile("pubkey-hex").data
println("${user.name}: ${user.about}")

// Get following list
val following = social.users().getFollowing("pubkey-hex").data
```

### Real-time Timeline (Social)

```kotlin
val stream = TimelineStream(nostr)
stream.onNoteCallback = { note ->
    println("New note: ${note.content}")
}
stream.start(followingPubkeys)
```

### NIP Utilities (Core)

```kotlin
// NIP-19: Bech32 encoding
val npub = nostr.nip().encodeNpub("pubkey-hex")
val entity = nostr.nip().decodeNip19("npub1...")

// NIP-05: DNS identity verification
val result = nostr.nip().resolveNip05("user@example.com")
```

## Supported NIPs

| NIP | Description | Status |
|-----|-------------|--------|
| NIP-01 | Basic protocol | Implemented |
| NIP-05 | DNS identity verification | Implemented |
| NIP-10 | Reply threading (e-tag markers) | Implemented |
| NIP-19 | Bech32 encoding (npub, nsec, note) | Implemented |
| NIP-25 | Reactions | Implemented |
| NIP-50 | Search | Implemented |

## Platform Support

| Platform | Core | Social | Signing |
|----------|------|--------|---------|
| JVM | Yes | Yes | Yes |
| iOS/macOS | Yes | Yes | Yes |
| Linux x64 | Yes | - | Yes |
| JS (Node/Browser) | Yes | Yes | No |
| Windows (mingwX64) | Yes | Yes | No |

> Signing (Schnorr/BIP-340) currently requires `secp256k1-kmp` which is only available on JVM, Apple, and Linux.
> On JS and Windows, use `NostrConfig` with a custom `NostrSigner` implementation.

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
