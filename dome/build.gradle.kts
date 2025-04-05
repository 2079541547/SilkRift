plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "eternal.future.silkrift.dome"
    compileSdk = 35

    defaultConfig {
        applicationId = "eternal.future.silkrift.dome"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
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

    signingConfigs {

        getByName("debug") {
            storeFile = file("test.jks")
            keyAlias = "test"
            storePassword = "123456"
            keyPassword = "123456"
        }

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":SilkRift"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.android.espresso.core)
}