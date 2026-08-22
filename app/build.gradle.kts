import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.assemblylinetycoon"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.assemblylinetycoon"
        minSdk = 26          // Android 8.0: adaptive-иконки, RuStore Billing (24+), Yandex Ads (21+)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables { useSupportLibrary = true }

        // Игре не нужны локали кроме русской и английской — экономим вес APK.
        resourceConfigurations += setOf("ru", "en")
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
            // signingConfig подключается в CI из GitHub Secrets, ключ в репозитории не хранится.
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
            isIncludeAndroidResources = false
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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
