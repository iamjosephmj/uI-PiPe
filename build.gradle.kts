plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka)
}

apiValidation {
    ignoredProjects.addAll(listOf(
        "sample-host", "sample-provider", "evil-host", "evil-provider",
        "sample-contract", "sample-solo",
    ))
}

dokka {
    moduleName = "uI-PiPe"
}
