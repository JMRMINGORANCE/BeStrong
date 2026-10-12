pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "BeStrong"
include(":core", ":designsystem", ":data", ":shared", ":mobile", ":wear", ":watchface", ":baselineprofile")

// Pantallas del móvil por función
include(":feature:workout", ":feature:routines", ":feature:history", ":feature:exercises", ":feature:home", ":feature:planning", ":feature:profile")
