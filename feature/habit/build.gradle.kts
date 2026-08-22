plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.sprout.feature.habit"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    buildFeatures { compose = true }
    testOptions.unitTests.isIncludeAndroidResources = true
}

kotlin { jvmToolchain(libs.versions.javaTarget.get().toInt()) }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:scoring"))
    implementation(project(":core:scheduling"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core)
    testImplementation(project(":core:database"))
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.room.testing)
}
