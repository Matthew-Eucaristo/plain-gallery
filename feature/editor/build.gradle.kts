plugins {
    alias(libs.plugins.plaingallery.android.feature)
}

android { namespace = "app.plaingallery.feature.editor" }

dependencies {
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
