plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ks = rootProject.file("bixie.keystore")

android {
    namespace = "hu.bixie.arlista"
    compileSdk = 34

    defaultConfig {
        applicationId = "hu.bixie.arlista"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "ARLISTA_URL", "\"${project.property("arlistaUrl")}\"")
    }

    signingConfigs {
        if (ks.exists()) {
            create("bixie") {
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASS")
                keyAlias = "bixie"
                keyPassword = System.getenv("KEYSTORE_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ks.exists()) signingConfigs.getByName("bixie") else signingConfigs.getByName("debug")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
