# knostr — Kotlin Multiplatform Nostr SDK

## Overview

Kotlin Multiplatform Library for the Nostr protocol. Provides three modules:
- **cipher**: Pure Kotlin secp256k1 / BIP-340 Schnorr implementation (zero external dependencies, all platforms)
- **core**: Low-level protocol operations (events, relay connections, signing, NIP utilities)
- **social**: High-level SNS abstraction layer (feeds, users, reactions, search, zaps, media, mutes, streaming)

## Package

`work.socialhub.knostr.cipher` (cipher), `work.socialhub.knostr` (core), `work.socialhub.knostr.social` (social)

## Entry Points

```kotlin
// Core — with signing
val nostr = NostrFactory.instance(privateKeyHex, relayUrls)

// Core — read-only
val nostr = NostrFactory.instance(relayUrls)

// Core — full config
val nostr = NostrFactory.instance(NostrConfig().also { ... })

// Social — wraps core
val social = NostrSocialFactory.instance(nostr)
```

## Module Structure

```
knostr/
├── cipher/                  # Pure Kotlin cryptography (zero dependencies)
│   └── src/commonMain/      # secp256k1, BIP-340, SHA-256, UInt256
│
├── core/                    # Nostr protocol layer
│   └── src/
│       ├── commonMain/      # Shared code (depends on cipher)
│       ├── jvmMain/         # JVM-specific (BlockingUtil)
│       ├── nativeMain/      # Native-specific (BlockingUtil)
│       └── jsMain/          # JS-specific (BlockingUtil stub)
│
├── social/                  # SNS abstraction layer
│   └── src/
│       ├── commonMain/      # Shared code (depends on core)
│       └── jvmTest/         # Integration tests
│
├── all/                     # Apple aggregation (XCFramework)
└── plugins/                 # Gradle build plugins
```

## Architecture

### Cipher Module

| Class | Purpose |
|-------|---------|
| `Secp256k1` | Public API — `pubkeyCreate()`, `signSchnorr()`, `verifySchnorr()` |
| `UInt256` | 256-bit unsigned integer arithmetic |
| `Sha256` | Pure Kotlin SHA-256 hash |
| `ECMath` | Elliptic curve point operations (add, double, multiply) |
| `ECPoint` | Affine/Infinity point representation |
| `JacobianPoint` | Projective coordinates for efficient computation |
| `Secp256k1Curve` | Curve constants (P, N, G) |
| `Secp256k1Field` | Optimized field arithmetic (mod p) |
| `ModularArithmetic` | Generic modular arithmetic |
| `TaggedHash` | BIP-340 tagged hashing |

### Core Module

| Package | Purpose |
|---------|---------|
| `entity/` | Data models: `NostrEvent`, `NostrFilter`, `NostrProfile`, `Nip05Result`, `Nip19Entity` |
| `relay/` | WebSocket relay management: `RelayConnection`, `RelayPool`, `RelayMessage`, `Subscription` |
| `signing/` | Event signing: `NostrSigner` interface, `Secp256k1Signer` (uses cipher module) |
| `api/` | Resource interfaces: `EventResource`, `RelayResource`, `NipResource` |
| `internal/` | Implementations: `NostrImpl`, `EventResourceImpl`, `RelayResourceImpl`, `NipResourceImpl` |
| `util/` | Utilities: `Hex`, `Bech32`, `BlockingUtil` |

### Social Module

| Package | Purpose |
|---------|---------|
| `model/` | SNS models: `NostrUser`, `NostrNote`, `NostrReaction`, `NostrThread`, `NostrZap`, `NostrMedia` |
| `api/` | Resource interfaces: `FeedResource`, `UserResource`, `ReactionResource`, `SearchResource`, `ZapResource`, `MediaResource`, `MuteResource` |
| `stream/` | Real-time subscriptions: `TimelineStream`, `NotificationStream` |
| `internal/` | Implementations: `NostrSocialImpl`, `SocialMapper`, `*ResourceImpl` |

## Social API Resources

| Resource | Methods | Description |
|----------|---------|-------------|
| `feed()` | `post`, `reply`, `repost`, `delete`, `getNote`, `getUserFeed`, `getHomeFeed`, `getMentions`, `getThread` | Feed & timeline management |
| `users()` | `getProfile`, `getProfiles`, `updateProfile`, `follow`, `unfollow`, `getFollowing`, `getFollowers`, `verifyNip05` | User profile management |
| `reactions()` | `like`, `unlike`, `react`, `unreact`, `getReactions`, `getUserReactions` | Reactions & likes |
| `search()` | `searchNotes`, `searchUsers` | Content search (NIP-50) |
| `zaps()` | `createZapRequest`, `getZapsForEvent`, `getZapsForUser`, `getLnurlPayInfo` | Lightning Zaps (NIP-57) |
| `media()` | `upload`, `getServerInfo` | File upload (NIP-96) |
| `mutes()` | `mute`, `unmute`, `getMuteList` | User muting (NIP-51) |

All methods have both `suspend` (async) and `Blocking` (sync) variants.

## Nostr Protocol Concepts

- **Events**: JSON objects signed with Schnorr (BIP-340). Identified by SHA-256 hash.
- **Kinds**: Integer type identifiers. 0=metadata, 1=text note, 3=follow list, 5=deletion, 6=repost, 7=reaction, 9734=zap request, 9735=zap receipt, 10000=mute list.
- **Relays**: WebSocket servers that store/forward events. Clients connect to multiple relays.
- **Filters**: JSON objects specifying event queries (by author, kind, tags, time range).
- **NIPs**: NIP-01 (base protocol), NIP-02 (follow list), NIP-05 (DNS identity), NIP-09 (deletion), NIP-10 (reply threading), NIP-18 (reposts), NIP-19 (bech32 encoding), NIP-25 (reactions), NIP-50 (search), NIP-51 (mute list), NIP-57 (zaps), NIP-96 (file upload), NIP-98 (HTTP auth).

## Key Patterns

- **Factory pattern**: `NostrFactory.instance(...)`, `NostrSocialFactory.instance(...)`
- **Resource interfaces**: `nostr.events()`, `social.feed()`, `social.users()`
- **Internal implementations**: `XxxImpl` suffix (e.g., `EventResourceImpl`, `FeedResourceImpl`)
- **Dual API**: All operations have `suspend` (async) and `Blocking` (sync) variants
- **Response wrapper**: `Response<T>` with `data` and optional `json`
- **expect/actual**: `BlockingUtil` (JVM=runBlocking, Native=runBlocking, JS=throws)

## Dependencies

| Library | Module | Purpose |
|---------|--------|---------|
| (none) | cipher | Pure Kotlin only |
| `khttpclient` | core | HTTP client + WebSocket (Ktor wrapper) |
| `kotlinx-serialization` | core | JSON serialization |
| `kotlinx-coroutines` | core, social | Async/suspend support |
| `kotlinx-datetime` | core | Timestamp handling |

## Build Commands

```bash
./gradlew cipher:jvmJar         # Build cipher JVM artifact
./gradlew core:jvmJar           # Build core JVM artifact
./gradlew social:jvmJar         # Build social JVM artifact
./gradlew cipher:jvmTest        # Run cipher tests (BIP-340 test vectors)
./gradlew core:jvmTest          # Run core JVM tests
./gradlew social:jvmTest        # Run social JVM tests (requires secrets.json)
./gradlew jvmJar                # Build all JVM artifacts (network-free)
make build                      # Full build (all modules)
```

## CI/CD

GitHub Actions workflows in `.github/workflows/`:
- `snapshot-publish.yml` — Publish SNAPSHOT to Repsy on main branch push
- `release-publish.yml` — Publish release to Maven Central on `v*` tag push
- `pods.yml` — Build XCFramework, push to knostr-cocoapods repo
- `js.yml` — Build npm package, push to knostr.js repo
- `swiftpackage.yml` — Create SwiftPackage, push to knostr-spm repo

## Platform Support

| Platform | Cipher | Core | Social | Signing |
|----------|--------|------|--------|---------|
| JVM | Yes | Yes | Yes | Yes |
| iOS/macOS | Yes | Yes | Yes | Yes |
| Linux x64 | Yes | Yes | N/A | Yes |
| JS (Node/Browser) | Yes | Yes | Yes | Yes |
| Windows (mingwX64) | Yes | Yes | Yes | Yes |

The cipher module provides pure Kotlin BIP-340 Schnorr signatures, enabling signing on all platforms.

## Testing

Tests require `secrets.json` at the project root with relay URLs and private keys. See `secrets.json.default` for the template.

### Cipher Tests

In `cipher/src/commonTest/kotlin/work/socialhub/knostr/cipher/`:
- `Secp256k1Test.kt` — Official BIP-340 test vectors, sign/verify round-trips
- `UInt256Test.kt` — 256-bit integer arithmetic
- `ModularArithmeticTest.kt` — Modular arithmetic
- `ECMathTest.kt` — Elliptic curve operations

### Social Tests

In `social/src/jvmTest/kotlin/work/socialhub/knostr/social/`:
- `AbstractTest.kt` — Base class with credential loading and relay connection helpers
- `FeedResourceTest.kt` — Post, reply, repost, delete, getNote, getUserFeed, getMentions, getThread
- `UserResourceTest.kt` — Profile, following, followers, profiles batch, NIP-05 verification
- `ReactionResourceTest.kt` — Like, unlike, custom reaction, getReactions, getUserReactions
- `SearchResourceTest.kt` — Note search, user search
- `ZapResourceTest.kt` — Zap request creation, zap receipt querying
- `MediaResourceTest.kt` — NIP-96 server info, image upload and post
- `MuteResourceTest.kt` — Mute, unmute, getMuteList

**Note**: Tests use `runBlocking` with a separate `CoroutineScope(Dispatchers.Default + SupervisorJob())` for relay connections because WebSocket connections are long-lived and block `coroutineScope`.

## Scope / Not Yet Implemented

- NIP-04/NIP-17 encrypted DMs
- NIP-42 relay authentication
- NIP-65 relay list management
- NIP-07 browser extension (JS)
