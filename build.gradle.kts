// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.nmcp) apply false
    alias(libs.plugins.nmcp.aggregation)
}

// Maven Central upload through the Central Portal publisher API. Credentials come
// from Gradle properties (for example ORG_GRADLE_PROJECT_mavenCentralUsername) and
// are only read when an upload task runs.
nmcpAggregation {
    centralPortal {
        username = providers.gradleProperty("mavenCentralUsername")
        password = providers.gradleProperty("mavenCentralPassword")
        // AUTOMATIC publishes once validation passes; USER_MANAGED stops at VALIDATED
        // so the deployment can be checked and dropped in the Portal.
        publishingType = providers.gradleProperty("nmcpPublishingType").orElse("AUTOMATIC")
        validationTimeout = java.time.Duration.ofMinutes(30)
    }
}

dependencies {
    nmcpAggregation(project(":sdk"))
}
