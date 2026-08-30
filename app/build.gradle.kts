import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gamepadbuddy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gamepadbuddy"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        val hasKeystore = rootProject.file("keystore.properties").exists()
        create("release") {
            if (hasKeystore) {
                val props = Properties().apply {
                    load(rootProject.file("keystore.properties").inputStream())
                }
                storeFile = file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (rootProject.file("keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        viewBinding = true
    }

    aaptOptions {
        // Giữ nguyên (không nén) binary daemon để chép ra filesDir rồi chmod +x.
        noCompress("mantisbuddy-arm64", "mantisbuddy-armv7")
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // File 04 Hướng A: tự động pairing/connect ADB không dây trong app (Kotlin Multiplatform, minSdk 23).
    implementation("com.flyfishxu:kadb:2.1.3")
    // TLS 1.3 cho pairing trên Android 8 (API 26); Android 9+ dùng platform provider.
    implementation("org.conscrypt:conscrypt-android:2.6.3")

    // File 09: unit test (JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.json:json:20240303") // cung cấp org.json trên JVM (khớp Android)
}
