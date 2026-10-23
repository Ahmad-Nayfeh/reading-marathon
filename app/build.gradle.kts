import java.security.KeyStore
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.roborazzi)
}

// Firebase (shared marathons): the real google-services.json is never committed. CI decodes it from
// the MARATHON_GOOGLE_SERVICES_BASE64 secret; local builds fall back to the placeholder, which builds
// fine and makes the app hide the "shared marathon" option.
val firebaseConfigured: Boolean = run {
    val real = file("google-services.json")
    if (!real.exists()) real.writeText(file("google-services.placeholder.json").readText())
    !real.readText().contains("\"project_id\": \"placeholder\"")
}

// Release signing: CI decodes the keystore from secrets into keystore.properties + a .jks file.
// Without them the release APK is signed with the debug key so it is still installable.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true
val releaseKeyPassword: String? = if (!hasReleaseKeystore) null else run {
    val storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
    val storePassword = keystoreProps.getProperty("storePassword") ?: ""
    val alias = keystoreProps.getProperty("keyAlias") ?: ""
    listOf(keystoreProps.getProperty("keyPassword") ?: "", storePassword).firstOrNull { candidate ->
        runCatching {
            val ks = KeyStore.getInstance("PKCS12")
            storeFile.inputStream().use { ks.load(it, storePassword.toCharArray()) }
            ks.getKey(alias, candidate.toCharArray()) != null
        }.getOrDefault(false)
    } ?: keystoreProps.getProperty("keyPassword")
}

android {
    namespace = "com.readingmarathon.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.readingmarathon.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (findProperty("marathon.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("marathon.versionName") as String?) ?: "1.0.0"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
