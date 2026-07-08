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
        maven("https://repo.jellyfin.org/releases/")
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
