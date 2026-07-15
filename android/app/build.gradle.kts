plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.luno.gateway"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "com.luno.gateway"
        // minSdk 26 is a locked decision (plan.md §Decisions): modern foreground
        // service + notification-channel semantics without legacy branches.
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Load-bearing infrastructure only (see plan.md "Native over plugins").
    // ServiceCompat/NotificationCompat/ContextCompat compatibility shims.
    implementation("androidx.core:core-ktx:1.13.1")
    // LifecycleService: the foreground service base with a lifecycle scope.
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    // StateFlow/coroutines for the agent's state source of truth.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
