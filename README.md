# 六级背词 · 莫璃（Android v0.2）

在现有 Kotlin / Compose 工程上迭代，离线、无账号、无广告、无单词额度限制。

## 学习

- 保留原有 841 词，补充个人词汇和阅读词；默认收藏 assume、perspective。
- 首页展示每日 30 次练习目标、到期复习量、累计反馈、学习天数、已学词数和最近七天记录。
- 背词先回忆，再显示释义，选择「不会 / 模糊 / 认识」。
- 词库支持英文、中文搜索；可收藏、取消收藏，从任意词进入学习。
- 阅读页保留原练习与 AI 答案说明，难词 chips 可直接收藏。

## 间隔复习

每个已学词保存到期日期（due）、熟悉度（familiarity，0–5）和连续认识次数（streak，最多 6）。

- 不会：熟悉度和 streak 归零，当天重练。
- 模糊：熟悉度减一（最低零），streak 归零，次日复习。
- 认识：熟悉度加一（最高五），按连续认识次数安排 1、3、7、14、30、60 天间隔。
- 原版 hardIds 自动迁移为当天到期；原学习位置与今日数量保留。
- 生词收藏和学习记录独立；移出收藏不会删除复习记录。
- 今日数量与累计反馈为练习次数，含重复复习；已学词数按词去重。

## 系统发音

使用 Android TextToSpeech，筛选已安装且不需要网络的英语语音。需要手机预先安装系统 TTS 引擎和离线英语语音包；缺少时会提示，不会调用在线语音。App 本身不申请 INTERNET 权限。

TTS 生命周期处理依据 [Android 官方文档](https://developer.android.com/reference/android/speech/tts/TextToSpeech)。

## 构建

Android 8.0（API 26）及以上；compileSdk / targetSdk 35；Kotlin 2.0.21、AGP 8.7.3、Gradle 8.9。使用 **JDK 21**。

```sh
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

GitHub Actions 在 main 推送或手动触发时运行测试并生成 `CET6-Moli-v0.2.apk`，在运行页 Artifacts 下载 `CET6-Moli-v0.2-apk`。这是调试 APK；旧版若签名不同，系统会拒绝覆盖安装，请先备份学习数据，不要直接卸载以免丢失。

## 验证与限制

复习调度单元测试覆盖失败归零、模糊重置、间隔递增与上限。学习数据通过 SharedPreferences 保存于本机。当前统计以本机日期为准，阅读题答案仍为原工程标注的 AI 推断。发音和手机界面仍需真机确认。
