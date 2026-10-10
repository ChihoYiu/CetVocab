# 第三方内容与许可 / Third-party notices

本项目**自己的代码**以 [MIT 许可](LICENSE)发布（Copyright (c) 2026 ChihoYiu）。
但仓库与安装包里还包含来自其他项目的内容，它们的许可如下。这些声明必须随分发保留。

---

## 1. 内置词库数据：ECDICT（MIT）

`app/src/main/assets/wordpacks/cet4.json`、`cet6.json`、`PACK_INFO.json` 是由 **ECDICT** 生成的派生数据
（四级 3,849 词 / 六级 5,407 词），随应用一起分发，因此按 MIT 的要求在此附上原始版权声明与许可正文。

来源与固定版本：

| 项 | 值 |
| --- | --- |
| 仓库 | <https://github.com/skywind3000/ECDICT> |
| 文件 | `ecdict.csv` |
| 固定提交 | `bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b` |
| 许可 | MIT |
| 版权 | Copyright (c) 2025 Linwei |

生成与校验脚本位于 `tools/wordpack/`，实测数字见
[docs/WORD_PACK_VALIDATION.md](docs/WORD_PACK_VALIDATION.md)。
音标字段做了一次**纯记号转写**（把西里尔字母 ә/є 与 ASCII 的 `'`/`:` 转成 IPA 的 ə/ɛ/ˈ/ː），
没有增删任何发音信息，脚本为 `tools/wordpack/normalize_phonetics.py`。

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

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 2. 回退用的极小演示词库

`app/src/main/java/com/cetvocab/app/data/seed/BuiltInWordPacks.kt` 中的 188 条词目是**本项目自撰**的示例数据
（仅在词库资产缺失时作为兜底），以 CC0-1.0（公共领域贡献）发布。

---

## 3. 未采用的候选数据源（仅记录，仓库不含其数据）

选型阶段比较过的候选，因许可或权利链问题**未采用**，仓库中不包含它们的任何数据；
相关下载仅存在于被 `.gitignore` 忽略的本地缓存目录：

| 候选 | 许可 | 未采用原因 |
| --- | --- | --- |
| `RealKai42/qwerty-learner` | GPL-3.0 | 传染性许可，不适合本项目的分发方式 |
| `mahavivo/english-wordlists` | 无 LICENSE | 无法确认再分发权利 |
| `KyleBing/english-vocabulary` | BSD-3-Clause | 上游 `kajweb/dict`、`KyleBing/dict` 均无许可，权利链不可追溯 |

---

## 4. 朗读（TTS）

朗读由**设备自带的系统文字转语音服务**完成，本仓库**不分发任何第三方音频**，
也不包含任何云服务凭据。历史上曾实现过基于 Wikimedia Commons / Wiktionary 真人录音的发音模块，
该模块已在本项目的迭代中被删除；当时的线上实测记录保留在
[docs/PRONUNCIATION_VERIFICATION.md](docs/PRONUNCIATION_VERIFICATION.md) 供追溯。

---

## 5. 开源依赖（Apache-2.0）

应用通过 Gradle 依赖以下库，均为 Apache License 2.0，其许可正文由其自身构件提供：

* AndroidX / Jetpack：Compose（UI、Material 3）、Navigation、Lifecycle、Room、DataStore、WorkManager、core-ktx
* Kotlin 标准库、`kotlinx-coroutines`、`kotlinx-serialization`

许可证与完整版本号见 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)。

---

## 6. 考试相关声明

本项目与任何考试机构（含 CET 主办方）**不存在合作关系**，也不声称存在。
词库的「四级 / 六级」归属直接取自 ECDICT 自带的 `tag` 字段，并非本项目对考试大纲的解读。
应用不承诺提分、押题或通过考试。
