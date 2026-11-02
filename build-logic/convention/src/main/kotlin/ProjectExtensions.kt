import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal object Sdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 30
}

internal fun ApplicationExtension.configureAndroid() {
    compileSdk = Sdk.COMPILE
    defaultConfig.minSdk = Sdk.MIN
    defaultConfig.targetSdk = Sdk.TARGET
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

internal fun LibraryExtension.configureAndroid() {
    compileSdk = Sdk.COMPILE
    defaultConfig.minSdk = Sdk.MIN
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
