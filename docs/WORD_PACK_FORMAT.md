# 词库导入格式说明

应用支持导入 **JSON** 与 **CSV** 两种格式的自有词库。导入前会做格式校验与预览，
确认后在一个数据库事务中写入；事务失败会整体回滚，不会破坏已有数据。

* 文件大小上限：**8 MiB**
* 记录条数上限：**20000 条**
* 释义最多 12 条，搭配最多 12 条，单词长度不超过 64 字符
* 同名单词（按 `level + 单词小写去空格` 判定）在文件内保留字段更完整的一条；
  与数据库已有记录冲突时**保留原有词条行 ID**，只更新内容，因此学习进度、错题与考试快照都不会丢失

## 字段定义

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `word` | ✅ | 英文单词，必须包含字母 |
| `meanings` | ✅ | 中文释义数组（CSV 中用 `\|` 或 `;` 分隔） |
| `phonetic` |  | 音标，建议包含斜杠，如 `/əˈbændən/` |
| `partOfSpeech` |  | 词性，如 `v.` `n.` `adj.` |
| `exampleSentence` |  | 英文例句 |
| `exampleTranslation` |  | 例句中文翻译 |
| `collocations` |  | 常见搭配数组 |
| `level` |  | `CET4` / `CET6` / `OTHER`，缺省使用应用当前词库级别 |
| `source` |  | 来源说明，建议填写，会显示在单词详情中 |
| `license` |  | 使用许可，建议填写，会显示在单词详情中 |
| `audioUri` |  | 可选音频地址（仅记录，离线功能不依赖它） |
| `id` |  | 可选，文件内的稳定编号，导出时可原样带回 |

> 请只导入你拥有合法使用权的词库。应用不捆绑任何商业词库、音频或第三方内容，
> 也不对导入内容的版权负责。

## JSON

支持两种形态：带元信息的对象，或直接是记录数组。

```json
{
  "version": 1,
  "name": "我的四级词库",
  "defaultLevel": "CET4",
  "source": "自建 / 已获授权",
  "license": "CC BY 4.0",
  "words": [
    {
      "id": "w-0001",
      "word": "abandon",
      "phonetic": "/əˈbændən/",
      "partOfSpeech": "v.",
      "meanings": ["放弃；抛弃", "遗弃"],
      "exampleSentence": "He had to abandon the plan because of the storm.",
      "exampleTranslation": "由于暴风雪，他不得不放弃这个计划。",
      "collocations": ["abandon a plan", "abandon hope"],
      "level": "CET4",
      "source": "自建 / 已获授权",
      "license": "CC BY 4.0"
    }
  ]
}
```

完整可用的示例文件：[`samples/word-pack-sample.json`](samples/word-pack-sample.json)

> 内置词库（`app/src/main/assets/wordpacks/`）比这个格式多一个**包级**字段 `exampleSource`，
> 用来说明例句的来源与许可（ECDICT 不含例句，例句来自 Tatoeba，CC BY 2.0 FR，见
> [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md) 与
> [WORD_PACK_VALIDATION.md](WORD_PACK_VALIDATION.md) 附录 B）。
> 你自己导入的文件**不需要**该字段，解析器会忽略未知的包级键。

## CSV

首行必须是表头。列名大小写不敏感，允许 `snake_case` 与 `camelCase`；
中文表头（如 `单词`、`释义`、`音标`、`词性`、`例句`、`搭配`、`级别`、`来源`、`许可`）同样可识别。

```csv
word,phonetic,partOfSpeech,meanings,exampleSentence,exampleTranslation,collocations,level,source,license
abandon,/əˈbændən/,v.,放弃；抛弃,He had to abandon the plan.,他不得不放弃这个计划。,abandon a plan|abandon hope,CET4,自建,CC0-1.0
absorb,/əbˈzɔːb/,v.,吸收；理解,Plants absorb water through their roots.,植物通过根吸收水分。,absorb water|absorb knowledge,CET4,自建,CC0-1.0
```

* 含逗号、引号或换行的单元格请用双引号包裹，内部双引号写两次（RFC 4180）
* 多值字段（释义、搭配）用 `|`、`;` 或换行分隔

完整可用的示例文件：[`samples/word-pack-sample.csv`](samples/word-pack-sample.csv)

## 出错时会怎样

* 单条记录缺单词或缺释义 → 该行被跳过，并在预览中列为「问题行」（第几行、哪个字段、原因）
* JSON 类型错误 / 截断 / CSV 列数不齐 → 返回可读的错误提示，不崩溃
* 文件过大 / 记录过多 → 直接拒绝并提示拆分
* 没有任何有效记录 → 提示「没有可导入的有效记录（共 N 行，其中 M 行有问题）」
* 写入过程中数据库异常 → 事务回滚，并在「我的 → 最近导入记录」中标记为「导入失败，数据已回滚」
