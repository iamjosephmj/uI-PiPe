pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "pipe"
include(":pipe")
include(":pipe-serialization")
include(":sample-contract")
include(":sample-provider")
include(":sample-host")
include(":sample-solo")
include(":evil-provider")
include(":evil-host")
