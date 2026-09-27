import java.util.Properties
plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp"); id("androidx.room") }
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun quoted(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android {
    namespace = "com.ekbotix.ekpayparser"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ekbotix.ekpayparser"
        minSdk = 28; targetSdk = 36; versionCode = 1; versionName = "0.1.0-sandbox"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "TEST_ONLY", "true")
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".sandbox"
            buildConfigField("String", "TEST_BASE_URL", quoted(local.getProperty("ekpay.testBaseUrl", "")))
            buildConfigField("boolean", "SANDBOX_NETWORKING", (local.getProperty("ekpay.sandboxNetworking") == "true").toString())
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "TEST_BASE_URL", "\"\"")
            buildConfigField("boolean", "SANDBOX_NETWORKING", "false")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF") }
}
kotlin { jvmToolchain(17) }
room { schemaDirectory("$projectDir/schemas") }
dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.work:work-testing:2.11.0")
}
