plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
}

android {
    namespace = "tech.ssemaj.pipe.serialization"
    compileSdk = 36
    defaultConfig { minSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    publishing { singleVariant("release") { withSourcesJar(); withJavadocJar() } }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    api(project(":pipe"))
    implementation(libs.kotlinx.serialization.cbor)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "tech.ssemaj.pipe"; artifactId = "pipe-serialization"; version = "2.0.0-alpha01"
            afterEvaluate { from(components["release"]) }
        }
    }
}
