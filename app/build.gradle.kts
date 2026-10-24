import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "nl.angryuncle"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.angryuncle"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing: reads keystore.properties from the project root, or from ~/.angryuncle-signing/.
    // Neither is committed. Without one, the release build is simply left unsigned.
    val keystoreProps = listOf(
        rootProject.file("keystore.properties"),
        File(System.getProperty("user.home"), ".angryuncle-signing/keystore.properties"),
    ).firstOrNull { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // ML Kit's native libraries are ~20 MB per CPU type. Real phones are ARM, so ship only those;
            // debug builds keep every ABI so they also run on x86 emulators.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    // Compress native libraries in the APK. They are extracted on install, which costs a little disk space,
    // but it roughly halves the download, and this app is shared as a downloadable APK.
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Head editor: in-app camera, gallery EXIF rotation, and on-device ML Kit.
    // The ML Kit artifacts bundle their models, so they work without Play Services downloading anything.
    val camerax = "1.5.3"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-compose:$camerax")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    testImplementation("junit:junit:4.13.2")
}
