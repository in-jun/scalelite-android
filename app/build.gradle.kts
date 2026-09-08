import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Local release signing, per https://developer.android.com/studio/publish/app-signing:
// keystore.properties (storeFile, storePassword, keyAlias, keyPassword) is loaded
// from the project root, or from ~/.android/release/ on a developer machine. Neither
// file is committed; without one the release build is produced unsigned, which is what
// CI does (release.yml signs the artifact afterwards with apksigner).
val keystorePropertiesFile = listOf(
    rootProject.file("keystore.properties"),
    File(System.getProperty("user.home"), ".android/release/keystore.properties"),
).firstOrNull { it.isFile }
val keystoreProperties = Properties().apply {
    keystorePropertiesFile?.inputStream()?.use(::load)
}

// Release builds take their version from the git tag: build.yml passes
// -PreleaseVersion=1.2.3 for tag v1.2.3. versionCode is derived so it stays monotonic.
val releaseVersion = providers.gradleProperty("releaseVersion").orNull
fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = version.split(".").map { it.toInt() }
    require(minor < 100 && patch < 100) { "version components must stay below 100: $version" }
    return major * 10_000 + minor * 100 + patch
}

android {
    namespace = "dev.injun.scalelite"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.injun.scalelite"
        minSdk = 26
        targetSdk = 37
        versionCode = releaseVersion?.let(::versionCodeOf) ?: 1
        versionName = releaseVersion ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        keystorePropertiesFile?.let { propsFile ->
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = File(keystoreProperties["storeFile"] as String)
                    .let { if (it.isAbsolute) it else File(propsFile.parentFile, it.path) }
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }
    lint {
        // Warnings are defects: fail the build instead of accumulating a report.
        warningsAsErrors = true
        abortOnError = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.all { test ->
            test.useJUnitPlatform()
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core-scale"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Health Connect
    implementation(libs.androidx.health.connect.client)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Local tests
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
