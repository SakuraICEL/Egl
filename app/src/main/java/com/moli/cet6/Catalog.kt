package com.moli.cet6

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

const val CATALOG_URL = "https://raw.githubusercontent.com/SakuraICEL/Egl/main/app/src/main/assets/catalog.json"
const val READING_URL = "https://raw.githubusercontent.com/SakuraICEL/Egl/main/app/src/main/assets/reading-pack.json"

data class WordBook(val id: String, val title: String, val description: String, val ids: Set<Int>)
data class Catalog(val words: List<Word>, val books: List<WordBook>, val coverage: String, val version: Int)

fun parseWords(array: JSONArray): List<Word> = (0 until array.length()).map { i ->
    val o = array.getJSONObject(i)
    Word(o.getInt("id"), o.getString("word"), o.optString("phonetic"), o.getString("meaning"), o.optString("definition"), o.optString("source", "原工程词表"))
        .also { require(it.id > 0 && it.word.isNotBlank() && it.word.length <= 100 && it.meaning.isNotBlank() && it.meaning.length <= 20000) { "词条格式不正确" } }
}.also { require(it.map { w -> w.id }.toSet().size == it.size) { "词条编号重复" } }

fun wordJson(word: Word): JSONObject = JSONObject().put("id", word.id).put("word", word.word).put("phonetic", word.phonetic)
    .put("meaning", word.meaning).put("definition", word.definition).put("source", word.source)

fun mergeWords(original: List<Word>, additions: List<Word>): List<Word> {
    val result = original.associateBy { it.id }.toMutableMap()
    additions.forEach { word ->
        val old = result[word.id]
        require(old == null || old.word.equals(word.word, true)) { "词库编号冲突，已保留现有数据" }
        if (old == null) result[word.id] = word
        else result[word.id] = old.copy(definition = word.definition.ifBlank { old.definition }, source = if (word.source == "ECDICT") "原词条 / ECDICT 补充" else old.source)
    }
    return result.values.toList()
}

fun parseCatalog(text: String, original: List<Word>): Catalog {
    val o = JSONObject(text)
    val version = o.getInt("version")
    require(version >= 1)
    val words = mergeWords(original, parseWords(o.getJSONArray("words")))
    require(words.size <= 30000)
    val known = words.map { it.id }.toSet()
    val booksJson = o.getJSONArray("books")
    require(booksJson.length() == 3)
    val books = (0 until booksJson.length()).map { i ->
        val b = booksJson.getJSONObject(i)
        val list = b.getJSONArray("ids")
        val ids = (0 until list.length()).map { list.getInt(it) }.toSet()
        require(ids.isNotEmpty() && known.containsAll(ids)) { "词库包含缺失词条" }
        WordBook(b.getString("id"), b.getString("title").take(40), b.getString("description").take(400), ids)
    }
    require(books.map { it.id }.toSet() == setOf("all", "high", "core"))
    val all = books.first { it.id == "all" }.ids
    require(books.all { all.containsAll(it.ids) })
    return Catalog(words, books, o.getString("coverage").take(2000), version)
}

fun validateHttpsEndpoint(value: String): URI {
    val uri = URI(value)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && uri.rawQuery == null) { "请填写不含密钥或参数的 HTTPS 接口地址" }
    return uri
}

fun validateLookup(query: String): String = query.trim().also {
    require(it.matches(Regex("[A-Za-z][A-Za-z '-]{0,79}"))) { "联网查词请输入一个英文单词或短语（最多 80 字符）" }
}

data class ReadingQuestion(val prompt: String, val options: List<String>, val answer: Int, val explanation: String)
data class ReadingPassage(val id: String, val title: String, val text: String, val source: String, val vocabulary: List<String>, val questions: List<ReadingQuestion>)

fun parseReadingPack(text: String): List<ReadingPassage> {
    val array = JSONObject(text).getJSONArray("passages")
    require(array.length() in 1..50) { "阅读包篇数不正确" }
    return (0 until array.length()).map { i ->
        val p = array.getJSONObject(i)
        val questions = p.getJSONArray("questions")
        require(questions.length() in 1..10)
        val qs = (0 until questions.length()).map { n ->
            val q = questions.getJSONObject(n)
            val opts = q.getJSONArray("options")
            require(opts.length() == 4)
            val answer = q.getInt("answer")
            require(answer in 0..3)
            ReadingQuestion(q.getString("prompt"), (0..3).map { opts.getString(it) }, answer, q.getString("explanation"))
                .also { require(it.prompt.isNotBlank() && it.prompt.length <= 2000 && it.options.all { option -> option.isNotBlank() && option.length <= 2000 } && it.explanation.isNotBlank()) }
        }
        val vocab = p.optJSONArray("vocabulary") ?: JSONArray()
        require(vocab.length() <= 60)
        ReadingPassage(p.getString("id"), p.getString("title"), p.getString("text"), p.getString("source"), (0 until vocab.length()).map { vocab.getString(it).take(80) }, qs)
            .also { require(it.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && it.title.isNotBlank() && it.title.length <= 150 && it.text.length in 100..15000 && it.source.isNotBlank()) }
    }.also { require(it.map { p -> p.id }.toSet().size == it.size) }
}

fun readingKey(passage: ReadingPassage): String = passage.id + "_" + (passage.text + passage.questions.toString()).hashCode()

fun readingPackJson(passages: List<ReadingPassage>): String {
    val array = JSONArray()
    passages.forEach { p ->
        val questions = JSONArray()
        p.questions.forEach { q -> questions.put(JSONObject().put("prompt", q.prompt).put("options", JSONArray(q.options)).put("answer", q.answer).put("explanation", q.explanation)) }
        array.put(JSONObject().put("id", p.id).put("title", p.title).put("text", p.text).put("source", p.source).put("vocabulary", JSONArray(p.vocabulary)).put("questions", questions))
    }
    return JSONObject().put("passages", array).toString()
}
