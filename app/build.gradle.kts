plugins {
    alias(libs.plugins.plaingallery.android.application)
    alias(libs.plugins.plaingallery.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

/**
 * Release signing comes from Gradle properties (`~/.gradle/gradle.properties`) or environment
 * variables in CI, never from the repo. Without them, release builds use the debug key, which is
 * fine for local testing.
 */
fun signingValue(name: String): String? =
    providers.gradleProperty("plaingallery.$name").orNull ?: providers.environmentVariable("PLAINGALLERY_${name.uppercase()}").orNull

val releaseKeystore = signingValue("keystore")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "app.plaingallery"

    defaultConfig {
        applicationId = "app.plaingallery"
        versionCode = 3
        versionName = "0.1.2"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValue("keystore_password")
                keyAlias = signingValue("key_alias")
                keyPassword = signingValue("key_password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Keep the encrypted dependency blob out of APKs (third-party stores reject it); Play reads it from bundles.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:library"))
    implementation(project(":feature:viewer"))
    implementation(project(":feature:search"))
    implementation(project(":feature:tidy"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    baselineProfile(project(":baselineprofile"))
}
