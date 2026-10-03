import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidBuiltinKotlin)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.drywall.keygen"
    compileSdk = libs.versions.compileSdk.get().toInt()

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.drywall.keygen"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Consola web de licencias (HTTPS). Ver app/build.gradle.kts.
        val consoleUrl = System.getenv("LICENSE_CONSOLE_URL")
            ?: localProperties.getProperty("LICENSE_CONSOLE_URL")
            ?: ""
        buildConfigField("String", "LICENSE_CONSOLE_URL", "\"$consoleUrl\"")

        // Token de dispositivo para publicar/sincronizar con la consola.
        val consoleToken = System.getenv("LICENSE_CONSOLE_TOKEN")
            ?: localProperties.getProperty("LICENSE_CONSOLE_TOKEN")
            ?: ""
        buildConfigField("String", "LICENSE_CONSOLE_TOKEN", "\"$consoleToken\"")
    }


    signingConfigs {
        create("release") {
            storeFile = file("keygen-key.jks")
            storePassword = System.getenv("KEYGEN_STORE_PASSWORD") ?: localProperties.getProperty("KEYGEN_STORE_PASSWORD")
            keyAlias = System.getenv("KEYGEN_KEY_ALIAS") ?: localProperties.getProperty("KEYGEN_KEY_ALIAS")
            keyPassword = System.getenv("KEYGEN_KEY_PASSWORD") ?: localProperties.getProperty("KEYGEN_KEY_PASSWORD")
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            val isSigningConfigValid = signingConfigs.getByName("release").let {
                it.storePassword != null && it.keyAlias != null && it.keyPassword != null
            }
            if (isSigningConfigValid) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    lint {
        xmlReport = true
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/INDEX.LIST"
        }
        jniLibs {
            pickFirsts += "**/libsqlcipher.so"
            pickFirsts += "**/libsqlite3x.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.google.material)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.google.accompanist.permissions)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.org.jsoup)
    implementation(libs.google.gson)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.com.squareup.okhttp3)
    implementation(dependencies.project(":common"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
