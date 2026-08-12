pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "pipe"
include(":pipe")
include(":sample-provider")
include(":sample-host")
include(":evil-provider")
include(":evil-host")
