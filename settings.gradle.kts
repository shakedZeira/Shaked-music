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
        // Required only by the OPTIONAL YouTube audio resolver (NewPipeExtractor).
        maven("https://jitpack.io")
    }
}

rootProject.name = "SpotifyReplica"
include(":app")
