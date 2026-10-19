import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val moduleVersion = providers.gradleProperty("version").get()
val moduleVersionCode = moduleVersion.substringBefore('-').split('.').map(String::toInt)
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

// Release signing comes from the environment (the release workflow sets these).
// Without them, assembleRelease produces an unsigned APK.
val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE_FILE")

android {
    namespace = "dev.etcherfx.lensunlock"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.etcherfx.lensunlock"
        minSdk = 31
        targetSdk = 37
        versionCode = moduleVersionCode
        versionName = moduleVersion
    }

    signingConfigs {
        if (releaseKeystore.isPresent) {
            create("release") {
                storeFile = file(releaseKeystore.get())
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            // No R8: the release APK carries the same bytecode as the device-tested debug build.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // No app icon on purpose: Android shows its default icon in the Xposed manager.
        disable += "MissingApplicationIcon"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    testImplementation(libs.junit)
    // The hook tests implement XposedInterface with fakes.
    testImplementation(libs.libxposed.api)
}
