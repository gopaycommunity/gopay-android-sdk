plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    jacoco
    `maven-publish`
    signing
}

android {
    namespace = "cz.gopay.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        
        val versionName = project.findProperty("sdk.version") as String? ?: "1.0.0"
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

// Publishing configuration
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = project.findProperty("sdk.groupId") as String? ?: "cz.gopay"
                artifactId = project.findProperty("sdk.artifactId") as String? ?: "sdk"
                version = project.findProperty("sdk.version") as String? ?: "1.0.0"

                from(components["release"])
                
                pom {
                    name.set("GoPay Android SDK")
                    description.set("Android SDK for GoPay payment integration")
                    url.set("https://github.com/gopay/gpy-sdk-android")
                    
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("gopay")
                            name.set("GoPay Team")
                            email.set("dev@gopay.com")
                        }
                    }
                    
                    scm {
                        connection.set("scm:git:git://github.com/gopay/gpy-sdk-android.git")
                        developerConnection.set("scm:git:ssh://github.com/gopay/gpy-sdk-android.git")
                        url.set("https://github.com/gopay/gpy-sdk-android")
                    }
                }
            }
        }
        
        repositories {
            // Local Maven repository for testing
            maven {
                name = "localRepo"
                url = uri(layout.buildDirectory.dir("repo"))
            }
            
            // Maven Local for development
            mavenLocal()
            
            // Example: Remote repository (uncomment and configure as needed)
            // maven {
            //     name = "releaseRepo"
            //     url = uri("https://your-maven-repo.com/releases")
            //     credentials {
            //         username = project.findProperty("mavenUsername") as String? ?: ""
            //         password = project.findProperty("mavenPassword") as String? ?: ""
            //     }
            // }
        }
    }
    
    // Configure signing
    signing {
        // Force signing to be required when signatory is configured
        setRequired {
            val isReleaseVersion = !(project.findProperty("sdk.version") as String? ?: "1.0.0").endsWith("SNAPSHOT")
            val isPublishing = gradle.taskGraph.hasTask("publish")
            val hasSignatory = project.findProperty("signing.gnupg.keyName") != null
            (isReleaseVersion && isPublishing) || hasSignatory
        }
        
        // Sign the release publication
        sign(publishing.publications["release"])
    }
}

// Custom tasks for publishing
tasks.register("publishToLocalRepo") {
    group = "publishing"
    description = "Publishes the SDK to local Maven repository"
    dependsOn("publishReleasePublicationToLocalRepoRepository")
}

// Task to show publishing information
tasks.register("showPublishingInfo") {
    group = "publishing"
    description = "Shows the current publishing configuration"
    
    doLast {
        val groupId = project.findProperty("sdk.groupId") as String? ?: "cz.gopay"
        val artifactId = project.findProperty("sdk.artifactId") as String? ?: "sdk"
        val version = project.findProperty("sdk.version") as String? ?: "1.0.0"
        
        println("=== GoPay SDK Publishing Configuration ===")
        println("Group ID: $groupId")
        println("Artifact ID: $artifactId")
        println("Version: $version")
        println("Namespace: ${android.namespace}")
        println("================================")
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
    
    // Coroutines dependency
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    implementation(libs.androidx.foundation.android)

    // Compose dependencies - foundation only
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test")

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