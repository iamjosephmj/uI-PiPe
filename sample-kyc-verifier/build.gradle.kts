plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "tech.ssemaj.pipe.kycverifier"
    compileSdk = 36
    defaultConfig {
        applicationId = "tech.ssemaj.pipe.kycverifier"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("verifier") {
            storeFile = rootProject.file("security/kyc-verifier.keystore")
            storePassword = "verifierpass"
            keyAlias = "verifier"
            keyPassword = "verifierpass"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("verifier") }
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
}
