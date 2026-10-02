import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidBuiltinKotlin)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.drywall.cleaner"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.drywall.cleaner"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0.2"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    signingConfigs {
        create("release") {
            storeFile = file("../app/release-key.jks")
            storePassword = System.getenv("DRYWALL_STORE_PASSWORD") ?: localProperties.getProperty("DRYWALL_STORE_PASSWORD")
            keyAlias = System.getenv("DRYWALL_KEY_ALIAS") ?: localProperties.getProperty("DRYWALL_KEY_ALIAS")
            keyPassword = System.getenv("DRYWALL_KEY_PASSWORD") ?: localProperties.getProperty("DRYWALL_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            val isSigningConfigValid = signingConfigs.getByName("release").let {
                it.storePassword != null && it.keyAlias != null && it.keyPassword != null
            }
            if (isSigningConfigValid) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(dependencies.project(":common"))
}
