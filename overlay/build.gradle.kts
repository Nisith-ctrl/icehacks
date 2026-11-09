// Owned by T04 (character overlay). T01 only created the skeleton.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.icehacks.saythegoal.overlay"
    compileSdk = 35
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
