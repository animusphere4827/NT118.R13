plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.nt118_project"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.nt118_project"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "TV3_MOCK_LEARNING_PATH", "true")
        }
        release {
            optimization {
                enable = false
            }
            buildConfigField("boolean", "TV3_MOCK_LEARNING_PATH", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
