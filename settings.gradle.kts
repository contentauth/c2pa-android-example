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
        flatDir {
            dirs("libs")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/redaranj/c2pa-android")
            credentials {
                username = "ngengesenior"
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        flatDir {
            dirs("app/libs")
        }
    }
}

rootProject.name = "ProofmodeC2pa"
include(":app")
