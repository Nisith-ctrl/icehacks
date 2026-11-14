import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Signing for the downloadable release APK. The keystore and its password stay on the builder's
// machine (local.properties is gitignored); without them, assembleRelease gives an unsigned APK.
//   SUUS_KEYSTORE=C:/Users/<you>/.android/suus-release.jks
//   SUUS_KEYSTORE_PASSWORD=...
//   SUUS_KEY_ALIAS=suus
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystorePath: String? = localProps.getProperty("SUUS_KEYSTORE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "com.icehacks.suus"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.icehacks.suus"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.0"
    }
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = localProps.getProperty("SUUS_KEYSTORE_PASSWORD")
                keyAlias = localProps.getProperty("SUUS_KEY_ALIAS", "suus")
                keyPassword = localProps.getProperty("SUUS_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
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
