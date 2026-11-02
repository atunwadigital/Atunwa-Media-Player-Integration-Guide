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
        // The Atunwa Media SDK, and the AdsWizz SDK it uses. Public, no credentials.
        maven("https://raw.githubusercontent.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/maven") {
            content {
                includeGroup("com.atunwadigital")
                includeGroup("com.adswizz")
            }
        }
    }
}

rootProject.name = "AtunwaMediaExample"
include(":app")
