plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.jrweb.musicplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.jrweb.musicplayer"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
    }

    signingConfigs {
        create("release") {
            val ks = file("jrweb-release.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "jrweb123456"
                keyAlias = "jrwebkey"
                keyPassword = "jrweb123456"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media:media:1.7.0")
}