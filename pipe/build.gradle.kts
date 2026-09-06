plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.dokka)
    id("maven-publish")
}

dokka {
    dokkaSourceSets {
        configureEach {
            // The AIDL wire layer is library-internal plumbing, not integrator API.
            perPackageOption {
                matchingRegex.set(".*\\.transport.*")
                suppress.set(true)
            }
        }
    }
}

android {
    namespace = "tech.ssemaj.pipe"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
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
    // PaneRoot is a Lifecycle/SavedState/ViewModelStore owner so Compose panes work out of the box.
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.savedstate)
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.iamjosephmj.uI-PiPe"; artifactId = "pipe"; version = libs.versions.pipe.get()
            afterEvaluate { from(components["release"]) }
        }
    }
}
