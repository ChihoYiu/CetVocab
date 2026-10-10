# 迭代报告 3 · 发音（系统 TTS）/ 学习进度 / 学习分析 / 用户激励

本报告对应 [产品优化方案](PRODUCT_OPTIMIZATION_PLAN.md) 的 S1–S6 阶段实施结果。
一句话结论：**四个方向全部落地，全量测试 370 个用例通过（0 失败 / 0 错误）**，
调试包与 R8 发布包均构建成功，并且**已从应用清单中移除 `INTERNET` 权限**（`apkanalyzer` 复核两个包均已不含它）。

---

## 0. 最重要的变更：发音方案反转（含代价）

上一轮交付的是「真人录音 + 美式/英式双口音」；本轮按新需求改为「**删除美英选项，使用系统 TTS**」。
这是一次方向反转，我把两边的事实都记录下来，避免以后有人只看一半：

| | 上一轮（已删除） | 本轮（当前实现） |
| --- | --- | --- |
| 音频 | Wikimedia Commons / Wiktionary 真人录音 | 系统文字转语音引擎 |
| 口音 | 美式、英式各自独立文件 | **不提供口音选择**，语言、声音、语速、音调跟随系统 |
| 覆盖 | 实测美式 11/14、英式仅 1/14 有录音 | **任意单词都能读**（覆盖问题消失） |
| 离线 | 首次需联网 | 引擎本身离线可用；语音包可能需要用户先下载 |
| 权限 | 需要 `INTERNET` + `ACCESS_NETWORK_STATE` | **应用清单已删除这两个声明**（`ACCESS_NETWORK_STATE` 仍会由 androidx.work 合并进来，见下） |
| 代码 | `data/pronunciation/`（6 个文件）+ 播放器 | 已全部删除，改为 `data/speech/`（4 个文件） |

删除的代码：`data/pronunciation/{PronunciationModels,AudioHttp,PronunciationSources,PronunciationCache,PronunciationRepository,AudioFormat}.kt`、
`ui/speech/PronunciationController.kt`、`ui/speech/PronunciationPlayer.kt`，以及 3 个测试类
（`AudioFormatTest` 4 + `PronunciationRepositoryTest` 12 + `PronunciationSourceTest` 8 = 24 个用例）
和已失效的 `AccentSelectionTest`（2 个用例，其职责由新的语音测试承接）。

---

## 1. 本轮修改的文件

### 1.1 发音（S1 + S2）

| 文件 | 变更 |
| --- | --- |
| `data/pronunciation/**` | **删除**（整个网络音频模块） |
| `ui/speech/PronunciationController.kt`、`PronunciationPlayer.kt` | **删除** |
| `data/speech/SpeechModels.kt` | 新增：`SpeechRateMode`、`SpeechContent`、`SpeechRequest`、`SpeechStatus`（含 `recovery`）、`SpeechRecovery`、`SpeechService` 接口 |
| `data/speech/SystemSpeechDefaults.kt` | 新增：读取系统 TTS 偏好（`tts_default_rate/pitch/synth/locale`），**读不到就返回 null，绝不猜** |
| `data/speech/SystemTtsSpeechService.kt` | 新增：系统默认引擎、初始化前请求排队（修掉「第一次点击没反应」）、三种失败分支、`openSystemSpeechSettings` / `requestVoiceDataInstall` |
| `data/speech/SpeechDiagnosticsStore.kt` | 新增：本机朗读成功/失败计数（不上传） |
| `ui/speech/SpeechController.kt` | 新增：四态状态机 + **3 秒初始化超时** + 重试 |
| `ui/speech/SpeechActions.kt` | 新增：把 `Word` 转成朗读请求（单词/例句/中文释义），统一失败动作 |
| `ui/components/SpeakButton.kt` | 新增：唯一的播放控件（Idle/Loading/Playing/Failed）+ `SpeechMessage` 恢复入口 |
| `ui/components/SpeechStatusCard.kt` | 新增：设置页「发音服务状态」（引擎、是否读到系统语速、试听、成功率） |
| `ui/components/WordCard.kt` | 移除美/英选择器，改用 `SpeakButton`；例句按钮仅在例句非空时出现 |
| `ui/screens/{StudyScreen,ReviewScreens,ProfileScreens,OnboardingScreen}.kt` | 接入新的朗读控件；设置页新增朗读速度/例句开关；单词详情新增「朗读释义」 |
| `data/settings/{UserSettings,SettingsRepository}.kt` | 删除 `PronunciationAccent`；新增 `speechRateMode`、`readExampleSentences`、`lastMotivationDayKey`、`celebratedStreak`、`celebratedMilestone` |
| `AndroidManifest.xml` | **删除** `INTERNET` 与 `ACCESS_NETWORK_STATE` 声明；`apkanalyzer` 复核：`INTERNET` 已不在任何产物中，`ACCESS_NETWORK_STATE` 仅由 `androidx.work` 合并进来（WorkManager 的网络约束需要它，应用代码从不读取网络状态） |
| 隐私文案（`DataTransferScreens`、`OnboardingScreen`） | 改回「不申请 INTERNET 权限；朗读使用系统语音服务」 |

### 1.2 学习进度（S3）

| 文件 | 变更 |
| --- | --- |
| `domain/study/NextStep.kt` | 新增：`NextStep`（Review / LearnNew / PracticeMistakes / AllDone / NoVocabulary）+ `NextStepPlanner`（优先级与时间估算） |
| `ui/components/ProgressCards.kt` | 新增：`TodayProgressCard`、`NextStepCard`、`WordStatusCard`、`StageGoalCard` |
| `ui/components/InsightCards.kt` | 新增：`InsightCard`、`MotivationLine`、`StatDefinition`（统计口径的唯一来源） |
| `ui/screens/HomeScreen.kt` | 重排为「进度 → 下一步 → 词汇状态 → 阶段目标 → 建议 → 激励 → 趋势 → 最近记录」，新增「统计口径」弹层 |
| `ui/ViewModels.kt` | `HomeUiState` 新增 `stats` / `nextStep` / `insights` / `motivation`；新增 `describeHome()` 与 `motivationFor()` |

### 1.3 学习分析（S4）

| 文件 | 变更 |
| --- | --- |
| `domain/insight/InsightModels.kt`、`InsightEngine.kt` | 新增：8 条规则、严重度排序、**数据不足时压制其它判断** |
| `domain/insight/SignalsCalculator.kt` | 新增：从已有的逐日趋势推导 7 天 / 前 7 天 / 前 14 天窗口、活跃天数与**活跃日中位数** |
| `data/repository/InsightRepository.kt` | 新增：把信号组装起来（窗口来自统计快照，额外三次查询补充遗忘、薄弱词、题型） |
| `data/local/dao/{ReviewEventDao,ProgressDao,ExamDao,QueryRows}.kt` | 新增 4 个只读查询：已掌握词的遗忘次数、首次作答次数、最弱词集合、题型正确率 |

### 1.4 激励（S5）

| 文件 | 变更 |
| --- | --- |
| `domain/motivation/**` | 新增：7 个场景 × 4 条原创语句 + 7 条带数字的变体，确定性选择，`isValid` 校验署名 |
| `domain/stats/{StatsModels,StatsCalculator}.kt` | `StageGoal` 增加 `nextMilestone` / `isReached`；阶段目标改为**里程碑制** |
| `ui/screens/StudyScreen.kt` | 完成页的收尾语改由 `MotivationEngine` 生成，并新增一句具体的下一步 |

---

## 2. 每类问题的根因

| # | 问题 | 根因 | 处理 |
| --- | --- | --- | --- |
| 1 | 口音选择是决策负担，且英式几乎无录音 | 上一轮把「两个口音」当作产品目标，但免费录音源的英式覆盖实测只有 1/14 | 按新需求删除口音选项，改用系统 TTS，覆盖率问题消失 |
| 2 | 系统默认语速/音调不被尊重 | Android **不会**把系统 TTS 偏好交给应用；上一版从不读取，直接用自己的默认值 | 读取 `Settings.Secure` 的 `tts_default_*`；**读不到就不调用** `setSpeechRate/setPitch`，保留引擎默认值（有单测守护） |
| 3 | 「暂停」状态无法实现 | Android `TextToSpeech` 只有 `stop()`，没有暂停/继续 | 按钮语义改为「播放 / 停止」，并把这个限制写进方案与代码注释，而不是做一个假暂停 |
| 4 | 例句无法朗读 | 播放按钮只接收单词字符串 | 新增 `SpeechActions`，单词/例句/释义各自可读；例句按钮仅在内容非空时渲染 |
| 5 | 首页数字齐全但「下一步做什么」缺失 | 数据层已算出计划与统计，但首页只展示事实，没有把事实转成一个动作 | 新增 `NextStepPlanner`（纯函数）+ `NextStepCard`，放在首屏第二张卡 |
| 6 | 阶段目标不可达、几乎不可能触发激励 | `stageGoal` 的 `target` 是**整个词库**（3,849 词），新用户看到「还差 3,529 词」 | 改为里程碑制（50/200/500/1000/2000），且**不会选择超出词库的里程碑** |
| 7 | 统计口径不透明 | 数字直接展示，没有任何说明 | `StatDefinition` 单一来源 + 首页「统计口径」弹层 + 单元测试断言关键陷阱（学习日 04:00、时长封顶、暂无数据≠0%） |
| 8 | 没有主动分析 | 统计页只呈现事实，不做判断，也没有建议层 | `domain/insight/` 8 条规则 + `InsightRepository` 取数；**数据不足时只输出「数据有限」** |
| 9 | 激励位置单一、与状态无关 | 只有完成页一段按正确率分档的固定文案 | 7 个场景的引擎 + 首页每天一条 + 完成页接入 + 破纪录/里程碑各自只庆祝一次 |
| 10 | 破纪录与里程碑会重复打扰 | 没有任何「已庆祝」的记录 | DataStore 记录 `celebratedStreak` / `celebratedMilestone` / `lastMotivationDayKey`，只在值变化时触发 |

---

## 3. 测试结果

全量：**370 个用例通过，0 失败 / 0 错误**（22 个测试类）。
本轮新增/重写的测试：

| 测试类 | 用例 | 覆盖 |
| --- | --- | --- |
| `SpeechControllerTest`（新） | 10 | 三种失败分支（引擎不可用/缺语音包/不支持）+ 3 秒超时 + 重试 + 连点取消 + 空白文本 + 关闭错误 |
| `NextStepPlannerTest`（新） | 9 | 到期优先、新词上限、错题、完成、空词库、负数防御、时间估算取整 |
| `SignalsCalculatorTest`（新） | 8 | 7 天 / 前 7 天窗口边界、活跃天数、活跃日中位数、未来数据、空历史返回 null |
| `StatDefinitionTest`（新） | 5 | 每条口径非空、标题唯一、关键陷阱文案、无 Markdown 残留 |
| `InsightEngineTest` | 42 | 8 条规则各自的触发/临界不触发、数据不足压制、排序、确定性 |
| `MotivationEngineTest` | 21 | 7 个场景各 ≥4 条、≤30 字、确定性、原创性、署名校验、无「0 天」 |
| `StatsCalculatorTest`（扩充） | 34 | 里程碑制（含超出词库的里程碑不得被选中） |
| `ComposeFlowTest`（扩充） | 14 | 首页四段式区块存在、下一步按钮可用、统计口径弹层可打开 |
| 删除 | −26 | 网络音频模块与口音选择的测试随功能一起删除 |

**本轮由测试发现并修复的真实缺陷**：

1. `weakestWordIds` 查询使用了 `user_word_progress.level`，而该表**没有 level 列**（等级属于 `word`）——
   编译期由 Room/KSP 拒绝，改为 `JOIN word`。这与上一轮 `ProgressDao` 踩过的是同一个坑。
2. 阶段目标改为里程碑后，`totalWords=10` 的导入词库会被要求达到 50 词（不可能达成）；
   已改为「里程碑必须 ≤ 词库规模」，并更新了相应的两个测试。
3. `SpeakButton` 使用了 `Icons.Filled.VolumeUp`，但本项目只依赖核心图标集，没有该图标；
   改用可用的 `Close` 并让无障碍描述说明「停止朗读」。
4. 首页「统计口径」按钮在 Robolectric 的极小默认窗口下**无法用触摸注入点击**（`performClick` 失败），
   改用 `performSemanticsAction(OnClick)` 验证**接线**是否成立 —— 这不是产品缺陷，但说明了该测试
   验证的是「按钮接上了弹层」，而不是「触摸落在按钮上」。

---

## 4. 尚未解决 / 需要确认

1. **系统 TTS 偏好的可读性未在真机验证**：`Settings.Secure` 的 `tts_default_rate/pitch` 在部分厂商 ROM 上
   可能返回 null。代码对此是安全的（读不到就不覆盖），但「语速跟随系统」这一点**需要真机确认**。
   设置页会如实显示「语速来自系统设置」或「使用引擎默认语速」。
2. **没有真正的暂停**：受 Android `TextToSpeech` 能力限制，当前是「播放 / 停止」。
   要支持暂停/继续必须改为 `synthesizeToFile()` + `MediaPlayer`，属于体验取舍，待确认是否需要。
3. **仍未在模拟器/真机跑过**：本机内存不足以启动模拟器。API 26 的行为仍由 Robolectric 的
   `@Config(sdk = [26])` 覆盖；建议在内存正常的机器上走查：首页四段式布局、朗读四态、
   设置页「试听」、以及首次启动的播种进度。
4. **R3（题型薄弱）与 R4（遗忘回潮）依赖新增查询**：它们的信号已在 `InsightRepository` 中接通，
   但**没有被真实的长期数据验证过阈值是否合适**（阈值都是公开常量，可调）。
   规则在数据不足时不会触发，因此不会误导用户。
5. **分析规则的阈值是产品参数**：8 条规则的阈值集中在 `InsightEngine` 的公开常量里，
   建议上线后用真实数据校准（例如 R2 的 8 个百分点、R5 的 3 天）。
6. **激励文案为纯原创**：没有引入任何外部名言，因此没有版权与署名问题；
   如果将来要引入公共领域引用，`MotivationSource.PUBLIC_DOMAIN` + `isValid` 的署名校验已经就位。
7. **`docs/PRONUNCIATION_VERIFICATION.md` 描述的是已删除的模块**：保留作为历史证据（它记录了
   「免费真人录音的英式覆盖只有 1/14」这一事实，正是改用系统 TTS 的依据之一），文首已加注说明。
8. **产品指标仍是本地度量**：`学习完成率 / 复习完成率 / 连续天数 / 朗读成功率` 在设备本机计算并展示，
   没有接入任何统计 SDK；次日留存等指标若要真实采集，需要先决定是否接受新增上报与相应隐私政策。
