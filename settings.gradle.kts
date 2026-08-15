pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    // PREFER_SETTINGS (not FAIL_ON_PROJECT_REPOS): hydra's build-time plugin (applied
    // conditionally in sample-kyc-verifier under -Prasp) injects a project-level Maven
    // repo ("hydraRuntime") to serve its vendored runtime AAR. FAIL_ON_PROJECT_REPOS
    // hard-errors on that project-level declaration; PREFER_SETTINGS instead ignores it
    // (warning only) and resolves exclusively from these settings-declared repos — so the
    // same "hydraRuntime" coordinate is re-declared below, pointed at the build-local m2
    // dir hydra populates, scoped to its group so it can't shadow other dependencies.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven(rootDir.resolve("build/hydra/m2")) {
            name = "hydraRuntime"
            content { includeGroup("io.ssemaj.rasp") }
        }
    }
}
rootProject.name = "pipe"
include(":pipe")
include(":pipe-serialization")
include(":sample-contract")
include(":sample-kyc-contract")
include(":sample-kyc-verifier")
include(":sample-kyc-host")
include(":sample-provider")
include(":sample-host")
include(":sample-solo")
include(":evil-provider")
include(":evil-host")
