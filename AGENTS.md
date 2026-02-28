# knostr — Kotlin Multiplatform Nostr SDK

## Overview

Kotlin Multiplatform Library for the Nostr protocol. Provides two modules:
- **core**: Low-level protocol operations (events, relay connections, signing, NIP utilities)
- **social**: High-level SNS abstraction layer (feeds, users, reactions, search, streaming)

## Package

`work.socialhub.knostr` (core), `work.socialhub.knostr.social` (social)

## Entry Points

```kotlin
// Core — with signing (JVM/Native only)
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
├── core/                    # Nostr protocol layer
│   └── src/
│       ├── commonMain/      # Shared code
│       ├── signingMain/     # secp256k1 signing (JVM, Apple, Linux)
│       ├── unsupportedSigningMain/  # Stub signing (JS, mingwX64)
│       ├── jvmMain/         # JVM-specific (BlockingUtil, signing)
│       ├── nativeMain/      # Native-specific (BlockingUtil)
│       └── jsMain/          # JS-specific (BlockingUtil stub)
│
├── social/                  # SNS abstraction layer
│   └── src/commonMain/
│
├── all/                     # Apple aggregation (XCFramework)
└── plugins/                 # Gradle build plugins
```

## Architecture

### Core Module

| Package | Purpose |
|---------|---------|
| `entity/` | Data models: `NostrEvent`, `NostrFilter`, `NostrProfile`, `Nip05Result`, `Nip19Entity` |
| `relay/` | WebSocket relay management: `RelayConnection`, `RelayPool`, `RelayMessage`, `Subscription` |
| `signing/` | Event signing: `NostrSigner` interface, `Secp256k1Signer` (BIP-340 Schnorr) |
| `api/` | Resource interfaces: `EventResource`, `RelayResource`, `NipResource` |
| `internal/` | Implementations: `NostrImpl`, `EventResourceImpl`, `RelayResourceImpl`, `NipResourceImpl` |
| `util/` | Utilities: `Hex`, `Bech32`, `BlockingUtil` |

### Social Module

| Package | Purpose |
|---------|---------|
| `model/` | SNS models: `NostrUser`, `NostrNote`, `NostrReaction`, `NostrThread`, `NostrZap`, `NostrMedia` |
| `api/` | Resource interfaces: `FeedResource`, `UserResource`, `ReactionResource`, `SearchResource`, `ZapResource`, `MediaResource` |
| `stream/` | Real-time subscriptions: `TimelineStream`, `NotificationStream` |
| `internal/` | Implementations: `NostrSocialImpl`, `SocialMapper`, `*ResourceImpl` |

## Nostr Protocol Concepts

- **Events**: JSON objects signed with Schnorr (BIP-340). Identified by SHA-256 hash.
- **Kinds**: Integer type identifiers. 0=metadata, 1=text note, 3=follow list, 5=deletion, 6=repost, 7=reaction, 9734=zap request, 9735=zap receipt, 1063=file metadata.
- **Relays**: WebSocket servers that store/forward events. Clients connect to multiple relays.
- **Filters**: JSON objects specifying event queries (by author, kind, tags, time range).
- **NIP-01**: Base protocol. NIP-05: DNS identity. NIP-10: Reply threading. NIP-19: Bech32 encoding. NIP-25: Reactions. NIP-50: Search. NIP-57: Lightning Zaps. NIP-96: File upload. NIP-98: HTTP Auth.

## Key Patterns

- **Factory pattern**: `NostrFactory.instance(...)`, `NostrSocialFactory.instance(...)`
- **Resource interfaces**: `nostr.events()`, `social.feed()`, `social.users()`
- **Internal implementations**: `XxxImpl` suffix (e.g., `EventResourceImpl`, `FeedResourceImpl`)
- **Dual API**: All operations have `suspend` (async) and `Blocking` (sync) variants
- **Response wrapper**: `Response<T>` with `data` and optional `json`
- **expect/actual**: `BlockingUtil` (JVM=runBlocking, Native=runBlocking, JS=throws), `createSigner` (JVM/Native=Secp256k1, JS=throws)

## Dependencies

| Library | Purpose |
|---------|---------|
| `khttpclient` | HTTP client + WebSocket (Ktor wrapper) |
| `kotlinx-serialization` | JSON serialization |
| `kotlinx-coroutines` | Async/suspend support |
| `kotlinx-datetime` | Timestamp handling |
| `secp256k1-kmp` | Schnorr signatures (JVM, Apple, Linux only) |
| `cryptography-kotlin` | Cryptographic primitives |

## Build Commands

```bash
./gradlew core:jvmJar          # Build core JVM artifact
./gradlew social:jvmJar         # Build social JVM artifact
./gradlew core:jvmTest          # Run core JVM tests
./gradlew social:jvmTest        # Run social JVM tests
make build                      # Full build (core + social)
```

## CI/CD

GitHub Actions workflows in `.github/workflows/`:
- `snapshot-publish.yml` — Publish SNAPSHOT to Repsy on main branch push
- `release-publish.yml` — Publish release to Maven Central on `v*` tag push
- `pods.yml` — Build XCFramework, push to knostr-cocoapods repo
- `js.yml` — Build npm package, push to knostr.js repo
- `swiftpackage.yml` — Create SwiftPackage, push to knostr-spm repo

## Platform Support

| Platform | Core | Social | Signing |
|----------|------|--------|---------|
| JVM | Yes | Yes | Yes (secp256k1-kmp) |
| iOS/macOS | Yes | Yes | Yes (secp256k1-kmp) |
| Linux x64 | Yes | N/A | Yes (secp256k1-kmp) |
| JS (Node/Browser) | Yes | Yes | No (stub) |
| Windows (mingwX64) | Yes | Yes | No (stub) |

## Testing

Tests require `secrets.json` at the project root with relay URLs and optionally private keys. See `secrets.json.default` for the template.

```bash
./gradlew core:jvmTest                    # Core tests
./gradlew social:jvmTest                  # Social API tests
./gradlew jvmJar                          # Network-free build verification
```

Test files are in `social/src/jvmTest/kotlin/work/socialhub/knostr/social/`:
- `AbstractTest.kt` — Base class with credential loading and relay connection helpers
- `FeedResourceTest.kt` — Post, reply, repost, delete
- `UserResourceTest.kt` — Profile, following, NIP-05 verification
- `ReactionResourceTest.kt` — Like, custom reaction, get reactions
- `SearchResourceTest.kt` — Note search, user search
- `ZapResourceTest.kt` — Zap request creation, zap receipt querying
- `MediaResourceTest.kt` — NIP-96 server info

**Note**: Tests use `runBlocking` with a separate `CoroutineScope(Dispatchers.Default + SupervisorJob())` for relay connections because WebSocket connections are long-lived and block `coroutineScope`.

## Scope / Not Yet Implemented

- NIP-04/NIP-17 encrypted DMs
- NIP-42 relay authentication
- NIP-65 relay list management
- planetlink integration adapter
- NIP-07 browser extension (JS)
