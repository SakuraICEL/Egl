package com.moli.cet6

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

const val DEFAULT_DAILY_GOAL = 30

data class Word(
    val id: Int,
    val word: String,
    val phonetic: String,
    val meaning: String,
    val definition: String = "",
    val source: String = "原工程词表"
)

data class StudyUiState(
    val words: List<Word> = emptyList(),
    val currentWordId: Int? = null,
    val todayCount: Int = 0,
    val hardIds: Set<Int> = emptySet(),
    val loadError: String? = null,
    val reviews: Map<Int, ReviewProgress> = emptyMap(),
    val notebook: Set<Int> = emptySet(),
    val history: Map<String, Int> = emptyMap(),
    val books: List<WordBook> = emptyList(),
    val selectedBook: String = "all",
    val coverage: String = "",
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    val onlineWord: Word? = null,
    val lookupBusy: Boolean = false,
    val lookupMessage: String? = null,
    val catalogBusy: Boolean = false,
    val catalogMessage: String? = null,
    val passages: List<ReadingPassage> = emptyList(),
    val readingResults: Map<String, List<Int>> = emptyMap(),
    val readingBusy: Boolean = false,
    val readingMessage: String? = null,
    val aiBusy: Boolean = false,
    val aiAnswer: String = "",
    val aiMessage: String? = null,
    val aiEndpoint: String = "https://api.deepseek.com/chat/completions",
    val aiModel: String = "deepseek-flash",
    val hasAiKey: Boolean = false
)

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

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshDay() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val speech = rememberSpeech()
    CompositionLocalProvider(LocalSpeech provides speech) {
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
                    listOf("首页", "背词", "阅读", "复习", "词库", "设置").forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("⌂", "Aa", "阅", "↻", "词", "⚙")[index], fontSize = 16.sp) },
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
                    0 -> HomeScreen(state, onStart = { tab = 1 }, onSelect = vm::selectBook, onReview = { tab = 3 })
                    1 -> WordScreen(state, vm::rateCurrent, vm::toggleNotebook) { word -> vm.askAi("请讲解六级单词 ${word.word}：${word.meaning}。给出词义、常见搭配、两个双语例句和易混词区别。"); tab = 5 }
                    2 -> ReadingLibraryScreen(state, vm, onAi = { vm.askAi(it); tab = 5 }, onLookup = { vm.lookup(it); tab = 4 })
                    3 -> ReviewScreen(state, vm::rateWord, vm::toggleNotebook)
                    4 -> VocabularyScreen(state, vm, onStudy = { id -> vm.studyWord(id); tab = 1 }, onAi = { vm.askAi(it); tab = 5 })
                    else -> AiSettingsScreen(state, vm)
                }
            }
        }
    }
}

}

@Composable
private fun ErrorScreen(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Text("词库加载失败：$message", textAlign = TextAlign.Center)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeScreen(state: StudyUiState, onStart: () -> Unit, onSelect: (String) -> Unit, onReview: () -> Unit) {
    val progress = (state.todayCount.coerceAtMost(state.dailyGoal) / state.dailyGoal.toFloat())
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenTitle("六级背词", "从阅读里记住真正会考的词")
            BookSelector(state, onSelect)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("今日任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${state.todayCount} / ${state.dailyGoal} 次", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(if (state.todayCount >= state.dailyGoal) "今天目标完成 ✓" else "再练 ${state.dailyGoal - state.todayCount.coerceAtMost(state.dailyGoal)} 次即可完成")
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.todayCount == 0) "开始今日背词" else "继续背词")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("当前词库", "${state.books.firstOrNull { it.id == state.selectedBook }?.ids?.size ?: 0}", Modifier.weight(1f))
                StatCard("待复习", "${state.reviews.values.count { !it.due.isAfter(LocalDate.now()) }}", Modifier.weight(1f))
            }
        }
        item { OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("先复习到期词") } }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("学习统计", fontWeight = FontWeight.Bold)
                    Text("累计反馈 ${state.history.values.sum()} 次 · 学习 ${state.history.size} 天")
                    Text("已完成阅读 ${state.readingResults.size} 篇练习")
                    Text("已学 ${state.reviews.size} 词 · 熟悉 ${state.reviews.values.count { it.familiarity >= 3 }} 词 · 生词本 ${state.notebook.size} 词")
                    val today = LocalDate.now()
                    Text("近七天：" + (6 downTo 0).joinToString("  ") { offset ->
                        val day = today.minusDays(offset.toLong())
                        "${day.monthValue}/${day.dayOfMonth}: ${state.history[day.toString()] ?: 0}"
                    })
                    Text("先看单词 → 自己回忆 → 再显示释义 → 按“不会 / 模糊 / 认识”评级。不会当天重练，模糊隔天复习；认识后逐步延长间隔。统计按反馈次数计。")
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
private fun WordScreen(state: StudyUiState, onRate: (Int) -> Unit, onNotebook: (Int) -> Unit, onAi: (Word) -> Unit) {
    val word = state.words.firstOrNull { it.id == state.currentWordId } ?: state.words.firstOrNull() ?: return
    var reveal by remember(word.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("背词", "今日 ${state.todayCount} / ${state.dailyGoal}")
        LinearProgressIndicator(
            progress = { state.todayCount.coerceAtMost(state.dailyGoal) / state.dailyGoal.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(word.word, fontSize = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (word.phonetic.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(word.phonetic, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SpeakButton(word.word)
                TextButton(onClick = { onNotebook(word.id) }) { Text(if (word.id in state.notebook) "移出生词本" else "加入生词本") }
                TextButton(onClick = { onAi(word) }) { Text("AI 讲词") }
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
            "${state.books.firstOrNull { it.id == state.selectedBook }?.title ?: "词库"} · 来源：${word.source}",
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
fun LegacyReadingScreen(state: StudyUiState, onNotebook: (Int) -> Unit) {
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
        item { Text("难词收藏（点击加入 / 移出生词本）", fontWeight = FontWeight.Bold) }
        items(readingVocabulary) { entry ->
            val word = state.words.firstOrNull { it.word == entry.first }
            if (word != null) AssistChip(onClick = { onNotebook(word.id) }, label = {
                Text("${if (word.id in state.notebook) "✓ " else "+ "}${word.word} · ${word.meaning}")
            })
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
private fun ReviewScreen(state: StudyUiState, onRate: (Int, Int) -> Unit, onNotebook: (Int) -> Unit) {
    val today = LocalDate.now()
    val due = state.words.filter { state.reviews[it.id]?.due?.let { day -> !day.isAfter(today) } == true }
        .sortedBy { state.reviews[it.id]?.due }
    var currentId by rememberSaveable { mutableStateOf<Int?>(null) }
    val word = due.firstOrNull { it.id == currentId } ?: due.firstOrNull()
    var reveal by remember(word?.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("间隔复习", "今日到期 ${due.size} 词 · 未来待复习 ${state.reviews.size - due.size} 词") }
        if (word == null) item { Text("今日复习完成。明天再来看看，或继续背词。") }
        else item {
            val progress = state.reviews.getValue(word.id)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(word.word, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    SpeakButton(word.word)
                    Text("到期 ${progress.due} · 熟悉度 ${progress.familiarity}/5 · 连续认识 ${progress.streak} 次")
                    if (reveal) {
                        Text(word.meaning)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("不会", "模糊", "认识").forEachIndexed { level, label ->
                                OutlinedButton(onClick = {
                                    onRate(word.id, level)
                                    currentId = due.firstOrNull { it.id != word.id }?.id
                                    reveal = false
                                }) { Text(label) }
                            }
                        }
                    } else Button(onClick = { reveal = true }) { Text("回忆后显示释义") }
                    TextButton(onClick = { onNotebook(word.id) }) { Text(if (word.id in state.notebook) "移出生词本" else "加入生词本") }
                }
            }
        }
    }
}

private data class Speech(val message: String, val speak: (String) -> Unit)
private val LocalSpeech = staticCompositionLocalOf { Speech("正在初始化语音…") {} }

@Composable
private fun rememberSpeech(): Speech {
    val context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("正在初始化语音…") }
    DisposableEffect(context) {
        var disposed = false
        val tts = TextToSpeech(context) { status ->
            if (!disposed) {
                val current = engine
                val voice = if (status == TextToSpeech.SUCCESS) current?.voices?.filter {
                    it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired &&
                        !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                }?.sortedBy { if (it.locale == Locale.US) 0 else 1 }?.firstOrNull() else null
                ready = voice != null && current?.setVoice(voice) == TextToSpeech.SUCCESS
                message = if (ready) "英语发音" else "请在系统语音设置中安装离线英语语音包"
            }
        }
        engine = tts
        onDispose { disposed = true; ready = false; tts.stop(); tts.shutdown(); engine = null }
    }
    return Speech(message) { text ->
        if (!ready) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        else if (engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cet6-word") == TextToSpeech.ERROR)
            Toast.makeText(context, "发音失败，请检查系统语音设置", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun SpeakButton(text: String) {
    val speech = LocalSpeech.current
    OutlinedButton(onClick = { speech.speak(text) }) { Text("▶ ${speech.message}") }
}

private val readingVocabulary = listOf(
    "adapt" to "适应；改编",
    "attach" to "附上；系上",
    "contracts" to "合同；收缩（contract 的复数或第三人称单数）",
    "feeds" to "供给；喂养（feed 的第三人称单数）",
    "foreseeable" to "可预见的",
    "heralds" to "预示；先驱（herald 的变形）",
    "impractical" to "不切实际的",
    "inaccessible" to "难以到达的；难以理解的",
    "investments" to "投资（复数）",
    "modify" to "修改；调整",
    "predominantly" to "主要地；占主导地位地",
    "preference" to "偏好",
    "shift" to "转变；移动",
    "traditional" to "传统的",
    "unexpectedly" to "出乎意料地",
    "mechanization" to "机械化",
    "infrastructure" to "基础设施",
    "agricultural" to "农业的",
    "migrant" to "移民的；移居者"
)
