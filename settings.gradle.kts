pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://repo.jellyfin.org/releases/") {
            content {
                includeGroupByRegex("org\\.jellyfin.*")
            }
        }
    }
}

rootProject.name = "Mellow"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:data")
include(":core:player")
include(":feature:home")
include(":feature:library")
include(":feature:player")
include(":feature:search")
include(":feature:settings")
include(":sync")

buildCache {
    remote<HttpBuildCache> {
        url = uri("https://cache.eu-central-a.buildfetch.com/FBi3wi/gradle/")

        credentials {
            username = "token-auth"
            
            // Set BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN="generated-token" as env variable (best for CI)
            // Set BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN="generated-token" in ~/.gradle/gradle.properties (best for mixed IDE & Terminal experience) 
            password = "BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN".let {
               providers.environmentVariable(it).orElse(providers.gradleProperty(it)).orNull
            }
        }

        // BuildFetch recommends starting with Cache writes enabled on CI (more reproducible environment).
        // On developer machines, enable writes if this environment is trusted & reproducible for quicker cache distribution and higher hit ratio.
        isPush = providers.environmentVariable("CI").isPresent
        
        isEnabled = url != null && credentials.username != null && credentials.password != null
    }
}
