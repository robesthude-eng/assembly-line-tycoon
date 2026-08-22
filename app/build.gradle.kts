import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------
// Версионирование
// ---------------------------------------------------------------------------
// Версия хранится в version.properties, а не в этом файле: так её может менять
// задача bumpPatch и читать CI, не разбирая Kotlin DSL регулярками.
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply { FileInputStream(versionFile).use(::load) }

fun versionProp(key: String): Int = versionProps.getProperty(key)?.trim()?.toIntOrNull()
    ?: error("В version.properties нет числового значения $key")

val appVersionMajor = versionProp("VERSION_MAJOR")
val appVersionMinor = versionProp("VERSION_MINOR")
val appVersionPatch = versionProp("VERSION_PATCH")

// В CI номер сборки берётся из счётчика прогонов GitHub Actions: он монотонно
// растёт и не зависит от того, что закоммичено. Локально — значение из файла.
val appBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    ?: versionProp("VERSION_BUILD")

/**
 * versionCode должен строго возрастать, иначе магазин и сам Android откажутся
 * ставить обновление поверх установленного. Схема разрядов:
 * мажор × 10 000 000 + минор × 100 000 + патч × 1 000 + номер сборки.
 * Запас: 999 сборок на патч, 99 патчей на минор, 99 миноров на мажор.
 */
val appVersionCode = appVersionMajor * 10_000_000 +
    appVersionMinor * 100_000 +
    appVersionPatch * 1_000 +
    appBuildNumber

val appVersionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"

// ---------------------------------------------------------------------------
// Подпись
// ---------------------------------------------------------------------------
// Ключ в репозиторий не попадает. Локально путь и пароли берутся из
// keystore.properties (он в .gitignore), в CI — из переменных окружения,
// которые workflow наполняет из GitHub Secrets.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use(::load)
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

// Путь резолвится от корня проекта, а не от модуля app: иначе относительный
// путь из keystore.properties молча не найдётся и APK выйдет неподписанным.
val keystoreFile = signingValue("storeFile", "KEYSTORE_FILE")?.let { path ->
    File(path).takeIf(File::isAbsolute) ?: rootProject.file(path)
}
val hasSigningConfig = keystoreFile?.exists() == true

android {
    namespace = "com.example.assemblylinetycoon"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.assemblylinetycoon"
        minSdk = 26          // Android 8.0: adaptive-иконки, RuStore Billing (24+), Yandex Ads (21+)
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables { useSupportLibrary = true }

        // Игре не нужны локали кроме русской и английской — экономим вес APK.
        resourceConfigurations += setOf("ru", "en")
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
                // v1 нужен для Android 8, v2/v3 — быстрая проверка на новых версиях.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true          // R8: обязательно для веса и производительности
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Без ключа сборка не падает, а остаётся неподписанной: так локальный
            // ./gradlew assembleRelease работает у любого разработчика.
            signingConfig = if (hasSigningConfig) signingConfigs.getByName("release") else null
        }
    }

    // Каталог исходников kotlin/ вместо java/ — договорённость проекта.
    sourceSets {
        getByName("main") { kotlin.srcDirs("src/main/kotlin") }
        getByName("test") { kotlin.srcDirs("src/test/kotlin") }
        getByName("androidTest") { kotlin.srcDirs("src/androidTest/kotlin") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // Отключено осознанно: игра не использует эти механизмы, сборка быстрее.
        viewBinding = false
        dataBinding = false
        aidl = false
        renderScript = false
        shaders = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    bundle {
        // AAB для RuStore: не режем по языкам, магазин отдаёт единый набор.
        language { enableSplit = false }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // Проверка «устаревшая версия Yandex Ads SDK» не должна ломать локальную сборку.
        disable += "MobileAdsSdkOutdatedVersion"
        // Сработка приходит из Glide, который транзитивно тянет Yandex Ads:
        // NotificationTarget умеет слать уведомления, поэтому lint требует
        // POST_NOTIFICATIONS. Игра уведомлений не отправляет, а лишнее
        // разрешение — это и вопрос на модерации RuStore, и отказы игроков.
        disable += "NotificationPermission"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Compose-тестам нужны настоящие ресурсы и тема приложения.
            isIncludeAndroidResources = true
            all {
                // Roborazzi пишет кадр в файл только при явном разрешении;
                // флаг нужен именно тестовой JVM, а не Gradle.
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all",
        )
    }
}

composeCompiler {
    // Метрики компилятора Compose: помогают ловить лишние рекомпозиции в игровом UI.
    reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
}

dependencies {
    // ── AndroidX / lifecycle ────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose)

    // ── Compose ─────────────────────────────────────────────────────────────
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ── Kotlin ──────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // ── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.androidx.datastore.preferences)  // настройки
    implementation(libs.androidx.datastore)              // типизированный снапшот GameState

    // ── Monetization ────────────────────────────────────────────────────────
    implementation(libs.yandex.mobileads)
    implementation(libs.rustore.billingclient)

    // ── Test ────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Compose-тесты гоняются в JVM через Robolectric: эмулятор в песочнице и
    // в CI недоступен, а проверять экран всё равно нужно на каждом коммите.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// ---------------------------------------------------------------------------
// Задачи управления версией
// ---------------------------------------------------------------------------
// Правило проекта: version.properties меняется только этими задачами.
// Ручная правка легко приводит к versionCode, который меньше уже
// опубликованного, — и обновление перестаёт устанавливаться.
//
// Реализация задачи лежит в buildSrc/: так она совместима с конфигурационным
// кэшем Gradle, который не умеет сериализовать ссылки на функции скрипта.

// Локальная копия ссылки на файл: внутри блока настройки задачи имя
// versionFile уже занято одноимённым свойством самой задачи.
val versionPropertiesFile = versionFile

fun registerBump(name: String, bumpPart: VersionPart, help: String) =
    tasks.register<BumpVersionTask>(name) {
        group = "versioning"
        description = help
        versionFile.set(versionPropertiesFile)
        part.set(bumpPart)
    }

registerBump("bumpBuild", VersionPart.BUILD, "Увеличить номер сборки: 0.1.0 (1) → 0.1.0 (2)")
registerBump("bumpPatch", VersionPart.PATCH, "Патч-версия: 0.1.0 → 0.1.1, счётчик сборок сбрасывается")
registerBump("bumpMinor", VersionPart.MINOR, "Минорная версия: 0.1.3 → 0.2.0")
registerBump("bumpMajor", VersionPart.MAJOR, "Мажорная версия: 0.9.1 → 1.0.0")

tasks.register("printVersion") {
    group = "versioning"
    description = "Показать версию, которую получит текущая сборка"
    // Значения захватываются в локальные переменные: лямбда не должна тянуть
    // за собой объект скрипта, иначе ломается конфигурационный кэш.
    val name = appVersionName
    val code = appVersionCode
    val signing = if (hasSigningConfig) "настроена" else "нет ключа, APK будет неподписанным"
    doLast {
        println("versionName = $name")
        println("versionCode = $code")
        println("подпись релиза = $signing")
    }
}
