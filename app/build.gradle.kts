import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Secrets live in local.properties (git-ignored), never in source.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The one place the version lives: chitti.versionName in gradle.properties, as MAJOR.MINOR.PATCH.
// versionCode is derived from it so every release is strictly greater than the last, and the
// release workflow refuses a tag that doesn't match it.
val chittiVersionName: String = providers.gradleProperty("chitti.versionName").get()
val chittiVersionCode: Int = run {
    val parts = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(chittiVersionName)?.groupValues?.drop(1)?.map(String::toInt)
        ?: error("chitti.versionName must be MAJOR.MINOR.PATCH, got '$chittiVersionName'")
    val (major, minor, patch) = parts
    require(minor < 100 && patch < 100) { "minor and patch must stay below 100" }
    major * 10_000 + minor * 100 + patch
}

// Release signing key, from local.properties (or written there by CI). Absent on machines
// without the key: release builds then come out unsigned and debug builds use the debug key.
val releaseKeystore: File? = localProperties.getProperty("RELEASE_STORE_FILE")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.owlcoders.chitti"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.owlcoders.chitti"
        minSdk = 26
        targetSdk = 36
        versionCode = chittiVersionCode
        versionName = chittiVersionName

        // Google Safe Browsing key, restricted in Cloud Console to this package + signing SHA-1.
        // Blank when absent: LinkGuard then checks links on the device only.
        buildConfigField(
            "String",
            "SAFE_BROWSING_API_KEY",
            "\"${localProperties.getProperty("SAFE_BROWSING_API_KEY", "")}\""
        )

        // Firebase (sign-in) and the Chitti backend (encrypted backups). Read from
        // local.properties so no project config is committed; blank means "not set up".
        fun local(key: String, default: String = "") =
            "\"${localProperties.getProperty(key, default)}\""
        buildConfigField("String", "FIREBASE_API_KEY", local("FIREBASE_API_KEY"))
        buildConfigField("String", "FIREBASE_APP_ID", local("FIREBASE_APP_ID"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", local("FIREBASE_PROJECT_ID", "chitti-bd8ce"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", local("GOOGLE_WEB_CLIENT_ID"))
        buildConfigField("String", "BACKEND_URL", local("BACKEND_URL"))
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Debug builds are signed with the release key too when it's present, so the phone, the
        // Firebase SHA fingerprints and the Safe Browsing key restriction all see one certificate.
        debug {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Accounts: Firebase Authentication (Google + email/password), Google sign-in via Credential Manager
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")

    // Security: hardware-backed encryption (Tink + Android Keystore) and the biometric app lock
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    implementation("androidx.biometric:biometric:1.1.0")

    // Personal documents: on-device document scanner (edge detection, crop, cleanup) and OCR
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Backdrop blur for the three glass surfaces (nav bar, tab bar, voice overlay)
    implementation("dev.chrisbanes.haze:haze:1.7.3")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.10.1")

    // Room
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // MediaPipe LLM Inference. Kept at the version the on-device gemma.bin was verified with.
    implementation("com.google.mediapipe:tasks-genai:0.10.14")

    // Unit tests (app/src/test)
    testImplementation("junit:junit:4.13.2")
}
