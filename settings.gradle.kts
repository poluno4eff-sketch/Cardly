pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("cz.adaptech.tesseract4android") } }
    }
}

rootProject.name = "Cardly"
include(":mobile")
include(":wear")
include(":core:model")
include(":core:data")
include(":core:barcode")
include(":core:sync")
