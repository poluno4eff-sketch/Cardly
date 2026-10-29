plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val cardlyStorePath = System.getenv("CARDLY_KEYSTORE_FILE")
val cardlyStorePassword = System.getenv("CARDLY_KEYSTORE_PASSWORD") ?: providers.gradleProperty("cardlyKeystorePassword").orNull
val cardlyKeyAlias = System.getenv("CARDLY_KEY_ALIAS") ?: providers.gradleProperty("cardlyKeyAlias").orNull
val cardlyKeyPassword = System.getenv("CARDLY_KEY_PASSWORD") ?: providers.gradleProperty("cardlyKeyPassword").orNull
val cardlyStoreFile = cardlyStorePath?.let { rootProject.file(it) }
val cardlyCanSign = cardlyStoreFile?.isFile == true && listOf(cardlyStorePassword, cardlyKeyAlias, cardlyKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "ru.cardly.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.cardly.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.3.0"
        // Распознавание визиток (Tesseract) содержит нативный код. Все современные телефоны
        // (Samsung и др.) — 64-битные ARM, остальные архитектуры только раздувают APK.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildFeatures { compose = true }

    // Переводы библиотек Google на 80+ языков не нужны — оставляем русский и английский.
    androidResources { localeFilters += setOf("ru", "en") }

    signingConfigs {
        create("cardlyBeta") {
            storeFile = cardlyStoreFile
            storePassword = cardlyStorePassword
            keyAlias = cardlyKeyAlias
            keyPassword = cardlyKeyPassword
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (cardlyCanSign) signingConfigs.getByName("cardlyBeta") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:barcode"))
    implementation(project(":core:sync"))

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Сканер штрихкодов Google (точнее ZXing, без разрешения на камеру) и распознавание кода с фото.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    // Съёмка карты/визитки с автоматической обрезкой по краям.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    // Офлайн-распознавание текста визиток (русский + английский).
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
