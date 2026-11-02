plugins {
    alias(libs.plugins.plaingallery.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.plaingallery.core.model" }

dependencies {
    api(libs.kotlinx.serialization.core)
    testImplementation(libs.junit)
}
