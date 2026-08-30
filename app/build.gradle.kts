plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.osabra.superaijarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.osabra.superaijarvis"
        minSdk = 24
        targetSdk = 34
        versionCode = 16
        versionName = "16.0-ALEXA"

        val geminiKey = project.findProperty("GEMINI_API_KEY") as String? ?: System.getenv("GEMINI_API_KEY") ?: ""
        val amazonClientId = project.findProperty("AMAZON_CLIENT_ID") as String? ?: System.getenv("AMAZON_CLIENT_ID") ?: ""
        val amazonProductId = project.findProperty("AMAZON_PRODUCT_ID") as String? ?: "Jarvis"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        buildConfigField("String", "AMAZON_CLIENT_ID", "\"$amazonClientId\"")
        buildConfigField("String", "AMAZON_PRODUCT_ID", "\"$amazonProductId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
