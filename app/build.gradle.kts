import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Secrets (leads API auth token) are read from local.properties / the LEADS_AUTH_TOKEN
// environment variable so they are never committed to source control. Surrounding quotes
// (if the value was pasted as "...") are stripped so the raw token reaches BuildConfig.
val leadsAuthToken: String = run {
    val fromEnv = System.getenv("LEADS_AUTH_TOKEN")
    val raw = if (!fromEnv.isNullOrBlank()) {
        fromEnv
    } else {
        val props = Properties()
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        props.getProperty("LEADS_AUTH_TOKEN").orEmpty()
    }
    raw.trim().removeSurrounding("\"")
}

android {
    namespace = "com.salescrm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.salescrm"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "LEADS_AUTH_TOKEN", "\"$leadsAuthToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

configurations.all {
    resolutionStrategy {
        force("com.squareup:javapoet:1.13.0")
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    ksp("com.squareup:javapoet:1.13.0")

    // Retrofit + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Coil (image loading)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.ui.tooling)

    // Unit testing
    testImplementation("junit:junit:4.13.2")
}