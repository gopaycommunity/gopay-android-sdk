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
    testImplementation(libs.junit)
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
        
        // Include Kotlin classes
        classDirectories.setFrom(
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                exclude(
                    "**/R.class",
                    "**/R$*.class",
                    "**/BuildConfig.*",
                    "**/Manifest*.*"
                )
            }
        )
        
        sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
        executionData.setFrom(files(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")))
    }
    
    tasks.named("testDebugUnitTest").configure {
        finalizedBy("jacocoTestReport")
    }
}