// Owned by T03 (planner loop, workflows, form mapping, prefs, undo).
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// ANTHROPIC_API_KEY comes from local.properties (gitignored). Missing key -> empty string, build still works.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val anthropicKey: String = localProps.getProperty("ANTHROPIC_API_KEY", "")

android {
    namespace = "com.icehacks.saythegoal.agent"
    compileSdk = 35
    defaultConfig {
        minSdk = 30
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${anthropicKey.replace("\"", "")}\"")
    }
    buildFeatures { buildConfig = true }
    packaging { resources { excludes += setOf("META-INF/*") } }
    testOptions { unitTests.isReturnDefaultValues = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.anthropic.java)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
