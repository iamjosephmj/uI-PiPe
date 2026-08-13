plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
    api(libs.kotlinx.serialization.cbor)
    testImplementation(libs.junit)
}
