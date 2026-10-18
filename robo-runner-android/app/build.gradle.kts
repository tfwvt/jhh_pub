plugins {
    id("com.android.application")
}

android {
    namespace = "com.tfishapps.roborunner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tfishapps.roborunner"
        minSdk = 26          // Android 8.0+
        targetSdk = 36       // Google Play requires API 36 for new apps and updates
        versionCode = 1      // raise by 1 for every Play Store upload
        versionName = "1.0"
    }

    // One fixed debug key (app/debug.keystore, the standard Android debug passwords), so every debug build,
    // from Android Studio or from GitHub, can install over the last one without wiping your saves.
    // It is NOT for the Play Store: release builds use your own key (see README).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
}
