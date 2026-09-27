plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionCodeFromEnvironment = System.getenv("ATA_VERSION_CODE") ?: "1"
require(versionCodeFromEnvironment.matches(Regex("[1-9]\\d*"))) {
    "ATA_VERSION_CODE must be a positive integer"
}

// Release signing is all-or-nothing: set all four variables or none.
val signingEnvironmentVariables = listOf(
    "ATA_KEYSTORE_PATH",
    "ATA_STORE_PASSWORD",
    "ATA_KEY_ALIAS",
    "ATA_KEY_PASSWORD",
)
val configuredSigningVariables = signingEnvironmentVariables.filter { !System.getenv(it).isNullOrEmpty() }
if (configuredSigningVariables.isNotEmpty() && configuredSigningVariables.size != signingEnvironmentVariables.size) {
    val missing = signingEnvironmentVariables - configuredSigningVariables.toSet()
    throw GradleException("Release signing is partially configured. Missing: ${missing.joinToString()}")
}
val releaseSigningEnabled = configuredSigningVariables.size == signingEnvironmentVariables.size

android {
    namespace = "com.bradflaugher.aboutthataction"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bradflaugher.aboutthataction"
        minSdk = 37
        targetSdk = 37
        versionCode = versionCodeFromEnvironment.toInt()
        versionName = System.getenv("ATA_VERSION_NAME") ?: "dev"
    }

    androidResources {
        localeFilters += listOf("en")
    }

    signingConfigs {
        if (releaseSigningEnabled) {
            create("release") {
                storeFile = file(System.getenv("ATA_KEYSTORE_PATH"))
                storePassword = System.getenv("ATA_STORE_PASSWORD")
                keyAlias = System.getenv("ATA_KEY_ALIAS")
                keyPassword = System.getenv("ATA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

// Show failing assertions in the build log (CI included), not just in the HTML report.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
}

// Headless screenshots: renders real game scenes through the same Renderer the
// app uses, via a java.awt backend, into docs/screenshots/.
//   ./gradlew :app:screenshots
tasks.register<Test>("screenshots") {
    description = "Renders README screenshots into docs/screenshots."
    group = "documentation"
    val unitTest = tasks.named<Test>("testDebugUnitTest")
    testClassesDirs = unitTest.get().testClassesDirs
    classpath = unitTest.get().classpath
    systemProperty("ata.screenshots", rootProject.file("docs/screenshots").absolutePath)
    systemProperty("java.awt.headless", "true")
    filter { includeTestsMatching("*ScreenshotTest*") }
    outputs.upToDateWhen { false }
}
