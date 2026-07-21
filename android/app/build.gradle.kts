import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Signing material comes from android/key.properties locally and from the
// environment in CI. Absent both, release builds fall back to the debug key so
// `flutter run --release` and the CI build matrix keep working — but such an
// artifact is not uploadable, which `fastlane validate` is there to catch.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, variable: String): String? =
    keystoreProperties.getProperty(property) ?: System.getenv(variable)

val releaseStoreFile = signingValue("storeFile", "LUNO_KEYSTORE_PATH")

android {
    namespace = "com.luno.gateway"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            allWarningsAsErrors = true
        }
    }

    // New warnings fail the build; today's are grandfathered in lint-baseline.xml.
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        baseline = file("lint-baseline.xml")
    }

    buildFeatures {
        // BuildConfig.DEBUG gates the debug-only cleartext (http/ws) allowance for LAN pairing.
        buildConfig = true
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

    // Play Protect's enhanced fraud protection blocks any internet-sideloaded install
    // (browser, messaging app, file manager) that declares RECEIVE_SMS. The permission is
    // only needed for inbound capture, so `sendOnly` drops it — and the SmsReceiver
    // registration in src/full/AndroidManifest.xml — to install clean from any source.
    // `full` is the complete gateway and must be installed via adb, managed Google Play,
    // or the Play Store. See docs/play-protect.md.
    flavorDimensions += "sms"
    productFlavors {
        create("full") {
            dimension = "sms"
            buildConfigField("boolean", "RECEIVE_SMS_ENABLED", "true")
        }
        create("sendOnly") {
            dimension = "sms"
            buildConfigField("boolean", "RECEIVE_SMS_ENABLED", "false")
        }
    }

    // Versioned schema exported from day one so future migrations have a baseline.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "LUNO_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "LUNO_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "LUNO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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
    // Wire protocol codec: JSON over WSS is the node<->backend contract (§8).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // OkHttp: the WSS socket + HTTPS enrollment client to the backend.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Room: the durable outbox/inbox spine (persist-before-act).
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    // WorkManager: the deferred backstop that revives the agent / drains the queue (M15).
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // MockWebServer: drives the REST/WS clients in unit tests without a real backend.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // okhttp-tls: self-signed cert so MockWebServer can serve the https:// RestClient demands.
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
}
