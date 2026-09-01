plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

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

        // Build-time default for the demo's development gateway URL, so a build can be aimed at
        // another environment without editing code:
        //     ./gradlew :app:installDebug -Pgopay.demo.baseUrl=https://gw.example.com/gp-gw/api/4.0/
        // Empty means "use the constant in DemoConfig". A runtime intent extra still wins over
        // this — see DemoLaunchOverrides. Credentials are deliberately NOT settable here: a Gradle
        // property gets baked into the APK, which is exactly what we don't want for a client
        // secret. Pass those as intent extras instead.
        // Escaped, because the value is interpolated straight into a Java string literal in the
        // generated BuildConfig — an unescaped quote there fails the build in generated code.
        val demoBaseUrl = (project.findProperty("gopay.demo.baseUrl") as String? ?: "")
            .trim()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        buildConfigField("String", "DEMO_BASE_URL", "\"$demoBaseUrl\"")
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
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}