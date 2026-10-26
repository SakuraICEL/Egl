package com.moli.cet6

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.io.File

class StudyViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("cet6_study", Context.MODE_PRIVATE)
    private val appContext = app
    private val catalogFile = File(app.filesDir, "catalog.json")
    private val readingFile = File(app.filesDir, "reading-pack.json")
    private val generatedFile = File(app.filesDir, "generated-reading.json")
    private var aiKey: String = ""
    private var lookupJob: Job? = null
    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<StudyUiState> = _state

    private fun loadInitialState(): StudyUiState {
        return try {
            val original = loadWords()
            val bundled = appContext.assets.open("catalog.json").bufferedReader().use { it.readText() }
            val catalog = runCatching { parseCatalog(catalogFile.readText(), original) }.getOrElse { parseCatalog(bundled, original) }
            val extras = parseWords(JSONArray(prefs.getString("externalWords", "[]") ?: "[]"))
            val words = mergeWords(catalog.words, extras)
            val selectedBook = prefs.getString("selectedBook", "all").orEmpty().takeIf { id -> catalog.books.any { it.id == id } } ?: "all"
            val today = LocalDate.now().toString()
            val storedDay = prefs.getString("day", "") ?: ""
            val todayCount = if (storedDay == today) prefs.getInt("todayCount", 0) else 0
            val storedCount = prefs.getInt("todayCount", 0)

            val hardIds = prefs.getString("hardIds", "")
                .orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
            val savedId = prefs.getInt("currentWordId", words.firstOrNull()?.id ?: -1)
            val currentId = words.firstOrNull { it.id == savedId }?.id ?: words.firstOrNull()?.id
            val reviewsJson = JSONObject(prefs.getString("reviews", "{}") ?: "{}")
            val reviews = buildMap {
                reviewsJson.keys().forEach { key ->
                    val o = reviewsJson.getJSONObject(key)
                    put(key.toInt(), ReviewProgress(LocalDate.parse(o.getString("due")), o.getInt("familiarity"), o.getInt("streak")))
                }
                hardIds.forEach { if (!containsKey(it)) put(it, ReviewProgress(LocalDate.now(), 0, 0)) }
            }
            val notebook = if (prefs.contains("notebook")) prefs.getString("notebook", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
                else words.filter { it.word in setOf("assume", "perspective") }.map { it.id }.toSet()
            val historyJson = JSONObject(prefs.getString("history", "{}") ?: "{}")
            val history = buildMap { historyJson.keys().forEach { put(it, historyJson.getInt(it)) } }.toMutableMap()
            if (storedCount > 0 && storedDay.isNotBlank() && !history.containsKey(storedDay)) history[storedDay] = storedCount
            val migratedHistory = JSONObject()
            history.forEach { (day, count) -> migratedHistory.put(day, count) }
            prefs.edit().putString("history", migratedHistory.toString()).putString("day", today).putInt("todayCount", todayCount).apply()
            prefs.edit().putString("notebook", notebook.joinToString(",")).apply()
            val passageText = appContext.assets.open("reading-pack.json").bufferedReader().use { it.readText() }
            val passages = runCatching { parseReadingPack(readingFile.readText()) }.getOrElse { parseReadingPack(passageText) }
            val generated = runCatching { parseReadingPack(generatedFile.readText()) }.getOrDefault(emptyList())
            val results = JSONObject(prefs.getString("readingResults", "{}") ?: "{}")
            val readingResults = buildMap {
                results.keys().forEach { key ->
                    val answers = results.getJSONArray(key)
                    put(key, (0 until answers.length()).map { answers.getInt(it) })
                }
            }
            StudyUiState(words, currentId, todayCount, hardIds, reviews = reviews, notebook = notebook, history = history,
                books = catalog.books, selectedBook = selectedBook, coverage = catalog.coverage,
                dailyGoal = prefs.getInt("dailyGoal", DEFAULT_DAILY_GOAL).coerceIn(5, 200),
                passages = (passages + generated).distinctBy { it.id }, readingResults = readingResults,
                aiEndpoint = prefs.getString("aiEndpoint", "https://api.deepseek.com/chat/completions").orEmpty(),
                aiModel = prefs.getString("aiModel", "deepseek-flash").orEmpty())
        } catch (e: Exception) {
            StudyUiState(loadError = e.message ?: "词库加载失败")
        }
    }

    private fun loadWords(): List<Word> {
        val text = getApplication<Application>().assets.open("words.json").bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(Word(o.getInt("id"), o.getString("word"), o.optString("phonetic"), o.getString("meaning")))
            }
        }
    }

    fun refreshDay() {
        val s = _state.value
        _state.value = s.copy(todayCount = s.history[LocalDate.now().toString()] ?: 0)
    }

    fun rateCurrent(level: Int) {
        val s = _state.value
        val id = s.currentWordId ?: return
        rateWord(id, level)
        val book = s.books.firstOrNull { it.id == s.selectedBook }
        val queue = s.words.filter { it.id in book?.ids.orEmpty() }
        val idx = queue.indexOfFirst { it.id == id }
        val next = queue.getOrNull((idx + 1) % queue.size.coerceAtLeast(1))?.id ?: id
        _state.value = _state.value.copy(currentWordId = next)
        prefs.edit().putInt("currentWordId", next).putInt("position_${s.selectedBook}", next).apply()
    }

    fun rateWord(id: Int, level: Int) {
        require(level in 0..2)
        val s = _state.value
        if (s.words.none { it.id == id }) return
        val today = LocalDate.now()
        val reviews = s.reviews + (id to scheduleReview(s.reviews[id], level, today))
        val hard = if (level < 2) s.hardIds + id else s.hardIds - id
        val history = s.history + (today.toString() to ((s.history[today.toString()] ?: 0) + 1))
        val json = JSONObject()
        reviews.forEach { (key, value) -> json.put(key.toString(), JSONObject().put("due", value.due.toString()).put("familiarity", value.familiarity).put("streak", value.streak)) }
        val historyJson = JSONObject()
        history.forEach { (day, count) -> historyJson.put(day, count) }
        prefs.edit().putString("reviews", json.toString()).putString("hardIds", hard.joinToString(","))
            .putString("history", historyJson.toString()).putString("day", today.toString())
            .putInt("todayCount", history.getValue(today.toString())).apply()
        _state.value = s.copy(reviews = reviews, hardIds = hard, history = history, todayCount = history.getValue(today.toString()))
    }

    fun studyWord(id: Int) {
        if (_state.value.words.none { it.id == id }) return
        _state.value = _state.value.copy(currentWordId = id)
        prefs.edit().putInt("currentWordId", id).apply()
    }

    fun toggleNotebook(id: Int) {
        val s = _state.value
        val ids = if (id in s.notebook) s.notebook - id else s.notebook + id
        prefs.edit().putString("notebook", ids.joinToString(",")).apply()
        _state.value = s.copy(notebook = ids)
    }

    fun selectBook(id: String) {
        val s = _state.value
        val book = s.books.firstOrNull { it.id == id } ?: return
        val saved = prefs.getInt("position_$id", -1).takeIf { it in book.ids }
        val current = saved ?: book.ids.firstOrNull()
        prefs.edit().putString("selectedBook", id).putInt("currentWordId", current ?: -1).apply()
        _state.value = s.copy(selectedBook = id, currentWordId = current)
    }

    fun setDailyGoal(value: Int) {
        val goal = value.coerceIn(5, 200)
        prefs.edit().putInt("dailyGoal", goal).apply()
        _state.value = _state.value.copy(dailyGoal = goal)
    }

    fun lookup(query: String) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            _state.value = _state.value.copy(lookupBusy = true, lookupMessage = null, onlineWord = null)
            try {
                val result = withContext(Dispatchers.IO) { OnlineService.lookup(query) }
                _state.value = _state.value.copy(onlineWord = result, lookupBusy = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(lookupBusy = false, lookupMessage = networkMessage(e)) }
        }
    }

    fun saveOnlineWord() {
        val s = _state.value
        val word = s.onlineWord ?: return
        val old = s.words.firstOrNull { it.word.equals(word.word, true) }
        val saved = old ?: word.copy(id = (s.words.maxOfOrNull { it.id } ?: 100000).coerceAtLeast(100000) + 1)
        val extras = parseWords(JSONArray(prefs.getString("externalWords", "[]") ?: "[]"))
        val updatedExtras = if (old == null) extras + saved else extras
        val notebook = s.notebook + saved.id
        prefs.edit().putString("externalWords", JSONArray().also { arr -> updatedExtras.forEach { arr.put(wordJson(it)) } }.toString())
            .putString("notebook", notebook.joinToString(",")).apply()
        _state.value = s.copy(words = if (old == null) s.words + saved else s.words, notebook = notebook, lookupMessage = "已加入生词本，可离线学习")
    }

    fun updateCatalog() {
        if (_state.value.catalogBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(catalogBusy = true, catalogMessage = null)
            try {
                val catalog = withContext(Dispatchers.IO) {
                    val text = OnlineService.request(CATALOG_URL)
                    val parsed = parseCatalog(text, loadWords())
                    val previous = _state.value.words.filter { it.id < 100000 }
                    val currentById = parsed.words.associateBy { it.id }
                    require(previous.all { old -> currentById[old.id]?.word?.equals(old.word, true) == true }) { "新词库不兼容现有编号，已保留原数据" }
                    atomicWrite(catalogFile, text)
                    parsed
                }
                val s = _state.value
                val extras = s.words.filter { it.id >= 100000 }
                _state.value = s.copy(words = mergeWords(catalog.words, extras), books = catalog.books, coverage = catalog.coverage,
                    catalogBusy = false, catalogMessage = "词库已更新，共 ${catalog.books.first { it.id == "all" }.ids.size} 词；学习记录已保留")
            } catch (e: Exception) { _state.value = _state.value.copy(catalogBusy = false, catalogMessage = networkMessage(e)) }
        }
    }

    fun updateReading() {
        if (_state.value.readingBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(readingBusy = true, readingMessage = null)
            try {
                val passages = withContext(Dispatchers.IO) {
                    val text = OnlineService.request(READING_URL)
                    val result = parseReadingPack(text)
                    atomicWrite(readingFile, text)
                    result
                }
                val s = _state.value
                _state.value = s.copy(passages = (passages + s.passages.filter { it.source.startsWith("AI") }).distinctBy { it.id },
                    readingBusy = false, readingMessage = "已下载 ${passages.size} 篇，可离线作答")
            } catch (e: Exception) { _state.value = _state.value.copy(readingBusy = false, readingMessage = networkMessage(e)) }
        }
    }

    fun submitReading(passage: ReadingPassage, answers: List<Int>) {
        require(answers.size == passage.questions.size && answers.all { it in 0..3 })
        val s = _state.value
        val results = s.readingResults + (readingKey(passage) to answers)
        val json = JSONObject()
        results.forEach { (key, values) -> json.put(key, JSONArray(values)) }
        prefs.edit().putString("readingResults", json.toString()).apply()
        _state.value = s.copy(readingResults = results)
    }

    fun clearReading(passage: ReadingPassage) {
        val s = _state.value
        val results = s.readingResults - readingKey(passage)
        val json = JSONObject()
        results.forEach { (key, values) -> json.put(key, JSONArray(values)) }
        prefs.edit().putString("readingResults", json.toString()).apply()
        _state.value = s.copy(readingResults = results)
    }

    fun configureAi(endpoint: String, model: String, key: String) {
        try {
            validateHttpsEndpoint(endpoint.trim())
            require(model.isNotBlank()) { "请填写模型名称" }
            if (key.isNotBlank()) aiKey = key.trim()
            prefs.edit().putString("aiEndpoint", endpoint.trim()).putString("aiModel", model.trim()).apply()
            _state.value = _state.value.copy(aiEndpoint = endpoint.trim(), aiModel = model.trim(), hasAiKey = aiKey.isNotBlank(), aiMessage = "设置已应用；密钥仅在本次运行中保留")
        } catch (e: Exception) { _state.value = _state.value.copy(aiMessage = e.message ?: "设置无效") }
    }

    fun clearAiKey() {
        aiKey = ""
        _state.value = _state.value.copy(hasAiKey = false, aiMessage = "密钥已清除")
    }

    fun askAi(prompt: String, generate: Boolean = false) {
        val s = _state.value
        if (s.aiBusy) return
        if (aiKey.isBlank()) {
            _state.value = s.copy(aiMessage = "请先设置自己的 AI 服务和 API 密钥")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(aiBusy = true, aiMessage = null, aiAnswer = "")
            try {
                val answer = withContext(Dispatchers.IO) { OnlineService.chat(s.aiEndpoint, s.aiModel, aiKey, prompt) }
                _state.value = _state.value.copy(aiAnswer = answer)
                if (generate) {
                    withContext(Dispatchers.IO) {
                        val jsonText = answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val generated = parseReadingPack(jsonText).mapIndexed { index, p -> p.copy(id = "ai_${System.currentTimeMillis()}_$index", source = "AI 生成练习 · 答案与解析需核对") }
                        val existing = runCatching { parseReadingPack(generatedFile.readText()) }.getOrDefault(emptyList())
                        val combined = (existing + generated).takeLast(50)
                        atomicWrite(generatedFile, readingPackJson(combined))
                        withContext(Dispatchers.Main) {
                            val current = _state.value
                            _state.value = current.copy(passages = current.passages.filterNot { it.source.startsWith("AI") } + combined,
                                aiMessage = "生成题已加入阅读题库，已缓存；请核对 AI 答案")
                        }
                    }
                }
                _state.value = _state.value.copy(aiBusy = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(aiBusy = false, aiMessage = networkMessage(e) + if (generate) "；未能导入的原始回答会保留在下方" else "")
            }
        }
    }

    private fun atomicWrite(file: File, text: String) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
    }

    private fun networkMessage(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "无法连接服务器，请检查网络；离线内容仍可使用"
        is java.net.SocketTimeoutException -> "连接超时，请稍后重试；本地内容已保留"
        is javax.net.ssl.SSLException -> "安全连接失败，请检查设备时间或网络"
        is org.json.JSONException -> "服务返回的格式不正确，已保留本地内容"
        is IllegalArgumentException, is IllegalStateException -> e.message ?: "内容校验失败"
        else -> "联网操作未完成，请检查网络或服务设置后重试"
    }

}

