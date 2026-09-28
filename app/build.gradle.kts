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
        minSdk = 31
        targetSdk = 37
        versionCode = versionCodeFromEnvironment.toInt()
        versionName = System.getenv("ATA_VERSION_NAME") ?: "dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Robolectric (menu screenshots only) needs the merged resources: fonts.
    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    // The Robolectric menu screenshots run only in :app:menuShots, never in `test`.
    if (name != "menuShots") {
        filter {
            excludeTestsMatching("*MenuShots*")
            isFailOnNoMatchingTests = false
        }
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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
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
    // -Pata.shots=<dir> renders somewhere else; -Pata.scene=<name> renders just one scene.
    systemProperty("ata.screenshots", (project.findProperty("ata.shots") as String?) ?: rootProject.file("docs/screenshots").absolutePath)
    systemProperty("ata.scene", (project.findProperty("ata.scene") as String?) ?: "")
    systemProperty("ata.full", (project.findProperty("ata.full") as String?) ?: "false")
    // -Pata.size=720x1600 renders the scenes at another screen size.
    systemProperty("ata.size", (project.findProperty("ata.size") as String?) ?: "")
    systemProperty("java.awt.headless", "true")
    filter { includeTestsMatching("*ScreenshotTest*") }
    outputs.upToDateWhen { false }
    // One command refreshes every README image, menus included.
    finalizedBy("menuShots")
}

// Headless menu screenshots: renders the real Compose menus over a real game
// frame with Robolectric native graphics, into app/build/menushots/.
//   ./gradlew :app:menuShots
tasks.register<Test>("menuShots") {
    description = "Renders the Compose menus into app/build/menushots."
    group = "documentation"
    val unitTest = tasks.named<Test>("testDebugUnitTest")
    testClassesDirs = unitTest.get().testClassesDirs
    classpath = unitTest.get().classpath
    systemProperty("ata.menushots", layout.buildDirectory.dir("menushots").get().asFile.absolutePath)
    // The README's menu images are regenerated here too (half size, like the game shots).
    systemProperty("ata.menushots.docs", rootProject.file("docs/screenshots").absolutePath)
    systemProperty("robolectric.graphicsMode", "NATIVE")
    // -ea turns on coroutine debug mode, which renames the thread on every dispatch: very slow here.
    systemProperty("kotlinx.coroutines.debug", "off")
    if (project.hasProperty("allDevices")) systemProperty("ata.menushots.all", "true")
    systemProperty("ata.menushots.only", (project.findProperty("only") ?: "").toString())
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
    maxHeapSize = "3g"
    filter { includeTestsMatching("*MenuShots*") }
    outputs.upToDateWhen { false }
}
