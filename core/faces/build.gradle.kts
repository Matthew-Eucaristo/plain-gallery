plugins {
    alias(libs.plugins.plaingallery.android.library)
}

android {
    namespace = "app.plaingallery.core.faces"
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
}

dependencies {
    api(project(":core:model"))
    implementation(libs.litert)
    testImplementation(libs.junit)
}
