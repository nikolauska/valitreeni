// One version string drives both names: versionCode must keep rising for phones to accept an update.
val appVersion = providers.gradleProperty("valitreeni.version").get()
// The app's versionCodeOf() uses the same formula to read release tags.
val appVersionCode = appVersion.split(".").map(String::toInt).let { parts ->
    require(parts.size == 3 && parts.all { it in 0..99 }) { "valitreeni.version must be major.minor.patch, each 0-99" }
    parts[0] * 10_000 + parts[1] * 100 + parts[2]
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.fittracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.fittracker"
        // Android 15 is the oldest phone it runs on; going lower would only add compat code.
        minSdk = 35
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UPDATE_URL", "\"https://api.github.com/repos/nikolauska/valitreeni/releases/latest\"")
    }

    signingConfigs {
        // Lives in ~/.gradle/gradle.properties, never in the repo: anyone with the key could push updates to every phone.
        val storeFile = providers.gradleProperty("valitreeni.storeFile").orNull
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = providers.gradleProperty("valitreeni.storePassword").get()
                keyAlias = providers.gradleProperty("valitreeni.keyAlias").get()
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        debug {
            // Lets the update flow be tested against a local server without publishing a release.
            providers.gradleProperty("valitreeni.updateUrl").orNull?.let { buildConfigField("String", "UPDATE_URL", "\"$it\"") }
        }
        release {
            // Smaller download for relatives on mobile data.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Lets Android list English and Finnish in the per-app language setting without a hand-kept XML list.
        generateLocaleConfig = true
    }

    testOptions {
        // Domain logic is plain Kotlin; returning defaults keeps stray android.* calls in tests from crashing them.
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Every string must exist in both English and Finnish; a missing translation should stop the build.
        error += "MissingTranslation"
    }
}

// Migration tests build old database versions on the device from the exported schemas.
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

ksp {
    // Exported schemas let future migrations be checked against what is already on the phone.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    // Pinned so the app and kotlinx-coroutines-test agree on the same coroutines version.
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android's org.json is a stub in local unit tests; the real library lets release parsing be tested.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
