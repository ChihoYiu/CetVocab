# 发布签名（Release signing）

本文说明如何为「薄荷单词 · CetVocab」生成签名包，以及**为什么密钥不会、也绝不能出现在仓库里**。

## 1. 为什么不提交密钥库

* 密钥库（`.jks`）一旦泄露，任何人都能伪造你的应用更新；而 Android 要求**同一应用的所有版本必须用同一密钥签名**，泄泄露后无法“换一把钥匙”——只能换包名重新发布，老用户无法平滑升级。
* 因此本项目：
  * `.gitignore` 忽略 `keystore.properties`、`*.jks`、`*.keystore`、`*.p12`、`*.pfx`；
  * `app/build.gradle.kts` 只从**环境变量**或**本地 `keystore.properties`** 读取签名信息，代码里没有任何口令；
  * 找不到签名信息时，release 构建保持**未签名**（不会失败，也不会偷偷用 debug 密钥签名）。

## 2. 生成密钥库（只做一次，务必备份）

```bash
keytool -genkeypair -v \
  -keystore cetvocab-release.jks \
  -alias cetvocab \
  -keyalg RSA -keysize 4096 -validity 10000
```

要点：

* **备份**：把 `.jks` 和口令放进密码管理器 / 加密备份。丢失 = 无法给已发布的应用发更新。
* 口令建议 ≥ 16 位随机字符，不要与任何账号口令相同。
* `-validity 10000` ≈ 27 年；Google Play 的推荐做法是同时启用 Play 应用签名（那是另一套密钥，与本仓库无关）。

## 3. 本机出正式包

在仓库根目录创建 `keystore.properties`（已被 gitignore）：

```properties
storeFile=cetvocab-release.jks
storePassword=你的密钥库口令
keyAlias=cetvocab
keyPassword=你的密钥口令
```

也可以完全不建文件，改用环境变量（推荐给脚本/CI）：

| 环境变量 | 含义 |
| --- | --- |
| `CETVOCAB_KEYSTORE_FILE` | 密钥库文件的绝对路径 |
| `CETVOCAB_KEYSTORE_PASSWORD` | 密钥库口令 |
| `CETVOCAB_KEY_ALIAS` | 密钥别名 |
| `CETVOCAB_KEY_PASSWORD` | 密钥口令 |

然后：

```bash
./gradlew :app:assembleRelease -x lintVitalRelease
# 产物：app/build/outputs/apk/release/app-release.apk（已签名）
```

验证签名（把 `<build-tools>` 换成本机版本）：

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

不配任何签名信息时，同一命令仍会成功，但产物是 `app-release-unsigned.apk`——**这种包在 Android 上无法安装**，只适合做构建检查。

## 4. 在 CI 里出正式包（推荐）

仓库已内置 [`.github/workflows/release.yml`](../.github/workflows/release.yml)：打 tag（如 `v1.0.1`）时自动构建签名包并上传到 Release。

需要在 GitHub 仓库设置 → Secrets and variables → Actions 里添加：

| Secret | 怎么得到 |
| --- | --- |
| `KEYSTORE_BASE64` | Linux/macOS：`base64 -w0 cetvocab-release.jks`；Windows PowerShell：`[Convert]::ToBase64String([IO.File]::ReadAllBytes("cetvocab-release.jks"))` |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | 别名（如 `cetvocab`） |
| `KEY_PASSWORD` | 密钥口令 |

工作流的行为：

* 密钥库在运行器上解码到 `$RUNNER_TEMP`（**不在工作区**，因此不会被 `actions/cache` 或 artifact 打包带走），任务结束前 `rm -rf` 清理；
* 没有配置 `KEYSTORE_BASE64` 时，只跑测试并给出警告，**不会失败**（fork 的人不会因此看到红叉）；
* 打包后会用 `apksigner verify --print-certs` 校验签名，避免“看起来成功其实没签上”。

## 5. 检查你是否误提交了密钥

```bash
git ls-files | grep -Ei '\.(jks|keystore|p12|pfx)$|keystore\.properties'
```

输出应为空。若已经提交过：**立即作废该密钥库并重新生成**——从 Git 历史里删除文件并不能阻止已经克隆的人拿到它。
