import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Release signing, configured **without ever putting a secret in the repository**.
 *
 * Two sources are supported, in this order:
 *  1. environment variables — what CI should use (GitHub Actions secrets);
 *  2. a local `keystore.properties` at the repository root — convenient on a developer machine.
 *
 * `keystore.properties`, `*.jks` and `*.keystore` are all in `.gitignore`, and this file never
 * prints a password. When neither source is present the release build simply stays **unsigned**
 * (the previous behaviour), so a fresh clone still builds and tests successfully — which is exactly
 * what a contributor who has no signing key needs.
 *
 * See docs/RELEASE_SIGNING.md for how to create the key and wire up the secrets.
 */
data class ReleaseSigning(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun readReleaseSigning(rootDir: java.io.File): ReleaseSigning? {
    fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    // 1. Environment variables (CI /脚本).
    val envStore = env("CETVOCAB_KEYSTORE_FILE")
    if (envStore != null) {
        return ReleaseSigning(
            storeFile = envStore,
            storePassword = env("CETVOCAB_KEYSTORE_PASSWORD").orEmpty(),
            keyAlias = env("CETVOCAB_KEY_ALIAS").orEmpty(),
            keyPassword = env("CETVOCAB_KEY_PASSWORD").orEmpty(),
        )
    }

    // 2. Local properties file (developer machine).
    val propertiesFile = rootDir.resolve("keystore.properties")
    if (!propertiesFile.isFile) return null
    val properties = Properties().apply { propertiesFile.inputStream().use { load(it) } }
    val file = properties.getProperty("storeFile")?.takeIf { it.isNotBlank() } ?: return null
    return ReleaseSigning(
        // A relative path is resolved against the root project, so the file can live anywhere.
        storeFile = rootDir.resolve(file).absolutePath,
        storePassword = properties.getProperty("storePassword").orEmpty(),
        keyAlias = properties.getProperty("keyAlias").orEmpty(),
        keyPassword = properties.getProperty("keyPassword").orEmpty(),
    )
}

val releaseSigning: ReleaseSigning? = readReleaseSigning(rootProject.projectDir)

android {
    namespace = "com.cetvocab.app"
    // API 37 is not published in the stable SDK channel yet, so 36 is the newest
    // stable compile target while the app still ships minSdk 26 / targetSdk 36.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cetvocab.app"
        minSdk = 26
        targetSdk = 36
        // 1.0.1 是首个使用正式签名密钥发布的版本；1.0.0 是调试签名的预览包。
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.storeFile)
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword
                // Both schemes: v1 keeps Android 8.0 happy, v2/v3 are what newer systems verify.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only attach a signing config when a key is actually available; otherwise the build
            // stays unsigned instead of failing.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources to run Compose/DB tests on the JVM.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { test ->
                // Tests are small; a bounded heap keeps the suite runnable on machines that are
                // already busy and makes memory problems fail loudly instead of swapping.
                test.maxHeapSize = "512m"
                test.jvmArgs("-XX:+UseSerialGC", "-Dfile.encoding=UTF-8")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

ksp {
    // Room schema export keeps every database version under version control so that
    // real migrations can be written and tested (never destructive migration).
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Room schema files are needed as assets by MigrationTestHelper inside unit tests.
android.sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
