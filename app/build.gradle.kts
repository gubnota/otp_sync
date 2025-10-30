import java.io.FileInputStream
import java.util.Properties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

val appPropertiesFile = rootProject.file(".env")
val appProperties = Properties().apply {
    load(FileInputStream(appPropertiesFile))
}

android {
    namespace = "net.fenki.otp_sync"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.fenki.otp_sync"
        minSdk = 21
        targetSdk = 35
        versionCode = appProperties.getProperty("version.code").toInt()
        versionName = appProperties.getProperty("version.name")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        all {
            buildConfigField("String", "secret", appProperties.getProperty("secret"))
            buildConfigField("String", "ids", appProperties.getProperty("ids"))
            buildConfigField("String", "backend_url", appProperties.getProperty("backend_url"))
        }
    }
    val buildTime = SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date())
    applicationVariants.all{
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val projName = rootProject.name
            val version = android.defaultConfig.versionName
            val suffix = when (name){
                "debug"-> "-debug"
                else -> ""
            }
            outputImpl.outputFileName = "${projName}-${version}-${buildTime}${suffix}.apk"
        }
    }
        compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
        buildConfig = true
    }
    signingConfigs {
        create("release") {
            storeFile = file(appProperties.getProperty("RELEASE_STORE_FILE"))
            storePassword = appProperties.getProperty(("RELEASE_STORE_PASSWORD"))
            keyAlias = appProperties.getProperty(("RELEASE_KEY_ALIAS"))
            keyPassword = appProperties.getProperty(("RELEASE_KEY_PASSWORD"))
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.core)
    testImplementation("org.robolectric:robolectric:4.12.1")
    implementation(libs.okhttp)
    implementation ("androidx.datastore:datastore-preferences:1.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("androidx.compose.foundation:foundation:1.5.1")
    // implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
}