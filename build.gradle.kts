plugins {
    alias(libs.plugins.android.application) apply false
    // Declared here only to pin the Kotlin version: AGP 9 ships built-in Kotlin support,
    // and the Compose compiler plugin must match the Kotlin compiler version exactly.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
