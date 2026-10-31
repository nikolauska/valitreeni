plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.fittracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.fittracker"
        // Only target device is a OnePlus 13 on Android 16; supporting older versions would only add compat code.
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Lets Android list English and Finnish in the per-app language setting without a hand-kept XML list.
        generateLocaleConfig = true
    }

    lint {
        // Every string must exist in both English and Finnish; a missing translation should stop the build.
        error += "MissingTranslation"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
