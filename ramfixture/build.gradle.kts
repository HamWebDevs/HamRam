plugins { id("com.android.application") }
android {
    namespace = "dev.hamweb.stayopen.ramfixture"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.hamweb.stayopen.ramfixture"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
