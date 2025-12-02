import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

fun propertyOrNull(key: String): String? =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() }

fun propertyOrDefault(key: String, fallback: String): String =
    propertyOrNull(key) ?: fallback

val emulatorBaseUrl = normalizeBaseUrl(
    propertyOrDefault("API_BASE_URL_EMULATOR", "http://10.0.2.2:5000/api")
)
val deviceBaseUrl = normalizeBaseUrl(
    propertyOrDefault("API_BASE_URL_DEVICE", "http://192.168.0.100:5000/api")
)
val customBaseUrl = normalizeBaseUrl(
    propertyOrDefault("API_BASE_URL_CUSTOM", propertyOrDefault("API_BASE_URL", emulatorBaseUrl))
)
val apiEnvironment = propertyOrDefault("API_ENV", "EMULATOR").uppercase()
val resolvedBaseUrl = when (apiEnvironment) {
    "DEVICE" -> deviceBaseUrl
    "CUSTOM" -> customBaseUrl
    else -> emulatorBaseUrl
}

println("Aura API environment: $apiEnvironment -> $resolvedBaseUrl")

android {
    namespace = "com.aura.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aura.music"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // API base URLs (override via local.properties)
        buildConfigField("String", "API_ENV", "\"$apiEnvironment\"")
        buildConfigField("String", "API_BASE_URL", "\"$resolvedBaseUrl\"")
        buildConfigField("String", "API_BASE_URL_EMULATOR", "\"$emulatorBaseUrl\"")
        buildConfigField("String", "API_BASE_URL_DEVICE", "\"$deviceBaseUrl\"")
        buildConfigField("String", "API_BASE_URL_CUSTOM", "\"$customBaseUrl\"")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    
    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-common:1.2.0")
    implementation("androidx.media3:media3-session:1.2.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("io.mockk:mockk-agent-jvm:1.13.9")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

