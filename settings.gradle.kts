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
        // TDLib arriva come .aar locale prodotto dal workflow, non da un repo remoto.
        flatDir { dirs("${rootDir}/libs") }
    }
}

rootProject.name = "Ted"

include(":protocol")
include(":app")
include(":wear")
