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

android {
    namespace = "com.owlcoders.chitti"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.owlcoders.chitti"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        buildConfigField("String", "FIREBASE_PROJECT_ID", local("FIREBASE_PROJECT_ID", "chitti-509103"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", local("GOOGLE_WEB_CLIENT_ID"))
        buildConfigField("String", "BACKEND_URL", local("BACKEND_URL"))
    }

    buildTypes {
        release {
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
