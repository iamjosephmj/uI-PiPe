plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "tech.ssemaj.pipe.kychost"
    compileSdk = 36
    defaultConfig {
        applicationId = "tech.ssemaj.pipe.kychost"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":pipe"))
    implementation(project(":pipe-serialization"))
    implementation(project(":sample-kyc-contract"))
    implementation(libs.appcompat)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime)
}
