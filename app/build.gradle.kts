plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingPath = providers.environmentVariable("STAYOPEN_KEYSTORE").orNull
val signingValues = listOf("STAYOPEN_STORE_PASSWORD", "STAYOPEN_KEY_ALIAS", "STAYOPEN_KEY_PASSWORD")
val hasReleaseKey = signingPath != null && signingValues.all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "dev.hamweb.stayopen"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.hamweb.stayopen"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    signingConfigs {
        if (hasReleaseKey) create("production") {
            storeFile = file(signingPath!!)
            storePassword = System.getenv("STAYOPEN_STORE_PASSWORD")
            keyAlias = System.getenv("STAYOPEN_KEY_ALIAS")
            keyPassword = System.getenv("STAYOPEN_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("production")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = true; checkReleaseBuilds = true }
    testOptions { animationsDisabled = true }
}

tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fail rather than accidentally distribute an unsigned production build."
    doLast { check(hasReleaseKey) { "Set all four STAYOPEN signing environment variables. See docs/RELEASING.md." } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
