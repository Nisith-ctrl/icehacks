plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.icehacks.suus"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.icehacks.suus"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":device"))
    implementation(project(":voice"))
    implementation(project(":agent"))
    implementation(project(":overlay"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
}
