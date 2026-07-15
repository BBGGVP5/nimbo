import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

val signingProperties = Properties().apply {
    val signingFile = rootProject.file("signing.properties")
    if (signingFile.isFile) {
        signingFile.inputStream().use(::load)
    }
}

fun releaseProperty(name: String): String? =
    providers.gradleProperty(name).orNull?.trim()?.takeIf(String::isNotEmpty)
        ?: signingProperties.getProperty(name)?.trim()?.takeIf(String::isNotEmpty)

val releaseStoreFile = releaseProperty("nimboReleaseStoreFile")
val releaseStorePassword = releaseProperty("nimboReleaseStorePassword")
val releaseKeyAlias = releaseProperty("nimboReleaseKeyAlias")
val releaseKeyPassword = releaseProperty("nimboReleaseKeyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it != null }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.danila.nimbo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.danila.nimbo"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(checkNotNull(releaseStoreFile))
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // A release without explicit credentials stays unsigned. This is safer
            // than silently signing a distributable build with the debug key.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
            isJniDebuggable = false
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
        }
    }

    // Build APKs per ABI
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    // Приложение использует только ru/en (через ui/i18n/Translate.kt), но
    // транзитивные зависимости (AppCompat, ML Kit, CameraX, и т.д.) тянут
    // переводы под ~70 локалей. Фильтр обрезает их в APK — экономия ощутимая,
    // а на UI не влияет, потому что строки в приложении захардкожены
    // в t("ru","en"), а не в values-*.
    androidResources {
        localeFilters += listOf("ru", "en")
    }

    // Блок информации о зависимостях добавляется в APK для Play Store-аналитики.
    // Без публикации в Play он не нужен, а ~10 КБ — сэкономим.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            // Сжимаем native-библиотеки внутри APK для уменьшения размера ABI-apk.
            // На minSdk 29 это безопасно, но установка может быть чуть дольше.
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                // Build-time метаданные, никогда не читаются в рантайме.
                "META-INF/maven/**",
                "META-INF/proguard/**",
                "META-INF/com/android/build/gradle/**",
                "META-INF/androidx/**/LICENSE.txt",
                // Bin-данные kotlinx-coroutines-debug — нужны только когда
                // подключают агент отладки корутин, в релизе бесполезны.
                "DebugProbesKt.bin",
                // README/changelogs, попавшие внутрь META-INF (gson, okhttp и др.).
                "META-INF/*.md",
                "META-INF/CHANGES",
                "META-INF/README.md",
                // .proto-исходники (schema-файлы) из транзитивных зависимостей.
                // Рантайм работает со скомпилированными классами, эти файлы
                // нужны только для генерации кода — в APK они мёртвый груз.
                "**/*.proto"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildToolsVersion = "37.0.0"
    ndkVersion = "28.2.13676358"

    sourceSets {
        getByName("main") {

        }
    }
}

// Устанавливаем имя до упаковки, через публичный Variant API AGP. Поэтому
// Generate Signed APK в Android Studio и Gradle создают сразу нужные файлы,
// а не штатные app-*.apk с дополнительными копиями рядом.
androidComponents {
    val version = android.defaultConfig.versionName ?: "1.0.0"
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?.replace('-', '_')
                ?: "universal"
            output.outputFileName.set("Nimbo_v${version}_${abi}_${variant.buildType}.apk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.window)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.test)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    testImplementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.animation:animation:1.10.5")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    // Per-app language switching via AppCompatDelegate.setApplicationLocales
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(files("libs/libxray.aar"))
    implementation("sh.calvin.reorderable:reorderable:3.0.0")
    // CameraX
    implementation("androidx.camera:camera-core:1.5.3")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
// ML Kit QR — play-services variant: модель barhopper/tflite (~6 МБ)
    // загружается из установленного Google Play Services вместо bundling в APK.
    // API (com.google.mlkit.vision.barcode.*) совпадает с bundled-версией,
    // поэтому код QrScannerScreen не меняется. На устройствах без GPS QR-сканер
    // не запустится — пользователю придётся вставлять ссылки подписки руками.
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
}
