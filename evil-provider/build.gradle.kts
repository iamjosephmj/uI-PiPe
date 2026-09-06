plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "tech.ssemaj.pipe.evilprovider"
    compileSdk = 36
    defaultConfig {
        applicationId = "tech.ssemaj.pipe.evilprovider"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("evil") {
            storeFile = rootProject.file("security/evil.keystore")
            storePassword = "evilpass"
            keyAlias = "evil"
            keyPassword = "evilpass"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("evil") }
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
    implementation(libs.coroutines.android)
}
