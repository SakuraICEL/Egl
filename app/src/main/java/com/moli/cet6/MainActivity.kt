package com.moli.cet6

import android.app.Application
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.time.LocalDate

private const val DAILY_GOAL = 30

data class Word(
    val id: Int,
    val word: String,
    val phonetic: String,
    val meaning: String
)

data class StudyUiState(
    val words: List<Word> = emptyList(),
    val currentWordId: Int? = null,
    val todayCount: Int = 0,
    val hardIds: Set<Int> = emptySet(),
    val loadError: String? = null
)

class StudyViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("cet6_study", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<StudyUiState> = _state

    private fun loadInitialState(): StudyUiState {
        return try {
            val words = loadWords()
            val today = LocalDate.now().toString()
            val storedDay = prefs.getString("day", "") ?: ""
            val todayCount = if (storedDay == today) prefs.getInt("todayCount", 0) else 0
            if (storedDay != today) prefs.edit().putString("day", today).putInt("todayCount", 0).apply()

            val hardIds = prefs.getString("hardIds", "")
                .orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
            val savedId = prefs.getInt("currentWordId", words.firstOrNull()?.id ?: -1)
            val currentId = words.firstOrNull { it.id == savedId }?.id ?: words.firstOrNull()?.id
            StudyUiState(words, currentId, todayCount, hardIds)
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

    fun rateCurrent(level: Int) {
        val s = _state.value
        val currentId = s.currentWordId ?: return
        val idx = s.words.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
        val nextId = s.words[(idx + 1) % s.words.size].id
        val newHard = s.hardIds.toMutableSet().apply {
            if (level <= 1) add(currentId) else remove(currentId)
        }.toSet()
        val newCount = s.todayCount + 1
        _state.value = s.copy(currentWordId = nextId, todayCount = newCount, hardIds = newHard)
        prefs.edit()
            .putInt("currentWordId", nextId)
            .putInt("todayCount", newCount)
            .putString("day", LocalDate.now().toString())
            .putString("hardIds", newHard.joinToString(","))
            .apply()
    }

    fun markMastered(wordId: Int) {
        val s = _state.value
        val newHard = s.hardIds - wordId
        _state.value = s.copy(hardIds = newHard)
        prefs.edit().putString("hardIds", newHard.joinToString(",")).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CET6App() }
    }
}

@Composable
fun CET6App(vm: StudyViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6C4DFF),
            secondary = Color(0xFF8B5CF6),
            tertiary = Color(0xFFA855F7),
            background = Color(0xFFFFF9FC),
            surface = Color(0xFFFFFBFF)
        )
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("首页", "背词", "阅读", "复习").forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("⌂", "Aa", "阅", "↻")[index], fontSize = 16.sp) },
                            label = { Text(title) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (state.loadError != null) {
                    ErrorScreen(state.loadError!!)
                } else when (tab) {
                    0 -> HomeScreen(state, onStart = { tab = 1 })
                    1 -> WordScreen(state, vm::rateCurrent)
                    2 -> ReadingScreen()
                    else -> ReviewScreen(state, vm::markMastered)
                }
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("词库加载失败：$message", textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeScreen(state: StudyUiState, onStart: () -> Unit) {
    val progress = (state.todayCount.coerceAtMost(DAILY_GOAL) / DAILY_GOAL.toFloat())
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenTitle("六级背词", "从阅读里记住真正会考的词")
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("今日任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${state.todayCount} / $DAILY_GOAL 个", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(if (state.todayCount >= DAILY_GOAL) "今天目标完成 ✓" else "再学 ${DAILY_GOAL - state.todayCount.coerceAtMost(DAILY_GOAL)} 个即可完成")
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.todayCount == 0) "开始今日背词" else "继续背词")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("高频词库", "${state.words.size}", Modifier.weight(1f))
                StatCard("待复习", "${state.hardIds.size}", Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("第一版学习逻辑", fontWeight = FontWeight.Bold)
                    Text("先看单词 → 自己回忆 → 再显示释义 → 按“不会 / 模糊 / 认识”评级。不会和模糊会自动进入复习页。")
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WordScreen(state: StudyUiState, onRate: (Int) -> Unit) {
    val word = state.words.firstOrNull { it.id == state.currentWordId } ?: state.words.firstOrNull() ?: return
    var reveal by remember(word.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("背词", "今日 ${state.todayCount} / $DAILY_GOAL")
        LinearProgressIndicator(
            progress = { state.todayCount.coerceAtMost(DAILY_GOAL) / DAILY_GOAL.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(word.word, fontSize = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (word.phonetic.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(word.phonetic, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(28.dp))
                if (reveal) {
                    Text(word.meaning, fontSize = 20.sp, textAlign = TextAlign.Center, lineHeight = 30.sp)
                } else {
                    OutlinedButton(onClick = { reveal = true }) { Text("显示释义") }
                }
            }
        }
        if (reveal) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onRate(0) }, Modifier.weight(1f)) { Text("不会") }
                OutlinedButton({ onRate(1) }, Modifier.weight(1f)) { Text("模糊") }
                Button({ onRate(2) }, Modifier.weight(1f)) { Text("认识") }
            }
        }
        Text(
            "词库来源：你上传的《大学英语六级高频词汇》",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class ClozeItem(val no: Int, val answer: String)
private val clozeItems = listOf(
    ClozeItem(26, "feeds"),
    ClozeItem(27, "shift"),
    ClozeItem(28, "impractical"),
    ClozeItem(29, "predominantly"),
    ClozeItem(30, "contracts"),
    ClozeItem(31, "adapt"),
    ClozeItem(32, "modify"),
    ClozeItem(33, "traditional"),
    ClozeItem(34, "foreseeable"),
    ClozeItem(35, "investments")
)
private val wordBank = listOf(
    "adapt", "attach", "contracts", "feeds", "foreseeable", "heralds", "impractical", "inaccessible",
    "investments", "modify", "predominantly", "preference", "shift", "traditional", "unexpectedly"
)

@Composable
private fun ReadingScreen() {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var answered by rememberSaveable { mutableStateOf(false) }
    val item = clozeItems[index]

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenTitle("阅读背词", "模拟卷完形 · 第 ${index + 1}/10 空") }
        item {
            AssistChip(
                onClick = {},
                label = { Text("答案为 AI 推断，非原卷标准答案") }
            )
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Text(
                    "As the recent courgette crisis and shortages of lettuce, eggplants and broccoli have shown, Spain’s fame as the vegetable garden of Europe is well deserved. The country’s huge agricultural sector—courgettes, lettuces, tomatoes and strawberries—__26__ a huge demand.\n\n" +
                        "There has been a major __27__ towards mechanization since the 1950s, but many crops still need to be harvested by hand. Even where mechanisation can be used, picking machines tend to be too expensive and __28__ for small-scale farmers.\n\n" +
                        "The tension between locals and migrant workers, __29__ from North and Sub-Saharan Africa and eastern Europe, is not a big problem, as many foreign workers have proper __30__.\n\n" +
                        "There is a machine that gathers strawberries, but you have to __31__ the crop to the machine. If machines were used, farmers would have to __32__ their entire infrastructure.\n\n" +
                        "The __33__ manual approach is likely to continue for the __34__ future, because fruit prices are not high enough to allow big __35__ in machinery.",
                    modifier = Modifier.padding(18.dp),
                    lineHeight = 24.sp
                )
            }
        }
        item {
            Text("请选择填入 __${item.no}__ 的词：", fontWeight = FontWeight.Bold)
        }
        items(wordBank) { option ->
            OutlinedButton(
                onClick = {
                    if (!answered) {
                        selected = option
                        answered = true
                        if (option == item.answer) score++
                    }
                },
                enabled = !answered,
                modifier = Modifier.fillMaxWidth()
            ) { Text(option) }
        }
        if (answered) {
            item {
                val correct = selected == item.answer
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (correct) "✓ 正确" else "✗ 这一空更合适的是 ${item.answer}", fontWeight = FontWeight.Bold)
                        Text("当前得分：$score / ${index + 1}")
                        Button(
                            onClick = {
                                if (index < clozeItems.lastIndex) {
                                    index++
                                    selected = null
                                    answered = false
                                } else {
                                    index = 0
                                    selected = null
                                    answered = false
                                    score = 0
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (index < clozeItems.lastIndex) "下一空" else "重新练习")
                        }
                    }
                }
            }
        }
        item {
            Text(
                "题目来源：你上传的《六级模拟卷（四）》Reading Section A；原文件在此版本中未附标准答案。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReviewScreen(state: StudyUiState, onMastered: (Int) -> Unit) {
    val hardWords = state.words.filter { it.id in state.hardIds }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("复习", "不会 / 模糊的词会自动出现在这里") }
        if (hardWords.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Text("暂时没有待复习词。背词时把“不确定”的词标成「模糊」即可加入这里。", Modifier.padding(18.dp))
                }
            }
        } else {
            items(hardWords, key = { it.id }) { word ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(word.word, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        if (word.phonetic.isNotBlank()) Text(word.phonetic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(word.meaning)
                        OutlinedButton(onClick = { onMastered(word.id) }) { Text("已掌握，移出复习") }
                    }
                }
            }
        }
    }
}
