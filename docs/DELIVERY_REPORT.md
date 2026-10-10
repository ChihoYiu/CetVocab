# 交付报告 · 薄荷单词 (CetVocab)

> **迭代更新（2026-10，共两轮）**：本报告描述首次交付时的状态，随后两轮迭代就地更新了相关内容。
> **第 2 轮**（[ITERATION_2_REPORT.md](ITERATION_2_REPORT.md)）：动画性能、内置词库完整性、状态栏反色。
> **第 3 轮**（[ITERATION_3_REPORT.md](ITERATION_3_REPORT.md)）：按新需求把发音改为**系统 TTS**（删除美式/英式
> 选项与网络音频模块）、首页改为「进度 → 下一步 → 词汇状态 → 阶段目标」四段式、新增 8 条学习分析规则与
> 7 个场景的动态激励，并**从清单移除了 `INTERNET` / `ACCESS_NETWORK_STATE` 声明**
> （`apkanalyzer` 复核：`INTERNET` 已不在产物中；`ACCESS_NETWORK_STATE` 仅由 `androidx.work` 合并进来）。

CET-4 / CET-6 单词记忆 Android 应用。单 Activity + Jetpack Compose + Material 3，`minSdk 26`（Android 8.0）。
全部功能离线可用，**不申请 `INTERNET` 权限**：朗读由设备的系统文字转语音（TTS）服务完成，
语言、声音、语速与音调跟随系统设置。

---

## 1. 已完成的功能

| # | 功能 | 实现要点 |
| --- | --- | --- |
| 1 | 首次启动流程 | 欢迎页 → CET-4/CET-6 → 每日新词与复习上限 → 学习提醒（含 Android 13+ 通知权限） → 发音偏好；「稍后设置 / 使用默认设置」保留默认值并完成引导 |
| 2 | 首页仪表盘 | 问候语 + 学习日日期、今日计划进度环（已完成 / 计划）、开始背单词、开始巩固错题、四个紧凑指标（今日已学、待复习、错题待巩固、今日正确率）、12 周趋势中的本周柱状图、最近 6 条学习记录、未完成会话恢复卡片、无词库时的空状态 |
| 3 | 单词记忆 | 默认隐藏释义（带过渡动画）、点击卡片显示、**英式 / 美式真实录音发音（可切换）**、词性、多条释义、例句 + 翻译、常见搭配、收藏、`12 / 50` 进度、不认识 / 模糊 / 已掌握、答题即写库、退出与进程重启后恢复、会话总结（正确率、用时、错词、再练错词） |
| 4 | 间隔重复 | 纯函数调度器（见第 6 节），到期词优先 → 未巩固词 → 新词；错题与长期未巩固词排在同批到期词之前；即时巩固队列有次数上限与「已在队列中不重复插入」规则 |
| 5 | 错题本 / 复习中心 | 6 个分类（今日待复习、未巩固词、高错误次数、最近错题、历史错题、收藏单词）、搜索、最少错误次数筛选、只看收藏、两种排序、收藏切换、单击进入详情、批量重新练习 / 标记已巩固 / 删除（二次确认）、空 / 加载 / 错误状态 |
| 6 | 闭卷考试 | 独立专注模式（无底部导航）：不显示释义 / 例句 / 搭配 / 正确选项；题号、总题数、已作答数、剩余时间；提前交卷；退出可选「保存草稿」或「放弃考试」；重复提交防护；开考即写入题目快照；支持恢复草稿与剩余时间；四种题型（英译中、中译英、四选一、拼写填空）；超时自动交卷 |
| 7 | 成绩页 | 总分（百分制）、正确率、答对 / 答错、用时、各题型正确率、薄弱题型建议、错题明细、加入错题本结果、再次练习错词、再考一次、返回首页 |
| 8 | 学习统计 | 累计学习单词、累计复习次数、连续学习天数与最长记录、学习时长（今日 / 累计）、正确率、复习完成率、考试平均分、词库进度（已学习 / 已掌握）、阶段目标、本周趋势、近 30 天趋势、12 周学习日历；无数据时展示鼓励型空状态（不显示误导性的 0 分） |
| 9 | 个人中心 / 设置 | 词库级别切换、词库列表与最近导入记录、每日新词、每日复习上限、提醒开关与时间（预设 + ±30 分钟）、发音偏好、卡片自动发音、浅色 / 深色 / 跟随系统、动态颜色（Android 12+ 才显示开关）、动画开关、恢复默认设置、清空学习进度、重置全部数据（均二次确认）、隐私说明、词库来源与许可、关于与版本号 |
| 10 | 词库导入 / 导出 | 系统文件选择器（SAF）；JSON 与 CSV；8 MiB / 20000 条上限；字段校验与问题行清单；导入预览（有效、问题行、文件内重复、级别、许可）；确认后单事务写入，失败回滚并记录；同名单词保留原有行 ID（学习进度不丢失）；导出词库（CSV）、学习记录（JSON）、完整备份（JSON，含版本号 / 导出时间 / 格式说明）；恢复备份需二次确认 |

## 2. 主要文件和模块

```
app/src/main/java/com/cetvocab/app/
├── MainActivity.kt                    单 Activity、edge-to-edge、只做 setContent
├── CetVocabApplication.kt             创建 AppContainer
├── AppContainer.kt                    依赖图（含测试用内存数据库注入点）
├── core/
│   ├── time/TimeProvider.kt           时钟抽象 + StudyDay（04:00 学习日边界、isDue 时钟异常处理）
│   ├── result/Outcome.kt              Outcome / AppError（用户可读失败信息）
│   └── json/JsonCodec.kt              JSON 编解码（列表列 + 文件解析）
├── domain/
│   ├── srs/SpacedRepetition.kt        间隔重复规则（纯函数）
│   ├── srs/WordProgress.kt            WordProgress / MasteryStatus / ReviewGrade
│   ├── study/StudySessionEngine.kt    队列构建 + 会话状态机（即时重复规则）
│   ├── study/StudyModels.kt           StudyCard / TodayPlan / SessionSummary / RecentEvent
│   ├── exam/ExamGenerator.kt          出题（可复现：种子随机）
│   ├── exam/ExamScoring.kt            评分（选择题 / 拼写题归一化）
│   ├── exam/ExamModels.kt             题型、题目快照、ExamScore / TypeAccuracy
│   ├── importer/WordPackParser.kt     JSON / CSV 解析与字段校验
│   ├── importer/CsvCodec.kt           手写 RFC 4180 CSV 读写
│   ├── importer/ImportModels.kt       ImportPreview / ImportIssue / ImportResult / 合并规则
│   ├── mistake/MistakeModels.kt       错题分类、筛选、列表模型
│   ├── stats/StatsCalculator.kt       连续天数、正确率、复习完成率、日历序列、阶段目标
│   └── model/Word.kt · CetLevel.kt    共享领域模型
├── data/
│   ├── local/CetVocabDatabase.kt      10 张表 + 1 个视图，version 1，导出 schema
│   ├── local/Migrations.kt            迁移策略与迁移数组
│   ├── local/dao/                     8 个 DAO（全部使用绑定参数）
│   ├── local/entity/                  10 个 Entity
│   ├── settings/SettingsRepository.kt Preferences DataStore（14 项设置，含范围钳制）
│   ├── repository/WordRepository.kt   内置词库播种、导入事务、词库删除
│   ├── repository/StudyRepository.kt  今日计划、队列、会话持久化与恢复
│   ├── repository/ProgressRecorder.kt 单事务写入：进度 + 日志 + 错题本 + 每日汇总
│   ├── repository/MistakeRepository.kt 错题本查询与批量操作、LIKE 转义
│   ├── repository/ExamRepository.kt   出卷 / 草稿 / 交卷 / 历史
│   ├── repository/StatsRepository.kt  SQL 聚合 + 纯函数统计
│   ├── repository/BackupRepository.kt 导出 / 恢复 / 清空
│   ├── io/DocumentIo.kt               SAF 文本读写（大小限制、错误包装）
│   ├── seed/BuiltInWordPacks.kt       内置演示词库（四级 84 + 六级 104 条自建数据）
│   └── mapper/Mappers.kt              Room 行 ↔ 领域模型
├── ui/
│   ├── ViewModels.kt                  10 个 ViewModel + 工厂（不可变 StateFlow，全部 @Immutable）
│   ├── OptIns.kt                      实验性 API 使用政策
│   ├── theme/                         Color / Type / Shape / Theme / SystemBars（薄荷绿 + 暖白 + 深色结构 + 状态栏反色）
│   ├── components/                    Buttons、States、Dialogs、Inputs、Cards、WordCard（16 类组件）
│   ├── navigation/                    Routes、AppBottomNavigation、CetVocabNavHost（显式过渡动画）
│   ├── screens/                       Onboarding、Home、Study、Review、Exam、Stats、Profile、DataTransfer、WordBrowser
│   └── speech/                        发音控制器（真实录音 + 离线合成音兜底 + 加载/错误状态）
├── data/pronunciation/                发音模块：录音源（Wikimedia/Wiktionary）、磁盘缓存、错误模型
└── work/StudyReminderWorker.kt        WorkManager 提醒 + 通知渠道 + 权限判断
```

## 3. 数据库表与迁移

`cet_vocab.db`（version 1，`exportSchema = true`，schema 快照位于 `app/schemas/`）：

| 表 | 主键 | 关键索引 | 说明 |
| --- | --- | --- | --- |
| `word_pack` | `id` (TEXT) | `level`, `is_built_in` | 词库来源、许可、版本、词数 |
| `word` | `id` (自增) | `(level, normalized_word)` 唯一、`pack_id`, `word`, `level` | 词条内容；`normalized_word` 与 `level` 一起构成去重身份 |
| `user_word_progress` | `word_id`（外键 → word，级联） | `due_at`, `status`, `wrong_count`, `last_reviewed_at`, `is_favorite` | 掌握状态、等级、重复次数、间隔、难度系数、到期时间、各类计数 |
| `review_event` | `id` (自增) | `word_id`, `answered_at`, `day_key`, `grade`, `source` | 每次作答的不可变日志（含学习日桶，避免 SQL 时区运算） |
| `wrong_answer` | `word_id`（外键 → word） | `last_wrong_at`, `wrong_count`, `resolved_at`, `is_favorite` | 错题本；`resolved_at` 标记已巩固但保留历史 |
| `study_session` | `id` (自增) | `status`, `level`, `updated_at` | 会话队列 JSON、游标、即时重复次数、各项计数 |
| `exam_session` | `id` (自增) | `status`, `level`, `created_at` | 草稿 / 已交卷、剩余秒数、分数、各题型正确率 JSON、随机种子 |
| `exam_question` | `id` (自增) | `(exam_id, order_index)` 唯一、`word_id`, `is_correct` | 题目快照：题干、选项 JSON、正确选项下标、标准答案、作答 |
| `daily_study_summary` | `(day_key, level)` 复合主键 | `day_key`, `level` | 每日 / 每级别汇总，供首页与统计使用 |
| `import_history` | `id` (自增) | `imported_at`, `status` | 导入 / 导出审计（成功或已回滚） |
| 视图 `word_with_progress` | — | — | `word LEFT JOIN user_word_progress`，所有队列与列表查询都基于它 |

时间一律以 epoch 毫秒存储；「学习日」按本地时区 04:00 切分（`StudyDay`），跨时区、夏令时与系统时钟异常均在
一处集中处理（`StudyDay.isDue` 将超过 400 天的将来时间视为到期）。

**迁移策略**：绝不使用 destructive migration。`Migrations.ALL_MIGRATIONS` 交给 `Room.databaseBuilder`；
版本升级时新增手写 `Migration` 并保留 `app/schemas/` 快照，迁移测试用该快照在真实数据库文件上验证升级路径
（`Migrations.CURRENT_VERSION` 与 `@Database(version = …)` 由测试断言保持一致）。
另外，为兼容 Android 8.0 自带的 SQLite 3.18，全部查询避免使用 UPSERT 语法、窗口函数与 `RETURNING`
（每日汇总采用事务内「读-改-写」）。

**词库与学习进度分离**：`word` / `word_pack` 只存内容，`user_word_progress` 等表存用户数据；
重新导入同一词库只更新词条内容并保留行 ID，因此掌握状态、错题与考试快照都不会丢失。

## 4. 间隔重复规则（可在应用内「关于」页看到同样的说明）

这是**透明的 SM-2 风格启发式规则**，不是经过临床验证的记忆模型，也不承诺提分或押题。三条规则的实现位于
`domain/srs/SpacedRepetition.kt`，全部是纯函数并有单元测试覆盖：

| 结果 | 状态变化 | 间隔与难度 | 再次出现 |
| --- | --- | --- | --- |
| 不认识 | 掌握等级 −1；连续答对归零；`wrongCount + 1`；若此前已毕业则 `lapseCount + 1` 并置为「待巩固」 | `ease − 0.20`（下限 1.3），`intervalDays = 0`，`repetitions` 归零 | 10 分钟后（本会话即时巩固队列），单次会话最多重复 3 次，且队列中已有该词时不重复插入 |
| 模糊 | 保留进度，`fuzzyCount + 1`，连续答对归零 | 间隔缩短为原间隔的 60%（毕业后不低于 1 天），`ease − 0.05`（下限 1.3） | 按缩短后的间隔 |
| 已掌握 | `repetitions + 1`，连续答对 +1，掌握等级 +1（上限 5） | 固定步长 1 / 3 / 7 / 14 天，其后 `interval × ease`；`ease + 0.15`（首次）或 `+ 0.05`（已 ≥ 2.2），上限 2.8；间隔上限 180 天 | 达到掌握等级 5 且间隔 ≥ 21 天时状态变为「已掌握」，逐步退出高频复习队列 |

**队列优先级**（`domain/study/StudySessionEngine.kt`，`StudyQueueBuilder`）：
1. 到期词（其中 `wrongCount > 0` 的排在前，其余按到期时间升序）；
2. 未到期但仍脆弱的词（「待巩固」或错误 ≥ 2 次的「学习中」词），按错误次数降序、最近复习时间升序；
3. 新词，按词库顺序，数量不超过每日新词目标。
复习词总量受「每日复习上限」约束，且到期词与未巩固词永远不会被新词挤掉。

**边界处理**：跨天按 04:00 学习日切分；时区变化在读取时重新推导日期桶，因此不会重复记账；应用重启后未完成会话
由 `study_session` 行恢复（含队列、游标与即时重复计数）；系统时间被往回调时，`apply` 会把「当前时间」夹到上次
复习时间之后，避免出现负间隔。

## 5. 测试

* **单元测试**（JVM，无 Android 依赖）：间隔重复三种结果与边界、到期排序与队列构建、即时重复上限、
  错题计数与掌握状态流转、考试出题不变量与评分（选择 / 拼写 / 未作答）、每日统计计算、日期切换与时区边界、
  JSON / CSV 校验与重复词合并、导入失败回滚（事务）、连续天数与百分比空值语义。
* **Room 测试**（Robolectric，`@Config(sdk = [26])`）：词库插入与查询、唯一索引、进度读写与 Flow 变化、
  错题记录更新、外键级联、视图对未学习词返回空进度、每日汇总复合主键、`ProgressRecorder` 的事务写入。
* **Compose UI 测试**（Robolectric）：首页进入学习、显示 / 隐藏释义、标记三种结果、错题本筛选与重新练习、
  开始闭卷考试与提交结果、导入预览与确认、设置修改后重新读取仍然生效、空 / 加载 / 错误状态、页面导航与返回。
  测试只使用稳定的语义节点与 testTag，不依赖坐标点击。

> **测试结果：370 个用例全部通过（0 失败 / 0 错误 / 0 跳过）**，覆盖纯 JVM 单元测试、Robolectric
> 数据库与事务测试、Compose UI 测试与状态栏测试；数据库、事务、UI 与状态栏测试均以 `@Config(sdk = [26])` 运行。
> 迭代后新增的 55 个回归测试见 [ITERATION_2_REPORT.md](ITERATION_2_REPORT.md) 第 5 节。

## 6. Android 8.0 兼容性

* `minSdk = 26`；`compileSdk = 36`、`targetSdk = 36`（API 37 尚未进入稳定 SDK 渠道）。
* 使用的 API 全部在 API 26 可用：`java.time`（26 起内置，无需 desugaring）、`NotificationChannel`（26 起必需，
  应用在启动与开启提醒时都会创建）、`ValueAnimator.areAnimatorsEnabled()`（26 起，用于尊重「移除动画」设置）。
* 通知权限：`POST_NOTIFICATIONS` 仅在 Android 13+ 请求，Android 8–12 行为不变；未授权时 Worker 正常结束、不发送通知。
* 文件导入 / 导出使用 SAF（`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`），不申请宽泛存储权限，Android 8 亦可用。
* 状态栏 / 导航栏 / 安全区域：`enableEdgeToEdge()` + `Scaffold` + `WindowInsets.safeDrawing`，未使用固定内边距；
  横竖屏、字体缩放、深色模式、高对比度下布局均使用 `heightIn(min = …)` 与可换行文本，不裁剪内容。
* 状态栏图标明暗由**应用内主题**决定（`ApplySystemBarAppearance`，`WindowInsetsControllerCompat`，
  API 26 起同一条代码路径），而不是系统夜间模式；浅色主题用深色图标、深色主题用浅色图标，
  运行时切换主题与重启后都正确。回归测试见 `SystemBarsTest`。
* 动态颜色仅在 Android 12+ 提供开关；默认始终使用薄荷绿品牌色，因此 Android 8 与新版本观感一致。
* 预测性返回与系统返回：使用 `NavHost` 默认行为，学习页与考试页用 `BackHandler` 接管为「暂停 / 保存草稿」。
* 数据库兼容 SQLite 3.18（见第 3 节）。
* 自动化层面：`DatabaseTest`、`ProgressRecorderTest`、`ComposeFlowTest` 均以 `@Config(sdk = [26])` 运行。

## 7. 已知限制

1. **内置词库**：已替换为 **ECDICT（MIT）** 的完整 CET-4（3,849 词）与 CET-6（5,407 词）词表，
   双向差集为 0，即相对该来源是完整的。该来源没有例句、例句翻译、常见搭配与双口音音标，
   这些字段按「不编造数据」原则留空（界面会隐藏空区块）。如果你需要例句，可自行导入带这些字段的词库。
   词库标准、许可证与校验细节见 [WORD_PACK_VALIDATION.md](WORD_PACK_VALIDATION.md)。
2. **无云端同步 / 账号系统**：按需求未接入账号、云服务、广告或统计 SDK；数据仅存本机，只能通过导出 / 恢复迁移。
3. **朗读依赖系统 TTS（迭代 3 起）**：朗读使用设备自带的文字转语音服务，语言/声音/语速/音调跟随系统设置。
   设备未安装引擎或缺少英语语音包时会给出提示并提供系统设置入口（不崩溃、不静默失败）。
   受 Android `TextToSpeech` 能力限制，按钮是「播放 / 停止」，**没有暂停/继续**（实现真暂停需要
   `synthesizeToFile` + `MediaPlayer`，属于未做的取舍）。`audioUri` 字段目前只记录不播放。
4. **每日统计的近似**：`studiedWords` 按「当天首次作答的不同单词数」计；跨设备导入备份后若有重叠，恢复正常但
   会替换该词库的学习数据（UI 已明确提示）。
5. **今日计划的时间点**：首页计划中的「到期数量」在订阅时计算，应用长时间停留首页时不会自动跳秒刷新
  （返回首页或数据变化时会刷新）。
6. **未实现**：真实网络同步、账号体系、更多题型（如听力、完形）、词库在线市场、学习提醒的精确时间选择器
  （现为预设时间 + ±30 分钟微调）。

## 8. 词库版权与数据来源

* 内置词库：**[skywind3000/ECDICT](https://github.com/skywind3000/ECDICT)**，文件 `ecdict.csv`，
  固定到提交 `bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b`，许可 **MIT**（Copyright (c) 2025 Linwei）。
  四级/六级归属取自该来源自带的 `tag` 列（`cet4` / `cet6`），本项目未推断任何单词的等级。
  每个词条都保留 `source` 与 `license` 字段并在单词详情中显示。
* 回退用的极小演示词库（资产缺失时的兜底）：**本项目自建**，许可 CC0-1.0，位于
  `app/src/main/java/com/cetvocab/app/data/seed/BuiltInWordPacks.kt`。
* 发音音频：Wikimedia Commons 与 Wiktionary 的录音，通常为 CC BY-SA 或 CC0；
  应用在播放控件旁与设置页显示来源与许可证，具体署名见对应文件页。
* 被否决的词库候选（`qwerty-learner` = GPL-3.0；`mahavivo/english-wordlists` 无许可证；
  `KyleBing/english-vocabulary` 权利链不可追溯）记录在词库校验报告中，未采用其数据。
* 应用不包含任何商业词典内容、官方考试大纲文本或第三方音频。
* 用户导入的词库、例句与音频，其版权与许可由用户自行负责；每条记录都会保存并在界面显示 `source` 与 `license`。
* 应用与任何考试机构（含 CET 主办方）不存在合作关系，也不声称存在；界面与文档均不做提分、押题或通过承诺。
* 开源组件：Jetpack Compose、Material 3、Room、DataStore、Navigation Compose、WorkManager、
  Kotlinx Coroutines / Serialization，均为 Apache-2.0。
* 示例导入文件：`docs/samples/word-pack-sample.json`、`docs/samples/word-pack-sample.csv`（同样为自建数据）。

## 9. 如何运行

见 [README.md](../README.md)。最简路径：

```bash
# local.properties 中配置 sdk.dir 指向 Android SDK（API 36 平台 + Build-Tools 36）
./gradlew :app:assembleDebug        # 构建调试 APK
./gradlew :app:installDebug         # 安装到设备 / 模拟器
./gradlew :app:testDebugUnitTest    # 运行全部测试（单元 + Robolectric 数据库 + Compose UI）
```

## 10. 后续可选优化项

1. 接入 MediaPlayer / ExoPlayer 播放 `audioUri` 本地或用户自有音频（仍不需要网络权限）。
2. 分词库学习计划（按考试日期倒排每日新词量），并给出「考前可完成度」提示（不做通过承诺）。
3. 增加「同义词 / 词根 / 形近词」复习视图，复用现有 `word` 表与错题队列。
4. 用 FTS4/FTS5（Android 8 支持 FTS4）替换当前 `LIKE` 搜索，词库规模变大后可显著提速。
5. 每日汇总改为增量 SQL（`UPDATE … SET x = x + 1`）以减少读-改-写，配合迁移到 v2 并补充迁移测试。
6. 会话内答题计时曲线与「反应时间」统计，用于更细致地判断「模糊」。
7. 导出增加 Anki / CSV 兼容模板，方便与其他学习工具互通。
8. 引入 Baseline Profile 与 Macrobenchmark，优化冷启动与列表滚动。
9. 数据库迁移 v2 示例：为 `user_word_progress` 增加「用户笔记」列，同时补充迁移测试与导出字段。

---

## 附录 A · 测试运行记录

最终一次完整运行（`:app:testDebugUnitTest`，本机实测）：

```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
370 tests completed, 0 failed, 0 errors, 0 skipped
```

| 测试套件 | 用例数 | 失败 | 类型 |
| --- | --- | --- | --- |
| `SpacedRepetitionTest` | 26 | 0 | 纯 JVM |
| `StudySessionEngineTest` | 22 | 0 | 纯 JVM |
| `ExamGeneratorTest` | 19 | 0 | 纯 JVM |
| `ExamScoringTest` | 19 | 0 | 纯 JVM |
| `WordPackParserTest` | 34 | 0 | 纯 JVM |
| `CsvCodecTest` | 16 | 0 | 纯 JVM |
| `StatsCalculatorTest` | 27 | 0 | 纯 JVM |
| `StudyDayTest` | 21 | 0 | 纯 JVM |
| `DatabaseTest` | 22 | 0 | Robolectric, `@Config(sdk = [26])` |
| `ProgressRecorderTest` | 20 | 0 | Robolectric, `@Config(sdk = [26])` |
| `ComposeFlowTest` | 11 | 0 | Robolectric + Compose UI, `@Config(sdk = [26])` |
| `BuiltInPackAssetTest`（迭代新增） | 7 | 0 | Robolectric, 校验内置词库完整性 |
| `BuiltInPackSeedTest`（迭代新增） | 7 | 0 | Robolectric, 播种与升级不丢进度 |
| `PronunciationRepositoryTest`（迭代新增） | 12 | 0 | 纯 JVM, 缓存/回退/错误映射 |
| `PronunciationSourceTest`（迭代新增） | 8 | 0 | 纯 JVM, 双口音 URL |
| `SystemBarsTest`（迭代新增） | 5 | 0 | Robolectric, `@Config(sdk = [26])` |
| `UiStateStabilityTest`（迭代新增） | 5 | 0 | Robolectric, 重组跳过与时间格式 |
| `WordBrowserScreenTest`（迭代新增） | 5 | 0 | Robolectric + Compose UI |
| `AudioFormatTest`（迭代新增） | 4 | 0 | 纯 JVM, 拒绝 HTML/非音频内容 |
| `AccentSelectionTest`（迭代新增） | 2 | 0 | Robolectric, 口音切换生效并持久化 |
| **合计** | **292** | **0** | |

覆盖的关键规则（摘要）：三种作答结果的间隔与难度变化、间隔上下限、时钟回拨；队列优先级与即时重复上限、
「已在队列中不重复插入」；出题的种子可复现性与选项不变量、选择题与拼写题判分、未作答计为错误；
连续学习天数（含「昨天仍算连续」与断档）、百分比空值语义、日历补零；04:00 学习日边界
（Asia/Shanghai 与跨夏令时的 America/New_York）；JSON / CSV 的重复合并、坏行跳过、超长字段、
空白行与错列行的容错；Room 的唯一索引、外键级联、视图对未学习词返回空进度、每日汇总复合主键、
Flow 变化；事务写入的「每日首次作答计数」与错题自动标记已巩固；Compose 流程的首页 → 学习 →
标记三种结果、错题本空状态与搜索、引导流程写入设置、设置项持久化、清空学习进度前的二次确认。

测试过程中修正的真实缺陷（均已修复并有测试守护）：

1. `SpacedRepetition.nextIntervalDays(0, …)` 曾把「从未答对」当成第 5 次以上处理，导致首个间隔为 35 天；
   现在 0 或 1 次成功都落在最初的 1 天步长。
2. `OnboardingViewModel.finish()` 未保护 `WorkManager.getInstance()`；在 WorkManager 未初始化的环境下
   会抛异常并让引导页永远停在「正在保存…」。现已改为尽力而为（与 `AppContainer.prepare()` 同一策略）。
3. `ProgressRecorder.bumpDailySummary` 曾用「等级 + 作答结果」推断是否新词，导致同一个词在同一天
   既计入新词又计入复习词；现在以「此前是否存在进度行」为唯一判据。

### 构建与产物验证

| 命令 | 结果 |
| --- | --- |
| `:app:assembleDebug` | ✅ 成功（迭代后重新构建，产物 `app-debug.apk` 14.61 MB，含 3.06 MiB 内置词库资产） |
| `:app:assembleRelease -x lintVitalRelease` | ✅ 成功（首次交付时验证，产物 `app-release-unsigned.apk`，`minifyReleaseWithR8` 已执行） |
| `apkanalyzer manifest min-sdk`（调试包与发布包） | ✅ 均为 **26** |
| `apkanalyzer manifest target-sdk` | ✅ 均为 36 |
| `apkanalyzer manifest permissions` | ✅ **不再包含 `INTERNET`**（迭代 3 已从清单移除，调试包与发布包均已复核）；当前为 `POST_NOTIFICATIONS` + `ACCESS_NETWORK_STATE`/`WAKE_LOCK`/`FOREGROUND_SERVICE`/`RECEIVE_BOOT_COMPLETED`（全部由 `androidx.work` 声明） |
| `apkanalyzer` 校验内置资产 | ✅ `assets/wordpacks/{cet4.json,cet6.json,manifest.json,PACK_INFO.json}` 已打进 APK |
| `:app:testDebugUnitTest` | ✅ 370 / 370 通过 |
| Room schema 导出 | ✅ `app/schemas/com.cetvocab.app.data.local.CetVocabDatabase/1.json` |

### 未能在本机完成的验证（诚实说明）

* **真机 / 模拟器（API 26）实机运行**：本机可用物理内存与提交内存长期处于临界状态（其他应用占用约 30 GB / 31.2 GB 提交上限，
  可用提交空间常在 1 GB 以下），Android 模拟器无法启动。因此 API 26 的验证是通过 Robolectric
  `@Config(sdk = [26])` 在真实 `android-all` API 26 镜像上运行数据库测试、事务测试与 Compose UI 测试完成的，
  另外静态核对了全部 API 使用（见第 6 节）。在有正常内存余量的机器上执行
  `./gradlew :app:installDebug` + 手工走查即可补齐实机验证。
* **`lintVitalRelease`**：AGP 的 lint 分析需要远超本机可用提交内存的元空间，运行时报
  `StandaloneAnalysisAPISessionBuilder` 内部错误（元空间耗尽），因此发布构建时以 `-x lintVitalRelease` 跳过；
  该任务是质量检查而非构建必需，在有内存余量的机器上直接运行 `./gradlew :app:lintVitalRelease` 即可。

### 构建环境的机器特定说明（不影响项目本身）

本机（Windows）为了在紧张的内存下完成构建，使用了以下一次性参数：`--no-daemon`、
`--max-workers=1`、`-Dkotlin.compiler.execution.strategy=in-process`、
`-Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC"`，并在每次构建前结束遗留的 Java 进程。
`gradle.properties` 中保留的是通用的适度配置（2 GB 堆、关闭并行）；在内存正常的机器上无需任何额外参数。

## 附录 B · 工具链与验证环境

| 项目 | 版本 / 说明 |
| --- | --- |
| Android Gradle Plugin | 9.4.1（AGP 9 起 Kotlin 支持内置，因此本项目**不**应用 `org.jetbrains.kotlin.android`） |
| Kotlin | 2.4.21（Compose 编译器插件与 serialization 插件同版本） |
| KSP | 2.3.12（Room 注解处理） |
| Gradle | 9.8.1（项目自带 Wrapper） |
| JDK | 17 字节码目标；构建时使用本机 JDK 25 运行 Gradle |
| compileSdk / targetSdk / minSdk | 36 / 36 / **26** |
| Compose BOM | 2026.06.01（Compose 1.11.4、Material 3 1.4.0） |
| Room / DataStore / WorkManager / Navigation | 2.8.5 / 1.2.1 / 2.12.0 / 2.9.8 |
| 版本管理 | 全部版本集中在 `gradle/libs.versions.toml`（Version Catalog） |
| 构建验证 | `:app:assembleDebug`、`:app:assembleRelease`（R8 压缩）、`:app:testDebugUnitTest` |

> 版本选择依据：编译目标为当前稳定的 API 36；AndroidX 组件固定为 AAR 元数据中 `minCompileSdk <= 36`
> 的最新版本（Compose 1.12.x、Navigation 2.10.x、Lifecycle 2.11.x、Core 1.19.x 需要 compileSdk 37，
> 而 API 37 尚未进入稳定 SDK 渠道，因此未采用）。Room schema 快照与迁移策略见第 3 节。
