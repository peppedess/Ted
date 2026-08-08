plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "it.peppedess.ted"
    compileSdk = 36

    defaultConfig {
        // DEVE essere identico a quello del modulo :wear, altrimenti
        // il Data Layer non instrada nulla fra i due dispositivi.
        applicationId = "it.peppedess.ted"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.0"

        // Iniettati dai GitHub Secrets in CI. Mai committare i valori.
        buildConfigField("int", "TG_API_ID", System.getenv("TG_API_ID") ?: "0")
        buildConfigField("String", "TG_API_HASH", "\"${System.getenv("TG_API_HASH") ?: ""}\"")

        ndk {
            // Solo arm64: Galaxy e Pixel 10 sono entrambi a 64 bit.
            // Le altre ABI raddoppierebbero l'APK per nulla.
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("shared") {
            // Chiave di sviluppo condivisa fra i moduli e fra le build.
            // Senza, ogni run della CI firma con una debug.keystore diversa
            // e l'APK non si installa sopra il precedente.
            storeFile = rootProject.file("keystore/ted.jks")
            storePassword = "tedtedted"
            keyAlias = "ted"
            keyPassword = "tedtedted"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
}
