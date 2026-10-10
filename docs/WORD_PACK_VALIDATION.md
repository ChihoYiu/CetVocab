# CET-4 / CET-6 内置词库校验报告（WORD PACK VALIDATION）

本报告记录 `app/src/main/assets/wordpacks/` 下三个词库资产的数据来源、许可、转换过程与
**实测**校验数字。所有数字均由 `tools/wordpack/validate_packs.py` 直接测量得出，
原始机器可读结果见 `tools/wordpack/validation_report.json`。

* 生成脚本：`tools/wordpack/build_packs.py`
* 校验脚本：`tools/wordpack/validate_packs.py`
* 资产：`cet4.json`、`cet6.json`、`PACK_INFO.json`

---

## 1. 数据来源

| 项 | 值 |
| --- | --- |
| 仓库 | <https://github.com/skywind3000/ECDICT> |
| 文件 | `ecdict.csv`（UTF-8 CSV，770,611 行） |
| 固定版本 | commit `bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b`（HEAD，提交日期 2025-03-28） |
| 固定下载地址 | `https://raw.githubusercontent.com/skywind3000/ECDICT/bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b/ecdict.csv` |
| 下载时间 | 2026-10-10T02:58:00Z（UTC） |
| 选取字段 | `word`、`phonetic`、`translation`、`tag` |
| 选取规则 | `tag` 列（空格分隔的考试标签）中包含 `cet4` / `cet6` |

ECDICT 的 `tag` 列是来源自带、**明确用于标注「是否属于各类考试大纲词汇」**的字段，其 README
（`ECDICT-README.md`，已缓存）写明：

> `tag` | 字符串标签：zk/中考，gk/高考，cet4/四级 等等标签，空格分割

因此「哪些词属于 CET-4 / CET-6」这一判断完全来自上游数据本身，而不是本项目的推测。

`ecdict.csv` 中带各考试标签的行数（实测）：

| tag | 行数 |
| --- | ---: |
| gre | 7,504 |
| toefl | 6,974 |
| **cet6** | **5,407** |
| ielts | 5,040 |
| ky | 4,801 |
| **cet4** | **3,849** |
| gk | 3,677 |
| zk | 1,603 |

---

## 2. 许可（Licence）

| 项 | 值 |
| --- | --- |
| SPDX 标识 | **MIT** |
| 名称 | MIT License |
| 版权行 | `Copyright (c) 2025 Linwei`（逐字来自源文件） |
| 读取地址 | `https://raw.githubusercontent.com/skywind3000/ECDICT/bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b/LICENSE` |
| 本地副本 | `tools/wordpack/cache/ECDICT-LICENSE` |
| 大小 | 1,063 字节 |

源 LICENSE 文件逐字开头（已完整核对，其余为 MIT 标准正文）：

```
MIT License

Copyright (c) 2025 Linwei

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

MIT 允许在应用内再分发，但**要求保留上述版权声明与许可声明**。因此：

* 每个词条的 `license` 字段写为 `MIT`，`source` 字段写为仓库 + 文件 + commit；
* 应用在词库详情中展示 `source` / `license`，即构成「随附声明」；
* 若产品后续要发布 binary，应确保 MIT 声明随发行物一起提供（当前资产内的
  `source` / `license` 字段已满足最低要求，但建议在「关于」页面一并展示完整 MIT 文本）。

---

## 3. 候选来源逐一核查结果（含被否决者）

按要求顺序核查了 4 个候选仓库。结论如下。

### 3.1 `qwerty-learner/qwerty-learner` — **否决**

* 该 URL 现已 404：仓库已改名为 **`RealKai42/qwerty-learner`**（23,351 stars）。
* GitHub API 返回的许可证为 **GPL-3.0**，并非任务描述中所说的 MIT。已实际下载
  `LICENSE`（35,149 字节）确认正文为 `GNU GENERAL PUBLIC LICENSE Version 3`。
* 任务预期的 `public/dicts/CET4_1.json`、`CET6_1.json` **不存在**；实际文件名是
  `CET4_T.json`（目录内 380 个文件）。
* **否决理由**：GPL-3.0 是强 copyleft，不是「MIT / Apache-2.0 / CC0 / CC-BY / 公有领域」中的任何一项。
  把 GPL-3.0 数据以打包形式内嵌进本应用，会引发对整个应用的分发许可传染性争议。
  在许可证链不清晰的情况下不予采用。
* 附带说明：即使忽略 GPL 问题，`qwerty-learner` 的词典文件本身也被其 README 描述为收录自多方
  整理的词表，上游来源不可考。

### 3.2 `KyleBing/english-vocabulary` — **数据质量最好，但因许可链不清晰而否决**

* 许可证：**BSD-3-Clause**（已下载 `LICENSE`，1,500 字节，`Copyright (c) 2022-2026, KyleBing`）。
  BSD-3-Clause 属于宽松许可，本身允许再分发。
* 数据质量实测（`full_line_jsonl/sentence/正序/四级.jsonl`、`六级.jsonl`）**明显优于 ECDICT**：

| 指标 | 四级 | 六级 |
| --- | ---: | ---: |
| 词条数 | 7,508 | 5,651 |
| 有 `us` 音标 | 7,383 | 5,569 |
| 有 `uk` 音标 | 7,442 | 5,601 |
| 有 `translations` | 7,508 | 5,651 |
| 有 `type`（词性） | 7,508 | 5,650 |
| 有 `phrases`（搭配） | 6,257 | 4,169 |
| 有 `sentences`（例句） | 7,344 | 5,520 |

* **否决理由（关键）**：该仓库 README 自述数据血缘为
  `kajweb/dict` → fork → `KyleBing/dict` → 简化 → 本库。
  经 GitHub API 核查，**`kajweb/dict` 与 `KyleBing/dict` 两个上游仓库都没有声明任何许可证**
  （`license: None`）。也就是说，`KyleBing/english-vocabulary` 只能为其**自身新增部分**授权，
  其**继承的词典数据**的再分发许可在上游是缺失的。
  仅因为一个仓库「包含」CET 词表，并不等于它有权再分发这些词表——此处 7,508 / 5,651 条
  词条、音标与例句的原始权利来源无法追溯，因此**不采用**。

### 3.3 `mahavivo/english-wordlists` — **否决（无许可证）**

* GitHub API 与仓库根目录均**没有任何 LICENSE 文件**，`license: None`。
* 仓库确有 `CET4_edited.txt`、`CET6_edited.txt`，但其 README 自述来源为
  「大学英语四级词汇表内容提取自 `http://fl.gzu.edu.cn/...pdf`」以及
  「整理自《全国大学英语四、六级考试大纲》2016 年版（`http://www.cet.edu.cn/file_2016_1.pdf`）」
  ——即**从第三方 PDF/官方大纲中提取**，并非作者原创。
* **否决理由**：无许可证 = 默认保留全部权利，明确**禁止**再分发；且内容来源指向第三方出版物，
  许可链完全断裂。这是最明确应当拒绝的候选。

### 3.4 `skywind3000/ECDICT` — **✅ 采用**

* 许可证：**MIT**（已逐字核对，见第 2 节）。明确允许在应用内再分发。
* 自带 `tag` 考试标签，可直接、可追溯地筛出 CET-4 / CET-6 词表（见第 1 节）。
* 数据质量局限：只有**单一音标**（不是 US/UK 双音标）、**没有例句**、**没有搭配**。
  按任务硬性规则 1，这些字段一律留空，**不自行编造**。
* 选它的核心理由：**许可链清晰且可核验**（单一仓库、单一 LICENSE、有明确版权人与年份），
  而 3.2 那个数据更漂亮的候选，其数据血缘无法授权。数据质量让位于可再分发性。

### 3.5 候选对比汇总

| 候选 | 许可证（实测） | 是否有 CET4/CET6 数据 | 结论 |
| --- | --- | --- | --- |
| `qwerty-learner`（现 `RealKai42/qwerty-learner`） | GPL-3.0（非 MIT） | 有（`CET4_T.json`/`CET6_T.json`） | 否决：copyleft 传染性 |
| `KyleBing/english-vocabulary` | BSD-3-Clause | 有（质量最佳） | 否决：上游 `kajweb/dict`/`KyleBing/dict` 无许可证，数据许可链断裂 |
| `mahavivo/english-wordlists` | **无** | 有 | 否决：无许可证 = 禁止再分发；来源为第三方 PDF |
| `skywind3000/ECDICT` | **MIT** | 有（`tag` 标注） | **采用** |

---

## 4. 转换步骤

对 `ecdict.csv` 逐行流式处理（`tools/wordpack/build_packs.py`），每个 level 独立一遍：

1. **筛选**：保留 `tag` 列空格分词后含 `cet4` 的行生成 `cet4.json`，含 `cet6` 的行生成 `cet6.json`。
   两个 level 各自独立挑选，**不做「六级去掉四级」的差集**（见第 6 节重叠说明）。
   这一点是刻意的：ECDICT 对 `abandon` 一类词同时打了 `cet4` 和 `cet6` 两个标签，
   说明按该来源的判断它同属两个大纲；若在此处做差集，就是在**用本项目的推断覆盖源数据的标注**。
   应用本身以 `level` 区分两个词库、且学习进度按 `level + 单词` 独立记录，因此重复词条不会互相干扰。
2. **释义切分**：把 `translation` 单元格按换行切成 1..N 条。少数行内含字面量
   `\n`（反斜杠 + n）而不是真实换行，脚本把两种写法都当作分隔符处理，避免把多条释义粘成一条。
3. **词性提取**：对每条释义组匹配行首词性标记 `^([A-Za-z]{1,6})\.\s*(.+)$`
   （`n.` `v.` `vt.` `vi.` `a.` `adv.` `prep.` …）。**仅当整个词条只出现一种词性标记时**，
   才把该标记从释义文本中剥离并写入 `partOfSpeech`；若同一词条混用多种词性标记，则**不剥离**，
   释义保持原样（含标记），`partOfSpeech` 留空。这样做是为了不丢信息、不做不可靠的猜测。
4. **音标**：直接取 `phonetic` 列原值；源值不带斜杠，因此在首尾各补一个 `/`（不修改内部字符）。
   由于 ECDICT 只有单一音标，`phonetic` 存该值，`phoneticUs` / `phoneticUk` 均为 `""`。
5. **不可用字段留空**：`exampleSentence`、`exampleTranslation`、`collocations` 源中不存在，
   全部输出为 `""` / `[]`。**没有编写任何自造释义、音标或例句。**
6. **词条去重**：按 `word.lower()` 在同一 level 内去重，冲突时保留信息量更大的一条
   （信息量 = 有无音标 + 释义条数 + 有无词性）。实测两个列表内均**无**重复，此步骤未实际删除任何行。
7. **id**：`<level 小写>-<4 位以上序号>`，序号为输出顺序中的 1-based 下标，因此稳定可复现。
8. **输出**：UTF-8、紧凑 JSON（`separators=(",", ":")`）、无尾逗号，`ensure_ascii=False` 保证中文
   不被转义。`PACK_INFO.json` 用 2 空格缩进以便人工阅读。

---

## 5. 实测校验数字

JSON 重新解析、逐对象键集合与 schema 完全一致、字节大小均为实测。

### 5.1 基本信息与 JSON 结构

| 指标 | CET-4 | CET-6 |
| --- | ---: | ---: |
| 文件 | `cet4.json` | `cet6.json` |
| 词条总数 | **3,849** | **5,407** |
| 文件字节 | 1,333,569 | 1,861,315 |
| 文件 MiB | 1.272 | 1.775 |
| JSON 可重新解析 | ✅ | ✅ |
| 包级键集合正确（`version`/`name`/`defaultLevel`/`source`/`license`/`words`） | ✅ | ✅ |
| 词条键集合与 schema 完全一致 | ✅（0 行异常） | ✅（0 行异常） |
| 每行 `level` 与本文件一致 | ✅ | ✅ |
| `defaultLevel` | `CET4` | `CET6` |

### 5.2 字段空值统计

| 字段 | CET-4 空 / 占比 | CET-6 空 / 占比 |
| --- | ---: | ---: |
| `phonetic` | 12 / 0.31% | 25 / 0.46% |
| `phoneticUs` | 3,849 / 100.00% | 5,407 / 100.00% |
| `phoneticUk` | 3,849 / 100.00% | 5,407 / 100.00% |
| `partOfSpeech` | 1,861 / 48.35% | 2,352 / 43.50% |
| `meanings` | **0 / 0.00%** | **0 / 0.00%** |
| `exampleSentence` | 3,849 / 100.00% | 5,407 / 100.00% |
| `exampleTranslation` | 3,849 / 100.00% | 5,407 / 100.00% |
| `collocations` | 3,849 / 100.00% | 5,407 / 100.00% |

反向（**非空**）比例，便于阅读：

| 字段 | CET-4 有值 | CET-6 有值 |
| --- | ---: | ---: |
| `phonetic` | 3,837（99.69%） | 5,382（99.54%） |
| `partOfSpeech` | 1,988（51.65%） | 3,055（56.50%） |
| `meanings` | 3,849（100%） | 5,407（100%） |

> `phonetic` 为空的 12 / 25 条，是源 `ecdict.csv` 中该单元格本身即为空，不是转换丢失——
> 清单可由 `fieldCoverage` 与源文件逐行比对复现。
> `partOfSpeech` 有值比例仅一半左右，原因是规则 3：混用多种词性标记的词条被有意留空。

### 5.3 词形与 id 健康检查

| 检查项 | CET-4 | CET-6 |
| --- | ---: | ---: |
| 小写形式在**同一列表内**重复的词条数 | **0** | **0** |
| 完全相同的词条重复数 | **0** | **0** |
| 重复 `id` | **0** | **0** |
| `id` 不符合 `<level>-NNNN` 格式 | **0** | **0** |
| `id` 为 level + 连续 1-based 序号 | ✅ | ✅ |
| `word` 含非 ASCII 字母 | **0** | **0** |
| `word` 含空白字符 | **0** | **0** |
| `word` 少于 2 个字符 | **0** | **0** |
| `word` 长于 32 个字符 | **0** | **0** |
| `word` 不含任何字母 | **0** | **0** |

因此**没有任何一项数据缺陷需要修复**：同列表重复为 0、空 `word` 为 0、JSON 结构完全合法。

### 5.4 释义规模（同时用于确认不超过应用解析上限）

| 指标 | CET-4 | CET-6 |
| --- | ---: | ---: |
| 释义条目总数 | 8,304 | 11,223 |
| 单词条释义数上限（实测） | 6 | 6 |
| 最长单条释义字符数 | 53 | 53 |
| 超过 512 字符的释义 | 0 | 0 |

对照应用自身的解析上限（`WordPackParser`：`MAX_MEANINGS = 12`、`MAX_TEXT_LENGTH = 512`、
`MAX_WORD_LENGTH = 64`、`MAX_FILE_BYTES = 8 MiB`、`MAX_RECORDS = 20,000`）：
两个资产分别 1.272 MiB / 1.775 MiB、3,849 / 5,407 条，**均在限值之内**。

### 5.5 两个列表之间的重叠

| 指标 | 值 |
| --- | ---: |
| 同时出现在 CET-4 与 CET-6 的词（小写比较） | **3,451** |
| 仅出现在 CET-4 | 398 |
| 仅出现在 CET-6 | 1,956 |
| 并集 | 5,805 |

重叠的前 20 个词：

```
abandon, abnormal, absence, absent, absolute, absorb, abstract, abuse, academic, academy,
accelerate, acceptance, access, accessory, accidental, accommodate, accommodation, accompany,
accord, accordance
```

这是**预期且合法**的：六级词表本身包含大量四级词。每个列表的词条 `level` 仍严格等于其所属文件
（`cet4.json` 全部为 `CET4`，`cet6.json` 全部为 `CET6`，已逐行验证）。

### 5.6 资产体积合计

| 文件 | 字节 |
| --- | ---: |
| `cet4.json` | 1,333,569 |
| `cet6.json` | 1,861,315 |
| `PACK_INFO.json` | 2,724 |
| **合计** | **3,197,608 字节（3.049 MiB）** |

---

## 6. 删除了什么、为什么

| 操作 | 数量（CET-4 / CET-6） | 原因 |
| --- | ---: | --- |
| 丢弃 `word` 为空的行 | 0 / 0 | 无有效单词，无法作为词条 |
| 丢弃 `translation` 为空（无释义）的行 | 0 / 0 | 应用要求 `meanings` 非空 |
| 同一列表内大小写重复而去重 | 0 / 0 | 实测无重复，未删除任何行 |
| 因 `word` 过长/含空白/无字母而删除 | 0 / 0 | 实测无此类行 |

**未删除任何带有有效数据的行。** 另外，以下内容**没有**被删除，只是按 schema 留空：
ECDICT 不提供的例句、例句翻译、搭配、US/UK 双音标。

与「从源中删除」相关的另一点说明：ECDICT 源文件里 `tag` 含 `cet4` 或 `cet6` 的行以外的一切
（其余约 76 万行，涉及 gre / toefl / ielts 等标签，以及未带任何考试标签的通用词）都被排除在本资产之外，
因为本资产只服务 CET-4 / CET-6。

---

## 7. 完整性与可复现性证明

**结论：相对于所选来源 ECDICT，内置词表对 CET-4 与 CET-6 都是完整的。**

证明方式是「源集合 ↔ 资产集合」的双向差集，而不是抽样或估计：

| level | 源中带该 tag 的行数 | 源中小写去重后词数 | 资产词条数 | 源中有但资产缺失 | 资产中有但源中没有 |
| --- | ---: | ---: | ---: | ---: | ---: |
| CET-4 | 3,849 | 3,849 | **3,849** | **0** | **0** |
| CET-6 | 5,407 | 5,407 | **5,407** | **0** | **0** |

即：ECDICT `ecdict.csv` 中每一个 `tag` 含 `cet4` 的词都在 `cet4.json` 中出现，反之亦然；
`cet6` 同理。两个方向差集均为空，故不存在遗漏或额外臆造的词条。

> 注意「完整」是**相对于 ECDICT 这个词表**而言，并非相对于教育部考试大纲的官方词表。
> 见第 8 节残余风险。

---

## 8. 残余风险

1. **「完整」的基准是 ECDICT，不是官方大纲。**
   第 7 节证明的是「资产 = ECDICT 的 cet4/cet6 标签集合」，并不能证明 ECDICT 的标签集合本身
   与教育部考试中心公布的 CET-4 / CET-6 词表逐词一致。ECDICT 是社区维护的词典数据库，
   其 tag 来源未在仓库中给出可核验的出处。
2. **考试词表的最终来源普遍不可考。**
   CET 词表在中文互联网上广泛流传，各种「四级词汇表」「六级核心词」版本条目数差异很大
   （3,800 ~ 7,500 不等）。ECDICT 收录了 3,849 / 5,407，KyleBing 收录了 7,508 / 5,651——
   两者对「六级词表」的规模判断相差约 2,000 词。这说明**不存在事实上的唯一权威清单**，
   本资产只对「ECDICT 的标签」负责。
3. **ECDICT 自身的上游也包含第三方素材。**
   其 README 自述数据来自「EDictAZ.txt」「一份四六级到 GRE 的词汇表」、为补齐音标而编写的爬虫、
   以及开源字典 `cdict-1.0-1.rpm` 等。MIT 许可证由仓库维护者（Linwei）对**整个仓库**给出，
   属于「上游仓库的声明」。也就是说，**本资产的许可链强度等于 ECDICT 维护者声明的强度**，
   本项目无法独立验证其每一行数据的原始权利。这一点必须向使用者如实披露。
4. **音标是金山词霸风格的纯 ASCII 转写，不是标准 IPA。**
   例如 `abandon` 为 `ә'bændәn`（源值），而标准 IPA 应为 `/əˈbændən/`。源中混用了多种字符集：
   `ә`(U+04D9, 西里尔字母 schwa)、`є`(U+0454, 西里尔字母 Ukrainian IE)、
   `θ`(U+03B8, 希腊字母 theta) 以及 `ɒ` `ʃ` `ʌ` `ʒ` `ɑ` `ŋ` `ð` `ɔ` 等拉丁扩展字符。
   本项目**原样保留**，未做任何规范化，因为改写音标等同于编造数据。若产品需要标准 IPA 或英美双音标，必须另行取得有清晰许可且
   提供 IPA 的数据源（3.2 的 KyleBing 有 IPA 与双音标，但许可链不合格）。
5. **`phoneticUs` / `phoneticUk` 在本次资产中恒为空字符串。**
   `PACK_INFO.json` 已用 `phoneticUsAvailable: false` / `phoneticUkAvailable: false` 明确标注，
   避免下游误以为可以按 US/UK 切换发音。应用现有的发音功能依赖 TTS，不受此影响。
6. **词性仅覆盖约一半词条。**
   这是「宁可留空也不猜」的直接后果：混用多种词性标记的词条（如 `abandon` 同时有 `vt.` 与 `n.`）
   `partOfSpeech` 留空，标记保留在 `meanings` 文本中。`meanings` 内容因此是可靠的，
   但 `partOfSpeech` 不是完整统计。
7. **源文件未随资产一起分发。**
   构建脚本从网络下载 62.9 MiB 的 `ecdict.csv`，该文件被缓存在 `tools/wordpack/cache/`
   （未纳入版本控制时应确认 `.gitignore`），应用内只带派生出的 3.05 MiB 资产。
   若要长期复现，需依赖第 1 节固定的 commit SHA；该 commit 若被 force-push 或删除则无法复现。

---

## 9. 复现方式

```bash
# 1) 下载源数据（urllib，无第三方依赖）
python tools/wordpack/download.py

# 2) 生成三个资产
python tools/wordpack/build_packs.py

# 3) 实测校验（重写 tools/wordpack/validation_report.json 并打印汇总）
python tools/wordpack/validate_packs.py
```

（Windows PowerShell 环境请先设置 `$env:PYTHONIOENCODING="utf-8"`，
且**不要**用 `Set-Content` / `Out-File` 写这些文件，会破坏 UTF-8 中文。）


---

## 附录 A · 迭代补充：音标记号转写（2026-10 迭代）

上游 ECDICT 的 `phonetic` 使用「金山风格」记号：绝大多数是真正的 IPA，但有**两个西里尔字母被当作
IPA 字母使用**，另用 ASCII 的 `'` 与 `:` 表示重音与长音。这会让 9,219 条音标中的约 6,300 条在界面上
显示为字形相近但编码错误的字符。

`tools/wordpack/normalize_phonetics.py` 对资产做了**纯记号转写**（不新增、不修改、不删除任何发音信息）：

| 原字符 | 转为 | 替换次数 |
| --- | --- | --- |
| U+04D9 CYRILLIC SMALL LETTER SCHWA（ә） | U+0259 LATIN SMALL LETTER SCHWA（ə） | 6,167 |
| U+0454 CYRILLIC SMALL LETTER UKRAINIAN IE（є） | U+025B LATIN SMALL LETTER OPEN E（ɛ） | 132 |
| U+0027 APOSTROPHE（重音） | U+02C8 MODIFIER LETTER VERTICAL LINE（ˈ） | 7,108 |
| U+003A COLON（长音） | U+02D0 MODIFIER LETTER TRIANGULAR COLON（ː） | 2,419 |

共 7,893 行被改写；转写后两个资产中**不再包含任何西里尔字符**（脚本实测 `remaining-cyrillic=0`），
并且 `validate_packs.py` 复跑后其余校验项（行数、重复、空字段比例、JSON 结构）全部不变。
逗号（用于分隔同一单词的不同读音，如 `/'kɒndʌkt, -dəkt/`）**刻意不改**，因为它是列表分隔符而非重音符号。

转写后的示例：`abandon /əˈbændən/`、`ability /əˈbiliti/`、`abbreviation /ə.briːviˈeiʃən/`。

自动化守护：`app/src/test/.../BuiltInPackAssetTest.kt` 中的 `phonetics are ipa - no cyrillic look alikes survive`
会遍历两个资产的每一条音标，逐字符断言其 Unicode 区块不是 CYRILLIC。

**残余风险（仍未解决）**：记号已是 IPA，但 ECDICT 的**音位体系**仍是金山风格（例如部分元音用 `i`
表示 /i/ 而非 /ɪ/）。若要严格 IPA 音位，需要另找许可清晰的双口音音标数据源；本轮未采用
`KyleBing/english-vocabulary` 的音标正是因为其权利链不可追溯（见第 1 节候选比较）。