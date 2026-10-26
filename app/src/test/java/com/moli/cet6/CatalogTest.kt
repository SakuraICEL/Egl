package com.moli.cet6

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class CatalogTest {
    private fun asset(name: String): String = File("src/main/assets/$name").readText()
    private fun original() = parseWords(org.json.JSONArray(asset("words.json")))

    @Test fun bundledCatalogHasCompleteConsistentMembershipAndStableIds() {
        val old = original()
        val catalog = parseCatalog(asset("catalog.json"), old)
        assertEquals(5806, catalog.books.first { it.id == "all" }.ids.size)
        assertEquals(841, catalog.books.first { it.id == "high" }.ids.size)
        assertEquals(1956, catalog.books.first { it.id == "core" }.ids.size)
        old.forEach { word ->
            val merged = catalog.words.first { it.id == word.id }
            assertEquals(word.word, merged.word)
            assertEquals(word.meaning, merged.meaning)
        }
        assertTrue(catalog.words.any { it.word == "assume" })
        assertTrue(catalog.words.any { it.word == "perspective" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun conflictingIdsNeverOverwriteLearningIdentity() {
        mergeWords(listOf(Word(1, "assume", "", "认为")), listOf(Word(1, "another", "", "另一个")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBrokenBookReferences() {
        val json = org.json.JSONObject(asset("catalog.json"))
        json.getJSONArray("books").getJSONObject(0).getJSONArray("ids").put(99999999)
        parseCatalog(json.toString(), original())
    }

    @Test fun bundledReadingRoundTripsAndHasValidAnswers() {
        val passages = parseReadingPack(asset("reading-pack.json"))
        assertEquals(3, passages.size)
        assertEquals(15, passages.sumOf { it.questions.size })
        assertEquals(passages, parseReadingPack(readingPackJson(passages)))
        val modified = passages[0].copy(text = passages[0].text + " New version.")
        assertNotEquals(readingKey(passages[0]), readingKey(modified))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesInvalidReadingAnswers() {
        val json = org.json.JSONObject(asset("reading-pack.json"))
        json.getJSONArray("passages").getJSONObject(0).getJSONArray("questions").getJSONObject(0).put("answer", 4)
        parseReadingPack(json.toString())
    }

    @Test fun secureEndpointValidationAndQueryBoundaries() {
        assertEquals("api.deepseek.com", validateHttpsEndpoint("https://api.deepseek.com/chat/completions").host)
        listOf("http://host.test/", "https://key@host.test/", "https://host.test/?key=secret", "https://host.test/#fragment").forEach {
            assertTrue(runCatching { validateHttpsEndpoint(it) }.isFailure)
        }
        assertEquals("assume", validateLookup(" assume "))
        listOf("", "../foo", "https://example.com", "你好", "a".repeat(81)).forEach { assertTrue(runCatching { validateLookup(it) }.isFailure) }
    }

    @Test fun limitsNetworkResponses() {
        assertArrayEquals(byteArrayOf(1, 2), ByteArrayInputStream(byteArrayOf(1, 2)).readBytesLimited(2))
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(3)).readBytesLimited(2) }.isFailure)
    }

    @Test fun dictionaryKeepsDefinitionExampleAndAttribution() {
        val json = """[{"word":"assume","phonetic":"əˈsjuːm","meanings":[{"partOfSpeech":"verb","definitions":[{"definition":"To suppose.","example":"I assume it works."}]}],"license":{"name":"CC BY-SA 3.0","url":"https://creativecommons.org/licenses/by-sa/3.0/"},"sourceUrls":["https://en.wiktionary.org/wiki/assume"]}]"""
        val word = parseDictionary(json)
        assertEquals("assume", word.word)
        assertTrue(word.meaning.contains("To suppose."))
        assertTrue(word.meaning.contains("I assume it works."))
        assertTrue(word.source.contains("CC BY-SA 3.0"))
        assertTrue(word.source.contains("wiktionary.org/wiki/assume"))
    }
}
