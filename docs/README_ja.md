# knostr

![badge][badge-jvm]
![badge][badge-ios]
![badge][badge-mac]
![badge][badge-windows]
![badge][badge-linux]

**このライブラリは [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) に対応した Nostr プロトコルクライアントライブラリです。**
[khttpclient] を依存関係に持っており、内部で Ktor Client を使用しています。
そのため、本ライブラリは、Kotlin Multiplatform かつ Ktor Client がサポートしているプラットフォームであれば利用可能です。
各プラットフォームでどのような挙動をするのかについては、[khttpclient] に依存します。

knostr は 2 つのモジュールを提供します:
- **core** — Nostr プロトコルの低レベル操作 (イベント、リレー接続、署名、NIP ユーティリティ)
- **social** — ソーシャル機能の高レベル抽象化レイヤー (フィード、ユーザー、リアクション、検索、Zap、メディアアップロード、ストリーミング)

## 使い方

以下は対応するプラットフォームにおいて Gradle を用いて Kotlin で使用する際の使い方になります。
また、テストコードも合わせて確認してください。

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

### 通常の Java プロジェクトで使用する場合

上記はすべて通常の Java プロジェクトにも追加して使用できます。依存関係にサフィックス `-jvm` を付けるだけです。

Maven の設定例:

```xml
<dependency>
    <groupId>work.socialhub.knostr</groupId>
    <artifactId>core-jvm</artifactId>
    <version>[VERSION]</version>
</dependency>
```

### リレーへの接続 (Core)

```kotlin
// 秘密鍵で作成 (JVM/Native のみ)
val nostr = NostrFactory.instance(
    privateKeyHex = "your-private-key-hex",
    relays = listOf("wss://relay.damus.io", "wss://nos.lol"),
)

// 読み取り専用 (署名なし)
val nostr = NostrFactory.instance(
    relays = listOf("wss://relay.damus.io"),
)

// リレーに接続
nostr.relays().connect()
```

### イベントの取得 (Core)

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

### ノートの投稿 (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// テキストノートを投稿
social.feed().post("Hello Nostr!")

// ノートにリプライ
social.feed().reply(
    content = "これはリプライです",
    replyToEventId = "target-event-id",
)

// ノートにいいね
social.reactions().like(
    eventId = "target-event-id",
    authorPubkey = "target-author-pubkey",
)
```

### ユーザープロフィール (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// プロフィールを取得
val user = social.users().getProfile("pubkey-hex").data
println("${user.name}: ${user.about}")

// フォローリストを取得
val following = social.users().getFollowing("pubkey-hex").data
```

### リアルタイムタイムライン (Social)

```kotlin
val stream = TimelineStream(nostr)
stream.onNoteCallback = { note ->
    println("新しいノート: ${note.content}")
}
stream.start(followingPubkeys)
```

### Zap / Lightning (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// Zap リクエスト (kind:9734) を作成
val zapRequest = social.zaps().createZapRequest(
    recipientPubkey = "target-pubkey-hex",
    amountMilliSats = 21000,
    relays = listOf("wss://relay.damus.io"),
    message = "素晴らしい投稿!",
    eventId = "target-event-id", // 省略可、null でプロフィール Zap
)

// ユーザーの Zap 受信を取得
val zaps = social.zaps().getZapsForUser("pubkey-hex", limit = 10).data

// Lightning アドレスから LNURL pay 情報を取得
val payInfo = social.zaps().getLnurlPayInfo("user@getalby.com").data
```

### メディアアップロード (Social)

```kotlin
val social = NostrSocialFactory.instance(nostr)

// NIP-96 サーバーのアップロード URL を取得
val uploadUrl = social.media().getServerInfo("https://nostr.build").data

// ファイルをアップロード
val media = social.media().upload(
    serverUrl = "https://nostr.build",
    fileData = imageBytes,
    fileName = "photo.jpg",
    mimeType = "image/jpeg",
    description = "写真",
).data
println("アップロード完了: ${media.url}")
```

### NIP ユーティリティ (Core)

```kotlin
// NIP-19: Bech32 エンコーディング
val npub = nostr.nip().encodeNpub("pubkey-hex")
val entity = nostr.nip().decodeNip19("npub1...")

// NIP-05: DNS ベースの ID 検証
val result = nostr.nip().resolveNip05("user@example.com")
```

## 対応 NIP

| NIP | 説明 | 状態 |
|-----|------|------|
| NIP-01 | 基本プロトコル | 実装済み |
| NIP-05 | DNS ベースの ID 検証 | 実装済み |
| NIP-10 | リプライスレッド (e-tag マーカー) | 実装済み |
| NIP-19 | Bech32 エンコーディング (npub, nsec, note) | 実装済み |
| NIP-25 | リアクション | 実装済み |
| NIP-50 | 検索 | 実装済み |
| NIP-57 | Lightning Zaps | 実装済み |
| NIP-96 | ファイルアップロード | 実装済み |
| NIP-98 | HTTP 認証 (NIP-96 用) | 実装済み |

## プラットフォームサポート

| プラットフォーム | Core | Social | 署名 |
|-----------------|------|--------|------|
| JVM | Yes | Yes | Yes |
| iOS/macOS | Yes | Yes | Yes |
| Linux x64 | Yes | - | Yes |
| JS (Node/Browser) | Yes | Yes | No |
| Windows (mingwX64) | Yes | Yes | No |

> 署名 (Schnorr/BIP-340) は現在 `secp256k1-kmp` を使用しており、JVM, Apple, Linux でのみ利用可能です。
> JS と Windows では、`NostrConfig` にカスタム `NostrSigner` 実装を設定して使用してください。

## ライセンス

MIT License

## 作者

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
