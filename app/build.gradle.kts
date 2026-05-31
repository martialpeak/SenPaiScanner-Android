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
        versionCode = 2
        versionName = "2.0.0"
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
}

configurations.all {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.legacy" -> useVersion("1.0.0")
            "androidx.documentfile" -> useVersion("1.1.0")
            "androidx.localbroadcastmanager" -> useVersion("1.1.0")
            "androidx.print" -> useVersion("1.1.0")
            "androidx.transition" -> useVersion("1.5.0")
            "androidx.viewpager2" -> useVersion("1.1.0")
            "androidx.dynamicanimation" -> useVersion("1.0.0")
            "androidx.recyclerview" -> useVersion("1.3.2")
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
    implementation(libs.activity.ktx)
}
