// Owned by T03 (planner loop, workflows, form mapping, prefs, undo).
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// LLM settings come from local.properties (gitignored). A missing key -> empty string; the build still works.
//   GROQ_API_KEY=gsk_...                      (required to plan; free key at console.groq.com)
//   GROQ_MODEL=openai/gpt-oss-120b            (optional override)
//   GROQ_VISION_MODEL=qwen/qwen3.8-27b        (optional override, used to describe photos)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String, default: String = "") = localProps.getProperty(name, default).replace("\"", "")

android {
    namespace = "com.icehacks.saythegoal.agent"
    compileSdk = 35
    defaultConfig {
        minSdk = 30
        buildConfigField("String", "GROQ_API_KEY", "\"${prop("GROQ_API_KEY")}\"")
        buildConfigField("String", "GROQ_MODEL", "\"${prop("GROQ_MODEL", "openai/gpt-oss-120b")}\"")
        buildConfigField("String", "GROQ_VISION_MODEL", "\"${prop("GROQ_VISION_MODEL", "qwen/qwen3.8-27b")}\"")
    }
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
