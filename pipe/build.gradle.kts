plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    id("maven-publish")
}

android {
    namespace = "tech.ssemaj.pipe"
    compileSdk = 36
    defaultConfig { minSdk = 35 }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    publishing { singleVariant("release") { withSourcesJar(); withJavadocJar() } }
    testOptions { unitTests { isReturnDefaultValues = true; isIncludeAndroidResources = true } }
}

dependencies {
    api(libs.androidx.activity)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation("androidx.annotation:annotation:1.9.1")
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "tech.ssemaj.pipe"; artifactId = "pipe"; version = "2.0.0-alpha01"
            afterEvaluate { from(components["release"]) }
        }
    }
}
