# 迭代 4 报告：开屏提示语 / 例句提示 / 词库搜索

一句话结论：**三项需求全部落地，均为本地数据、离线可用**；全量单元测试 **404 个用例通过
（0 失败 / 0 错误，26 个测试类）**，`assembleDebug` 构建成功。本轮**没有数据库结构变更**
（Room schema 仍为 1，无需迁移），内置词库通过**已有的就地升级路径**（pack version 2 → 3）补入例句，
不删除、不重建任何行，学习进度 / 错题 / 收藏 / 考试快照都不受影响。

---

## 1. 需求 → 实现对照

| 需求 | 落地方式 | 关键位置 |
| --- | --- | --- |
| ① 本地励志提示语 ≥370 条，冷启动随机展示、不连续重复、离线、可校验 | 内置 **398 条**原创提示语资源 + 纯 Kotlin 选择器 + DataStore 记录上一条 | `assets/motivation/tips.json`、`domain/motivation/StartupTip.kt`、`data/motivation/StartupTipSource.kt`、`ui/screens/StartupTipScreen.kt` |
| ② 揭晓答案前展示相关英文例句 | 例句数据写入**已有的** `exampleSentence` / `exampleTranslation` 字段；卡片未揭晓面只显示英文例句（**不含中文翻译**），揭晓后才给释义与翻译 | `tools/wordpack/attach_examples.py`、`ui/components/WordCard.kt` |
| ③ 词库搜索（英文精确 / 前缀 / 模糊、中文释义、大小写与空格、结果显示例句、点击进详情） | 复用并增强**已有**的词库浏览页：新增**首页入口**、**拼写相近（编辑距离）兜底匹配**、结果行音标 / 词性 / 例句 | `ui/screens/HomeScreen.kt`、`domain/search/ApproximateWordMatcher.kt`、`data/repository/WordRepository.kt`、`ui/screens/WordBrowserScreen.kt` |

---

## 2. 功能一：冷启动开屏提示语

### 2.1 数据

* 文件：`app/src/main/assets/motivation/tips.json`（随包分发，**不联网**）。
* 数量：**398 条**（要求 ≥370），固定 8 个主题各 49–50 条：
  坚持与习惯 / 自律与行动 / 时间与节奏 / 积累与复利 / 面对困难 / 自信与心态 / 成长与复盘 / 生活与平衡。
* 每条含 `id`（`tip-0001`…，稳定可复现）、`category`、`text`。
* 文本为**本项目原创**（CC0-1.0），**不含任何名人名言、书名或第三方引用**，因此不存在「虚构名人名言」的问题；
  有意的约束：不出现熬夜 / 拼命 / 淘汰 / 不配 / 必须 / 来不及等施压措辞。
* 生成与校验链：`tools/motivation/parts/*.json`（两批撰写稿）→ `build_tips.py`
  （完全重复与近似重复剔除、按分类稳定排序、分配 id）→ `validate_tips.py`（数量、去重、分类、
  长度、禁用词、最大相似度）→ `StartupTipTest`（同样的检查进入 CI）。

实测（`validate_tips.py` 输出）：

| 指标 | 值 |
| --- | --- |
| 条数 | 398 |
| 分类数 | 8（每类 49–50） |
| 正文长度（去标点） | 最短 11，最长 20，平均 15.3 |
| id / 文本唯一 | 均为 `True` |
| 最大近似相似度 | **0.455**（阈值 0.60） |
| 合并时剔除 | 近似重复 2 条（完全重复 0 条） |

### 2.2 展示逻辑

* **只在冷启动显示一次**：由 `MainViewModel` 持有状态，不是导航目的地，因此切页面 / 回到首页都不会重播；
  旋转屏幕由 ViewModel 存活，也不会重播。
* **随机但避开上一条**：`StartupTipPicker.pick(tips, avoidId, random)` 先排除上次展示的 id 再随机，
  「避免连续两次相同」而不是「保证永不重复」；只有一条时仍会展示它（绝不会变成空白）。
  上次的 id 存在 DataStore（`last_startup_tip_id`），因此跨进程重启有效。
* **轻量、不拖慢启动**：提示语叠加在**已有的**冷启动那一屏上——首次安装时那一屏本来就在写词库并显示真实进度，
  所以提示语没有增加新的启动步骤。它至少显示 900 ms，若词库仍在写入则最多再等 1 600 ms（合计上限 2.5 s），
  **点击任意位置立即进入**；词库就绪后首页与导航图已经在它下方绘制完成，淡出即用。
* **降级**：资产缺失 / JSON 损坏 / 条目为空时，`AssetStartupTipSource` 返回内置的默认一句
  （「今天也从一个单词开始。」），首屏不会空白，也绝不会因为一个鼓励功能而启动失败。
* 动画时长跟随「动画效果」开关（关闭时为 0 ms）。

### 2.3 验收对照

| 验收项 | 结果 |
| --- | --- |
| ≥370 条 | 398 条 ✅ |
| 脚本 / 测试检查数量与重复 | `validate_tips.py` + `StartupTipTest` ✅ |
| 断网可展示 | 资产内置，应用无 `INTERNET` 权限 ✅ |
| 不影响启动与进入主界面 | 与原有播种进度同屏，可点击跳过 ✅ |
| 不影响导航与原有功能 | 只做根级叠加，未改动导航图 ✅ |

---

## 3. 功能二：揭晓答案前的英文例句

### 3.1 关键事实：原词库例句覆盖率为 0

`PACK_INFO.json` 的 `fieldCoverage` 明确记录：ECDICT 不提供例句，改造前
`exampleSentence` / `exampleTranslation` 的空值率是 **100%**。因此「先给语境」这件事的前提是先有真实例句数据。

### 3.2 数据方案（第二个来源，构建期抓取、随包内置）

| 项 | 值 |
| --- | --- |
| 来源 | The Tatoeba Project（<https://tatoeba.org>）英文句 + 中文译文 |
| 许可 | **CC BY 2.0 FR**（署名型），署名与逐句 id 见 `THIRD-PARTY-NOTICES.md`、`docs/EXAMPLE_SENTENCE_SOURCES.tsv` |
| 快照 | 2026-10-13T02:09:54Z（带 SHA-256，见 `tools/wordpack/cache/tatoeba/PROVENANCE.json`） |
| 句对 | 68,553 对 → 通过句形与可读性过滤 **46,460 句** |
| 选取 | **每个词只选一句**（原形优先 → 质量档位 → 更短句 → 句 id 兜底） |
| 繁体译文 | 用 OpenCC（Apache-2.0）字符 / 短语表做**构建期**最长匹配转写，词表不随包分发 |
| 写入方式 | 写进**已有的** `exampleSentence` / `exampleTranslation` 列，pack version 2 → 3 |

「可读性」用可量化的口径：句子 4–14 词、无数字 / 引号 / 分号 / 全大写缩写，且句中**超纲词**越少档位越高
（不超纲的基准 = CET-4/CET-6 词表 ∪ ECDICT 词频前 8,000 核心词 ∪ 约 250 个功能词）；含人名（Tom / Mary 之类）
会降档。**没有为任何词编造句子，也没有套用同一句式**。

### 3.3 实测覆盖率

| 指标 | CET-4 | CET-6 | 合计 |
| --- | ---: | ---: | ---: |
| 词条数 | 3,849 | 5,407 | 9,256 |
| **有例句** | **3,230（83.92%）** | **3,695（68.34%）** | **6,925（74.81%）** |
| 无例句（字段留空） | 619 | 1,712 | 2,331 |
| 其中原形直接命中 | 2,961 | 3,294 | — |
| 其中经规则变形命中 | 269 | 401 | 670 |
| 其中译文由繁体转写 | 537 | 779 | 1,316 |

去重后（CET-4 与 CET-6 重叠 3,451 词，独立词 5,805）：**4,054 个词有例句（69.84%）**，1,751 个没有。
独立句子 3,704 句用于 4,054 个词（最高复用 6 次），英文 4–14 词（平均 6.5），中文 4–31 字（平均 10.5）。

### 3.4 交互实现（最小改动）

* 只改 `WordCard`：未揭晓时若有例句就显示 **"例句提示"** 区块（英文原句 + 「先读例句猜猜它的意思」），
  **不显示中文翻译、不显示释义**；揭晓后沿用原有 `WordMeaningBlock`（释义 + 例句 + 翻译 + 搭配），
  即翻译与释义同时出现，不会提前泄露。
* 背词、巩固练习、错题复习、单词详情都复用同一个 `WordCard`，因此都自动生效，**没有复制第二套答题逻辑**。
* 无例句的词回到原来的「先回忆一下，再点击卡片查看释义」提示，不出现空区块或崩溃。
* **闭卷考试保持不变**：考试的产品规则就是全程不给提示（原代码刻意让考试卡拿不到正确答案），
  本轮没有为了「统一体验」破坏这条规则，并在使用指南里写明。

### 3.5 验收对照

| 验收项 | 结果 |
| --- | --- |
| 适用流程在揭晓前展示例句 | 背词 / 巩固 / 错题复习 / 详情 ✅ |
| 例句与单词匹配 | 自动化断言「句中确实出现该词或它的规则变形」，且**实测 0 条例句不含该词** ✅ |
| 不提前泄露答案 | 未揭晓面无释义、无中文翻译（UI 测试断言两者都不存在）✅ |
| 原有判分 / 错题 / 复习正常 | 未改判分与调度代码；回归测试全通过 ✅ |
| 缺失时合理降级 | 空例句 → 原提示语；搜索行不显示例句行；朗读按钮不出现 ✅ |

---

## 4. 功能三：词库搜索

### 4.1 先盘点了已有能力

词库浏览页此前已存在（`Routes.WORD_BROWSER`，入口在「我的」），并且已经支持：
英文 `LIKE '%q%'` 包含匹配（天然覆盖精确与前缀）、中文释义匹配、`ESCAPE '!'` 转义用户输入的 `%` `_`、
按「单词前缀优先」排序、220 ms 防抖、结果上限 200、后台线程查询、点击进入单词详情。
**因此本轮没有重写搜索，只补上真正缺的三件事**：

1. **首页入口**：首页顶栏加一个放大镜（`home_open_word_browser`，内容描述「词库搜索」），
   不改动首页仪表盘的段落结构；「我的」里的原入口保留。
2. **拼写相近兜底**（`ApproximateWordMatcher`）：当精确搜索命中很少（< 8 条）且输入是纯英文字母
   （长度 ≥ 3）时，追加一次**编辑距离**匹配——含**相邻字母写反**（`recieve` → `receive` 记为 1 次编辑），
   距离阈值按长度取 1 / 2 / 3。候选来自 SQL 的**长度窗口**查询（±阈值，上限 4,000 行，`ORDER BY word`），
   距离计算在 `Dispatchers.Default` 上做，结果单独成段并标注「拼写相近」，**绝不冒充精确结果**。
3. **结果行信息**：单词、音标、词性、最多 3 条释义（搜中文时能看见命中的那一义项）、有例句时加一行英文例句
   （详情页给完整例句与翻译）。

### 4.2 匹配方式与数据来源（实测）

| 能力 | 实现 |
| --- | --- |
| 英文精确 / 前缀 / 包含 | `word LIKE '%q%'`，前缀命中排前 |
| 拼写相近（模糊） | 编辑距离（OSA，含换位）+ 长度窗口候选，结果独立成段 |
| 中文释义 | `meanings LIKE '%q%'`（释义以 JSON 数组存储，包含匹配即可命中） |
| 大小写 | `LIKE` 对 ASCII 不区分大小写；查询前 `trim()`，前后空格被忽略 |
| 数据来源 | **本机数据库中的词库**（内置 CET-4/CET-6 或用户导入），不存在词库以外的词条 |
| 离线 | 无网络调用；应用不申请 `INTERNET` 权限 |
| 性能 | 220 ms 防抖、上限 200 条、长度窗口上限 4,000 行、距离计算在后台调度器 |
| 空 / 无结果 | 有明确空状态文案；近似段有说明文案 |

### 4.3 为什么没有加索引、没有改表结构

`LIKE '%q%'` 带前置通配符，**任何索引都无法用于该条件**（SQLite 只能全表扫描再过滤），
加索引只会增加 schema 变更与迁移风险，却换不到查询收益。当前词库 9,256 行（单级 3,849 / 5,407 行），
扫描成本在毫秒级，且已经在 `Dispatchers.IO`（Room suspend）与防抖之后执行。
因此本轮**刻意不做**数据库结构变更：Room schema 仍为 1，无需迁移，用户数据零风险。

---

## 5. 数据安全与兼容

* **没有数据库结构变更**：没有新增表 / 列 / 索引，`schemaVersion` 仍为 1，不存在迁移失败导致丢数据的可能。
* **内置词库升级走既有路径**：`seedPack()` 按 `(level, normalized_word)` 匹配后 **update 现有行**
  （保留行 id），因此所有指向这些词的学习进度、复习事件、错题记录与考试快照全部保留；
  本次只是把 pack version 由 2 提到 3，让已安装的设备能拿到新字段。
  `BuiltInPackAssetTest` 会在真实资产上跑一遍「播种 → 复查行数 → 二次播种不重复」。
* **用户导入词库不受影响**：导入逻辑未改；例句字段本来就支持导入（JSON / CSV 均有对应字段）。
* **既有用户数据不被清空**：本轮没有触碰任何清理 / 重置逻辑。
* 已知的既有行为（不是本轮引入）：若用户导入的词条与内置词条同名（同 level、同 normalized word），
  内置包升级会用内置内容更新该行——这是原有的、为保证词库可升级而设计的行为，本轮沿用并在此说明。

---

## 6. 测试与构建

| 项目 | 结果 |
| --- | --- |
| 单元测试 `:app:testDebugUnitTest` | **404 个用例通过，0 失败 / 0 错误 / 0 跳过**（26 个测试类；上一轮为 377 / 22） |
| 调试包 `:app:assembleDebug` | **BUILD SUCCESSFUL**，`app-debug.apk` **14.86 MB**（上一轮 14.49 MB，+0.37 MB，即例句文本压缩后的体积） |
| 资产校验 `validate_packs.py` | 词条数、id 稳定性、结构、空值比例全部复跑通过 |
| 提示语校验 `validate_tips.py` | 398 条、唯一性、分类、长度、禁用词、相似度全部通过 |

本轮修改过程中被测试真正拦下的问题（说明测试有效，不是走过场）：

| 问题 | 拦截方式 |
| --- | --- |
| 例句的「规则变形」生成过宽，出现过 `kin ← kind`、`hop ← hoping`（实为 hope）、`mast ← master` 这类**形近词误配** | 自查脚本逐条比对「句中是否真的出现该词」，据此收紧变形规则；收紧后复核 **0 条例句不含该词** |
| `WordRepository` 实现类里直接引用接口 companion 常量导致编译失败 | `:app:compileDebugKotlin` 直接报 `UNRESOLVED_REFERENCE` |
| 4 个 UI 测试因 Compose 语义合并（可点击容器吞掉子节点 tag）而失败 | `:app:testDebugUnitTest` 报错并提示改用 `useUnmergedTree`，修正后全绿 |

本轮新增 / 扩充的自动化检查：

| 测试 | 覆盖 |
| --- | --- |
| `StartupTipTest`（新） | 真实资产的条数 / 唯一性 / 长度 / 禁用词 / 最大相似度；选择器避开上一条；解析容错 |
| `StartupTipScreenTest`（新） | 提示语与分类展示、点击即继续、播种进度文案、无提示语时仍可用 |
| `WordCardExampleTest`（新） | 揭晓前只有英文例句（无释义、无翻译）；揭晓后释义与翻译出现；无例句时回退原提示 |
| `ApproximateWordMatcherTest`（新） | 距离（含换位 / 对称 / 提前退出）、适用性判定、排序确定性、边界 |
| `WordBrowserScreenTest`（扩充） | 拼错时出现「拼写相近」段与建议标记；精确命中时不出现该段；结果行显示音标与例句 |
| `ComposeFlowTest`（扩充） | 首页放大镜能打开词库搜索 |
| `BuiltInPackAssetTest`（扩充） | 真实资产例句覆盖率下限、例句与译文成对、句长、无数字 / 无汉字混入、**句中确实含该词或其变形** |

---

## 7. 已知问题 / 残余风险 / 需要人工确认

1. **例句是众包语料**，个别句子偏短、像片段，或译文取意译（例：`bough` 的英文谚语配中文谚语）。
   筛选只能降低概率，不能保证逐句完美；如需更高质量，需人工校订或另找许可清晰的例句库。
2. **不做词义消歧**：选句只看拼写与可读性，不看该句用的是哪个义项。背词模式会列出全部释义，
   因此不会出现「例句与题目释义冲突」的直接矛盾；严格意义上的义项对齐需要词义标注数据，本轮没有做。
3. **例句覆盖率不完整**（独立词 69.84%，CET-6 更低）：低频词很难找到既简单又含该词的句子。
   应用以「隐藏空字段 + 原提示语」降级，不会用假数据补齐。
4. **繁体转写是机械转换**，极少数专有名词可能不理想；转换只发生在含繁体字的句子上，且可逐句回溯。
5. **署名依赖逐句来源表**：`docs/EXAMPLE_SENTENCE_SOURCES.tsv` 记录了每个词所用句子的 Tatoeba 句 id；
   若该表被删除，署名将只剩项目级署名（许可仍满足 CC BY 的最低要求，但可追溯性变差）。
6. **开屏提示语的可读时长是产品取舍**：至少 900 ms（可点击跳过）。若希望「零等待」，可把
   `MainViewModel.STARTUP_TIP_MIN_MILLIS` 调到 0——但那样在词库已就绪的机器上就几乎看不见提示语。
7. **需要人工确认**：真机截图尚未补充（README 的「界面」区目前仍是首页 / 学习卡两张旧图），
   开屏提示语与词库搜索页还没有真机截图；`docs/images/` 需要你提供或允许我用模拟器 / 真机补拍。

---

## 8. 变更文件清单

**新增（应用代码）**

| 文件 | 用途 |
| --- | --- |
| `app/src/main/assets/motivation/tips.json` | 398 条冷启动提示语（内置资源） |
| `app/src/main/java/com/cetvocab/app/domain/motivation/StartupTip.kt` | 提示语模型、容错解析、避开上一条的选择器 |
| `app/src/main/java/com/cetvocab/app/data/motivation/StartupTipSource.kt` | 从资产读取并降级 |
| `app/src/main/java/com/cetvocab/app/ui/screens/StartupTipScreen.kt` | 开屏提示语界面（含播种进度） |
| `app/src/main/java/com/cetvocab/app/domain/search/ApproximateWordMatcher.kt` | 编辑距离（OSA）与近似匹配排序 |

**修改（应用代码）**

| 文件 | 改动 |
| --- | --- |
| `AppContainer.kt` | 注入 `startupTipSource` |
| `MainActivity.kt` | 传入「跳过开屏」回调 |
| `ui/CetVocabApp.kt` | 开屏提示语叠加 + 淡出，保留原有播种进度 |
| `ui/ViewModels.kt` | `MainViewModel` 选择 / 持久化 / 定时关闭提示语；`WordBrowserViewModel` 增加近似结果 |
| `data/settings/UserSettings.kt`、`SettingsRepository.kt` | 新增 `lastStartupTipId` 及其读写 |
| `data/local/dao/WordDao.kt` | 新增长度窗口候选查询（供近似匹配） |
| `data/repository/WordRepository.kt` | 新增 `searchApproximate` 与阈值常量 |
| `ui/components/WordCard.kt` | 未揭晓面显示英文例句提示（不含翻译） |
| `ui/screens/WordBrowserScreen.kt` | 「拼写相近」分段、结果行音标 / 词性 / 释义 / 例句、空状态文案 |
| `ui/screens/HomeScreen.kt` | 顶栏词库搜索入口 |
| `ui/navigation/CetVocabNavHost.kt` | 把搜索入口接到词库路由 |
| `app/build.gradle.kts` | versionCode 4 / versionName 1.0.3 |

**数据与工具**

| 文件 | 改动 |
| --- | --- |
| `app/src/main/assets/wordpacks/cet4.json`、`cet6.json` | 写入例句字段，pack version → 3 |
| `app/src/main/assets/wordpacks/manifest.json`、`PACK_INFO.json` | 版本与例句覆盖率记录（含来源 / 许可 / 快照） |
| `tools/motivation/build_tips.py`、`validate_tips.py`、`parts/*.json` | 提示语的合并与校验（可复现） |
| `tools/wordpack/build_tatoeba_pairs.py` | 从 Tatoeba 导出构建英中句对 |
| `tools/wordpack/attach_examples.py` | 选句、繁体转写、写入资产、产出报告与来源表 |
| `tools/wordpack/validate_packs.py` | 允许并记录 `exampleSource` 包级键 |
| `tools/wordpack/example_attachment_report.json` | 本轮选句的实测数字与抽样 |
| `docs/EXAMPLE_SENTENCE_SOURCES.tsv` | 词 → Tatoeba 句 id（署名与追溯） |
| `docs/WORD_PACK_VALIDATION.md` | 新增**附录 B**（例句来源、规则、覆盖率、残余风险），同步体积与字段统计 |
| `docs/USER_GUIDE.md`、`README.md`、`docs/README.md`、`THIRD-PARTY-NOTICES.md` | 面向用户 / 开发者的说明与许可更新 |

**测试**

| 文件 | 改动 |
| --- | --- |
| `domain/motivation/StartupTipTest.kt` | 新增 |
| `domain/search/ApproximateWordMatcherTest.kt` | 新增 |
| `ui/screens/StartupTipScreenTest.kt` | 新增 |
| `ui/components/WordCardExampleTest.kt` | 新增 |
| `ui/WordBrowserScreenTest.kt`、`ui/ComposeFlowTest.kt`、`data/seed/BuiltInPackAssetTest.kt` | 扩充 |
