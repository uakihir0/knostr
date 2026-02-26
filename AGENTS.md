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
| `model/` | SNS models: `NostrUser`, `NostrNote`, `NostrReaction`, `NostrThread` |
| `api/` | Resource interfaces: `FeedResource`, `UserResource`, `ReactionResource`, `SearchResource` |
| `stream/` | Real-time subscriptions: `TimelineStream`, `NotificationStream` |
| `internal/` | Implementations: `NostrSocialImpl`, `SocialMapper`, `*ResourceImpl` |

## Nostr Protocol Concepts

- **Events**: JSON objects signed with Schnorr (BIP-340). Identified by SHA-256 hash.
- **Kinds**: Integer type identifiers. 0=metadata, 1=text note, 3=follow list, 5=deletion, 6=repost, 7=reaction.
- **Relays**: WebSocket servers that store/forward events. Clients connect to multiple relays.
- **Filters**: JSON objects specifying event queries (by author, kind, tags, time range).
- **NIP-01**: Base protocol. NIP-05: DNS identity. NIP-10: Reply threading. NIP-19: Bech32 encoding. NIP-25: Reactions. NIP-50: Search.

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
make build                      # Full build (core + social)
```

## Platform Support

| Platform | Core | Social | Signing |
|----------|------|--------|---------|
| JVM | Yes | Yes | Yes (secp256k1-kmp) |
| iOS/macOS | Yes | Yes | Yes (secp256k1-kmp) |
| Linux x64 | Yes | N/A | Yes (secp256k1-kmp) |
| JS (Node/Browser) | Yes | Yes | No (stub) |
| Windows (mingwX64) | Yes | Yes | No (stub) |

## Testing

Tests require `secrets.json` with relay URLs and optionally private keys.
Network-free build verification: `./gradlew jvmJar`

## Scope / Not Yet Implemented

- NIP-04/NIP-17 encrypted DMs
- NIP-42 relay authentication
- NIP-65 relay list management
- CI/CD workflows
- planetlink integration adapter
- NIP-07 browser extension (JS)
