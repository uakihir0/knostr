package work.socialhub.knostr

import work.socialhub.knostr.entity.NostrFilter
import work.socialhub.knostr.internal.InternalUtility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NostrFilterTest {

    @Test
    fun testSerialize_withETags() {
        val filter = NostrFilter(
            eTags = listOf("abc123", "def456"),
        )
        val json = InternalUtility.toJson(filter)
        // SerialName("#e") should produce "#e" key in JSON
        assertTrue(json.contains("\"#e\""))
        assertTrue(json.contains("abc123"))
    }

    @Test
    fun testSerialize_withPTags() {
        val filter = NostrFilter(
            pTags = listOf("pubkey1", "pubkey2"),
        )
        val json = InternalUtility.toJson(filter)
        assertTrue(json.contains("\"#p\""))
        assertTrue(json.contains("pubkey1"))
    }

    @Test
    fun testSerialize_nullOmitted() {
        val filter = NostrFilter(
            kinds = listOf(1),
            limit = 10,
        )
        val json = InternalUtility.toJson(filter)
        // explicitNulls = false should omit null fields
        assertFalse(json.contains("\"ids\""))
        assertFalse(json.contains("\"authors\""))
        assertFalse(json.contains("\"since\""))
        assertFalse(json.contains("\"#e\""))
        assertFalse(json.contains("\"#p\""))
        assertTrue(json.contains("\"kinds\""))
        assertTrue(json.contains("\"limit\""))
    }

    @Test
    fun testSerialize_withTTags() {
        val filter = NostrFilter(
            tTags = listOf("nostr", "bitcoin"),
        )
        val json = InternalUtility.toJson(filter)
        assertTrue(json.contains("\"#t\""))
        assertTrue(json.contains("nostr"))
        assertTrue(json.contains("bitcoin"))
    }

    @Test
    fun testSerialize_withDTags() {
        val filter = NostrFilter(
            dTags = listOf("bookmark"),
        )
        val json = InternalUtility.toJson(filter)
        assertTrue(json.contains("\"#d\""))
        assertTrue(json.contains("bookmark"))
    }

    @Test
    fun testSerialize_withATags() {
        val filter = NostrFilter(
            aTags = listOf("30023:pubkey:article-slug"),
        )
        val json = InternalUtility.toJson(filter)
        assertTrue(json.contains("\"#a\""))
        assertTrue(json.contains("30023:pubkey:article-slug"))
    }

    @Test
    fun testSerialize_nullOmitsNewTags() {
        val filter = NostrFilter(
            kinds = listOf(1),
        )
        val json = InternalUtility.toJson(filter)
        assertFalse(json.contains("\"#t\""))
        assertFalse(json.contains("\"#d\""))
        assertFalse(json.contains("\"#a\""))
    }

    @Test
    fun testDeserialize_withNewTags() {
        val json = """{"kinds":[30001],"#d":["bookmark"],"#t":["nostr"],"#a":["30023:pk:slug"]}"""
        val filter = InternalUtility.fromJson<NostrFilter>(json)
        assertEquals(listOf("bookmark"), filter.dTags)
        assertEquals(listOf("nostr"), filter.tTags)
        assertEquals(listOf("30023:pk:slug"), filter.aTags)
    }

    @Test
    fun testDeserialize() {
        val json = """{"kinds":[1,6],"authors":["abc"],"#e":["evt1"],"limit":50}"""
        val filter = InternalUtility.fromJson<NostrFilter>(json)
        assertEquals(listOf(1, 6), filter.kinds)
        assertEquals(listOf("abc"), filter.authors)
        assertEquals(listOf("evt1"), filter.eTags)
        assertEquals(50, filter.limit)
    }
}
