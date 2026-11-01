// Ported from Store (YAZ/Store at HEAD, app/build.gradle.kts), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, app/build.gradle.kts).
// Left out of Store's build: apksig and ARSCLib (its install kit), the GIF decoder (its safemedia kit).
// Added: the lifecycle-process library, which the update kit needs.
plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so no kotlin-android here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yaz.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yaz.launcher"
        minSdk = 31
        targetSdk = 37
        versionCode = 7
        versionName = "0.1.6"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("launcher.storeFile").orNull
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("launcher.storePassword").orNull
                keyAlias = providers.gradleProperty("launcher.keyAlias").orNull
                keyPassword = providers.gradleProperty("launcher.keyPassword").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Only attached when the signing properties are supplied, so a
            // local build without a keystore still produces an unsigned APK.
            if (providers.gradleProperty("launcher.storeFile").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The unit tests call Android classes the model only names (ComponentName, UserHandle): their stubs return defaults.
    // Screens drawn on the JVM by Robolectric, as Store's, SMS's and Contacts' builds have it (Now in Android's core/screenshot-testing).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
                it.maxHeapSize = "4096m"
                // Robolectric on JDK 17 and later (https://robolectric.org/getting-started/).
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
                )
            }
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/INDEX.LIST",
            "/META-INF/DEPENDENCIES"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    // Installs assets/dexopt/baseline.prof (from baseline-prof.txt) at the first launch after each update, as SMS does
    // (YAZ/SMS at HEAD, app/build.gradle.kts; Google, Apache-2.0,
    // https://github.com/androidx/androidx/tree/androidx-main/profileinstaller/profileinstaller).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
