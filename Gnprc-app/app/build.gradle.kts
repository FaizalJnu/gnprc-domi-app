plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gnprc_app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.gnprc_app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
//    // Face features
//    implementation("com.google.mlkit:face-detection:16.0.0")
//    // Text features
//    implementation("com.google.android.gms:play-services-mlkit-text-recognition:16.0.0")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}