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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // RuStore SDK (billing). Официальный внешний репозиторий VK/RuStore.
        maven {
            url = uri("https://artifactory-external.vkpartner.ru/artifactory/maven")
            content { includeGroup("ru.rustore.sdk") }
        }
    }
}

rootProject.name = "AssemblyLineTycoon"
include(":app")
