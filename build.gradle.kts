plugins {
    alias(libs.plugins.android.application) apply false
    // Declared (not applied) so AGP's built-in Kotlin uses this KGP version,
    // which must match the Compose compiler plugin version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
