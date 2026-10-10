# 迭代报告 · 稳定性 / 词库完整性 / 发音质量 / 主题适配

本轮迭代只做四件事：修掉动画相关的性能与观感缺陷、把内置词库补成完整的四级与六级词表、把发音从
「系统 TTS 单一口音」换成「真实录音的英式/美式双口音 + 缓存 + 失败处理」、修复浅色主题下状态栏
图标不反色的问题。所有改动都建立在既有架构上（Room + Repository + ViewModel + Compose），
没有引入新的架构层，也没有做无关重构。

测试结果：**292 个用例全部通过（0 失败 / 0 错误）**，其中新增 55 个回归测试。详见第 5 节。

---

## 1. 动画掉帧问题

### 1.1 根因（逐条对应代码里实际存在的问题）

| # | 根因 | 位置 | 为什么会导致卡顿/不流畅 |
| --- | --- | --- | --- |
| A1 | 进度环动画做在了**颜色**上，而颜色从不变化；真正需要动画的**弧度**是瞬间跳变的 | `ui/components/Cards.kt` `ProgressRing` | 每一帧都在跑一个改变不了任何像素的动画（无效工作），而用户看到的进度仍然是硬跳变 |
| A2 | 进度环的圆弧绘制起点固定为 `Offset(inset, …)`，没有水平居中 | 同上 | 圆环贴着卡片左边绘制，视觉上是错位而不是居中，属于必须修的渲染缺陷 |
| A3 | 首页仪表盘用 `Column(verticalScroll)`，六个区块 + 图表一次性全部组合和测量 | `ui/screens/HomeScreen.kt` | 首帧要测量全部内容；滚动时没有回收，任何状态变化都要重新测量全部子树 |
| A4 | 列表每行每次重组都新建 `SimpleDateFormat`（还有 `Date`） | `ui/screens/ReviewScreens.kt` `formatTimestamp` | 滚动和每次重组都在分配对象 → GC 抖动 → 掉帧 |
| A5 | 趋势图 7 根柱子各自新建 `DateTimeFormatter` | `ui/screens/HomeScreen.kt` `WeeklyTrendChart` | 每次重组 7 次格式化器构造 |
| A6 | 问候语与日期在**属性 getter** 里调用 `LocalDate.now()` 并构造格式化器 | `ui/ViewModels.kt` `HomeUiState` | 每次重组（含滚动帧）都取系统时间 + 构造对象，且值可能在两帧之间改变 |
| A7 | 没有任何 UI 状态类标注 `@Immutable` | `ui/ViewModels.kt` | `List`/`Map`/`Set` 字段被编译器推断为**不稳定**，Compose 无法跳过 → Room/DataStore 每次发射都重组整屏（含 40sp 单词文本的排版） |
| A8 | NavHost 使用 Navigation 默认的 **700 ms** 交叉淡入淡出，且完全不响应应用内「动画效果」开关 | `ui/navigation/CetVocabNavHost.kt` | 700 ms 内两个整屏同时组合、测量、绘制，是全应用最贵的一帧序列；开关关掉后导航仍在动画 |
| A9 | 学习卡释义是瞬时替换，没有过渡 | `ui/components/WordCard.kt` | 观感生硬（不是掉帧，但属于同一类「不流畅」） |
| A10 | 列表增删没有条目动画 | `ui/screens/ReviewScreens.kt` | 筛选、批量删除、标记已巩固时条目瞬间跳位 |

### 1.2 修改

* **A1+A2**：`ProgressRing` 改为对**弧度**做 `animateFloatAsState`（520 ms，`FastOutSlowInEasing`），
  并且**在 `Canvas` 的绘制 lambda 内读取动画值** —— 动画帧只失效绘制阶段，不重组、不重新测量、不重排
  文字；圆环固定为 156dp 正方形并按宽高居中；删除了无意义的颜色动画。
  *这不是「降低动画质量」：环从「没有动画」变成「有真实平滑动画」。*
* **A3**：首页改为 `LazyColumn`，六个区块各有 `key`，屏幕外不再组合与测量。
* **A4+A5**：时间格式化全部改为**单例 `DateTimeFormatter`**（`java.time`，API 26 可用），逐行调用不再分配。
* **A6**：问候语与日期改为**每次状态发射时在 ViewModel 计算一次**（`greetingFor(hour)`、`HomeDateFormatter`），
  getter 里不再取系统时间，并补了单元测试固定其行为。
* **A7**：给 9 个 UI 状态类加 `@Immutable`，并新增可测量的回归测试：**发射一个相等状态不得触发整屏重组**。
* **A8**：NavHost 显式声明过渡（淡入淡出 + 1/16 屏宽的共享轴位移，全部作用于图形层），时长 300 ms
  （Material 3 的 medium-2），并接入应用内动画开关（关闭时为 `0 ms`）。选择 300 ms 的理由是它与其他
  页面动效一致，且把「两屏同时绘制」的窗口从 700 ms 缩短到 300 ms；**没有删除或劣化任何过渡**。
* **A9**：释义展开改为 `AnimatedContent`（淡入 + 轻微上移，240 ms）。刻意**不**使用尺寸动画：
  尺寸动画会让整张卡（含 40sp 单词）每帧重新测量，这正是要避免的；改用图形层属性后卡片只在动画开始时
  改变一次高度。
* **A10**：错题本列表条目加 `Modifier.animateItem()`，条目增删与重排平滑。

### 1.3 剩余的动画性能话题（用户可见结论）

`@Immutable` 保证的是「状态相等时不重组」；当某个字段真的变化时，接收该状态的屏幕**应该**重组。
本轮没有引入 `kotlinx.collections.immutable`，因此以 `List<T>` 为**参数**的叶子组件在列表实现变化时
仍会重组（父级已跳过时则不会）。这在当前数据量下不构成问题，已在「未解决事项」中记录为可选优化。

---

## 2. 内置词库完整性

### 2.1 先识别词库标准（按要求，不编造数据）

原项目**没有**任何标准词表来源：内置的 188 条是上一轮为演示功能自撰的示例数据，`BuiltInWordPacks`
里明确写着「演示数据」。因此本轮先做的是**选择并核实一个可再分发的标准来源**，而不是继续扩充自撰内容。

选择结果：**`skywind3000/ECDICT`（MIT 许可）**，`ecdict.csv`，固定到提交
`bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b`。四级/六级归属直接取该数据源自带的 `tag` 列
（`cet4` / `cet6`），**没有由我们推断任何单词的等级**。

被否掉的候选（许可证原因，而非数据质量）：

| 候选 | 数据质量 | 否决原因 |
| --- | --- | --- |
| `RealKai42/qwerty-learner`（原 `qwerty-learner`） | 好，含 us/uk 音标 | 许可证是 **GPL-3.0**（不是以为的 MIT），传染性强 |
| `mahavivo/english-wordlists` | 一般 | **完全没有 LICENSE 文件**，README 说明数据来自某高校 PDF 与 2016 年考纲 → 无权再分发 |
| `KyleBing/english-vocabulary` | 最好（7,508/5,651 词、英/美音标、例句） | BSD-3 本身可用，但 README 记载血统为 `kajweb/dict → KyleBing/dict`，而上游两个仓库**均无许可证** → 权利链不可追溯 |

### 2.2 完整性验证结果（实测）

| 列表 | 词数 | 与来源双向差集 | 文件内重复 | 重复 id | 非法单词 | 空释义 | 空音标 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CET-4 | **3,849** | 缺失 0 / 多余 0 | 0 | 0 | 0 | 0 | 12 (0.31%) |
| CET-6 | **5,407** | 缺失 0 / 多余 0 | 0 | 0 | 0 | 0 | 25 (0.46%) |

* 两表重叠 3,451 词（六级包含四级，符合预期），仅四级 398、仅六级 1,956，并集 5,805。
* 资源体积 3.06 MiB（`cet4.json` 1.30 MB、`cet6.json` 1.82 MB、`manifest.json`、`PACK_INFO.json`），
  已确认打进 APK（`assets/wordpacks/*`）。
* **例句、例句翻译、常见搭配、美/英音标字段为空**：ECDICT 没有这些数据，按「不编造」原则留空，
  界面本身对空字段会隐藏对应区块。
* **音标做了一次纯记号转写**：ECDICT 用的是「金山风格」音标，其中 6,167 处用**西里尔字母 ә (U+04D9)**
  冒充 IPA 的 ə、132 处用 **є (U+0454)** 冒充 ɛ，另有用 `'`/`:` 表示重音与长音。脚本
  `tools/wordpack/normalize_phonetics.py` 把它们机械地转写成 ə / ɛ / ˈ / ː（**只转写记号，不增删任何
  发音信息**），结果：9,219 条音标中不再含任何西里尔字符，且断言有回归测试守护。
* 详细报告（含逐项测量表与残余风险）：[WORD_PACK_VALIDATION.md](../WORD_PACK_VALIDATION.md)。

### 2.3 加载路径的两个真实缺陷（这才是「词库能不能用」的关键）

**(1) 已安装的 App 永远拿不到更大的词表。** 原 `ensureBuiltInPacks()` 只在
`countInPack(...) == 0` 时才写入：老用户升级 App 后，包已存在，于是**整段逻辑直接跳过**，
新的完整词表和任何修正都进不来。这解释了「内置词库永远是示例词」这个现象。

**(2) 直接替换词表会摧毁学习数据。** 如果用 `INSERT OR REPLACE` 重建词条，行 id 会变，
而 `user_word_progress` / `review_event` / `wrong_answer` / `exam_question` 全部通过 `word_id`
外键指向它 —— 掌握状态、错题、考试快照会一起丢失。

修改后的加载器：

* 新增 `assets/wordpacks/manifest.json` + `data/seed/BuiltInPackLoader.kt`：**用与用户导入完全相同的
  `WordPackParser` 解析内置 JSON**，字段结构天然统一，并受同一套大小/条数/字段校验约束；
* `ensureBuiltInPacks()` 改为**版本感知**：比较资产版本与库内 `word_pack.version`，一致则整段跳过
  （避免每次冷启动重新解析 3 MB JSON），不一致则执行升级；
* 升级按 `(level, normalized_word)` 匹配：**已存在的词条原地 `@Update`（保留主键 → 进度、收藏、
  错题、考试快照全部保留）**，只插入新词，从不删除任何行；`INSERT(REPLACE)` 在这个路径上被明确禁用
  并在代码注释中说明原因；
* 9,000+ 行写入按 400 行一个事务分块，块间 `yield()`，冷启动页显示真实进度
  （「正在准备词库 CET-4 词汇（1200 / 3849）」）而不是一个看起来卡死的转圈；
* 资产缺失或损坏时回退到原有的 188 条内置演示词，App 不会变成空词库。

### 2.4 词库的浏览与搜索

完整词表有 9,000+ 词，「能不能查到一个词」是功能需求而不是加分项。新增：
`ui/screens/WordBrowserScreen.kt` + `WordBrowserViewModel` + `WordDao.search`（`word` 与 `meanings`
的 `LIKE`，前缀命中优先排序，`ESCAPE '!'` 转义用户输入的通配符，`LIMIT 200`），入口在
**我的 → 浏览 / 搜索词库**。查询做了 **220 ms 防抖** + `flatMapLatest`，输入过程中不会每敲一个字就
扫全表；结果为空、加载中都有独立状态。搜索与展示同时覆盖四级与六级（跟随当前词库级别）。

---

## 3. 发音升级：真实英式/美式录音

> **已被取代（迭代 3）**：本节描述的双口音真人录音方案已按新的产品需求**整体删除**，
> 应用改为使用系统 TTS 且不提供口音选择（详见 [ITERATION_3_REPORT.md](ITERATION_3_REPORT.md)）。
> 本节保留作为历史记录：其中的实测数据（英式覆盖 1/14）与工程结论（HTTP 429 错误页、
> 缓存原子写入、失败分支）是后续决策的依据，删除代码并不意味着这些结论失效。

### 3.1 根因

原实现（`ui/speech/PronunciationPlayer.kt`）只有一个 `TextToSpeech` 引擎，用 `Locale.US` / `en-GB`
切换语言：这是**同一个合成引擎换口音参数**，不是两种真实发音；并且存在一个真实缺陷 ——
引擎尚未初始化完成时 `speak()` 直接 `return`，所以**第一次点击通常没有任何反应**。此外没有缓存、
没有失败提示、没有美/英选择入口（只有设置里一个偏好，且不产生可感知的差别）。

### 3.2 方案（复用既有架构，新增独立模块）

新增 `data/pronunciation/` 模块，全部通过接口注入，与 Room/Repository 模式一致：

| 文件 | 职责 |
| --- | --- |
| `PronunciationModels.kt` | `PronunciationClip`（含来源与许可证）、`PronunciationError`（含用户可读文案）、`PronunciationSource` 接口 |
| `AudioHttp.kt` | `AudioHttpClient`（`HttpURLConnection`，6 s/12 s 超时，失败返回 `null`/`-1` 而**不抛异常**）、`NetworkStatus`（离线判定） |
| `PronunciationSources.kt` | ① Wikimedia Commons（**真人录音**，`En-us-*.ogg` / `En-uk-*.ogg` 两个独立文件，经公开 API 解析文件 URL）② dictionaryapi.dev（Wiktionary 录音，按 `-us.`/`-uk.` 文件名区分口音，**拒绝**把一个口音的文件复用给另一个） |
| `PronunciationCache.kt` | 磁盘缓存（`cacheDir/pronunciation/<accent>/<sha1(word)>.<ext>`）、**原子写入**（先 `.tmp` 再改名，中断不会留下半截文件）、`.src` 旁挂文件记录来源以便命中缓存时仍能正确署名、64 MiB 上限 + LRU 淘汰 |
| `PronunciationRepository.kt` | 解析顺序：缓存 → 源1 → 源2 → 失败；扩展名从 **URL 最后一段**推导（不再被主机名里的点误导） |

播放与状态机：`ui/speech/PronunciationController.kt`

* `MediaPlayer`（`prepareAsync` + 单实例复用 + 错误回调），不再以 TTS 为主方案；
* 状态机 `IDLE → LOADING → PLAYING / ERROR`，**每个请求都以某个状态结束**，不会出现按钮转圈卡死：
  仓库的所有网络调用都有超时且以值返回失败；
* 再次点击会取消上一个请求（`job.cancel()` + `player.stop()`），连点不会叠加下载；
* **离线兜底**：真人录音取不到时使用系统合成音，但 UI 明确标注「系统合成音（离线兜底）」并同时显示
  原因（例如「当前没有网络…」）—— TTS 从「唯一方案」降级为「有标注的兜底」，且修复了首次点击无效的缺陷。

### 3.3 产品入口与可见性

* 学习卡与单词详情页有 **「美 / 英」两个胶囊按钮**，一点即切换，切换写入 DataStore（与设置页同一项，
  两处永不失配）；
* 播放按钮在加载时显示进度圈；卡片下方显示**来源与许可证**（如「Wikimedia Commons 真人录音 · CC BY-SA」）
  或失败原因；
* 设置页「发音」区块：美/英选择、自动发音、**音频来源与许可证列表**、**缓存大小 + 清空缓存**按钮；
* 权限：新增 `INTERNET` + `ACCESS_NETWORK_STATE`，在清单与应用内隐私说明中都写明**唯一用途是获取单词
  发音音频**，学习/复习/考试/统计仍然完全离线；`AboutScreen` 与引导页文案已同步更正（原文写的是
  「不申请 INTERNET 权限」，现在已不再成立，必须改）。密钥方面：两条源都是**无密钥**接口，
  代码中没有任何 token；未来若接入付费服务，只需实现 `PronunciationSource` 并把凭据放在构建配置中。

### 3.4 真实可用性验证（线上实测）

见 [PRONUNCIATION_VERIFICATION.md](../PRONUNCIATION_VERIFICATION.md) 与机器可读的
`tools/pronunciation/findings.json`：脚本对 14 个词（普通 3、长词 3、特殊拼写 8）**实际调用两个线上接口**，
逐词记录美/英命中情况、解析出的 URL、文件字节数、许可证，并下载音频核对字节与 SHA-1。
结果（**实测，不是估计**；数字取自最终一次运行 `2026-10-10T04:04Z`）：

| 指标 | 实测结果 |
| --- | --- |
| 美式（Source A，Wikimedia Commons）有录音 | **11 / 14** |
| 英式（Source A）有录音 | **1 / 14**（仅 `apple`） |
| 美、英都是**各自独立**文件（不同 URL、不同大小、不同 SHA-1） | 1 / 14（`apple`：美 14,420 B / 英 16,265 B） |
| 确认完全无录音 → 走合成音兜底 | 2 / 14（`internationalization`、`characteristically`） |
| **无法确认**（Source A 无结果且 Source B 当时超时未应答） | 1 / 14（`well-being`） |
| Source B（dictionaryapi）实际给出 HTTP 应答的词 | 仅 6 / 14 |

两点必须如实说明，否则数字会被误读：

* `well-being` 是**未验证**而不是「确认没有录音」—— 对这个词 Source B 因服务端超时从未被成功查询；
* Source B 在本次运行中只有 6/14 词给出应答（其余超过 8 s 超时；经排查 DNS 0.04 s、TCP 0.22 s、
  TLS 0.24 s，且 40 s 冷却后仍超时，说明是其**服务端延迟**而非本地网络）。因此**英式覆盖率只被部分测量，
  不能据此断言「这些词不存在英式录音」**。

另外验证脚本做了一次独立交叉检查：直接在 Commons 文件命名空间搜索整个 `en-uk-*` 家族
（含 `En-uk-<word>` 与 `En-uk-<Word>` 两种大小写），14 个词里只有 `apple` 存在英式文件。
也就是说**英式缺口不是本项目探针标题太窄造成的**，而是这一命名家族本身在这些词上基本不存在
（英式录音可能存在于其他不相关标题下，脚本未穷举）。

结论与产品含义：**英式覆盖率明显低于美式，这是本轮最需要产品决策的一项**（见第 6 节）。
验证脚本还发现 `ability` 在 dictionaryapi 上**只有美式文件**：一个「有就用」的实现在这里会把同一个
录音同时当成英式和美式播放 —— 本项目的 `DictionaryApiSource.pickAudio` 明确拒绝这种行为
（只接受文件名带 `-uk.` 的条目，否则返回 null 交给下一源/兜底），并有两个单测固定该行为。

**线上实测暴露的最高影响风险（已加固）**：`upload.wikimedia.org`（文件 CDN，非 API 主机）对下载请求返回
**HTTP 429 + HTML 错误页**，且带 `Retry-After: 10`；该错误体为 **1,965 字节，高于应用原本 512 字节的下限**，
同时 URL 仍以 `.ogg` 结尾 —— 也就是说**只靠「字节数够大 + 扩展名像音频」两道检查会把这个错误页当成音频写进缓存**，
直到解码时才失败，而下载成本已经花掉。因此新增 `AudioFormat.kt`，用三重独立判断拒绝它：

* 状态码非 2xx → 直接拒绝（覆盖 429 场景，这也是验证脚本给出的第 1 条建议）；
* `Content-Type` 为 `text/html` / `application/json` → 在读取响应体之前就拒绝；
* 下载完成后校验文件**头部魔数**必须匹配 Ogg（`OggS`）/ MP3（`ID3` 或帧同步）/ WAV（`RIFF`）/
  FLAC（`fLaC`）/ MP4（`ftyp`）之一，否则删除临时文件并视为失败。

这一层是纯字节判断，`AudioFormatTest` 的 4 个单测覆盖，其中**直接使用实测到的 429 HTML 错误页作为样本**，
不依赖网络。需要说明：429 本身**无法在客户端消除**（服务端限流），应用的应对是「拒绝缓存 +
依次尝试下一个源 + 明确提示」，而不是假装成功。

**对真实响应的独立核对（我本人复核，缓存原始响应在 `tools/pronunciation/cache/`）**：

1. **响应结构与应用解析一致**：Commons 返回 `query.pages.<pageid>.title` +
   `imageinfo[0].url`（并附带 `extmetadata.LicenseShortName`，实测为 `CC BY-SA 3.0`），
   正是 `WikimediaCommonsSource.extractFileUrl` 读取的字段；dictionaryapi 返回数组，
   元素内 `phonetics[].audio`，与 `DictionaryApiSource.pickAudio` 一致。
2. **真实 URL 带查询串**：Commons 解析出的 URL 形如
   `…/6/66/En-us-misunderstanding.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo`。
   如果缓存扩展名从整个 URL 推导就会得到错误结果 —— 这也是本轮由测试发现的真实缺陷
   （见第 5 节），现已在只取 URL 最后一段后正确处理。
3. **英式覆盖率明显低于美式**（实测）：`En-us-misunderstanding.ogg`（20 KB）、
   `En-us-rhythm.ogg` **存在**；而 `En-uk-misunderstanding`、`En-uk-rhythm`、`En-uk-abandon`、
   `En-us-characteristically` 在该接口上**不存在**。独立的 `en-uk-*` 命名空间搜索也只在 14 词中的
   `apple` 上找到英式文件。这正好说明为什么「多源 + 回退 + 明确提示」是必需的，而不是可以省的工程量。

---

## 4. 浅色主题下状态栏不反色

### 4.1 根因

`MainActivity` 调用的是无参 `enableEdgeToEdge()`，其默认 `SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)`
按**系统**夜间模式（`Configuration.UI_MODE_NIGHT_*`）决定状态栏图标明暗，而且只在 `onCreate`
执行一次。应用内主题（`ThemeMode`）是 DataStore 里**独立**的一项设置，两者可以不一致，于是：

| 系统 | 应用内 | 结果 |
| --- | --- | --- |
| 浅色 | 深色 | 深色图标画在深色栏上 → **看不见** |
| 深色 | 浅色 | 浅色图标画在暖白栏上 → **看不见**（用户报告的正是这一侧） |
| 任意 | 运行时切换 | 图标颜色**完全不变**，直到重建 Activity |

`values/themes.xml` 的 `windowLightStatusBar` 同样只跟随系统，无法表达应用内选择。

### 4.2 修改

新增 `ui/theme/SystemBars.kt`：`SystemBarIconAppearance`（描述**图标**而非背景，因为 edge-to-edge 下
状态栏是透明的、露出应用背景色）、`SystemBarStyler` 接口 + `WindowSystemBarStyler`（用
`WindowInsetsControllerCompat`，因此 **API 26 起走同一条代码路径**，30 以下映射到
`SYSTEM_UI_FLAG_LIGHT_STATUS_BAR`），并在 Android 10+ 关闭系统对比度蒙层，使状态栏背景严格等于主题背景。

`CetVocabTheme` 内部调用 `ApplySystemBarAppearance(darkTheme)`：**主题解析出的 dark 标志就是唯一依据**，
每次主题变化（含运行时切换）都会重新应用；同时通过 `LocalDarkTheme` 暴露该值，避免任何地方重复推导。
预览（`isInEditMode`）与没有 Activity 的场景自动跳过；测试可注入 `LocalSystemBarStyler` 断言映射。

### 4.3 验证

`SystemBarsTest`（5 个用例，`@Config(sdk = [26])`）：
浅色 → 请求深色图标；深色 → 请求浅色图标；**运行时切换主题会重新应用**；
并且在**真实 window 上**断言 API 26 的 `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR` 分别被置位/清除。
不同页面共用同一主题宿主，系统重启后由 DataStore 恢复主题并在首次组合时重新应用，因此
「不同页面 / 切换过程 / 重启后」三种情形由同一段逻辑保证。

---

## 5. 测试结果

新增 10 个测试类（`SystemBarsTest`、`UiStateStabilityTest`、`PronunciationRepositoryTest`、
`PronunciationSourceTest`、`AudioFormatTest`、`AccentSelectionTest`、`BuiltInPackAssetTest`、
`BuiltInPackSeedTest`、`WordBrowserScreenTest`，共 55 个新用例），并修复了一个原有的测试隔离缺陷。

| 套件 | 用例 | 结果 |
| --- | --- | --- |
| SpacedRepetitionTest | 26 | ✅ |
| StudySessionEngineTest | 22 | ✅ |
| ExamGeneratorTest / ExamScoringTest | 19 / 19 | ✅ |
| WordPackParserTest / CsvCodecTest | 34 / 16 | ✅ |
| StatsCalculatorTest / StudyDayTest | 27 / 21 | ✅ |
| DatabaseTest / ProgressRecorderTest | 22 / 20 | ✅ |
| **BuiltInPackAssetTest**（内置词库完整性 + 真实资产端到端播种） | 7 | ✅ |
| **BuiltInPackSeedTest**（播种与升级不丢进度） | 7 | ✅ |
| **PronunciationRepositoryTest**（缓存/回退/错误） | 12 | ✅ |
| **PronunciationSourceTest**（双口音 URL） | 8 | ✅ |
| **AudioFormatTest**（拒绝 HTML/非音频内容） | 4 | ✅ |
| **AccentSelectionTest**（口音切换生效并持久化） | 2 | ✅ |
| **SystemBarsTest**（状态栏反色） | 5 | ✅ |
| **UiStateStabilityTest**（重组跳过/时间格式） | 5 | ✅ |
| ComposeFlowTest | 11 | ✅ |
| **WordBrowserScreenTest**（词库浏览与搜索） | 5 | ✅ |
| **合计** | **292** | **0 失败 / 0 错误** |

构建验证：`:app:assembleDebug` 成功（APK 14.61 MB，词库资产已确认打进 `assets/wordpacks/`）；
`:app:assembleRelease -x lintVitalRelease` 成功（R8 压缩后 2.35 MB，`minifyReleaseWithR8` 已执行，
发布包同样包含词库资产与 `INTERNET`/`ACCESS_NETWORK_STATE`）；
`apkanalyzer` 确认**调试包与发布包均为 minSdk 26 / targetSdk 36**。

本轮由测试**发现并修复的真实缺陷**（不是测试适配）：

1. `extensionOf("https://x.test/a/apple")` 会把主机名里的点当成扩展名（返回 `test`），
   已改为只取 URL 最后一段 —— 而线上真实 URL 恰好带查询串（见 3.4），这个缺陷会直接影响缓存。
2. `StudyViewModel.start()` 在队伍为空时提前返回，**没有把设置里的口音同步进状态**：
   此时状态里的 accent 可能是上一次会话的旧值，与用户刚改的设置不一致。
   已在空队列分支同步 `accent` / `autoPlayPronunciation`（无需额外 I/O，`settings` 已在作用域内）；
   由新增的 `AccentSelectionTest` 固定。

---

## 6. 尚未解决的问题 / 外部依赖 / 待产品确认

1. **英式发音覆盖率不足（本轮最大的产品缺口，已实测）**：14 个抽样词中，美式 **11/14** 有录音，
   而**英式只有 1/14（仅 `apple`）**；2/14（`internationalization`、`characteristically`）完全无录音，
   1/14（`well-being`）因 Source B 当时超时而**未验证**。独立的 `en-uk-*` 命名空间搜索确认：
   缺口不是本项目探针标题太窄所致。也就是说「英式」在多数单词上会回退到合成音（并明确标注）。
   三个可选方向需要产品决策：
   (a) 接受现状并在界面上如实说明英式覆盖有限（以及「英式录音确实稀缺」这一客观事实）；
   (b) 接入付费发音服务（接入点已预留为 `PronunciationSource` 实现，凭据走构建配置）；
   (c) 引入许可清晰、带英式音标/音频的开源数据源（需要先解决授权可追溯性）。
   我没有为了让数字好看而放宽「英式只能用英式录音」的规则 —— 那正是需求明确禁止的「用同一份音频冒充两种口音」。
   需要澄清的测量局限：Source B 在本次运行中只有 6/14 词给出应答，因此**英式覆盖是部分测量，
   不能据此断言这些词不存在英式录音**。
2. **Wikimedia 下载限流（HTTP 429 + `Retry-After: 10`，实测为间歇性出现）**：这是服务端限流，客户端无法消除。
   应用的行为是：拒绝把错误页写入缓存（`AudioFormat` 的三重校验）、依次尝试下一个源、失败时给出可读提示并
   回退合成音。若在真实设备上频繁遇到，可考虑加入 `Retry-After` 感知的**有界重试**（服务端已给出等待秒数）
   或改用付费 CDN —— 需要产品确认。本轮未实现重试，因为那会延长单次播放的等待时间，属于体验取舍。
3. **Source B（dictionaryapi.dev）可靠性偏低（实测）**：14 词中仅 6 词在 8 s 内应答，
   且经排查为服务端延迟（DNS/TCP/TLS 均在 0.25 s 内，40 s 冷却后仍超时）。当前策略是把它当作
   **best-effort 第二源**，超时即交给兜底；不建议把英式发音的期望寄托在它上面。
4. **音标来源为 ECDICT 金山风格**：本轮只把西里尔字母与 `'`/`:` 转写成 IPA 记号，其**音位体系**仍是
   金山风格（例如用 `i` 表示 /i/ 而非 /ɪ/ 的场合）。若要严格 IPA，需要另找有许可的音标数据源
   （`KyleBing` 的音标质量最好但权利链不可追溯，因此未采用）。
5. **内置词库缺少例句与搭配**：ECDICT 不提供，按「不编造」原则留空。若要例句，需要引入有明确许可的
   双语例句语料，或由用户自行导入（导入管线已支持这些字段）。
6. **词库规模带来的首次启动成本**：9,000+ 行首次写入约需数百毫秒到数秒（取决于设备），
   目前用带真实进度的加载页承载。可进一步改为「先可用的分级加载」（先写 500 词让用户立刻开始，
   其余后台补齐），但这会引入部分词表可见的中间态，需要产品确认是否接受。
7. **`List<T>` 参数的叶子组件稳定性**：未引入 `kotlinx.collections.immutable`，
   详见 1.3。属于可选优化。
8. **模拟器仍未验证**：本机可用物理内存/提交内存长期紧张（其他应用占用约 30/31 GB 提交上限），
   无法启动 Android 模拟器。API 26 的验证仍然依赖 Robolectric 的 `android-all` API 26 镜像
   （本轮新增的 55 个用例中有 31 个运行在 `@Config(sdk = [26])`），加上静态 API 审查。
   建议在内存正常的机器上执行 `./gradlew :app:installDebug` 做一次真机/模拟器走查，
   重点看：首页进度环动画、页面过渡、状态栏在浅/深主题下的对比度、以及首次启动的词库播种进度。
9. **`lintVitalRelease` 未能在本机运行**（AGP lint 需要超出本机可用提交内存的元空间），
   发布构建时以 `-x lintVitalRelease` 跳过；该任务是质量检查而非构建必需。
10. **待产品确认的文案**：应用内已如实说明「发音音频需要联网，其余离线」，与上一版
   「完全不联网」的对外说法不同；如果对外宣传物料仍写着「完全离线」，需要同步更新。
   另外「英式发音」在界面上也应避免暗示 100% 覆盖 —— 当前实现会如实标注回退到合成音。

---

## 附录 · 本轮修改的文件

**新增（main）**：`data/pronunciation/{PronunciationModels,AudioHttp,PronunciationSources,PronunciationCache,PronunciationRepository,AudioFormat}.kt`、
`data/seed/BuiltInPackLoader.kt`、`ui/theme/SystemBars.kt`、`ui/speech/PronunciationController.kt`、
`ui/screens/WordBrowserScreen.kt`、`assets/wordpacks/{cet4.json,cet6.json,manifest.json,PACK_INFO.json}`

**修改（main）**：`ui/components/Cards.kt`（进度环）、`ui/components/WordCard.kt`（展开过渡 + 美/英控件 + 加载态）、
`ui/screens/HomeScreen.kt`（LazyColumn + 格式化器提升）、`ui/screens/ReviewScreens.kt`（格式化器 + 条目动画 + 详情页发音）、
`ui/screens/StudyScreen.kt`（发音控制器）、`ui/screens/ProfileScreens.kt`（发音设置与缓存）、
`ui/screens/DataTransferScreens.kt` + `ui/screens/OnboardingScreen.kt`（隐私文案）、
`ui/ViewModels.kt`（`@Immutable`、问候语/日期下沉、`setAccent`、词库浏览 VM、发音缓存状态）、
`ui/theme/Theme.kt`（应用状态栏外观）、`ui/navigation/{Routes,CetVocabNavHost}.kt`（过渡 + 新路由）、
`ui/speech/PronunciationPlayer.kt`（TTS 降级为兜底且修复首次点击无效）、
`data/repository/WordRepository.kt`（版本化播种 + 升级 + 搜索）、`data/local/dao/{WordDao,QueryRows}.kt`（批量更新、身份映射、搜索）、
`AndroidManifest.xml`（INTERNET / ACCESS_NETWORK_STATE 及其理由）

**新增（test）**：`SystemBarsTest`、`UiStateStabilityTest`、`PronunciationRepositoryTest`、`PronunciationSourceTest`、
`AudioFormatTest`、`AccentSelectionTest`、`BuiltInPackAssetTest`、`BuiltInPackSeedTest`、`WordBrowserScreenTest`；
修改 `ComposeFlowTest`（共享内存数据库修复隔离）

**工具与文档**：`tools/wordpack/{download,build_packs,validate_packs,normalize_phonetics,measure_sources}.py`、
`tools/pronunciation/verify_audio.py`、`docs/WORD_PACK_VALIDATION.md`、`docs/PRONUNCIATION_VERIFICATION.md`、
`docs/DELIVERY_REPORT.md`（就地更新）、`README.md`、`.gitignore`、本报告
