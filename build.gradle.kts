// AGP 8.13's bundled R8 can't read Kotlin 2.4 metadata; 9.1.29 is the first that can
// (https://developer.android.com/build/kotlin-support). Drop this once on an AGP that bundles it.
buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("com.android.tools:r8:9.1.57")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.aboutLibraries) apply false
}

// Hilt reads Kotlin metadata with kotlin-metadata-jvm, 2.2 in Hilt 2.58 (the last for AGP 8), which stops at metadata
// 2.3; the code is compiled by Kotlin 2.4, as jellyfin-sdk-kotlin 1.9 requires. Drop this once Hilt's own supports 2.4.
subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-metadata-jvm") {
                useVersion(libs.versions.kotlin.get())
                because("Hilt has to read the metadata of Kotlin 2.4 classes")
            }
        }
    }
}
