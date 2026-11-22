plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.personal.translator"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.personal.translator"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { debug { applicationIdSuffix = ".dev" }; release { isMinifyEnabled = false } }
}
dependencies { testImplementation("junit:junit:4.13.2") }
