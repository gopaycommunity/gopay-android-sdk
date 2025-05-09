plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    jacoco
}

android {
    namespace = "com.gopay.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Network dependencies
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    
    testImplementation(libs.junit)
    // Retrofit test dependencies
    testImplementation("com.squareup.retrofit2:retrofit-mock:2.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Mockito dependencies for unit testing
    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

jacoco {
    toolVersion = "0.8.13"
}

afterEvaluate {
    tasks.register<JacocoReport>("jacocoTestReport") {
        description = "Generates code coverage report"
        group = JavaBasePlugin.BUILD_TASK_NAME
        dependsOn("testDebugUnitTest")
        
        reports {
            xml.required.set(true)
        }
        
        // Define exclusions
        val excludes = listOf(
            // Default excludes
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            
            // Kotlin data classes
            "**/*$*.*", // Synthetic methods
            "**/model/*.*", // Exclude model package
            
            // For other classes, exclude common generated methods
            "**/*\$DefaultImpls.*", // Default interface implementations
            "**/*ComponentCallbacksImpl*.*",
            "**/*_Factory*.*",
            "**/*Companion*.*" // Companion objects
        )
        
        // Include Kotlin classes with exclusions
        classDirectories.setFrom(
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                exclude(excludes)
            }
        )
        
        sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
        executionData.setFrom(files(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")))
    }
    
    tasks.named("testDebugUnitTest").configure {
        finalizedBy("jacocoTestReport")
    }
}