import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.mellow.core.designsystem"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi")
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.bundles.compose)
    api(libs.compose.material.icons.extended)
    api(libs.bundles.coil)
    api(libs.androidx.window)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
}
