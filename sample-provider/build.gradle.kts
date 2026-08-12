plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "tech.ssemaj.pipe.sampleprovider"
    compileSdk = 36
    defaultConfig {
        applicationId = "tech.ssemaj.pipe.sampleprovider"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":pipe"))
    implementation(libs.appcompat)
}
