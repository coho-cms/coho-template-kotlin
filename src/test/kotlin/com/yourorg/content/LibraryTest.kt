package com.yourorg.content

import com.cohocms.delivery.ClientOptions
import com.cohocms.delivery.ContentConfig
import com.cohocms.delivery.ReleasedConfig
import com.cohocms.delivery.Transport
import com.cohocms.delivery.TransportResponse
import com.yourorg.content.coho.Coho
import com.yourorg.content.tools.Counted
import com.yourorg.content.tools.count
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks that hold for any project's types, so they keep passing after you regenerate for yours.
 * Add tests for your own reads beside them.
 */
class LibraryTest
{
    private fun answering(answer: (String) -> String) = Transport()
    { request ->
        CompletableFuture.completedFuture(TransportResponse(200, mapOf("content-type" to "application/json"), answer(request.url).toByteArray()))
    }

    @Test
    fun `the library names the type model it was generated from`()
    {
        assertTrue(Regex("sha256:[0-9a-f]{64}").matches(Coho.SOURCE.schemaHash))
    }

    @Test
    fun `a client asks nothing until it is read from, and the home page is one request`()
    {
        val asked = mutableListOf<String>()
        val transport = answering()
        { url ->
            asked += url
            """{"roots":[],"entries":{},"unresolved":{}}"""
        }
        val coho = Coho(ContentConfig(ReleasedConfig("https://delivery.test", Coho.SOURCE.project), "prod"), ClientOptions(transport))
        assertEquals(emptyList(), asked)

        assertNull(coho.homepage())
        assertEquals(listOf("https://delivery.test/v1/projects/${Coho.SOURCE.project}/prod/entries-by-slug/home-page?depth=2"), asked)
    }

    @Test
    fun `the catalog counts every type a hundred entries a request`()
    {
        // Sizes either side of a page, so some types take more than one request.
        val types = Coho.CONTENT_TYPES.sorted()
        val sizes = types.withIndex().associate { (i, type) -> type to listOf(30, 100, 170, 0)[i % 4] }
        val asked = mutableListOf<String>()
        val transport = answering()
        { url ->
            val query = URI.create(url).query.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
            val type = query.getValue("type")
            val offset = query.getValue("offset").toInt()
            val size = maxOf(0, minOf(query.getValue("limit").toInt(), sizes.getValue(type) - offset))
            asked += "$type@$offset"
            val ids = (offset until offset + size).map { "\"$type-$it\"" }
            val entries = ids.joinToString(",") { """$it:{"id":$it,"slug":$it,"type":"$type","fields":{}}""" }
            """{"roots":[${ids.joinToString(",")}],"entries":{$entries},"unresolved":{},"hasMore":${offset + size < sizes.getValue(type)}}"""
        }

        val counted = count(ContentConfig(ReleasedConfig("https://delivery.test", "p1"), "qa"), ClientOptions(transport))

        assertEquals(types.map { Counted(it, sizes.getValue(it)) }, counted)
        // One request per hundred entries, and one for an empty type: 170 asks at offsets 0 and 100.
        assertEquals(types.flatMap { type -> (0 until maxOf(1, (sizes.getValue(type) + 99) / 100)).map { "$type@${it * 100}" } }, asked)
    }
}
