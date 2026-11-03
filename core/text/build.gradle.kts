plugins {
    alias(libs.plugins.plaingallery.android.library)
}

android {
    namespace = "app.plaingallery.core.text"
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
}

dependencies {
    // LiteRT pulls in Play AI/asset delivery (proprietary, downloads models) for ModelProvider, which is unused.
    implementation(libs.litert) { exclude(group = "com.google.android.play") }
    testImplementation(libs.junit)
}
