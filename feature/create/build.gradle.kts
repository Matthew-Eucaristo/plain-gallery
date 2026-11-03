plugins {
    alias(libs.plugins.plaingallery.android.feature)
}

android { namespace = "app.plaingallery.feature.create" }

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    testImplementation(libs.junit)
}
