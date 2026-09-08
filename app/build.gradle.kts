import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Demo gateway and merchant values, read from a `-P` project property or, failing that, the
// gitignored `local.properties` in the repo root. A missing key is an empty string, so the build
// works without them.
val demoProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun demoProperty(key: String): String =
    (project.findProperty(key) as String? ?: demoProperties.getProperty(key) ?: "")
        .trim()
        // The value is interpolated into a Java string literal in the generated BuildConfig.
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

// The SDK appends the trailing slash; only the scheme has to be right here, and getting it wrong
// should fail the build rather than the app at launch.
val demoBaseUrl = demoProperty("gopay.demo.baseUrl")
require(
    demoBaseUrl.isEmpty() ||
        demoBaseUrl.startsWith("http://", ignoreCase = true) ||
        demoBaseUrl.startsWith("https://", ignoreCase = true)
) { "gopay.demo.baseUrl must be empty or an http(s) URL, got '$demoBaseUrl'" }

android {
    namespace = "com.gopay.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gopay.example"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "DEMO_BASE_URL", "\"$demoBaseUrl\"")
        buildConfigField("String", "DEMO_CLIENT_ID", "\"${demoProperty("gopay.demo.clientId")}\"")
        buildConfigField("String", "DEMO_SHAREABLE_KEY", "\"${demoProperty("gopay.demo.shareableKey")}\"")
        buildConfigField("String", "DEMO_CLIENT_SECRET", "\"${demoProperty("gopay.demo.clientSecret")}\"")
        buildConfigField("String", "DEMO_GOID", "\"${demoProperty("gopay.demo.goid")}\"")
    }

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
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val materialIconsVersion = "1.5.4"

dependencies {
    // Use local SDK module to test in-progress SDK API changes.
    implementation(project(":sdk"))
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}