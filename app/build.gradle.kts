import java.util.Properties
import java.io.FileInputStream

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    FileInputStream(localPropsFile).use { localProps.load(it) }
}
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    namespace = "com.example.comfyapp"
    compileSdk = 36

    defaultConfig {

        applicationId = "com.example.comfyapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Credenciales desde local.properties
        buildConfigField("String", "ODOO_BASE_URL", "\"${localProps.getProperty("ODOO_BASE_URL")}\"")
        buildConfigField("String", "ODOO_DB",       "\"${localProps.getProperty("ODOO_DB")}\"")
        buildConfigField("int",    "ODOO_UID",      localProps.getProperty("ODOO_UID"))
        buildConfigField("String", "ODOO_API_KEY",  "\"${localProps.getProperty("ODOO_API_KEY")}\"")
        buildConfigField("String", "AZURE_SPEECH_KEY",    "\"${localProps.getProperty("AZURE_SPEECH_KEY", "")}\"")
        buildConfigField("String", "AZURE_SPEECH_REGION", "\"${localProps.getProperty("AZURE_SPEECH_REGION", "")}\"")
        buildConfigField("String", "AZURE_SPEECH_VOICE",  "\"${localProps.getProperty("AZURE_SPEECH_VOICE", "")}\"")
        buildConfigField("String", "GEMINI_API_KEY",      "\"${localProps.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "GEMINI_MODEL",        "\"${localProps.getProperty("GEMINI_MODEL", "")}\"")

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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.robotemi:sdk:1.136.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation(libs.junit)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.android.material:material:1.12.0")
    // MQTT Eclipse Paho
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    // Azure Speech SDK
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.43.0@aar") { isTransitive = true }

}
