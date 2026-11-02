plugins {
    alias(libs.plugins.plaingallery.android.feature)
}

android { namespace = "app.plaingallery.feature.viewer" }

dependencies {
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
}
