# 贡献指南

欢迎提 Issue 与 PR。这个项目面向 Android 8.0 及以上的真实学习场景，改动请优先考虑**稳定性**与**数据安全**。

## 最快上手

```bash
git clone https://github.com/ChihoYiu/CetVocab.git
cd CetVocab
./gradlew :app:testDebugUnitTest     # 全部单元测试（约 370 个）
./gradlew :app:assembleDebug        # 调试包
```

* JDK 17+（CI 用 21）、Android SDK：platform 36 与 build-tools 36.0.0。
* `local.properties` 里写 `sdk.dir=...`，或设置 `ANDROID_HOME`。**该文件不要提交**（已在 .gitignore）。
* Windows 上**路径不能含中文等非 ASCII 字符**，否则 Android Gradle 插件会拒绝构建。

## 提交前请确认

1. `./gradlew :app:testDebugUnitTest` 全绿；新增行为要**同时新增测试**。
2. 不要在源码里写入任何密钥、口令、Token；签名材料一律通过环境变量或本地 `keystore.properties`
   （见 [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md)）。
3. 不要新增第三方依赖，除非在 PR 描述里说明理由与体积影响（当前依赖见 `gradle/libs.versions.toml`）。
4. 不要使用破坏性数据库迁移（`fallbackToDestructiveMigration`），改 schema 必须写 Migration 并补测试。
5. UI 改动请在真机或模拟器上看一眼：浅色 / 深色主题、200% 字体缩放、横竖屏。

## 代码约定

* 分层：`ui/` 只做呈现，`domain/` 是纯 Kotlin（无 Android 依赖，便于单测），`data/` 负责存取。
  Composable 不直接碰 DAO / DataStore / 文件系统；`NavController` 不传进页面。
* 状态：ViewModel 暴露不可变 `StateFlow`；UI 状态类加 `@Immutable`（否则 Compose 无法跳过重组）。
* 文案：中文、具体、不夸张。统计类文案要与 `StatDefinition` 一致；**不要编造数据**：
  上游没有的字段就留空并在界面隐藏。
* 注释与文档默认写中文，代码标识符用英文。
* 测试用真实的语义节点与 `testTag`，不要靠坐标点击。

## PR 怎么提

* 一个 PR 做一件事，标题写清「改了什么 + 为什么」。
* 描述里请包含：**根因**（如果是修 bug）、改动范围、验证方式（跑了什么、结果如何）、
  以及未解决或需要权衡的地方。
* 如果改动影响用户可见行为，请同步更新 `README.md` 或 `docs/USER_GUIDE.md`。
* CI 会在 PR 上自动跑测试与构建；失败请先修 CI 再看代码。

## 数据与许可

* 内置词库来自 [ECDICT](https://github.com/skywind3000/ECDICT)（MIT），派生数据的生成与校验脚本在
  `tools/wordpack/`。**新增或替换词库数据必须**：更新 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)、
  说明来源与许可、并给出可复现的生成脚本与校验数字。
* 不要引入许可不明或与 MIT / Apache-2.0 不兼容的内容（GPL 代码、无许可数据、来源不明的音频）。
* 激励语句请**原创**；确实要引用外部名言时，只能使用明确属于公共领域的内容，并保留作者与出处。

## 行为准则

参与本项目即表示同意遵守 [行为准则](CODE_OF_CONDUCT.md)。
