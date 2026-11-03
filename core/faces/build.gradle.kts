plugins {
    alias(libs.plugins.plaingallery.android.library)
}

android {
    namespace = "app.plaingallery.core.faces"
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
}

dependencies {
    api(project(":core:model"))
    // LiteRT pulls in Play AI/asset delivery (proprietary, downloads models) for ModelProvider, which is unused.
    implementation(libs.litert) { exclude(group = "com.google.android.play") }
    testImplementation(libs.junit)
}
