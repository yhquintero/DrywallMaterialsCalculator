import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidBuiltinKotlin)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.google.hilt)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.drywall.calculator"
    compileSdk = libs.versions.compileSdk.get().toInt()

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.drywall.calculator"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("long", "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "0L")

        // URL HTTPS de la Consola de Licencias (server/). Se configura con
        //   LICENSE_CONSOLE_URL=https://licencias.tudominio.com ./gradlew :app:assembleRelease
        // o en local.properties (LICENSE_CONSOLE_URL=...). Sin valor ⇒ la app
        // usa únicamente la clave pública empaquetada.
        val consoleUrl = System.getenv("LICENSE_CONSOLE_URL")
            ?: localProperties.getProperty("LICENSE_CONSOLE_URL")
            ?: ""
        buildConfigField("String", "LICENSE_CONSOLE_URL", "\"$consoleUrl\"")

        // Pinning opcional: SHA-256 (hex) de la clave pública esperada.
        // Se lee en la consola → Claves de firma. Vacío ⇒ se acepta la vigente.
        val consoleKeySha = System.getenv("LICENSE_CONSOLE_KEY_SHA256")
            ?: localProperties.getProperty("LICENSE_CONSOLE_KEY_SHA256")
            ?: ""
        buildConfigField("String", "LICENSE_CONSOLE_KEY_SHA256", "\"$consoleKeySha\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-key.jks")
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.google.material)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.google.hilt.android)
    ksp(libs.google.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.google.gson)

    implementation(libs.io.coil.compose)

    implementation(libs.google.accompanist.permissions)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.google.mlkit.text.recognition)
    implementation(libs.google.mlkit.barcode.scanning)

    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.org.jsoup)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.google.play.integrity)

    implementation(dependencies.project(":common"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
