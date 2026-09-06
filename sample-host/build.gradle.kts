plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "tech.ssemaj.pipe.samplehost"
    compileSdk = 36
    defaultConfig {
        applicationId = "tech.ssemaj.pipe.samplehost"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests { isIncludeAndroidResources = true }
    }
}

dependencies {
    implementation(project(":pipe"))
    implementation(project(":pipe-serialization"))
    implementation(project(":sample-contract"))
    implementation(libs.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.cbor)
    implementation(libs.material.components)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.bcpkix)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.uiautomator)
}
