plugins {
    alias(libs.plugins.plaingallery.android.library)
    alias(libs.plugins.plaingallery.android.compose)
}

android { namespace = "app.plaingallery.core.ui" }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.coil.compose)
    testImplementation(libs.junit)
}
