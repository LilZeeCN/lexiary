import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 密钥只从本机 local.properties（deepseek.apiKey=...）或环境变量 DEEPSEEK_API_KEY 读取，
// 两者都不入库；缺省留空，应用内会提示未配置，见 README「配置 API Key」。
val secrets = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val deepseekKey: String =
    secrets.getProperty("deepseek.apiKey")
        ?: System.getenv("DEEPSEEK_API_KEY")
        ?: ""

android {
    namespace = "com.lilzee.zee"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lilzee.zee"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.02"
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepseekKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
}
