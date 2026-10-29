plugins {
    id("com.android.library")
}

android {
    namespace = "ru.cardly.core.sync"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
