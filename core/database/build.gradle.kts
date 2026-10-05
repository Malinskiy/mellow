plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.mellow.core.database"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // MigrationTestHelper reads the exported schemas from the test assets.
    sourceSets {
        getByName("test").assets.srcDir("$projectDir/schemas")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// The million-track query benchmark (QueryBenchmark) runs only when asked for, with -PperfOutput=<results.json>.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("perfOutput").orNull?.let { output ->
        systemProperty("mellow.perf.output", output)
        // A benchmark has to run: never replay a cached or up-to-date result.
        outputs.cacheIf { false }
        outputs.upToDateWhen { false }
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.bundles.room)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
}
