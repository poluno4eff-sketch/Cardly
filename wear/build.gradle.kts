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
    namespace = "ru.cardly.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.cardly.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 10
        versionName = "0.3.0"
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
            isMinifyEnabled = true
            isShrinkResources = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    implementation(project(":core:barcode"))
    implementation(project(":core:sync"))

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    // Компликация «Оплата» на циферблат.
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
