plugins {
    alias(libs.plugins.plaingallery.android.library)
}

android {
    namespace = "app.plaingallery.core.text"
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
}

dependencies {
    implementation(libs.litert)
    testImplementation(libs.junit)
}
