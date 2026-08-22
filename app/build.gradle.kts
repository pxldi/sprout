plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.sprout"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.sprout"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        // Literal, hand-bumped: F-Droid reproducible builds must not derive this.
        versionCode = 1
        versionName = "0.1.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // --- Reproducible-build hygiene (F-Droid), from commit one ---
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    androidResources {
        generateLocaleConfig = false
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            // No Google dependencies. INTERNET permission is added only by :sync when enabled.
        }
        create("play") {
            dimension = "distribution"
        }
    }

    buildTypes {
        release {
            // No git metadata in the APK — it varies per checkout and breaks reproducibility.
            vcsInfo { include = false }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin { jvmToolchain(libs.versions.javaTarget.get().toInt()) }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:scoring"))
    implementation(project(":core:scheduling"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))
    implementation(project(":feature:today"))
    implementation(project(":feature:habit"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
