plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.senpaiscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.senpaiscanner"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    configurations.all {
        resolutionStrategy {
            // Force newer versions of legacy support libs to avoid conflicts
            force("androidx.legacy:legacy-support-core-utils:1.0.0")
            force("androidx.documentfile:documentfile:1.1.0")
            force("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
            force("androidx.print:print:1.1.0")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
}
